# FocusForge Architecture

## 1. System Philosophy & Rules
Core rule: **NOTHING RUNS UNLESS SOMETHING NEEDS IT RIGHT NOW.**
Always-on components are tiny and strictly event-driven.

### Layers:
1. **UI (Compose)**: Home, Timer, Shield, History, Permissions, Protection, Private Rules (`FLAG_SECURE` + `BiometricPrompt`), Why Blocked. Runs only when visible in foreground.
2. **CORE (Main Process)**: Room DB (v5), `PolicyStore` (`StateFlow<PolicySnapshot>`), `PrivateListStore` + `PrivateVault` (AndroidKeyStore AES-256-GCM), `ProfileScheduler` (`AlarmManager` stub), `RuleChangeGate` (stub).
3. **ENFORCEMENT**:
   - `AppBlockerService` gated by `PollingGate` / `WatcherGateLogic` (`UsageEvents`, screen ON + active profile + non-empty blocked apps).
   - `DnsVpnService`: DNS-only TUN (`10.0.0.1/32`, `fd00::1/128`), `BlocklistEngine` (memory-mapped sorted 64-bit FNV-1a binary search), `SafetyAllowlist`, `DnsPacketParser` (IPv4/IPv6, UDP + TCP fallback, EDNS, NXDOMAIN / `0.0.0.0` / SERVFAIL), `VpnHealthMonitor` (fail-open), `DnsDecisionBuffer` (debug-only opt-in 300-entry ring buffer).
   - `BlockedAppActivity`: "Time to Focus" interceptor with active Timer/Profile context and Private App masking.

### Invariants & Safety Guarantees:
- **Never lock user out**: Phone, SMS, Dialer, System Settings, Emergency Bypass (3s hold-to-unlock) are hard-exempt.
- **Fail OPEN**: Repeated upstream DNS errors or revoked VPN automatically stop the tunnel and show `"Protection paused: network issue"` with a 1-tap restart.
- **Local-first & Private**: Zero queried domains written to disk or logs. Private entries are hashed (and optionally Keystore-encrypted), excluded from backups, and never shown in notifications or logs.

---

## 2. Profile-to-Category Table

| Profile | App Blocking | `ADULT_CORE` | `ADULT_EXTENDED` | `DISTRACTION` | `DOH_ENDPOINTS` |
|---|---|---|---|---|---|
| `OFF` | Inactive | User pref (default ON) | User pref (default OFF) | Inactive | User pref (default ON) |
| `RELAXED` | Active (if non-empty) | User pref (default ON) | User pref (default OFF) | Inactive | User pref (default ON) |
| `STRICT` | Active (if non-empty) | User pref (default ON) | User pref (default OFF) | User pref (default OFF) | User pref (default ON) |

---

## 3. DNS Decision Order (First Match Wins)

1. **Built-in Safety Allowlist** (non-removable): Android connectivity-check/captive-portal, OS/Play/FCM/NTP, emergency/gov services, configured upstream bootstrap hostnames.
2. **User Allowlist** (hashed): overrides `DISTRACTION` and `PRIVATE` rules (does not bypass `ADULT`).
3. **Private List** (hashed): user-managed discreet blocks.
4. **Category Tiers** (`ADULT_CORE`, `ADULT_EXTENDED`, `DISTRACTION`, `DOH_ENDPOINTS`) per `PolicySnapshot.enabledTiers`.
5. **Default ALLOW**: forwarded via protected socket (`VpnService.protect()` + physical network binding) to selected upstream resolver with 1 retry and TCP fallback on `TC=1` (`SERVFAIL` on failure).
