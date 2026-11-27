# Third-Party Dependencies and Sources

This file records all dependencies, tools, upstream blocklists, and corpora used in FocusForge, ensuring strict compatibility with GPLv3 and redistribution.

| Name | Role | URL | License | Version / Date Fetched | Tier / Scope |
|---|---|---|---|---|---|
| **AndroidX Core, Lifecycle, Compose** | Android Framework & UI | https://developer.android.com/jetpack | Apache 2.0 | AndroidX BOM / Jetpack | App Runtime |
| **Room** | Local SQLite persistence | https://developer.android.com/training/data-storage/room | Apache 2.0 | 2.6.1 | App Runtime |
| **Hilt** | Dependency Injection | https://dagger.dev/hilt/ | Apache 2.0 | 2.51.1 | App Runtime |
| **JUnit** | Unit Testing Framework | https://junit.org/junit4/ | EPL 2.0 | 4.13.2 | Test Only |
| **StevenBlack/hosts (Porn alternate)** | Adult domain blocklist | https://github.com/StevenBlack/hosts | MIT | 2026-11-27 | `ADULT_CORE` (FP-filtered) & `ADULT_EXTENDED` |
| **Mozilla Public Suffix List** | Public suffix & shared-hosting guard | https://publicsuffix.org/list/public_suffix_list.dat | MPL 2.0 | 2026-11-27 | `public_suffix_hashes.bin` |
| **Tranco Top-1M (Top 10K slice)** | False-positive guard corpus | https://tranco-list.eu/ | CC BY 4.0 | 2026-11-27 | Build / CI Tooling Only |
| **curl DoH endpoints** | Public DoH bypass prevention | https://github.com/curl/curl/wiki/DNS-over-HTTPS | Public Domain / MIT | 2026-11-27 | `DOH_ENDPOINTS` |
