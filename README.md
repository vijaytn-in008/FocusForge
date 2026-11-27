# FocusForge

> Free, open-source (GPLv3), local-first Android app designed to block pornography and distraction apps/websites, plan deep-work time blocks, and track focus progress.

---

## Philosophy and Principles

- **100% Local-First**: No accounts, no backend servers, no telemetry, no analytics. Your study habits, session logs, and filter rules never leave your device.
- **Safety Guarantee**: Never locks you out of your device. Phone calls, SMS, the system dialer, system settings, and an emergency bypass are always accessible. Fails **OPEN** if anything in DNS enforcement misbehaves.
- **Privacy Guarantee**: No queried or blocked domain name is ever written to disk or logs. Only aggregate daily counters are stored.
- **GPLv3 Licensed**: Free software for everyone, forever.

---

## Features (Phases 1–3)

- **Phase 1 — Focus Timer & Session Log**: Persistent countdown service, Deep vs. Shallow work tracking, Room database.
- **Phase 2 — App Shield & Screen Time**: `UsageEvents`-based foreground app blocker gated by screen ON/OFF (`PollingGate`), "Time to Focus" interceptor (`BlockedAppActivity`), wall-clock deduplicated screen time breakdown.
- **Phase 3 — Local DNS Filter & Private Rules**:
  - **DNS-only VPN (`DnsVpnService`)**: Routes only virtual DNS (`10.0.0.1/32` and `fd00::1/128`); all other traffic bypasses the app.
  - **Memory-mapped FNV-1a Blocklist Engine**: Suffix-aware binary search over sorted 64-bit hashes (`ADULT_CORE` ON by default, `ADULT_EXTENDED` OFF by default, `DISTRACTION` under `STRICT` profile only, `DOH_ENDPOINTS`). Protected by Mozilla Public Suffix List + Tranco Top-10K false-positive guard.
  - **User Allowlist**: Stored as hashes only; overrides distraction/private rules without bypassing adult filters.
  - **Discreet Private List**: Hash-only matching, optional Keystore AES-256-GCM encrypted vault, `BiometricPrompt` lock, `FLAG_SECURE`, masked display (`p•••••.com`) with 10s auto-hide, IME no-personalized-learning flags, clipboard clear on paste, Share-to-FocusForge, and backup exclusions.
  - **Transparency & Self-Repair**: Opt-in in-memory 300-entry DNS decision buffer, "Why was this blocked?" screen with one-tap false-positive reporting, Private DNS strict-mode detection, fail-open health monitor, and 5-minute pause.

---

## Privacy & What DNS Blocking Can and Cannot Do

### What DNS Blocking Covers
- Blocks domain lookups system-wide across browsers (Chrome, Firefox, etc., normal and incognito/private tabs) and apps when using system DNS.
- Blocks known public DoH endpoints (`DOH_ENDPOINTS`) so browsers fall back to local DNS.

### What DNS Blocking Cannot Cover
- **Hard-coded IP DoH**: If a browser or app connects directly to a DoH resolver by IP address (e.g. `1.1.1.1` / `8.8.8.8`) without a DNS lookup, local DNS filtering cannot intercept it.
- **Android Private DNS Strict Mode**: Setting Android's Private DNS to a custom hostname bypasses the VPN DNS path (FocusForge detects this and warns you).
- **Other Devices / Networks**: FocusForge protects only the phone it is installed on, while the VPN is active.
- **Cached Connections**: Existing open connections or cached DNS entries may persist briefly until closed or refreshed.

### Privacy Statement
- FocusForge never sends your DNS queries, browsing history, or personal lists to any FocusForge server.
- Allowed DNS queries are forwarded only to your chosen upstream resolver (default: your active network's own DNS servers).
- **No queried domain is ever written to disk or logs.**

### Privacy of Your Private List
- **Protected**:
  - Stored as 64-bit FNV-1a hashes (or optional Keystore AES-256-GCM vault unlocked only via biometric/device credential).
  - Never appears in notifications (`VISIBILITY_PRIVATE`), lock-screen content, Room logs, decision buffer, "Why was this blocked?", or Android Auto-Backup / D2D transfer.
  - `FLAG_SECURE` blocks screenshots, screen recording, and recents thumbnails on Private List screens.
  - Input fields disable autofill, suggestions, and personalized keyboard learning; paste-import clears the clipboard.
- **Not Protected (Platform / Physical Limits)**:
  - Your keyboard's own history if a third-party IME ignores `IME_FLAG_NO_PERSONALIZED_LEARNING`.
  - Your browser's own history/cache.
  - Router or ISP DNS logs for non-blocked queries.
  - Anyone who knows your device PIN/pattern or has a fingerprint enrolled on the phone.
  - Rooted devices with privileged memory/storage access.

---

## Building From Source

```bash
# Verify blocklists & false-positive guard
python3 tools/verify_blocklists.py

# Run unit tests & build debug APK
./gradlew testDebugUnitTest assembleDebug

# Install onto a connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## License

FocusForge is licensed under the **GNU General Public License v3.0 (GPLv3)**. See [LICENSE](file:///home/vijay/Documents/Projects/FocusForge/LICENSE) for details.
