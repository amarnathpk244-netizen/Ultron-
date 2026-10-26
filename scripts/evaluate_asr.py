#!/usr/bin/env python3
"""Optional developer corpus evaluator. Not run for this delivery; needs real consented recordings."""
import argparse, json, statistics, time, wave
from pathlib import Path
def distance(a,b):
    row=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        fresh=[i]
        for j,y in enumerate(b,1):
            fresh.append(min(fresh[-1]+1,row[j]+1,row[j-1]+(x!=y)))
        row=fresh
    return row[-1]
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("corpus",help="JSON array of {wav, text}, 16kHz mono PCM16 recordings")
    parser.add_argument("--model",required=True);parser.add_argument("--tokens",required=True)
    args=parser.parse_args()
    import sherpa_onnx, numpy as np
    recognizer=sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
        model=args.model,tokens=args.tokens,num_threads=2,provider="cpu")
    corpus=json.loads(Path(args.corpus).read_text())
    assert corpus,"No recordings supplied"
    words=chars=word_errors=char_errors=0
    latency=[];out=[]
    for entry in corpus:
        with wave.open(entry["wav"],"rb") as wav:
            assert wav.getframerate()==16000 and wav.getnchannels()==1 and wav.getsampwidth()==2
            pcm=np.frombuffer(wav.readframes(wav.getnframes()),dtype="<i2").astype(np.float32)/32768
        stream=recognizer.create_stream();stream.accept_waveform(16000,pcm)
        start=time.perf_counter();recognizer.decode_stream(stream);seconds=time.perf_counter()-start
        ref=entry["text"].strip();hyp=stream.result.text.strip()
        words+=len(ref.split());chars+=len(ref)
        word_errors+=distance(ref.split(),hyp.split());char_errors+=distance(ref,hyp)
        latency.append(seconds);out.append({"wav":entry["wav"],"reference":ref,"hypothesis":hyp,"decode_seconds":seconds})
    print(json.dumps({"device":"host running this evaluator; not an Android benchmark",
        "utterances":len(out),"WER":word_errors/max(words,1),"CER":char_errors/max(chars,1),
        "median_decode_seconds":statistics.median(latency),"results":out},ensure_ascii=False,indent=2))
if __name__=="__main__":main()
