# FocusForge Privacy Policy & Transparency Statement

**Last Updated:** November 2026  
**License:** GNU General Public License v3.0 (GPLv3)  
**Repository:** https://github.com/vijaytn-in008/FocusForge

FocusForge is built to make deep-work and distraction-blocking tools **free for everyone**—both studying students and professionals doing cognitively demanding work—without accounts, subscriptions, ads, or data harvesting.

---

## 1. What We Do & What We Offer
FocusForge provides on-device tools to help you stay focused:
- **Focus Timer & Session Journal:** Deep Work & Shallow Work countdowns, persistent timer service, and local session history.
- **App Shield & Notification Silencing:** Blocks selected distraction apps during focus hours and dismisses notifications from blocked apps while a focus session or profile is active.
- **Screen Time Insights:** Computes wall-clock deduplicated daily screen time (Today, Last 5 Days, and Overall Max Usage) locally on-device.
- **Local DNS Filter & Private Rules:** Filters distracting and adult domains locally on-device via a DNS-only `VpnService`.
- **Shareable Focus Report Card:** Generates an optional local PNG summary card with privacy toggles so you control what others see.

---

## 2. How Local Inspection Works

### Local DNS Inspection & Forwarding (`VpnService`)
- When DNS Protection is enabled, FocusForge runs a local-only Android `VpnService` (`DnsVpnService`) that routes only virtual DNS addresses (`10.0.0.1/32` and `fd00::1/128`) on your device.
- **No non-DNS traffic passes through FocusForge.**
- **Local matching:** Each DNS hostname query is checked on-device against your enabled local blocklist tiers (`ADULT_CORE`, optional `ADULT_EXTENDED`, optional `DISTRACTION`, `DOH_ENDPOINTS`) and your hashed Private/Allowlist rules.
- **DNS forwarding:** Allowed (non-blocked) DNS queries are forwarded directly from your device to the **upstream DNS resolver you choose** in DNS Protection settings (your active network's System Default DNS, Cloudflare `1.1.1.1`, Quad9 `9.9.9.9`, or Google `8.8.8.8`).

### Browser & Screen Inspection
- FocusForge does **not** use `AccessibilityService` to read browser page text, keystrokes, or screen content.
- Website blocking works at the **local DNS lookup level** across browsers (including normal and incognito/private tabs) without inspecting page text or URL paths.

### Local App Usage & Screen Time (`UsageStatsManager` / `PACKAGE_USAGE_STATS`)
- App Shield and Screen Time query Android's `UsageStatsManager` **locally on-device** to detect the current foreground app package name and compute wall-clock deduplicated daily screen time.
- App usage statistics are never uploaded or shared off your device.

### Local Notification Silencing (`NotificationListenerService`)
- When Notification Access is granted, `FocusNotificationListenerService` checks only the **package name** of incoming notifications during active focus hours and dismisses notifications from apps you have marked as blocked.
- FocusForge **never reads, logs, or stores notification titles, message text, or sender details**.

---

## 3. What Is Saved vs. What Is Never Saved

### Saved Locally on Your Device Only
- **Focus Sessions:** Subject, optional topic/reflection note, start time, duration, Deep/Shallow type, and 1–5 star rating (stored in local Room SQLite database).
- **App Shield Rules & Interceptions:** Package names of apps you choose to block, emergency bypass logs, and blocked app attempt counts.
- **Private / Custom Rules:** Stored as **64-bit FNV-1a hashes** (or optional Android Keystore **AES-256-GCM** encrypted vault unlocked via biometric/device credential). Excluded from Android Auto-Backup and Device-to-Device transfer.
- **Aggregate Counters & Preferences:** Daily blocked DNS count, pause count, display name, and timer/filter settings.

### Never Saved or Transmitted
- **Zero readable domain names** from DNS queries or Private Rules are ever written to disk, Room databases, or system logs.
- **No browsing history**, **no DNS query history**, and **no URLs** are persisted.
- **No notification content** is read or saved.
- **No personal data** is ever transmitted to FocusForge developers or third-party analytics/ad SDKs.

---

## 4. Ownership & User Responsibility

### What FocusForge Takes Ownership Of
- Keeping all core enforcement, timer logic, screen-time math, and session storage **100% local and free**.
- Never operating FocusForge tracking servers, analytics, or telemetry.
- Never writing readable queried/blocked domain names to disk or logs.
- Failing **OPEN** if local DNS enforcement encounters an error, and never blocking emergency calls, SMS, the system dialer, or system settings.

### What FocusForge Does NOT Take Ownership Of (User Responsibility)
- **Upstream DNS Resolvers:** Allowed DNS queries are forwarded to the third-party or network resolver you select, which operates under that resolver's own privacy policy.
- **Network / ISP / Browser Limits:** FocusForge cannot control router or ISP DNS logs, your browser's own history/cache, third-party keyboard IMEs that ignore `IME_FLAG_NO_PERSONALIZED_LEARNING`, hard-coded IP DoH traffic, or Android Private DNS strict hostname mode.
- **Device Security & Rule Choices:** Protecting your device PIN/biometrics, choosing which apps/sites to block, using emergency bypasses, and choosing what to post with the Share Card are your responsibility.
- **Rooted / Modified Devices:** FocusForge cannot protect local memory or storage from privileged root access on rooted devices.

---

## 5. Data Deletion
- Uninstalling FocusForge or clearing app storage in Android Settings permanently deletes all locally stored sessions, rules, and preferences.
