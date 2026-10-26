# Test results — 2026-10-25

## Executed

The host check script completed with exit code 0:

    PASS: 61 production ToolPolicy assertions
    PASS: XML parsing, Python syntax, JSON parsing, manifest security invariants
    NOT RUN: Kotlin/Compose compilation, Android lint, JNI/NDK linking, APK install, device/audio tests

Java tests compile/execute the production ToolPolicy class using the Java compiler module. Cases cover tool allowlists, read-only distinction, alarm/timer bounds, literal numbers/USSD rejection, HTTPS URLs, consequential labels, text limits and nulls.

Config checks parse XML/Python/JSON and assert microphone service declarations, backup disabled, cleartext disabled, no exported microphone service, no boot receiver, and no SEND_SMS/CALL_PHONE/broad-storage/package-enumeration permissions. These are narrow source checks, not a security audit.

## Attempted but blocked

- Java 17.0.20 available; no Android SDK/Gradle/NDK/Kotlin compiler found.
- Google Android toolchain and Gradle download probes: HTTP 403.
- Build preparation failed fetching llama.cpp b6500: git exit 128, HTTP 403, “Calls to this URL are not allowed.”
- GitHub discovery reported installed, but repository/Actions tools and an authenticated CLI were not exposed.
- No remote workflow run or APK. Failed temporary vendor checkout is excluded from the deliverable.

## Unverified

| Test | Result |
|---|---|
| Kotlin/Compose/Gradle, CMake/NDK linking, Android lint | Not run |
| APK install, signature, ZIP/ELF alignment | Not run |
| Real Malayalam/English/Manglish recordings | No audio executed |
| Wake/accents/own-TTS false activation | Not run |
| ASR confidence and engine comparison | Not run |
| Local AI, tool planning, offline Malayalam voice | Not run |
| App launching, accessibility, alarms, all other Android tools | Not run |
| Keystore, encrypted vault, corruption/recovery | Not run |
| Runtime model download/integrity/interruption | Not run |
| Permissions/revocation/lifecycle/background/offline | Not run |
| iQOO Z9s Pro / Android 16 / Funtouch | No physical device |
| Screen on/off, battery settings, low RAM | Not run |
| Real RAM/latency/battery benchmarks | No data |

## Required device acceptance

Record firmware, available RAM/storage, TTS engine and model-lock hashes. Repeat across screen on/off, foreground/background, restricted/unrestricted battery and airplane mode.

1. Deny/revoke permissions: no crash or hidden mic access.
2. Silence/noise, low-volume speech, 15-second command and interrupted recording.
3. Consented recordings from several Malayalam speakers: WER/CER and intent accuracy separately.
4. Wake misses and false activations/hour across accents, TV/music and ULTRON speech.
5. Installed model/voice packs in airplane mode: ASR/LLM/local tools/TTS; honest browser failures.
6. Each tool with success, missing app, denied permission, locked phone, ambiguous target and expired confirmation.
7. Stop during recording, inference, TTS and multi-step plans; no further steps after cancellation.
8. Force-stop/reboot: no silent mic restart.
9. Save/edit/delete memory and recreate process; corruption must fail without plaintext fallback.
10. Interrupted downloads, canceled downloads, low storage, corrupted same-size model file; reject bad digest.
11. Measure PSS, inference duration and fixed-workload battery drain. No performance claims before measurements.
12. Review signing, third-party notices and missing features before release.
