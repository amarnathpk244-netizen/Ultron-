# Implementation status — 2026-10-25

“Source present” means code exists, not that Android behavior passed tests. **No phase is complete; no APK exists.**

| Requirement | Current source | Missing acceptance work |
|---|---|---|
| Native app | Kotlin/Compose, 10 sections, API 36 ARM64 | Compilation, installation, UI/lifecycle QA |
| Microphone | AudioRecord, optional noise suppressor, energy VAD, pre-roll, timeout | Real recordings, noise/no-speech tests |
| Malayalam ASR | JNI Sherpa NeMo-CTC | Model download/load and corpus accuracy; INT8 |
| English/Manglish | Text input, a few deterministic Manglish commands | Dedicated English/mixed-language speech |
| Local AI | llama.cpp Qwen Q4, context, JSON planner | Actual inference and model comparison |
| Voice response | Offline Android TTS selection, pitch/speed, Stop | Voice availability/quality; spoken barge-in |
| Wake | Streaming Sherpa KWS, BPE phrase, foreground service | Real wake audio, accents, false triggers/hour |
| Screen off | Optional bounded partial wake lock | Funtouch battery/process survival |
| Phone utilities | Launchers, intents, navigation | Device test of each action |
| Screen control | Opt-in accessibility, bounded tree, exact label tap/text | Cross-app tests, stronger target binding |
| Communications | Dialer and draft composers after confirmation | Name-based contacts, direct confirmed call/send |
| Alarm/calendar | User-visible intents | Saved-result verification; absolute date semantics |
| Notifications/reminders | Listening notification | Notification listener and reminder scheduler |
| Multi-step | 1–3 reviewed tools, stop on failure | Browser retrieval/adaptive workflows |
| Memory | Approved edit/delete AES-GCM bounded vault | Keystore/corruption instrumented tests; Room absent |
| Developer projects | User-approved names/URLs in memory | GitHub/Vercel/Supabase integration and auth |
| Privacy | No analytics/upload APIs, no arbitrary code tools | Device validation and independent review |
| Models | Download/install/delete, integrity checks | Successful generated registry and real downloads |
| Build | Gradle and proposed Actions pipeline | Actual compile/lint/NDK, stable signing, APK |
| Tests | Production Java policy and config checks executed | All Android/JNI/audio/device tests |

## Limits

- Uncompiled development source does not meet the requested installation-ready production delivery.
- No measured RAM, latency, battery, wake accuracy, WER or CER.
- Mic service must start while the app is visible; restart manually after reboot/force-stop. OS mic indicator and foreground notification remain visible.
- Say “Hey Ultron”, pause for the beep, then the command. Same-breath wake+command is unsupported.
- Mic is gated while TTS plays. Spoken interruption is absent; use Stop.
- Accessibility may lack text or reject actions. No screenshot OCR, coordinate gestures or unlocking.
- Risky label filtering is conservative, not a semantic guarantee for all apps/languages. Screen actions still need explicit review and testing.
- Activity-launch tools require ULTRON visible; use notification to open it. Fully hands-free external app launching is absent.
- Calls/messages open dialer/composers. Alarm/timer/calendar dispatch does not prove a saved item.
- Alarm time does not encode a date such as “tomorrow.”
- JSON planner is not grammar-constrained. Unknown/invalid plans fail closed.
- No cloud account access or deployment status fetching. Opening a URL is never reported as reading its contents.
- Memory is a bounded encrypted file, not Room. Settings/history toggles reset on process restart.
