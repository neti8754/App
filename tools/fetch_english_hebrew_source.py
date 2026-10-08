#!/usr/bin/env python3
"""Fetch the open Wiktionary English→Hebrew wordlist by alphabet shard.

Source: User:Matthias_Buchmeier/en-he-[a-z] on English Wiktionary.
The resulting JSON contains English headwords, Hebrew translations, POS tags,
and SEE redirects. Keep source attribution and CC BY-SA notice with any export.
"""
from __future__ import annotations

import html
import json
import re
import time
from collections import defaultdict
from pathlib import Path

import requests

API = "https://en.wiktionary.org/w/api.php"
OUT = Path("build-dict/wiktionary_en_he.json")
REPORT = Path("build-dict/SOURCE-ATTRIBUTION.txt")
UA = "KindleEnglishHebrewDictionary/1.0 (dictionary build; contact via repository issues)"
HEBREW = re.compile(r"[\u0590-\u05FF]")
ASCII_HEAD = re.compile(r"^[A-Za-z0-9][A-Za-z0-9 .,'\-!?&]{0,99}$")
TAG = re.compile(r"\{([^{}]+)\}")

def strip_wiki(s: str) -> str:
    s = re.sub(r"\[\[([^|\]]+)\|([^\]]+)\]\]", r"\2", s)
    s = re.sub(r"\[\[([^\]]+)\]\]", r"\1", s)
    s = re.sub(r"\{\{[^{}]*\}\}", " ", s)
    s = re.sub(r"'''?", "", s)
    s = re.sub(r"<[^>]*>", " ", s)
    return html.unescape(s)

def normal_head(s: str) -> str:
    s = strip_wiki(s)
    s = re.sub(r"^(?:\*|#|:)+\s*", "", s).strip()
    s = re.sub(r"\s+", " ", s)
    return s.strip(" \t*#")

def parse_pos(left: str):
    m = re.search(r"\{([^{}]+)\}", left)
    if not m:
        return set()
    raw = m.group(1).strip().lower()
    aliases = {
        "n": "n", "noun": "n", "adj": "adj", "adjective": "adj",
        "v": "v", "verb": "v", "adv": "adv", "adverb": "adv",
        "pron": "pron", "pronoun": "pron", "prep": "prep",
        "preposition": "prep", "interj": "interj", "interjection": "interj",
        "det": "det", "determiner": "det", "num": "num", "numeral": "num",
        "prop": "prop", "proper noun": "prop", "phrase": "phrase",
        "idiom": "phrase", "proverb": "phrase", "conj": "conj",
        "conjunction": "conj", "suffix": "suffix", "prefix": "prefix",
        "letter": "n", "pronunciation": "n", "abbrev": "n",
        "abbreviation": "n", "part": "n",
    }
    return {aliases[part.strip()] for part in re.split(r"[,;/]", raw) if part.strip() in aliases}

def parse_hebrew(right: str) -> list[str]:
    s = strip_wiki(right)
    s = re.sub(r"/[^/]{1,100}/", " ", s)     # pronunciation/transliteration
    s = re.sub(r"\{[^{}]*\}", " ", s)        # grammatical gender/number notes
    s = re.sub(r"\([^()]*\)", " ", s)        # romanization/parenthetical notes
    # Keep text groups that actually contain Hebrew letters; trim separators.
    found = re.findall(r"[\u0590-\u05FF][\u0590-\u05FF\s\u05F3\u05F4\u05BE\-'״׳.,;:!?…/]*", s)
    cleaned = []
    for bit in found:
        bit = re.sub(r"\s+", " ", bit).strip(" \t,;:!?./-")
        bit = re.sub(r"\s*[,;]\s*", "; ", bit)
        bit = re.sub(r"\s+", " ", bit).strip(" ;")
        if bit and HEBREW.search(bit) and bit not in cleaned:
            cleaned.append(bit)
    # Some rows have multiple linked Hebrew alternatives separated by punctuation.
    terms = []
    for bit in cleaned:
        for term in re.split(r"\s*[;,]\s*", bit):
            term = term.strip(" \t,;:!?./-")
            if term and HEBREW.search(term) and term not in terms:
                terms.append(term)
    return terms

def parse_row(line: str):
    if " | ::" not in line:
        return None
    left, right = line.split(" | ::", 1)
    left_clean = strip_wiki(left).strip()
    m = re.match(r"^(.*?)\s+\{([^{}]+)\}(?:\s+\((.*?)\))?(?:\s+.*)?$", left_clean)
    if not m:
        return None
    word = normal_head(m.group(1))
    if not word or not ASCII_HEAD.fullmatch(word):
        return None
    # Ignore navigation/heading artifacts rather than lexical entries.
    if word.startswith(("http", "User:", "File:")):
        return None
    pos = parse_pos(left_clean)
    translations = parse_hebrew(right)
    see = None
    see_m = re.search(r"\bSEE:\s*([A-Za-z0-9][A-Za-z0-9 .,'’'\-]{0,99})", left_clean, re.I)
    if see_m:
        see = normal_head(see_m.group(1)).strip(" .,:;")
    return word.lower(), translations, pos, see

def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    sess = requests.Session()
    sess.headers.update({"User-Agent": UA})
    merged = {}
    redirects = {}
    fetched = 0
    errors = []
    for letter in "abcdefghijklmnopqrstuvwxyz":
        title = f"User:Matthias_Buchmeier/en-he-{letter}"
        params = {
            "action": "query", "format": "json", "formatversion": "2",
            "prop": "revisions", "rvprop": "content", "rvslots": "main",
            "titles": title,
        }
        response = sess.get(API, params=params, timeout=90)
        response.raise_for_status()
        data = response.json()
        page = data.get("query", {}).get("pages", [{}])[0]
        if page.get("missing") or not page.get("revisions"):
            errors.append(f"Missing shard: {title}")
            continue
        raw = page["revisions"][0].get("slots", {}).get("main", {}).get("content", "")
        if len(raw) < 300:
            errors.append(f"Unexpectedly short shard: {title} ({len(raw)} bytes)")
            continue
        fetched += 1
        for line in raw.splitlines():
            row = parse_row(line)
            if not row:
                continue
            word, trans, pos, see = row
            obj = merged.setdefault(word, {"word": word, "translations": [], "pos": [], "see": []})
            for tr in trans:
                if tr not in obj["translations"] and len(tr) <= 110 and len(obj["translations"]) < 10:
                    obj["translations"].append(tr)
            for tag in pos:
                if tag not in obj["pos"]:
                    obj["pos"].append(tag)
            if see and see.lower() not in [x.lower() for x in obj["see"]]:
                obj["see"].append(see)
        time.sleep(0.15)

    rows = []
    for word, obj in merged.items():
        obj["pos"].sort()
        if obj["translations"] or obj["see"]:
            rows.append(obj)
    rows.sort(key=lambda x: x["word"].casefold())
    if fetched < 24:
        raise RuntimeError(f"Only {fetched}/26 alphabet shards fetched; refusing partial export. {errors}")
    with OUT.open("w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, separators=(",", ":"))
    REPORT.write_text(
        "Source: User:Matthias_Buchmeier/en-he-a through en-he-z, English Wiktionary\n"
        "Source project: https://en.wiktionary.org/wiki/User:Matthias_Buchmeier\n"
        "License stated on source: Creative Commons Attribution-ShareAlike 3.0 Unported; GNU Free Documentation License\n"
        "Dictionary source version stated on source pages: 20200901\n"
        f"Alphabet shards fetched: {fetched}/26\n"
        f"Distinct English headwords/phrases parsed: {len(rows):,}\n"
        f"Entries with Hebrew translation: {sum(bool(x['translations']) for x in rows):,}\n"
        f"Entries with SEE redirects: {sum(bool(x['see']) for x in rows):,}\n"
        f"Warnings: {len(errors)}\n" + "\n".join(errors) + "\n",
        encoding="utf-8",
    )
    print(f"Fetched {fetched}/26 shards; parsed {len(rows)} unique entries; "
          f"{sum(bool(x['translations']) for x in rows)} have Hebrew.")
    if len(rows) < 10000:
        raise RuntimeError(f"Only {len(rows)} entries parsed, expected a comprehensive source; inspect source format.")

if __name__ == "__main__":
    main()
