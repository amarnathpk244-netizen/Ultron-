# PROJECT ULTRON — native Android source, development 0.1

**No APK has been generated for this delivery. This is uncompiled development source, not a finished or production-tested assistant. None of Phases 1–5 can be signed off as complete.**

The authoring environment has Java 17 but no Android SDK, Gradle, Kotlin compiler or NDK. Downloads from Google, Gradle and GitHub were rejected with HTTP 403. Local source preparation failed fetching llama.cpp. GitHub was listed as installed by plugin discovery, but no GitHub repository/Actions tool or authenticated CLI was exposed. Consequently the workflow was prepared but not submitted or run remotely. No repository was created or changed.

## Deliverable

Kotlin + Jetpack Compose application, ARM64-only JNI runtime, Android 16/API 36 target, min API 31. Source includes microphone foreground service, VAD recording, Sherpa Malayalam ASR and wake-word bindings, llama.cpp local inference, a restricted tool router, accessibility service, encrypted local memory, model manager, ten UI sections and GitHub Actions build configuration.

These are source implementations. Successful Android compilation, model compatibility and device behavior still require verification. See [status](docs/STATUS.md) and [test results](docs/TEST_RESULTS.md).

## Build without a PC or phone compilation

GitHub Actions can compile on remote runners, controlled through a phone browser. This workflow is included, not yet executed. Source must first be placed in a GitHub repository through an authorized repository connection; this delivery does not have that capability.

Once source is in a repository, open **Actions → Build ULTRON Android → Run workflow**. The job installs the toolchain, fetches pinned-tag engine sources, resolves model files to immutable commits, records SHA-256 digests, bundles the small wake model, compiles, lints, tests the Java policy, and verifies APK signature, ZIP alignment and ELF alignment.

Only a successful job uploads **ULTRON-arm64-development**, containing:

- app-debug.apk — development-signed installable APK.
- SHA256SUMS.
- Exact source ZIP including generated dependency/model locks and Gradle wrapper.
- Build/lint/test reports.

A failed job produces no verified APK. This source has not passed that job. No download URL or Actions run URL exists yet.

Public repository runners are the intended free build route; account quotas/eligibility apply. Do not enable paid overage. There is no paid service, API or cloud inference inside the Android application.

## Models selected for initial integration

| Component | Initial choice | Reason and limits |
|---|---|---|
| Malayalam ASR | Community Sherpa-ready IndicConformer NeMo CTC, FP32 | Real model/tokens are published; no language auto-detection to drift into Russian. ~493 MB upstream model. INT8 conversion and accuracy are not verified here. |
| Wake word | Sherpa Zipformer GigaSpeech 3.3M, INT8 encoder/joiner | Small streaming keyword engine; HEY ULTRON tokenized with that model's BPE during build. Accent/false-trigger performance is unmeasured. |
| Brain | Official Qwen2.5 1.5B Instruct Q4_K_M GGUF | Apache-2.0, small quantized baseline. CPU inference, 2,048-token context, at most 256 output tokens. Malayalam/tool-planning quality is unmeasured. |
| Speech output | Installed Android TTS offline voice | Network-required voices are rejected. No guaranteed male Malayalam voice is bundled. Pitch can be adjusted. |

This is a provisional selection based on published compatibility, not a measured model comparison. English/Manglish text is accepted. Dedicated English speech recognition, robust mixed-language ASR and accent handling have **not** been implemented or validated. Whisper is not silently used as a fallback.

The user-supplied iQOO Z9s Pro / Snapdragon 7 Gen 3 / 8 GB / Android 16 specification is the target; no physical device was connected and its firmware/ABI has not been queried.

See [model sources](docs/MODELS.md) for downloads, first-resolution trust and licenses.

## First installation after a successful build

1. Download the successful Actions artifact, unzip it and tap app-debug.apk.
2. If Android asks, allow installation from that download/file app for this installation.
3. Open ULTRON. Open **AI models**, download ASR and brain, and install the bundled wake pack using its Download button.
4. Open **Permissions**. Grant microphone and notifications. Grant camera only for flashlight; accessibility only for screen control.
5. Open **Voice settings → Install offline voices**. Install a Malayalam offline voice if your engine provides one. Test with internet off.
6. Return to Assistant. Test push-to-talk first. Review transcription and proposed action.
7. Enable **Hey Ultron** while the app is visible. Say the phrase, pause for the beep, then say the command.
8. For screen control, additionally enable **Automation controls → Allow reviewed screen actions**.

No Termux, terminal, account inside the app, model path editing or on-phone build is required. Android's permission dialogs and TTS voice installation remain user-controlled.

The debug signing identity is not a production release key. Different runners can generate different debug keys, preventing updates over a previous install. A stable protected release signing configuration is future work. Uninstalling loses memory. Do not treat this delivery as a release.

## Practical behavior

Battery/time run as read-only tools. Device-changing plans expire after 60 seconds and require confirmation. Most Android activity launches require ULTRON visible; the service never bypasses background launch restrictions. Navigation and approved screen actions can be voice-confirmed from an external app.

Calls open a dialer; SMS/WhatsApp open composers. Nothing automatically places a call or sends a message. Alarm/timer/calendar tools open the relevant system UI; they do not claim a saved alarm/event without observing it.

Only exact visible, unique, enabled clickable labels can be tapped. Consequential labels such as Send, Pay, Delete, Confirm and their listed Malayalam forms are rejected. Password fields and System UI are excluded. This is a conservative guard, not a semantic safety guarantee for all languages/apps.

## Privacy

Audio is transient PCM in RAM, not written or uploaded. No network inference or telemetry SDK exists. Internet is used by the model downloader only after a button press; user-requested browser/composer actions may use their own apps' networks. No API keys or account tokens are requested.

Memory/optional history use a bounded AES-256-GCM vault with an Android Keystore key and atomic writes. Room was not used: this implementation stores a small document (3,000-character approved memory, up to 40 history entries) rather than an encrypted SQL database. Backup is disabled. Keystore security level depends on the device.

Permissions and continuous listening are not silently reenabled after reboot. Automation/history toggles are off on process restart. Audio, screen content and communication action results do not enter persistent history. Saved replies and approved memory are user-deletable.

## Project structure

- app/src/main/java/org/ultron/assistant/: UI, service, tools, memory, downloader, controller.
- app/src/main/cpp/: native Sherpa/llama bridge and CMake build.
- .github/workflows/android.yml: proposed remote APK build.
- scripts/prepare_build.py: source/model resolution; no model conversion.
- scripts/check_project.py: executable host policy/config tests.
- scripts/check_elf.py: packaged native-library alignment check.
- scripts/evaluate_asr.py: recording-corpus evaluator, not run here.
- docs/: status, test evidence, troubleshooting, limitations.

## Developer reproduction

JDK 17, Gradle 8.13, AGP 8.10.1, Kotlin/Compose plugin 2.1.21, SDK 36, NDK 28.1.13356709, CMake 3.22.1. The workflow generates the Gradle wrapper; no wrapper JAR is in this original source archive because it could not be downloaded here.

Native tags: llama.cpp b6500 and Sherpa-ONNX v1.12.14. First successful preparation outputs full commit hashes; retaining dependencies.lock.json, wake-release.lock.json and models.lock.json enables subsequent locked preparations. This archive has an empty model registry rather than invented hashes. CI must generate it before packaging.

Gradle transitive dependencies, Actions major-version references and upstream native transitive downloads are not fully hermetically pinned. A complete reproducible production build and SBOM remain open work.
