#!/usr/bin/env python3
"""
FocusForge CI Blocklist Verifier (R2)
Fails with non-zero exit code if:
1. Any .bin file hash mismatches assets/blocklists/manifest.json.
2. ADULT_CORE matches ANY non-adult domain in Tranco Top-10K.
3. ADULT_CORE fails to match ANY domain in must_block_adult.txt.
4. Any blocklist .bin contains a public suffix or shared hosting/CDN parent.
"""

import bisect
import hashlib
import json
import os
import struct
import sys

from build_blocklists import (
    ASSETS_DIR,
    CORPUS_DIR,
    fnv1a_64,
    load_domain_lines,
)


def load_sorted_hashes(bin_path: str) -> list[int]:
    data = open(bin_path, "rb").read()
    if len(data) % 8 != 0:
        raise ValueError(f"{bin_path} size {len(data)} is not a multiple of 8")
    count = len(data) // 8
    return list(struct.unpack(f">{count}Q", data))


def contains_hash(sorted_hashes: list[int], target: int) -> bool:
    idx = bisect.bisect_left(sorted_hashes, target)
    return idx < len(sorted_hashes) and sorted_hashes[idx] == target


def matches_suffix_aware(domain: str, sorted_hashes: list[int]) -> bool:
    parts = domain.split(".")
    for i in range(len(parts) - 1):
        candidate = ".".join(parts[i:])
        if contains_hash(sorted_hashes, fnv1a_64(candidate)):
            return True
    return False


def main() -> int:
    manifest_path = os.path.join(ASSETS_DIR, "manifest.json")
    manifest = json.load(open(manifest_path, "r", encoding="utf-8"))

    # 1. Verify SHA-256 checksums
    for name, meta in manifest["assets"].items():
        path = os.path.join(ASSETS_DIR, name)
        actual_sha = hashlib.sha256(open(path, "rb").read()).hexdigest()
        if actual_sha != meta["sha256"]:
            print(f"FAIL: {name} sha256 mismatch: expected {meta['sha256']}, got {actual_sha}")
            return 1

    adult_core = load_sorted_hashes(os.path.join(ASSETS_DIR, "adult_core.bin"))
    adult_extended = load_sorted_hashes(os.path.join(ASSETS_DIR, "adult_extended.bin"))
    distraction = load_sorted_hashes(os.path.join(ASSETS_DIR, "distraction.bin"))
    doh = load_sorted_hashes(os.path.join(ASSETS_DIR, "doh_endpoints.bin"))
    psl_hashes = set(load_sorted_hashes(os.path.join(ASSETS_DIR, "public_suffix_hashes.bin")))

    # 2. Ensure NO public suffix or shared-hosting parent is in any blocklist
    for name, hashes in [
        ("adult_core.bin", adult_core),
        ("adult_extended.bin", adult_extended),
        ("distraction.bin", distraction),
        ("doh_endpoints.bin", doh),
    ]:
        overlap = set(hashes) & psl_hashes
        if overlap:
            print(f"FAIL: {name} contains {len(overlap)} public suffix / shared-parent hashes!")
            return 1

    # 3. Tranco Top-10K False-Positive Guard on ADULT_CORE
    must_block = set(load_domain_lines(os.path.join(CORPUS_DIR, "must_block_adult.txt")))
    tranco = set(load_domain_lines(os.path.join(CORPUS_DIR, "tranco_top10k.txt"))) - must_block
    fps = [d for d in sorted(tranco) if matches_suffix_aware(d, adult_core)]
    if fps:
        print(f"FAIL: ADULT_CORE blocked {len(fps)} popular Tranco domains: {fps[:20]}")
        return 1

    # 4. Must-Block Adult Corpus Check
    missed = [d for d in sorted(must_block) if not matches_suffix_aware(d, adult_core)]
    if missed:
        print(f"FAIL: ADULT_CORE missed {len(missed)} must-block adult domains: {missed}")
        return 1

    print(
        f"PASS: All blocklist checks succeeded "
        f"(ADULT_CORE={len(adult_core)}, Tranco checked={len(tranco)}, must_block={len(must_block)}, 0 FPs)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
