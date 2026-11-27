#!/usr/bin/env python3
"""
FocusForge Blocklist Generator
Converts domain lists into compact, sorted binary files of 64-bit FNV-1a hashes.
Format: big-endian 8-byte uint64 per domain, sorted in ascending order for O(log N) mmap binary search.
"""

import struct
import os
import sys
import urllib.request

FNV_OFFSET_BASIS = 0xcbf29ce484222325
FNV_PRIME = 0x100000001b3
MASK64 = 0xffffffffffffffff

def fnv1a_64(domain: str) -> int:
    h = FNV_OFFSET_BASIS
    for b in domain.encode('utf-8'):
        h = (h ^ b) * FNV_PRIME
        h &= MASK64
    return h

def normalize_domain(raw: str) -> str:
    d = raw.strip().lower()
    if d.startswith("#") or not d:
        return ""
    parts = d.split()
    if len(parts) >= 2 and (parts[0] in ("0.0.0.0", "127.0.0.1", "::1")):
        d = parts[1]
    else:
        d = parts[0]
    
    d = d.removeprefix("http://").removeprefix("https://")
    d = d.split('/')[0].split(':')[0]
    d = d.removeprefix("www.").removesuffix(".")
    return d

CURATED_DOH_ENDPOINTS = [
    "cloudflare-dns.com", "one.one.one.one", "dns.google", "dns.google.com",
    "doh.opendns.com", "dns.quad9.net", "doh.cleanbrowsing.org", "adguard-dns.com",
    "dns.adguard.com", "doh.mullvad.net", "doh.tiar.app", "mozilla.cloudflare-dns.com",
    "chrome.cloudflare-dns.com", "dns.nextdns.io", "dns.switch.ch", "dns.digitale-gesellschaft.ch",
    "doh.applied-privacy.net", "doh.ffmuc.net", "doh.libredns.gr", "resolver2.dns.open-ch.ch",
    "doh.dns.sb", "jp.tiar.app", "dns.oszx.co", "doh.crypto.sx"
]

CURATED_DISTRACTION = [
    "tiktok.com", "tiktokv.com", "tiktokcdn.com", "musical.ly", "byteoversea.com", "ibytedtos.com",
    "instagram.com", "cdninstagram.com", "threads.net",
    "facebook.com", "fbcdn.net", "fbsbx.com", "fb.com",
    "twitter.com", "x.com", "twimg.com",
    "snapchat.com", "sc-cdn.net",
    "reddit.com", "redd.it", "redditstatic.com", "redditmedia.com",
    "youtube.com", "googlevideo.com", "ytimg.com", "youtu.be",
    "netflix.com", "nflxext.com", "nflximg.net", "nflxvideo.net",
    "twitch.tv", "ttvnw.net", "jtvnw.net",
    "hotstar.com", "disneyplus.com", "primevideo.com", "hulu.com",
    "pinterest.com", "pinimg.com", "tumblr.com", "9gag.com", "buzzfeed.com",
    "discord.com", "discordapp.com", "discordapp.net",
    "roblox.com", "epicgames.com", "steampowered.com", "steamcommunity.com"
]

def fetch_stevenblack_adult_domains():
    url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts"
    print(f"Fetching upstream StevenBlack porn blocklist from {url}...")
    domains = []
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'FocusForge-Blocklist-Builder/1.0'})
        with urllib.request.urlopen(req, timeout=30) as resp:
            for line in resp:
                line_str = line.decode('utf-8', errors='ignore')
                d = normalize_domain(line_str)
                if d and d not in ("localhost", "local", "broadcasthost"):
                    domains.append(d)
        print(f"Successfully fetched {len(domains)} upstream adult domains.")
        return domains
    except Exception as e:
        print(f"Warning: Failed to fetch upstream list ({e}), falling back to curated seed.")
        return []

def generate_binary(domain_list, output_path):
    hashes = set()
    for raw in domain_list:
        d = normalize_domain(raw)
        if d:
            hashes.add(fnv1a_64(d))
    
    sorted_hashes = sorted(hashes)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "wb") as f:
        for h in sorted_hashes:
            f.write(struct.pack(">Q", h))
    print(f"Generated {output_path} with {len(sorted_hashes)} unique domains ({os.path.getsize(output_path)} bytes)")

if __name__ == "__main__":
    base_dir = os.path.dirname(os.path.abspath(__file__))
    assets_dir = os.path.join(base_dir, "..", "app", "src", "main", "assets", "blocklists")
    
    generate_binary(CURATED_DOH_ENDPOINTS, os.path.join(assets_dir, "doh_endpoints.bin"))
    generate_binary(CURATED_DISTRACTION, os.path.join(assets_dir, "distraction.bin"))
    
    adult_domains = fetch_stevenblack_adult_domains()
    if not adult_domains:
        # Fallback list if internet is blocked
        adult_domains = ["pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "youporn.com"]
    generate_binary(adult_domains, os.path.join(assets_dir, "adult.bin"))
    print("All blocklists successfully compiled.")
