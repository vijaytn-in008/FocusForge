# FocusForge — Play Store Listing & Release Checklist

## Store Listing Copy

### App Name
FocusForge — Focus & Blocker

### Short Description (<= 80 chars)
Local-first deep work timer, app shield, and on-device DNS distraction blocker.

### Full Description
FocusForge is a 100% local-first digital sanctuary designed to help you reclaim deep focus without sacrificing your privacy.

**Key Features:**
- **Deep Work & Shallow Work Timer:** Native scroll-wheel timer (`HRS : MIN : SEC`), persistent countdown notification, and full-screen session completion reflection (`Record & Save Journey`, `Save Later`, or `Discard`).
- **App Shield & Screen Time:** Block distracting apps during focus hours, silence notifications from blocked apps during focus sessions, and inspect today's and past 5 days' wall-clock deduplicated screen time.
- **Local On-Device DNS Filter (`VpnService`):** Blocks distraction and adult domains locally using fast, memory-mapped FNV-1a hash blocklists. Only DNS queries are checked on-device; allowed queries are forwarded directly to your chosen resolver (System Default, Cloudflare, Quad9, or Google).
- **Private / Custom Rules:** Hash-only rule matching with optional Keystore AES-256-GCM encryption, biometric lock, and `FLAG_SECURE` screen protection.
- **Social Focus Report Card:** Generate a shareable daily focus card with granular privacy toggles (Name, Sections, 7-Day Total, Rating).

**Privacy & Permissions Disclosure:**
- **No accounts, no cloud servers, no telemetry.**
- **Local DNS VPN (`VpnService`):** Used solely on-device to filter DNS lookups locally and forward allowed queries to your chosen DNS resolver. FocusForge does not use `AccessibilityService` to read browser text.
- **Usage Access (`PACKAGE_USAGE_STATS`):** Used locally on-device for App Shield foreground detection and Screen Time analytics.
- **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`):** Used for the "Time to Focus" interceptor and full-screen session completion prompt.
- **Notification Access (`BIND_NOTIFICATION_LISTENER_SERVICE`):** Optional; used locally during focus hours to dismiss notifications from blocked apps without reading notification text.

---

## Release & Rollout Checklist

1. **CI Build Verification:** Confirm `.github/workflows/build.yml` passes `verify_blocklists.py`, `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, and `bundleRelease`.
2. **Local Release Testing:** Install the release-signed APK (`app-release.apk`) on physical test devices and verify upgrades over previous versions preserve Room database sessions and rules.
3. **Google Play Internal Testing:** Upload the release-signed **AAB** (`app-release.aab`) to the Play Console **Internal Testing** track first.
4. **Policy & Store Review:** Ensure Play Console Data Safety and `VpnService` declaration match `docs/PRIVACY_POLICY.md`, then validate install/upgrade behavior before promoting to wider rollout.
