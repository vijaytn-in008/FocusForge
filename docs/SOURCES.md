# FocusForge Blocklist Sources

All blocklists and corpora bundled with FocusForge are verified for GPLv3-compatible redistribution, normalized (lowercase, trailing-dot stripped, IDN Punycode), filtered against the Mozilla Public Suffix List, shared-hosting/CDN parents, and the Tranco Top-10K false-positive guard, and compiled into sorted 64-bit FNV-1a binary files.

| Tier / Asset | Source | Upstream URL | License | Version / Date | Entries | Measured FP Rate (Tranco 10K) |
|---|---|---|---|---|---:|---:|
| `ADULT_CORE` (`adult_core.bin`) | StevenBlack/hosts (porn alternate, FP-filtered) | https://github.com/StevenBlack/hosts | MIT | 2026-11-28 | 143,681 | 0.0000% |
| `ADULT_EXTENDED` (`adult_extended.bin`) | StevenBlack/hosts (porn alternate, broader) | https://github.com/StevenBlack/hosts | MIT | 2026-11-28 | 157,239 | 0.0000% |
| `DISTRACTION` (`distraction.bin`) | FocusForge curated distraction domains (STRICT profile only) | https://github.com/vijay/FocusForge | GPLv3 | 2026-11-28 | 48 | N/A (opt-in profile) |
| `DOH_ENDPOINTS` (`doh_endpoints.bin`) | Public DNS-over-HTTPS resolvers (curl wiki) | https://github.com/curl/curl/wiki/DNS-over-HTTPS | Public Domain / MIT | 2026-11-28 | 24 | N/A (DoH bypass guard) |
| `public_suffix_hashes.bin` | Mozilla Public Suffix List + shared hosting parents | https://publicsuffix.org/list/public_suffix_list.dat | MPL 2.0 | 2026-11-28 | 10,515 | N/A (safety guard) |

## False-Positive Guard Corpus
- **Tranco Top-10K**: https://tranco-list.eu/ (CC BY 4.0, fetched 2026-11-28, 10,000 popular domains)
- **Filtered out of `ADULT_CORE`**: 14281 domains overlapping Tranco non-adult top-10k; 12 public suffixes / OS & CDN parents rejected completely.

## Binary Format Specification
Each `.bin` file is a headerless packed array of big-endian 64-bit unsigned integers (`uint64`):
- **Hash function**: FNV-1a 64-bit (`offset_basis = 0xcbf29ce484222325`, `prime = 0x100000001b3`) over Punycode ASCII normalized domains
- **Ordering**: Ascending unsigned 64-bit order for $O(\log_2 N)$ memory-mapped binary search
- **Integrity**: Verified via SHA-256 checksums in `assets/blocklists/manifest.json`
