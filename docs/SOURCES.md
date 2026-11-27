# FocusForge Blocklist Sources

The blocklists used in FocusForge are curated from verified GPLv3-compatible and permissive open-source repositories, stripped of comments, normalized, and compiled into compact binary search files.

| Category | Source | Upstream URL | License | Binary File |
|---|---|---|---|---|
| **ADULT** | Curated seed + StevenBlack hosts extension | https://github.com/StevenBlack/hosts | MIT | `assets/blocklists/adult.bin` |
| **DISTRACTION** | Curated short-video, social media, & streaming | FocusForge curated set | GPLv3 / MIT | `assets/blocklists/distraction.bin` |
| **DOH_ENDPOINTS** | Public DNS-over-HTTPS resolvers list | https://github.com/curl/curl/wiki/DNS-over-HTTPS | Public Domain / MIT | `assets/blocklists/doh_endpoints.bin` |

## Binary Format Specification
Each `.bin` file is a packed array of 64-bit unsigned integers (big-endian):
- **Hash function**: FNV-1a 64-bit (`offset_basis = 0xcbf29ce484222325`, `prime = 0x100000001b3`)
- **Ordering**: Ascending binary order
- **Search complexity**: $O(\log_2 N)$ via `java.nio.LongBuffer` binary search over memory-mapped file
- **RAM footprint**: Zero heap allocations. Handled directly by kernel page cache.
