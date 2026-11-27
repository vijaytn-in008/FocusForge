# FocusForge Privacy Policy

**Last Updated:** November 2026  
**License:** GNU General Public License v3.0 (GPLv3)  
**Repository:** https://github.com/vijaytn-in008/FocusForge

FocusForge is a 100% local-first digital focus and distraction-blocking Android application. We operate **no FocusForge backend servers**, **no user accounts**, **no analytics**, and **no telemetry**.

---

## 1. Local Inspection & How Blocking Works

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

## 2. What Is Saved vs. What Is Never Saved

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

## 3. Sharing Focus Reports
- If you tap **Share Focus Report**, FocusForge generates a PNG summary image locally on-device using only the fields you enable (**Name**, **Sections**, **7-Day**, **Rating**) and opens Android's system share sheet so you control where and whether to post it.

---

## 4. Data Deletion
- Uninstalling FocusForge or clearing app storage in Android Settings permanently deletes all locally stored sessions, rules, and preferences.
