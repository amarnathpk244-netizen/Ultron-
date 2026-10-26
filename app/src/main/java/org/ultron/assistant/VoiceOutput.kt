package org.ultron.assistant
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class VoiceOutput(context: Context) {
 private val ready=CompletableDeferred<Boolean>()
 private var current: CompletableDeferred<Unit>?=null
 @Volatile var mutedUntil=0L;private set
 private val engine=TextToSpeech(context) { ready.complete(it==TextToSpeech.SUCCESS) }
 @Volatile var speaking=false;private set
 var pitch=0.85f
 var rate=0.95f
 init {
  engine.setOnUtteranceProgressListener(object: UtteranceProgressListener(){
   override fun onStart(id: String?) { speaking=true }
   override fun onDone(id: String?) { speaking=false;mutedUntil=android.os.SystemClock.elapsedRealtime()+700;current?.complete(Unit) }
   @Deprecated("TTS API") override fun onError(id: String?) { speaking=false;current?.completeExceptionally(IllegalStateException("TTS synthesis failed")) }
  })
 }
 suspend fun speak(text: String): String? = withContext(Dispatchers.Main) {
  if(withTimeoutOrNull(10_000){ready.await()}!=true)return@withContext "Offline voice engine ലഭ്യമല്ല."
  val language=if(text.any{it in '\u0D00'..'\u0D7F'}) "ml" else "en"
  val choices=engine.voices?.filter{!it.isNetworkConnectionRequired && it.locale.language==language}.orEmpty()
  val selected=choices.maxByOrNull{it.quality}
   ?: return@withContext "Offline "+language+" voice install ചെയ്യണം. മറുപടി screen-ൽ ലഭ്യമാണ്."
  engine.voice=selected;engine.setPitch(pitch);engine.setSpeechRate(rate)
  speaking=true
  val done=CompletableDeferred<Unit>();current=done
  try {
   if(engine.speak(text.take(3500),TextToSpeech.QUEUE_FLUSH,null,"ultron")!=TextToSpeech.SUCCESS)
    return@withContext "Voice output ആരംഭിക്കാൻ കഴിഞ്ഞില്ല."
   if(withTimeoutOrNull(60_000){done.await()}==null)return@withContext "Voice output timed out."
   null
  } finally { engine.stop();speaking=false;mutedUntil=android.os.SystemClock.elapsedRealtime()+700;current=null }
 }
 fun stop() { engine.stop();speaking=false;mutedUntil=android.os.SystemClock.elapsedRealtime()+700;current?.complete(Unit) }
 fun close() { stop();engine.shutdown() }
}
