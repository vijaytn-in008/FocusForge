# Phase 3 Rewrite Plan

> **Created**: 2026-11-27  
> **Branch**: `phase3-rewrite` (from tag `pre-phase3-rewrite`)  
> **Status**: AWAITING APPROVAL — no code changes until approved  
> **Room schema**: currently v4 → will become v5 after rewrite

---

## A. INVENTORY — Old Phase 3 Code

Everything below was added after Phase 2 and belongs to the old, flawed Phase 3 implementation.

### A.1 Kotlin Source Files

| # | File | Purpose | Lines |
|---|------|---------|-------|
| 1 | `app/src/main/java/com/focusforge/app/enforcement/DnsVpnService.kt` | DNS-only VPN service (broken upstream forwarding) | 489 |
| 2 | `app/src/main/java/com/focusforge/app/enforcement/DnsPacketParser.kt` | IP/UDP/DNS packet parser + NXDOMAIN builder | 183 |
| 3 | `app/src/main/java/com/focusforge/app/enforcement/BlocklistEngine.kt` | Memory-mapped FNV-1a blocklist engine | 225 |
| 4 | `app/src/main/java/com/focusforge/app/enforcement/OnDeviceContentInspector.kt` | Keyword/query content inspector (accessibility) | 220 |
| 5 | `app/src/main/java/com/focusforge/app/enforcement/PollingGate.kt` | Screen ON/OFF broadcast gate | 76 |
| 6 | `app/src/main/java/com/focusforge/app/enforcement/ProfileScheduler.kt` | AlarmManager stub + manual profile switch | 42 |
| 7 | `app/src/main/java/com/focusforge/app/service/BrowserAccessibilityService.kt` | AccessibilityService for browser URL/title inspection | 222 |
| 8 | `app/src/main/java/com/focusforge/app/data/model/PolicyModels.kt` | `PolicySnapshot`, `BlockingProfile`, `DomainCategory` enums | 49 |
| 9 | `app/src/main/java/com/focusforge/app/data/repository/PolicyRepository.kt` | `PolicyRepository` interface + `PolicyRepositoryImpl` | 220 |
| 10 | `app/src/main/java/com/focusforge/app/data/local/entity/ProfileEntity.kt` | Room entity: `profiles` table | 11 |
| 11 | `app/src/main/java/com/focusforge/app/data/local/entity/DomainBlockCategoryEntity.kt` | Room entity: `domain_block_categories` table | 11 |
| 12 | `app/src/main/java/com/focusforge/app/data/local/entity/CustomDomainEntity.kt` | Room entity: `custom_domains` table (PLAINTEXT domains!) | 12 |
| 13 | `app/src/main/java/com/focusforge/app/data/local/entity/DnsBlockLogEntity.kt` | Room entity: `dns_block_log` table (logs PLAINTEXT domains!) | 13 |
| 14 | `app/src/main/java/com/focusforge/app/data/local/dao/ProfileDao.kt` | DAO for `profiles` | 20 |
| 15 | `app/src/main/java/com/focusforge/app/data/local/dao/DomainBlockCategoryDao.kt` | DAO for `domain_block_categories` | 29 |
| 16 | `app/src/main/java/com/focusforge/app/data/local/dao/CustomDomainDao.kt` | DAO for `custom_domains` | 35 |
| 17 | `app/src/main/java/com/focusforge/app/data/local/dao/DnsBlockLogDao.kt` | DAO for `dns_block_log` | 28 |
| 18 | `app/src/main/java/com/focusforge/app/ui/protection/ProtectionScreen.kt` | Full Protection UI (697 lines, 4 tabs) | 697 |
| 19 | `app/src/main/java/com/focusforge/app/ui/protection/ProtectionViewModel.kt` | ProtectionViewModel | 182 |

### A.2 Test Files

| # | File | Tests |
|---|------|-------|
| 1 | `app/src/test/java/com/focusforge/app/enforcement/BlocklistEngineTest.kt` | FNV-1a, suffix matching, binary search |
| 2 | `app/src/test/java/com/focusforge/app/enforcement/DnsPacketParserTest.kt` | DNS packet parse/build |
| 3 | `app/src/test/java/com/focusforge/app/enforcement/OnDeviceContentInspectorTest.kt` | Content inspector keywords, educational exemption |
| 4 | `app/src/test/java/com/focusforge/app/data/repository/PolicySnapshotDerivationTest.kt` | PolicySnapshot derivation |

### A.3 Binary Assets

| # | File | Size | Contents |
|---|------|------|----------|
| 1 | `app/src/main/assets/blocklists/adult.bin` | 974,896 bytes | 121,862 FNV-1a hashes from StevenBlack porn/hosts (false positives: beacons*.gvt2.com etc.) |
| 2 | `app/src/main/assets/blocklists/distraction.bin` | 392 bytes | 49 curated distraction domains (no longer used, was supposed to be removed) |
| 3 | `app/src/main/assets/blocklists/doh_endpoints.bin` | 192 bytes | 24 DoH endpoint domains |

### A.4 Resource Files

| # | File |
|---|------|
| 1 | `app/src/main/res/xml/browser_accessibility_service_config.xml` |

### A.5 Tool Scripts

| # | File |
|---|------|
| 1 | `tools/build-blocklist.py` — downloads StevenBlack list, generates `.bin` files; includes curated DISTRACTION and DOH lists |

### A.6 Room Schema

| # | File |
|---|------|
| 1 | `app/schemas/com.focusforge.app.data.local.AppDatabase/4.json` — schema v4 including Phase 3 tables |

### A.7 Manifest Entries (Phase 3 only)

```xml
<!-- DNS VPN Service -->
<service android:name=".enforcement.DnsVpnService"
    android:permission="android.permission.BIND_VPN_SERVICE"
    android:foregroundServiceType="specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Local DNS filtering to block adult content and distractions" />
    <intent-filter>
        <action android:name="android.net.VpnService" />
    </intent-filter>
</service>

<!-- Browser Accessibility Inspector -->
<service android:name=".service.BrowserAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data android:name="android.accessibilityservice"
        android:resource="@xml/browser_accessibility_service_config" />
</service>
```

### A.8 Manifest Permissions (Phase 3 only)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### A.9 DI Bindings (Phase 3)

**`AppModule.kt`** provides:
- `provideProfileDao()` → `ProfileDao`
- `provideDomainBlockCategoryDao()` → `DomainBlockCategoryDao`
- `provideCustomDomainDao()` → `CustomDomainDao`
- `provideDnsBlockLogDao()` → `DnsBlockLogDao`

**`RepositoryModule.kt`** binds:
- `bindPolicyRepository()` → `PolicyRepository` / `PolicyRepositoryImpl`

### A.10 Notification Channel (Phase 3)

**`FocusForgeApp.kt`**: `VPN_CHANNEL_ID = "focusforge_vpn_channel"` — created as "DNS Protection Guard"

### A.11 SharedPreferences Keys (Phase 3)

- `"dns_protection_enabled"` in `focusforge_prefs` — read by `BootCompletedReceiver` and written by `ProtectionViewModel`

### A.12 String Resources (Phase 3)

```xml
<string name="accessibility_service_description">Protects against adult websites…</string>
```

### A.13 Navigation (Phase 3)

- `Screen.Protection` route in `Screen.kt`
- `composable(Screen.Protection.route)` in `NavGraph.kt`
- `onNavigateToProtection` callback in `BlockedAppsScreen` → `NavGraph`

### A.14 Documentation (Phase 3)

- `docs/ARCHITECTURE.md` — references DnsVpnService, BlocklistEngine, ProtectionScreen
- `docs/STATUS.md`
- `docs/SOURCES.md`
- `docs/THIRD_PARTY.md`
- `docs/proposals/PHASE-3-PROPOSAL.md`

### A.15 Cross-References FROM Phase 1-2 Code INTO Phase 3

These Phase 1-2 files import or reference Phase 3 code and need surgical edits:

| Phase 1-2 File | Phase 3 References |
|----------------|-------------------|
| `AppDatabase.kt` | Imports & registers `ProfileEntity`, `DomainBlockCategoryEntity`, `CustomDomainEntity`, `DnsBlockLogEntity` + their DAOs; `MIGRATION_3_4` |
| `AppModule.kt` | Provides Phase 3 DAOs (ProfileDao, DomainBlockCategoryDao, CustomDomainDao, DnsBlockLogDao) |
| `RepositoryModule.kt` | Binds `PolicyRepository` / `PolicyRepositoryImpl` |
| `AppBlockerService.kt` | Imports `PolicySnapshot`, `PolicyRepository`, `PollingGate`; uses `currentPolicy.isAppBlockingEffective` and `currentPolicy.blockedAppPackages` |
| `BootCompletedReceiver.kt` | Checks `dns_protection_enabled` and calls `DnsVpnService.start()` |
| `HomeViewModel.kt` | Imports `PolicyRepository`, `BlockingProfile`, `DnsVpnService`; combines `policyRepository.policySnapshot` and `DnsVpnService.isVpnRunning` into `HomeUiState` |
| `HomeScreen.kt` | Displays `isDnsActive`, `activeProfile` from `HomeUiState` |
| `BlockedAppsScreen.kt` | Has `onNavigateToProtection` callback; imports `BlockingProfile` |
| `BlockedAppsViewModel.kt` | Imports `PolicyRepository`, `ProfileScheduler`, `BlockingProfile`; calls `profileScheduler.switchProfile()` |
| `NavGraph.kt` | Imports `ProtectionScreen`, `ProtectionViewModel`; has `Screen.Protection` composable |
| `Screen.kt` | Defines `Screen.Protection`; imports `Icons.Filled.Public` and `Icons.Outlined.Public` |
| `PermissionsScreen.kt` | References `VpnService.prepare()` for the VPN permission card |
| `FocusForgeApp.kt` | Creates `VPN_CHANNEL_ID` notification channel |
| `strings.xml` | `accessibility_service_description` string |

---

## B. DELETE LIST

Everything below is **removed entirely** in a single commit during R1. No dead code, no commented-out blocks, no feature flags.

### B.1 Delete Files (complete removal)

```
app/src/main/java/com/focusforge/app/enforcement/DnsVpnService.kt
app/src/main/java/com/focusforge/app/enforcement/DnsPacketParser.kt
app/src/main/java/com/focusforge/app/enforcement/BlocklistEngine.kt
app/src/main/java/com/focusforge/app/enforcement/OnDeviceContentInspector.kt
app/src/main/java/com/focusforge/app/enforcement/PollingGate.kt          (REWRITE, not reuse)
app/src/main/java/com/focusforge/app/enforcement/ProfileScheduler.kt     (REWRITE, not reuse)
app/src/main/java/com/focusforge/app/service/BrowserAccessibilityService.kt
app/src/main/java/com/focusforge/app/data/model/PolicyModels.kt          (REWRITE with new fields)
app/src/main/java/com/focusforge/app/data/repository/PolicyRepository.kt (REWRITE as PolicyStore)
app/src/main/java/com/focusforge/app/data/local/entity/ProfileEntity.kt
app/src/main/java/com/focusforge/app/data/local/entity/DomainBlockCategoryEntity.kt
app/src/main/java/com/focusforge/app/data/local/entity/CustomDomainEntity.kt
app/src/main/java/com/focusforge/app/data/local/entity/DnsBlockLogEntity.kt
app/src/main/java/com/focusforge/app/data/local/dao/ProfileDao.kt
app/src/main/java/com/focusforge/app/data/local/dao/DomainBlockCategoryDao.kt
app/src/main/java/com/focusforge/app/data/local/dao/CustomDomainDao.kt
app/src/main/java/com/focusforge/app/data/local/dao/DnsBlockLogDao.kt
app/src/main/java/com/focusforge/app/ui/protection/ProtectionScreen.kt
app/src/main/java/com/focusforge/app/ui/protection/ProtectionViewModel.kt
app/src/main/res/xml/browser_accessibility_service_config.xml
app/src/main/assets/blocklists/adult.bin
app/src/main/assets/blocklists/distraction.bin
app/src/main/assets/blocklists/doh_endpoints.bin
app/src/test/java/com/focusforge/app/enforcement/BlocklistEngineTest.kt
app/src/test/java/com/focusforge/app/enforcement/DnsPacketParserTest.kt
app/src/test/java/com/focusforge/app/enforcement/OnDeviceContentInspectorTest.kt
app/src/test/java/com/focusforge/app/data/repository/PolicySnapshotDerivationTest.kt
tools/build-blocklist.py
```

### B.2 Delete from Manifest

- Remove `DnsVpnService` `<service>` block (lines 88–100)
- Remove `BrowserAccessibilityService` `<service>` block (lines 112–123)
- Remove `INTERNET` permission (line 36) — will be re-added in R3 when VPN is rewritten
- Remove `ACCESS_NETWORK_STATE` permission (line 37) — will be re-added in R3

### B.3 Delete from AppDatabase.kt

- Remove `ProfileEntity`, `DomainBlockCategoryEntity`, `CustomDomainEntity`, `DnsBlockLogEntity` from `@Database(entities = [...])`
- Remove `profileDao()`, `domainBlockCategoryDao()`, `customDomainDao()`, `dnsBlockLogDao()` abstract functions
- Remove their imports
- Remove `MIGRATION_3_4` — replaced by new `MIGRATION_4_5` (see section D)

### B.4 Delete from DI

**`AppModule.kt`**: Remove `provideProfileDao()`, `provideDomainBlockCategoryDao()`, `provideCustomDomainDao()`, `provideDnsBlockLogDao()` and their imports.

**`RepositoryModule.kt`**: Remove `bindPolicyRepository()` and its imports.

### B.5 Delete from FocusForgeApp.kt

- Remove `vpnChannel` creation block and `VPN_CHANNEL_ID` constant — will be re-added in R3

### B.6 Delete from strings.xml

- Remove `accessibility_service_description`

### B.7 Delete from Navigation

- Remove `Screen.Protection` from `Screen.kt` and its icon imports (`Icons.Filled.Public`, `Icons.Outlined.Public`)
- Remove `composable(Screen.Protection.route)` from `NavGraph.kt` and its `ProtectionScreen`/`ProtectionViewModel` imports

### B.8 Delete SharedPreferences Key

- Remove all reads/writes of `"dns_protection_enabled"` from `BootCompletedReceiver.kt` and wherever else it appears

### B.9 Delete Docs

- `docs/proposals/PHASE-3-PROPOSAL.md` — superseded by this plan

---

## C. KEEP LIST — Phase 1-2 Code and Connection Seams

### C.1 Core Phase 1-2 Files (KEEP AS-IS)

```
app/src/main/java/com/focusforge/app/FocusForgeApp.kt                   (edit: remove VPN channel only)
app/src/main/java/com/focusforge/app/MainActivity.kt                    (no changes)
app/src/main/java/com/focusforge/app/BlockedAppActivity.kt              (no changes)
app/src/main/java/com/focusforge/app/service/TimerService.kt            (no changes)
app/src/main/java/com/focusforge/app/data/local/Converters.kt           (no changes)
app/src/main/java/com/focusforge/app/data/local/dao/SessionDao.kt       (no changes)
app/src/main/java/com/focusforge/app/data/local/dao/BlockedAppDao.kt    (no changes)
app/src/main/java/com/focusforge/app/data/local/dao/BlockedAppAttemptDao.kt (no changes)
app/src/main/java/com/focusforge/app/data/local/dao/EmergencyBypassLogDao.kt (no changes)
app/src/main/java/com/focusforge/app/data/local/entity/SessionEntity.kt  (no changes)
app/src/main/java/com/focusforge/app/data/local/entity/BlockedAppEntity.kt (no changes)
app/src/main/java/com/focusforge/app/data/local/entity/BlockedAppAttemptEntity.kt (no changes)
app/src/main/java/com/focusforge/app/data/local/entity/EmergencyBypassLogEntity.kt (no changes)
app/src/main/java/com/focusforge/app/data/model/Session.kt              (no changes)
app/src/main/java/com/focusforge/app/data/model/SessionType.kt          (no changes)
app/src/main/java/com/focusforge/app/data/model/ScreenTimeModels.kt     (no changes)
app/src/main/java/com/focusforge/app/data/repository/SessionRepository.kt (no changes)
app/src/main/java/com/focusforge/app/data/repository/BlockedAppRepository.kt (no changes)
app/src/main/java/com/focusforge/app/data/repository/ScreenTimeRepository.kt (no changes)
app/src/main/java/com/focusforge/app/data/repository/UserPreferencesRepository.kt (no changes)
app/src/main/java/com/focusforge/app/ui/timer/TimerScreen.kt            (no changes)
app/src/main/java/com/focusforge/app/ui/timer/TimerViewModel.kt         (no changes)
app/src/main/java/com/focusforge/app/ui/history/HistoryScreen.kt        (no changes)
app/src/main/java/com/focusforge/app/ui/history/HistoryViewModel.kt     (no changes)
app/src/main/java/com/focusforge/app/ui/bot/ForgeBotGuideDialog.kt      (no changes)
app/src/main/java/com/focusforge/app/ui/theme/Color.kt                  (no changes)
app/src/main/java/com/focusforge/app/ui/theme/Theme.kt                  (no changes)
app/src/main/java/com/focusforge/app/ui/theme/Type.kt                   (no changes)
app/src/main/java/com/focusforge/app/ui/navigation/BottomNavBar.kt      (no changes)
app/src/main/java/com/focusforge/app/util/TimeUtils.kt                  (no changes)
app/src/test/java/com/focusforge/app/data/ConvertersTest.kt             (no changes)
app/src/test/java/com/focusforge/app/util/TimeUtilsTest.kt              (no changes)
```

### C.2 Phase 1-2 Files That Need Surgical Edits (Seams)

#### `AppDatabase.kt`
- **Remove**: Phase 3 entities from `@Database`, Phase 3 DAOs, `MIGRATION_3_4`, Phase 3 imports
- **Add**: New `MIGRATION_4_5` that drops the 4 old tables (see section D)
- **Keep**: Schema version bumps to 5; Phase 1-2 entities and DAOs untouched

#### `AppModule.kt`
- **Remove**: Phase 3 DAO providers and imports
- **Keep**: `provideAppDatabase()` (update migration list), `provideSessionDao()`, `provideBlockedAppDao()`, `provideEmergencyBypassLogDao()`, `provideBlockedAppAttemptDao()`

#### `RepositoryModule.kt`
- **Remove**: `bindPolicyRepository()` and imports
- **Keep**: `bindSessionRepository()`, `bindUserPreferencesRepository()`, `bindBlockedAppRepository()`, `bindScreenTimeRepository()`

#### `AppBlockerService.kt`
- **Remove**: `PolicyRepository` and `PolicySnapshot` imports, `policyRepository` injection, `PollingGate` import
- **Replace with**: New `PolicyStore` injection (from rewritten code) for `PolicySnapshot`; new `PollingGate` (rewritten)
- **Seam**: `currentPolicy.isAppBlockingEffective` and `currentPolicy.blockedAppPackages` → these property names stay identical in the new `PolicySnapshot`, so AppBlockerService just needs its import path updated

#### `BootCompletedReceiver.kt`
- **Remove**: `dns_protection_enabled` check and `DnsVpnService.start()` call
- **Keep**: `app_blocker_enabled` check and `AppBlockerService.start()`
- **Phase 3 rewrite will re-add**: DNS VPN restart on boot in R3

#### `HomeViewModel.kt`
- **Remove**: `PolicyRepository` import, `DnsVpnService` import, `policyRepository.policySnapshot` combine, `DnsVpnService.isVpnRunning` combine
- **Simplify temporarily**: `activeProfile` defaults to `OFF`, `isDnsActive` defaults to `false` until the new PolicyStore is wired in R1
- **Seam**: After R1, re-wire to new `PolicyStore.snapshot` and new VPN running state

#### `HomeScreen.kt`
- **Keep**: UI that shows `isDnsActive` and `activeProfile` — these fields stay in `HomeUiState`; they'll just be `false`/`OFF` until R1 wires them

#### `BlockedAppsScreen.kt`
- **Remove**: `onNavigateToProtection` parameter (temporarily; re-added when new Protection screen is built in R5)
- **Keep**: Everything else; profile selector chips stay

#### `BlockedAppsViewModel.kt`
- **Remove**: `ProfileScheduler` injection (replace with new one in R1)
- **Replace**: `PolicyRepository` → new `PolicyStore` (identical `.policySnapshot` interface)
- **Keep**: `setProfile()`, `toggleBlockerService()` logic

#### `NavGraph.kt`
- **Remove**: `Screen.Protection` composable, `ProtectionScreen`/`ProtectionViewModel` imports
- **Keep**: All other routes

#### `Screen.kt`
- **Remove**: `Screen.Protection` data object, `Icons.Filled.Public`/`Icons.Outlined.Public` imports
- **Keep**: All other Screen objects

#### `PermissionsScreen.kt`
- **Remove**: VPN permission card (`VpnService.prepare()` check and import) — will be re-added in R3
- **Keep**: Usage Stats, Overlay, Battery Optimization cards

#### `FocusForgeApp.kt`
- **Remove**: `vpnChannel` creation block and `VPN_CHANNEL_ID`
- **Keep**: Timer, Alert, Blocker channels

#### `strings.xml`
- **Remove**: `accessibility_service_description`
- **Keep**: All other strings

#### `app/build.gradle.kts`
- **Keep as-is** for R1 (no dependency changes yet)
- R2 may add a Gradle task for blocklist generation

---

## D. DATA MIGRATION PLAN

### D.1 Current State (Schema v4)

The old `MIGRATION_3_4` created 4 tables:
- `profiles` — stores active blocking profile (`OFF`/`RELAXED`/`STRICT`/`REST`)
- `domain_block_categories` — toggle for `ADULT`, `DOH_ENDPOINTS`, `DISTRACTION`
- `custom_domains` — **PLAINTEXT domain names** with `listType` (`ALLOW`/`BLOCK`)
- `dns_block_log` — **PLAINTEXT domain names** with timestamps

### D.2 Privacy Concern

`custom_domains` and `dns_block_log` store **readable domain names** on disk. This directly violates the privacy requirement: "no queried domain is ever written to disk."

### D.3 Migration v4 → v5

**`MIGRATION_4_5`** (single destructive migration):

```sql
-- 1. Drop the old Phase 3 tables (data is not migrated)
DROP TABLE IF EXISTS `profiles`;
DROP TABLE IF EXISTS `domain_block_categories`;
DROP TABLE IF EXISTS `custom_domains`;
DROP TABLE IF EXISTS `dns_block_log`;

-- 2. VACUUM to reclaim space and ensure no plaintext domain fragments
--    remain in the SQLite free-page pool
VACUUM;
```

**Why we DROP instead of migrating rows:**

1. **`custom_domains`**: Contains plaintext domains. The new Private List (R4) stores only hashes (and optionally encrypted names in a separate vault file, never in Room). Migrating these plaintext strings into hashes would require reading them into memory and hashing them — acceptable as a one-time operation. However:
   - The old `custom_domains` has only `ALLOW` and `BLOCK` entries mixed together.
   - The new architecture separates them: ALLOW → `customAllowedDomainHashes` (in a new table or DataStore); BLOCK → Private List (hash store + optional encrypted vault).
   - The user likely has very few custom entries (the app was barely usable with DNS forwarding broken).
   - **Decision**: DROP the table. The user re-enters their few custom domains after the update. This is safer than risking a flawed migration that might leak names or corrupt hashes.

2. **`dns_block_log`**: Contains plaintext domains. The new design stores ONLY aggregate counters (blocked today / this week), never individual domain names. No migration possible — the data format is fundamentally different. DROP and VACUUM.

3. **`profiles`** and **`domain_block_categories`**: Simple config data. The new PolicyStore uses different tables with different schemas (tiered categories: `ADULT_CORE`, `ADULT_EXTENDED`, `DISTRACTION`, `DOH_ENDPOINTS` instead of single `ADULT`). Not worth migrating 2 rows. DROP.

### D.4 VACUUM Safety

SQLite `VACUUM` rewrites the entire database file, ensuring no plaintext domain fragments remain in freed pages. This is critical for the privacy guarantee. The VACUUM runs inside the Room migration transaction.

### D.5 Schema v5 Contents

After `MIGRATION_4_5`, the database contains only Phase 1-2 tables:
- `sessions` (v1)
- `blocked_apps` (v2)
- `emergency_bypass_logs` (v2)
- `blocked_app_attempts` (v3)

New Phase 3 tables will be added in subsequent migrations as needed (R1 may add a `protection_settings` table; R4 adds the private list hash store).

---

## E. RISK LIST

### E.1 Build Failure After Deletion

| Risk | Mitigation |
|------|-----------|
| Phase 1-2 files import Phase 3 classes | Complete cross-reference inventory in section A.15 identifies every file. All surgical edits listed in section C.2. Build verified immediately after deletion commit. |
| Room schema mismatch (entities removed but version not bumped) | `MIGRATION_4_5` drops all 4 tables, version set to 5. `fallbackToDestructiveMigration()` NOT used — explicit migration only. |
| Hilt dependency graph breaks (missing bindings) | Remove all Phase 3 `@Provides` and `@Binds` from DI modules. Temporarily stub `PolicyStore` with an `@Singleton` class that exposes `StateFlow<PolicySnapshot>` with a default OFF snapshot. |

### E.2 Phase 1 (Timer) Regression

| Risk | Mitigation |
|------|-----------|
| TimerService is fully independent of Phase 3 | No imports from enforcement/, no PolicyRepository reference. Zero risk. |
| Timer notification channel unaffected | Only `VPN_CHANNEL_ID` is removed; Timer uses `TIMER_CHANNEL_ID`. |
| **Verification**: Start a focus session, let it complete, check notification and session log. | Manual phone test after R1. |

### E.3 Phase 2 (App Blocker) Regression

| Risk | Mitigation |
|------|-----------|
| `AppBlockerService` depends on `PolicySnapshot.isAppBlockingEffective` and `.blockedAppPackages` | The new `PolicyStore` exposes the exact same `PolicySnapshot` data class with the same property names. The import path changes but the API is identical. |
| `AppBlockerService` depends on `PollingGate` | PollingGate is rewritten with the same public API (`startListening()`, `stopListening()`, `isScreenInteractive()`, callback). AppBlockerService's constructor call doesn't change. |
| `BlockedAppsViewModel` depends on `ProfileScheduler.switchProfile()` | New ProfileScheduler has the same API. |
| Profile selector chips (`BlockingProfile.OFF`, `RELAXED`, `STRICT`) in Shield UI | `BlockingProfile` enum is rewritten with the same values. |
| `BootCompletedReceiver` starts DNS VPN on boot | Removed temporarily — only `AppBlockerService` restart on boot remains. Re-added in R3. |
| **Verification**: Select 2 apps to block, switch to STRICT, verify they're intercepted. Switch to OFF, verify they're unblocked. Reboot, verify AppBlockerService restarts. | Manual phone test after R1. |

### E.4 Database Integrity

| Risk | Mitigation |
|------|-----------|
| VACUUM fails mid-migration | Room migration runs in a transaction. If VACUUM fails, the entire migration rolls back. The old schema persists and the app works on the old code. |
| User loses custom domain entries | Acceptable — the app's DNS forwarding was broken, so entries were non-functional anyway. Document in release notes. |
| Phase 1-2 data (sessions, blocked apps, bypass logs, attempts) is preserved | MIGRATION_4_5 only drops Phase 3 tables. Phase 1-2 tables are untouched. Verified by querying session count before and after migration. |

### E.5 Device-Specific Concerns

| Risk | Mitigation |
|------|-----------|
| Accessibility service stays enabled in Android Settings after removal | Manual step: disable via `adb shell settings put secure enabled_accessibility_services ""` or through Settings > Accessibility on the phone. Document in R6 checklist. |
| VPN stays configured after removing DnsVpnService | VPN permission is per-app; removing the service declaration means Android will not route VPN traffic. No leftover risk. |
| Old notification channel "DNS Protection Guard" persists | Either delete programmatically in FocusForgeApp.onCreate() or document as harmless (empty channel, no notifications). I recommend deleting it: `notificationManager.deleteNotificationChannel(VPN_CHANNEL_ID)` as a cleanup step. |

---

## F. OPEN QUESTIONS

Before I begin R1, I need your input on:

### F1. Upstream DNS Resolver Preference
The design says "configurable upstream resolver." For the **default**, which do you prefer?
- **Option A**: System DNS (from `LinkProperties.dnsServers`) with public fallback (Cloudflare/Google) — this is what most DNS filter apps do
- **Option B**: Cloudflare 1.1.1.1 / Google 8.8.8.8 always — ignores carrier DNS
- **Concern**: On your Jio connection, carrier DNS (`192.0.0.1` CGNAT dummy) was unreachable. IPv4 public DNS also timed out. Only option that might work is IPv6 public DNS or trying `protect()` + `bindSocket()` more carefully. I'll investigate this in R3 with proper fallback chains.

### F2. Blocklist Source for ADULT_CORE
The old code used **StevenBlack/hosts (porn alternate)** which is MIT-licensed (GPLv3-compatible). It had false positives (`beacons*.gvt2.com` etc.). Options:
- **Option A**: Keep StevenBlack as ADULT_CORE source but filter through the Tranco top-10K false-positive guard (R2 will catch and remove false positives)
- **Option B**: Use a different curated list (suggestions welcome)
- **Option C**: Both — StevenBlack filtered through Tranco guard = CORE; raw StevenBlack = EXTENDED

### F3. ADULT_EXTENDED Source
For the EXTENDED tier (OFF by default, warning shown), which additional lists? Options:
- OISD NSFW list (license: CC BY-NC-SA 4.0 — **NOT GPLv3-compatible** for redistribution)
- URLhaus malware + adult combined
- Or skip EXTENDED entirely for now and add it later

### F4. Public Suffix List Source
Needed to refuse blocking public suffixes (`.com`, `.co.uk`, etc.) and shared hosting parents. The **Mozilla Public Suffix List** is MPL 2.0, which is GPLv3-compatible. Confirm this is acceptable, or suggest an alternative.

### F5. Existing Custom Domains
You mentioned the DNS forwarding was broken so the app was barely functional. Do you have any custom domain entries saved that you want preserved, or is it safe to drop all 4 Phase 3 tables with no migration?

### F6. Old Notification Channel Cleanup
Should I programmatically delete the old "DNS Protection Guard" notification channel in `FocusForgeApp.onCreate()` as a one-time cleanup, or just leave it (harmless but visible in app notification settings)?

### F7. Accessibility Service Cleanup
The `BrowserAccessibilityService` may still be enabled in your phone's accessibility settings. After R1, should I include an ADB command in the verification checklist to disable it, or will you handle it manually?
