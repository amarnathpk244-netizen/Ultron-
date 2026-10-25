#include <cstring>
#include <algorithm>
#include <jni.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <stdexcept>
#include "llama.h"
#include "sherpa-onnx/c-api/c-api.h"

namespace {
std::mutex inferenceMutex;
std::atomic_bool cancelled{false};
struct Utf {
 JNIEnv* env; jstring value; const char* data;
 Utf(JNIEnv* e, jstring s):env(e),value(s),data(e->GetStringUTFChars(s,nullptr)) {}
 ~Utf(){ if(data) env->ReleaseStringUTFChars(value,data); }
};
void fail(JNIEnv* e, const char* msg) {
 e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),msg);
}
struct Wake {
 const SherpaOnnxKeywordSpotter* spotter = nullptr;
 const SherpaOnnxOnlineStream* stream = nullptr;
 ~Wake() {
  if(stream) SherpaOnnxDestroyOnlineStream(stream);
  if(spotter) SherpaOnnxDestroyKeywordSpotter(spotter);
 }
};
jbyteArray bytes(JNIEnv* e,const std::string& s) {
 auto result=e->NewByteArray(static_cast<jsize>(s.size()));
 if(result) e->SetByteArrayRegion(result,0,s.size(),reinterpret_cast<const jbyte*>(s.data()));
 return result;
}
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_ultron_assistant_NativeEngine_transcribe(JNIEnv* e,jobject,jstring model,jstring tokens,jfloatArray pcm) {
 std::lock_guard<std::mutex> lock(inferenceMutex);
 Utf m(e,model),t(e,tokens);
 const int n=e->GetArrayLength(pcm);
 if(n<1600 || n>16000*20) { fail(e,"Invalid audio length"); return nullptr; }
 std::vector<float> audio(n); e->GetFloatArrayRegion(pcm,0,n,audio.data());
 SherpaOnnxOfflineRecognizerConfig cfg{};
 cfg.feat_config.sample_rate=16000; cfg.feat_config.feature_dim=80;
 cfg.model_config.nemo_ctc.model=m.data;
 cfg.model_config.tokens=t.data; cfg.model_config.num_threads=2;
 cfg.model_config.provider="cpu"; cfg.decoding_method="greedy_search";
 auto r=SherpaOnnxCreateOfflineRecognizer(&cfg);
 if(!r){fail(e,"Malayalam ASR model could not load");return nullptr;}
 auto s=SherpaOnnxCreateOfflineStream(r);
 if(!s){SherpaOnnxDestroyOfflineRecognizer(r);fail(e,"ASR stream could not load");return nullptr;}
 SherpaOnnxAcceptWaveformOffline(s,16000,audio.data(),n);
 SherpaOnnxDecodeOfflineStream(r,s);
 auto result=SherpaOnnxGetOfflineStreamResult(s);
 std::string text=result && result->text ? result->text : "";
 if(result) SherpaOnnxDestroyOfflineRecognizerResult(result);
 SherpaOnnxDestroyOfflineStream(s); SherpaOnnxDestroyOfflineRecognizer(r);
 // JNI modified UTF-8 agrees with Malayalam BMP output. LLM uses bytes for arbitrary UTF-8.
 return e->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_ultron_assistant_NativeEngine_generate(JNIEnv* e,jobject,jstring path,jstring prompt,jint limit) {
 std::lock_guard<std::mutex> lock(inferenceMutex);
 cancelled=false;
 static std::once_flag init;
 std::call_once(init,[]{llama_backend_init();});
 Utf file(e,path),input(e,prompt);
 auto mp=llama_model_default_params(); mp.n_gpu_layers=0; mp.use_mmap=true;
 std::unique_ptr<llama_model,decltype(&llama_model_free)> model(llama_model_load_from_file(file.data,mp),llama_model_free);
 if(!model){fail(e,"GGUF model could not load");return nullptr;}
 auto cp=llama_context_default_params();
 cp.n_ctx=2048; cp.n_batch=256; cp.n_ubatch=128; cp.n_threads=4; cp.n_threads_batch=4;
 cp.abort_callback=[](void*){return cancelled.load();};
 std::unique_ptr<llama_context,decltype(&llama_free)> ctx(llama_init_from_model(model.get(),cp),llama_free);
 if(!ctx){fail(e,"Not enough memory for local AI");return nullptr;}
 auto vocab=llama_model_get_vocab(model.get());
 int count=llama_tokenize(vocab,input.data,static_cast<int>(strlen(input.data)),nullptr,0,true,true);
 if(count>=0){fail(e,"Tokenizer failed");return nullptr;}
 std::vector<llama_token> tok(-count);
 count=llama_tokenize(vocab,input.data,strlen(input.data),tok.data(),tok.size(),true,true);
 if(count<=0 || count>1600){fail(e,"Conversation too long; clear context");return nullptr;}
 for(int pos=0;pos<count;pos+=256){
  int n=std::min(256,count-pos);
  if(cancelled) return bytes(e,"");
  if(llama_decode(ctx.get(),llama_batch_get_one(tok.data()+pos,n))!=0){
   fail(e,"AI prompt evaluation failed");return nullptr;
  }
 }
 std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> sampler(llama_sampler_init_greedy(),llama_sampler_free);
 std::string output;
 for(int i=0;i<std::min(std::max(1,limit),256) && !cancelled;i++){
  auto token=llama_sampler_sample(sampler.get(),ctx.get(),-1);
  if(llama_vocab_is_eog(vocab,token)) break;
  std::vector<char> piece(256);
  int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,true);
  if(n<0){piece.resize(-n);n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,true);}
  if(n>0) output.append(piece.data(),n);
  if(llama_decode(ctx.get(),llama_batch_get_one(&token,1))!=0){
   if(cancelled) break;
   fail(e,"AI generation failed");return nullptr;
  }
 }
 return bytes(e,output);
}
extern "C" JNIEXPORT void JNICALL
Java_org_ultron_assistant_NativeEngine_cancelGeneration(JNIEnv*,jobject){cancelled=true;}

extern "C" JNIEXPORT jlong JNICALL
Java_org_ultron_assistant_NativeEngine_openWake(JNIEnv* e,jobject,jstring directory,jstring keywords){
 Utf dir(e,directory),kw(e,keywords);
 std::string root=dir.data;
 std::string encoder=root+"/encoder.onnx",decoder=root+"/decoder.onnx",joiner=root+"/joiner.onnx",tokens=root+"/tokens.txt";
 SherpaOnnxKeywordSpotterConfig cfg{};
 cfg.feat_config.sample_rate=16000;cfg.feat_config.feature_dim=80;
 cfg.model_config.transducer.encoder=encoder.c_str();
 cfg.model_config.transducer.decoder=decoder.c_str();
 cfg.model_config.transducer.joiner=joiner.c_str();
 cfg.model_config.tokens=tokens.c_str();cfg.model_config.num_threads=1;
 cfg.model_config.provider="cpu";cfg.max_active_paths=4;cfg.num_trailing_blanks=2;
 cfg.keywords_score=1.5f;cfg.keywords_threshold=0.35f;cfg.keywords_file=kw.data;
 auto w=std::make_unique<Wake>();
 w->spotter=SherpaOnnxCreateKeywordSpotter(&cfg);
 if(!w->spotter){fail(e,"Wake model could not load");return 0;}
 w->stream=SherpaOnnxCreateKeywordStream(w->spotter);
 if(!w->stream){fail(e,"Wake stream could not load");return 0;}
 return reinterpret_cast<jlong>(w.release());
}
extern "C" JNIEXPORT jboolean JNICALL
Java_org_ultron_assistant_NativeEngine_feedWake(JNIEnv* e,jobject,jlong handle,jfloatArray pcm){
 auto w=reinterpret_cast<Wake*>(handle); if(!w)return false;
 int n=e->GetArrayLength(pcm);
 if(n<=0 || n>16000) return false;
 std::vector<float> data(n);e->GetFloatArrayRegion(pcm,0,n,data.data());
 SherpaOnnxOnlineStreamAcceptWaveform(w->stream,16000,data.data(),n);
 bool detected=false;
 while(SherpaOnnxIsKeywordStreamReady(w->spotter,w->stream)){
  SherpaOnnxDecodeKeywordStream(w->spotter,w->stream);
  auto r=SherpaOnnxGetKeywordResult(w->spotter,w->stream);
  if(r && r->keyword && strlen(r->keyword)>0) detected=true;
  if(r) SherpaOnnxDestroyKeywordResult(r);
 }
 if(detected) SherpaOnnxResetKeywordStream(w->spotter,w->stream);
 return detected;
}
extern "C" JNIEXPORT void JNICALL
Java_org_ultron_assistant_NativeEngine_resetWake(JNIEnv*,jobject,jlong handle){
 auto w=reinterpret_cast<Wake*>(handle);
 if(w) SherpaOnnxResetKeywordStream(w->spotter,w->stream);
}
extern "C" JNIEXPORT void JNICALL
Java_org_ultron_assistant_NativeEngine_closeWake(JNIEnv*,jobject,jlong handle){delete reinterpret_cast<Wake*>(handle);}
