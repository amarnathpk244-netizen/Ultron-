# Troubleshooting

## APK missing
Build was blocked. Source ZIP is not installable and must not be renamed APK. A successful remote workflow is still needed.

## Build fails
Inspect the first failed Actions step. Model metadata must resolve successfully; no cloud fallback is used. Combined llama/Sherpa/ONNX Android compilation is unverified. Preserve generated lock files. If ELF/ZIP checks fail, rebuild/update the offending library with 16KB support; do not remove the check.

## Android rejects update
Different CI runners may generate different debug signing keys. Stable release signing is absent. Uninstall loses memory; arrange a reviewed persistent signing setup before updates.

## No speech
Grant mic permission; turn on Android's global mic switch. Close other recording apps, wait for the beep and try a quiet room. Energy VAD is basic and uncalibrated for this user's voice. Retry manually.

## Wrong transcription
Cancel rather than confirm. Retry or type. Malayalam-only ASR may misrecognize English words. There is no calibrated ASR confidence. Confirmation mitigates unintended execution but does not fix recognition.

## No voice response
Install a Malayalam offline TTS voice if available, then test in airplane mode. Network-required voices are rejected. Text still works. Low pitch does not guarantee a natural male voice.

## Background listening stops
Start while ULTRON is visible. Enable notifications. Review Funtouch's app battery/background options; wording varies by firmware. Keep-awake mode is capped at two hours and costs battery. No survival guarantee; restart after reboot/force-stop.

## Wake heard, command missed
Say Hey Ultron, pause for the beep, then speak. Wake+command in one breath is unsupported. Accent/threshold testing is still required.

## Background app launch rejected
Android restricts it. Open ULTRON from its listening notification and confirm. No assistant-role privilege or bypass is implemented.

## Accessibility fails
Enable Android Accessibility plus ULTRON's separate screen-automation toggle. Keep the target app visible/unlocked; ask by wake word and voice-confirm within 60 seconds. Confirming inside ULTRON changes the active window.

Only exact unique visible clickable labels and focused non-password text fields are supported. Consequential labels, System UI, secure/unlabeled/canvas elements may be unavailable. No coordinate fallback.

## Request accepted but action incomplete
An intent dispatched or accessibility request accepted is not proof of saved alarm, placed call, sent message or loaded website. Review/complete in the relevant app.

## Deployment checks unavailable
No GitHub/Vercel/Supabase integration/auth exists. Confirmed URL opening uses a browser/network, not a verified deployment status tool.
