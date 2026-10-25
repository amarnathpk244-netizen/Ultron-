#!/usr/bin/env python3
"""CI-only dependency/model resolution. Android users never run this script."""
from pathlib import Path
import hashlib, json, subprocess, tarfile, tempfile, urllib.request

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/"app/src/main/assets"
def get(url):
    request=urllib.request.Request(url,headers={"User-Agent":"ULTRON-build/0.1"})
    with urllib.request.urlopen(request,timeout=120) as r:
        return r.read()
def sha(data): return hashlib.sha256(data).hexdigest()
def metadata(url): return json.loads(get(url))
def run(*args): return subprocess.check_output(args,text=True).strip()
def native_sources():
    lockpath=ROOT/"dependencies.lock.json"
    lock=json.loads(lockpath.read_text()) if lockpath.exists() else {}
    for name,repo,tag in [
        ("llama","https://github.com/ggml-org/llama.cpp.git","b6500"),
        ("sherpa","https://github.com/k2-fsa/sherpa-onnx.git","v1.12.14"),
    ]:
        dest=ROOT/"vendor"/name
        revision=lock.get(name,{}).get("commit",tag)
        if not dest.exists():
            dest.mkdir(parents=True)
            run("git","-C",str(dest),"init")
            run("git","-C",str(dest),"remote","add","origin",repo)
        has_head=subprocess.run(["git","-C",str(dest),"rev-parse","--verify","HEAD"],
            stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0
        if not has_head:
            run("git","-C",str(dest),"fetch","--depth=1","origin",revision)
            run("git","-C",str(dest),"checkout","--detach","FETCH_HEAD")
        commit=run("git","-C",str(dest),"rev-parse","HEAD")
        if name in lock and commit != lock[name]["commit"]:
            raise RuntimeError("Native source lock mismatch: "+name)
        lock[name]={"repo":repo,"tag":tag,"commit":commit}
    lockpath.write_text(json.dumps(lock,indent=2)+"\n")

def hf_entries(repo,paths,group,old):
    existing=[x for x in old if x["id"]==group]
    revision=existing[0]["revision"] if existing else "main"
    data=metadata("https://huggingface.co/api/models/"+repo+"/revision/"+revision+"?blobs=true")
    commit=data["sha"]
    found={s["rfilename"]:s for s in data["siblings"]}
    result=[]
    for source,dest in paths:
        actual=next((x for x in found if x.lower()==source.lower()),None)
        if actual is None: raise RuntimeError("Model file not found: "+repo+"/"+source)
        entry=found[actual]
        url="https://huggingface.co/"+repo+"/resolve/"+commit+"/"+actual
        lfs=entry.get("lfs") or {}
        checksum=lfs.get("sha256")
        size=lfs.get("size") or entry.get("size")
        if not checksum:
            content=get(url);checksum=sha(content);size=len(content)
        result.append(dict(id=group,path=dest,url=url,sha256=checksum,size=size,revision=commit))
    return result

def safe_member(archive,name):
    candidates=[m for m in archive.getmembers() if m.name.endswith("/"+name) and m.isfile()]
    if len(candidates)!=1:raise RuntimeError("Missing or ambiguous wake file: "+name)
    return archive.extractfile(candidates[0]).read()

def wake_entries(old):
    import sentencepiece as spm
    name="sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2"
    url="https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/"+name
    content=get(url)
    archive_sha=sha(content)
    recorded=next((x.get("archive_sha256") for x in old if x["id"]=="wake"),None)
    if recorded and recorded!=archive_sha:raise RuntimeError("Wake archive changed since locked build")
    (ROOT/"wake-release.lock.json").write_text(json.dumps({"url":url,"sha256":archive_sha},indent=2)+"\n")
    result=[]
    with tempfile.TemporaryDirectory() as temp:
        tarpath=Path(temp)/"wake.tar.bz2";tarpath.write_bytes(content)
        with tarfile.open(tarpath,"r:bz2") as archive:
            base="-epoch-12-avg-2-chunk-16-left-64"
            for source,dest in [("encoder"+base+".int8.onnx","encoder.onnx"),
                                ("decoder"+base+".onnx","decoder.onnx"),
                                ("joiner"+base+".int8.onnx","joiner.onnx"),
                                ("tokens.txt","tokens.txt")]:
                payload=safe_member(archive,source)
                out=ASSETS/"wake"/dest;out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(payload)
                result.append(dict(id="wake",path="wake/"+dest,url="asset:///wake/"+dest,sha256=sha(payload),
                                   size=len(payload),archive_sha256=archive_sha))
            bpe=Path(temp)/"bpe.model";bpe.write_bytes(safe_member(archive,"bpe.model"))
            tokenizer=spm.SentencePieceProcessor(model_file=str(bpe))
            pieces=tokenizer.encode("HEY ULTRON",out_type=str)
            if "<unk>" in pieces:raise RuntimeError("Wake phrase contains unknown tokens")
            tokens={line.rsplit(" ",1)[0] for line in (ASSETS/"wake/tokens.txt").read_text().splitlines()}
            if not all(piece in tokens for piece in pieces):raise RuntimeError("Wake token mismatch")
            (ASSETS/"keywords.txt").write_text(" ".join(pieces)+" @HEY_ULTRON\n")
    return result

def main():
    ASSETS.mkdir(parents=True,exist_ok=True)
    native_sources()
    lockfile=ASSETS/"models.lock.json"
    old=json.loads(lockfile.read_text()) if lockfile.exists() else []
    entries=hf_entries("jeswinjestin/sherpa-onnx-nemo-ctc-indicconformer-malayalam",
        [("model.onnx","asr/model.onnx"),("tokens.txt","asr/tokens.txt")],"asr",old)
    entries+=hf_entries("Qwen/Qwen2.5-1.5B-Instruct-GGUF",
        [("qwen2.5-1.5b-instruct-q4_k_m.gguf","brain/qwen.gguf")],"brain",old)
    entries+=wake_entries(old)
    lockfile.write_text(json.dumps(entries,indent=2)+"\n")
    print("Native commits and model hashes locked. Wake assets bundled. ASR and LLM download in-app.")
if __name__=="__main__":main()
