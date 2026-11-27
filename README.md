# FocusForge

> **Free, open-source (GPLv3), local-first focus companion for everyone** — built for **studying students** and **anyone doing cognitively demanding work**.

FocusForge was created from a simple idea: distraction blocking, deep-work tracking, and digital discipline tools should be **genuinely free for everyone**, without paywalls, subscriptions, accounts, or data harvesting.

---

## Who FocusForge Is For
- **Studying Students:** Exam preparation, deliberate practice blocks, blocking social/video distractions during study hours, and tracking daily & weekly focus consistency.
- **Deep-Work Professionals & Creators:** Engineers, writers, researchers, and anyone performing cognitively demanding tasks who needs uninterrupted flow, screen-time awareness, and notification silence.

---

## What FocusForge Offers (Phases 1–3 Complete)

- **Phase 1 — Focus Timer & Session Log:**
  - Native scroll-wheel countdown (`HRS : MIN : SEC`), persistent background foreground service, Deep Work vs. Shallow Work tracking, full-screen session completion reflection (`Record & Save Journey`, `Save Later`, or `Discard`), and local Room history.
- **Phase 2 — App Shield & Screen Time:**
  - `UsageStatsManager`-based foreground app blocker gated by screen ON/OFF (`PollingGate`), full-screen "Time to Focus" interceptor, emergency hold-to-bypass safety, notification silencing during focus hours (`NotificationListenerService`), wall-clock deduplicated screen time (Today, Last 5 Days, Overall Max Usage), and overall intercepted attempt stats.
- **Phase 3 — Local DNS Filter & Private Rules:**
  - **Local-Only DNS VPN (`DnsVpnService`):** Routes only virtual DNS (`10.0.0.1/32` and `fd00::1/128`); all other traffic bypasses FocusForge.
  - **Memory-Mapped FNV-1a Blocklist Engine:** Suffix-aware binary search over sorted 64-bit hashes (`ADULT_CORE`, optional `ADULT_EXTENDED`, optional `DISTRACTION`, `DOH_ENDPOINTS`) guarded against false positives using Mozilla Public Suffix List + Tranco Top-10K.
  - **Discreet Private / Custom Rules:** Hash-only matching, optional Keystore AES-256-GCM vault, `BiometricPrompt` lock, `FLAG_SECURE`, masked display, clipboard clear on paste, and backup exclusions.
  - **Shareable Focus Report Card:** Generate a `1080x1350` social-ready daily report with your display name, FocusForge logo, and granular privacy toggles (`Name`, `Sections`, `7-Day`, `Rating`).

---

## Upcoming Phases (Roadmap)

| Phase | Planned Scope | Status |
|---|---|---|
| **Phase 4** | **Time Blocks & Deep Work Planner:** Daily time-block scheduling with `AlarmManager` triggers and calendar-style focus blocks | Upcoming |
| **Phase 5** | **Tamper Resistance & Rest Mode:** `RuleChangeGate` cooldowns, stronger profile lock-ins, and scheduled recovery/rest windows | Upcoming |
| **Phase 6** | **Goals, Study Tools & FSRS:** Study milestones, FSRS spaced-repetition tools, and deeper analytics | Upcoming |
| **Phase 7** | **Autopilot & Voice-First Layer:** Hands-free session initiation and smart focus routines | Upcoming |
| **Phase 8** | **On-Device Study Companions:** Lumi & Zavi study companions, audio pre-rendering, and teach-back workflows | Upcoming |

---

## How We Operate: Ownership & User Responsibility

### What FocusForge Takes Ownership Of
- **100% On-Device Processing:** Sessions, timers, app blocking, screen time calculations, and DNS rule checks run locally on your device.
- **Zero Servers & Zero Telemetry:** No accounts, no FocusForge cloud servers, no analytics SDKs, and no ads.
- **Zero Readable Domain Logging:** Neither queried DNS hostnames nor Private Rules are ever written as readable text to disk or logs.
- **Fail-Open Safety:** Emergency phone calls, SMS, the system dialer, and system settings are never locked out. If local DNS enforcement encounters an unexpected error, it fails **OPEN** so your internet is not broken.

### What FocusForge Does NOT Take Ownership Of (User Responsibility)
- **Chosen Upstream DNS Resolver:** Allowed (non-blocked) DNS queries are forwarded to the resolver you select (System Default, Cloudflare `1.1.1.1`, Quad9 `9.9.9.9`, or Google `8.8.8.8`), which is governed by that provider's own policy.
- **Network / ISP / Browser Limits:** FocusForge cannot prevent router/ISP logging, browser history/cache, third-party keyboard IMEs ignoring `IME_FLAG_NO_PERSONALIZED_LEARNING`, hard-coded IP DoH connections, or custom Android Private DNS hostname mode.
- **Device & Rule Management:** Securing your device PIN/biometrics, deciding which apps/domains to block, and using emergency bypasses responsibly remain under your control.
- **Rooted / Modified Devices:** On rooted devices, privileged processes outside FocusForge can access device memory or storage.

See [docs/PRIVACY_POLICY.md](file:///home/vijay/Documents/Projects/FocusForge/docs/PRIVACY_POLICY.md) and [docs/STORE_LISTING.md](file:///home/vijay/Documents/Projects/FocusForge/docs/STORE_LISTING.md) for full details.

---

## Installation & Building From Source

### Option 1: Install Release APK via ADB
```bash
# Build non-debuggable release APK
./gradlew assembleRelease

# Install onto a connected Android device (preserving existing local data)
adb install -r -d app/build/outputs/apk/release/app-release.apk
```

### Option 2: Verify & Build Debug / Play Bundle (AAB)
```bash
# 1. Verify blocklists & false-positive guard
python3 tools/verify_blocklists.py

# 2. Run unit tests & build Debug APK, Release APK, and Release AAB
./gradlew testDebugUnitTest assembleDebug assembleRelease bundleRelease
```

---

## License

FocusForge is free software licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](file:///home/vijay/Documents/Projects/FocusForge/LICENSE) for details.
