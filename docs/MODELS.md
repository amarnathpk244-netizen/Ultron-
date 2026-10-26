# Model and runtime sources

## Malayalam

- Community model: https://huggingface.co/jeswinjestin/sherpa-onnx-nemo-ctc-indicconformer-malayalam
- Original: https://github.com/AI4Bharat/IndicConformerASR
- Model card states CC-BY-4.0. Attribute AI4Bharat/NVIDIA, the ONNX converters credited by the card, and jeswinjestin for Sherpa packaging. No weights are in this source archive.
- Published files are model.onnx and tokens.txt. CI resolves sizes and SHA-256; APK downloads on demand.
- FP32 is identified as FP32. No unverified INT8 URL is supplied.
- Audio: mono float PCM [-1,1], 16,000 Hz, max 15 seconds per command.
- Accuracy, confidence calibration and mixed English vocabulary require measurement.

## Brain

- https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF
- Apache-2.0 according to the official model card.
- CI selects qwen2.5-1.5b-instruct-q4_k_m.gguf, immutable HF revision URL and signed-in-APK SHA-256.
- llama.cpp: https://github.com/ggml-org/llama.cpp/tree/b6500 (MIT).
- CPU-only, 4 threads, mmap, model/context released after every response. Reloading reduces idle RAM but can increase latency. GPU/NPU acceleration is not implemented.
- No measured claim that 1.5B is the best Malayalam model.

## Wake

- Sherpa runtime: https://github.com/k2-fsa/sherpa-onnx/tree/v1.12.14 (Apache-2.0).
- Model docs: https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
- Archive: https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2
- CI extracts model/tokens by explicit basename; archive paths/symlinks are not blindly extracted.
- Same-release BPE produces HEY ULTRON tokens, checked against vocabulary.
- APK bundles wake assets and hashes. Wake-pack installation needs no phone internet.
- Model/data notices must be reviewed for production redistribution. Runtime license alone does not establish every model/data license.

## Download trust

First CI preparation trusts dependency tags, HF main revisions and wake bytes from upstream over HTTPS, then records immutable commits/digests. HF LFS SHA-256 comes from repository metadata; builder hashes small text files. Later preparations reuse the records.

Android trusts its signed-in-APK registry, not remote checksums. Downloads are size-bounded, hashed, staged in private storage and installed after matching. Hashes detect corruption/replacement against the registry, not model quality or compromised upstream at first resolution.

No model bytes/hashes were resolved successfully here. The registry is empty until CI succeeds.

## TTS and recognizer limits

Android TTS is used only if the installed voice reports no network requirement. Text remains available if no Malayalam offline voice exists. Gender, naturalness and offline pack availability are not guaranteed.

IndicConformer is the only command ASR integrated. English-only ASR, Whisper.cpp comparisons, calibrated speech confidence, neural VAD and a dedicated deep male Malayalam TTS model are not delivered.
