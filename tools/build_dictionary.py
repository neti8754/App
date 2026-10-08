from pathlib import Path
import html
import re
import unicodedata
import requests

ROOT = Path("build-dict")
ROOT.mkdir(parents=True, exist_ok=True)

RAW_URL = "https://en.wiktionary.org/w/index.php?title=User%3AMatthias_Buchmeier%2Fen-he-a&action=raw"
r = requests.get(RAW_URL, timeout=60, headers={"User-Agent": "Mozilla/5.0 Kindle-Dictionary-Builder"})
r.raise_for_status()
raw = r.text
if "Creative Commons Attribution-ShareAlike" not in raw and "19339 English glosses" not in raw:
    raise RuntimeError("Unexpected Wiktionary source response")

raw = raw.replace("\\r", "")
entries = {}

def clean_markup(s):
    s = re.sub(r"\\[\\[([^\\]|]+)\\|([^\\]]+)\\]\\]", r"\\2", s)
    s = re.sub(r"\\[\\[([^\\]]+)\\]\\]", r"\\1", s)
    s = re.sub(r"\\{\\{[^}]+\\}\\}", "", s)
    s = re.sub(r"'''?", "", s)
    s = s.replace("&nbsp;", " ")
    s = re.sub(r"\\s+", " ", s).strip()
    return html.escape(s, quote=False)

def hebrew_only(s):
    parts = re.findall(r"[\\u0590-\\u05FF][\\u0590-\\u05FF\\s'\\-\\.\\/\\u05F3\\u05F4]*", s)
    out = " ".join(p.strip(" ,;") for p in parts)
    out = re.sub(r"\\s+", " ", out).strip(" ,;")
    return out

def strip_translation_meta(s):
    s = clean_markup(s)
    s = re.sub(r"\\s*\\{[^}]+\\}\\s*", " ", s)
    s = re.sub(r"/[^/]{1,60}/", " ", s)
    s = re.sub(r"\\s+", " ", s).strip()
    return s

for line in raw.splitlines():
    if " | ::" not in line:
        continue
    left, right = line.split(" | ::", 1)
    left = left.strip()
    right = right.strip()
    m = re.match(r"^(.+?)\\s+\\{([^}]+)\\}(?:\\s+\\((.*?)\\))?(?:\\s+.*)?$", left)
    if not m:
        continue
    head = clean_markup(m.group(1)).strip()
    pos = m.group(2).strip().lower()
    gloss = clean_markup(m.group(3) or "").strip()
    if not re.search(r"[A-Za-z]", head):
        continue
    if len(head) > 80:
        continue

    # Prefer actual Hebrew letters, ignoring gender/grammar markers.
    rhs_plain = re.sub(r"\\{[^}]+\\}", " ", right)
    rhs_plain = re.sub(r"/[^/]{1,80}/", " ", rhs_plain)
    rhs_plain = re.sub(r"\\[\\[([^\\]|]+)\\|([^\\]]+)\\]\\]", r"\\2", rhs_plain)
    rhs_plain = re.sub(r"\\[\\[([^\\]]+)\\]\\]", r"\\1", rhs_plain)
    rhs_plain = rhs_plain.replace("SEE:", " ")
    rhs_plain = re.sub(r"\\s+", " ", rhs_plain).strip(" ;,")
    heb = hebrew_only(rhs_plain)
    if not heb:
        # Fall back to cleaned target because some translations are short
        # or contain punctuation not captured by the Hebrew-only regex.
        cand = strip_translation_meta(right)
        heb = hebrew_only(cand)
    if not heb:
        continue

    key = head.lower()
    rec = entries.setdefault(key, {"head": head, "pos": set(), "glosses": [], "he": []})
    if pos:
        rec["pos"].add(pos)
    if gloss and gloss not in rec["glosses"]:
        rec["glosses"].append(gloss)
    for t in re.split(r"[,;]", heb):
        t = t.strip(" .")
        if t and t not in rec["he"] and len(t) <= 90:
            rec["he"].append(t)

# Better-known irregular inflections.
IRREG = {
    "be": ["am", "is", "are", "was", "were", "been", "being"],
    "have": ["has", "had", "having"],
    "do": ["does", "did", "done", "doing"],
    "go": ["goes", "went", "gone", "going"],
    "say": ["says", "said", "saying"],
    "make": ["makes", "made", "making"],
    "see": ["sees", "saw", "seen", "seeing"],
    "get": ["gets", "got", "gotten", "getting"],
    "take": ["takes", "took", "taken", "taking"],
    "come": ["comes", "came", "come", "coming"],
    "know": ["knows", "knew", "known", "knowing"],
    "think": ["thinks", "thought", "thinking"],
    "give": ["gives", "gave", "given", "giving"],
    "find": ["finds", "found", "finding"],
    "tell": ["tells", "told", "telling"],
    "become": ["becomes", "became", "becoming"],
    "show": ["shows", "showed", "shown", "showing"],
    "leave": ["leaves", "left", "leaving"],
    "feel": ["feels", "felt", "feeling"],
    "put": ["puts", "putting"],
    "bring": ["brings", "brought", "bringing"],
    "begin": ["begins", "began", "begun", "beginning"],
    "keep": ["keeps", "kept", "keeping"],
    "hold": ["holds", "held", "holding"],
    "write": ["writes", "wrote", "written", "writing"],
    "stand": ["stands", "stood", "standing"],
    "hear": ["hears", "heard", "hearing"],
    "let": ["lets", "letting"],
    "mean": ["means", "meant", "meaning"],
    "meet": ["meets", "met", "meeting"],
    "run": ["runs", "ran", "run", "running"],
    "pay": ["pays", "paid", "paying"],
    "sit": ["sits", "sat", "sitting"],
    "speak": ["speaks", "spoke", "spoken", "speaking"],
    "lie": ["lies", "lay", "lain", "lying"],
    "lead": ["leads", "led", "leading"],
    "read": ["reads", "read", "reading"],
    "grow": ["grows", "grew", "grown", "growing"],
    "lose": ["loses", "lost", "losing"],
    "fall": ["falls", "fell", "fallen", "falling"],
    "send": ["sends", "sent", "sending"],
    "build": ["builds", "built", "building"],
    "understand": ["understands", "understood", "understanding"],
    "break": ["breaks", "broke", "broken", "breaking"],
    "spend": ["spends", "spent", "spending"],
    "cut": ["cuts", "cutting"],
    "rise": ["rises", "rose", "risen", "rising"],
    "drive": ["drives", "drove", "driven", "driving"],
    "buy": ["buys", "bought", "buying"],
    "wear": ["wears", "wore", "worn", "wearing"],
    "choose": ["chooses", "chose", "chosen", "choosing"],
    "eat": ["eats", "ate", "eaten", "eating"],
    "catch": ["catches", "caught", "catching"],
    "teach": ["teaches", "taught", "teaching"],
    "sell": ["sells", "sold", "selling"],
    "fight": ["fights", "fought", "fighting"],
    "throw": ["throws", "threw", "thrown", "throwing"],
    "shut": ["shuts", "shut", "shutting"],
}

def simple_plural(w):
    if not re.fullmatch(r"[A-Za-z]+", w): return None
    lw = w.lower()
    if lw.endswith("y") and len(w) > 1 and w[-2].lower() not in "aeiou":
        return w[:-1] + "ies"
    if lw.endswith(("s", "x", "z", "ch", "sh")):
        return w + "es"
    return w + "s"

def simple_verb_forms(w):
    if not re.fullmatch(r"[A-Za-z]+", w): return []
    lw = w.lower()
    if lw in IRREG:
        return IRREG[lw]
    forms = []
    third = (w[:-1] + "ies") if lw.endswith("y") and len(w) > 1 and w[-2].lower() not in "aeiou" else (w + "es" if lw.endswith(("s","x","z","ch","sh")) else w + "s")
    ing = w + "ing"
    if lw.endswith("e") and not lw.endswith("ee"):
        ing = w[:-1] + "ing"
    past = w + "ed"
    if lw.endswith("e"):
        past = w + "d"
    elif lw.endswith("y") and len(w) > 1 and w[-2].lower() not in "aeiou":
        past = w[:-1] + "ied"
    forms.extend([third, past, ing])
    return forms

def inflections(head, pos):
    if not re.fullmatch(r"[A-Za-z]+(?:[-'][A-Za-z]+)*", head):
        return []
    lw = head.lower()
    out = []
    if lw in IRREG:
        out.extend(IRREG[lw])
    if "n" in pos:
        p = simple_plural(head)
        if p: out.append(p)
    if "v" in pos:
        out.extend(simple_verb_forms(head))
    if "adj" in pos and len(head) <= 12:
        out.extend([head + "er", head + "est"])
    return sorted(set(x for x in out if x.lower() != lw), key=str.lower)

# Only build entries with at least one Hebrew translation.
items = sorted(entries.values(), key=lambda x: x["head"].lower())
items = [x for x in items if x["he"]]
if len(items) < 15000:
    raise RuntimeError(f"Parsed only {len(items)} entries; source parsing likely failed")

# Group by first letter. We keep numbers/symbols with A.
groups = {c: [] for c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ"}
for item in items:
    c = item["head"][0].upper()
    groups[c if c in groups else "A"].append(item)

def esc(s):
    return html.escape(str(s), quote=True)

# Minimal stylesheet. Direction is explicit for Hebrew while the headword remains LTR.
css = """body { font-family: serif; margin: 0; }
.entry { margin: 0 0 0.4em 0; }
.head { font-size: 1.15em; }
.he { direction: rtl; unicode-bidi: plaintext; text-align: right; }
.meta { font-size: 0.85em; }
.gloss { font-size: 0.9em; }
hr { height: 1px; border: 0; }
.letter { page-break-before: always; }
"""
(ROOT/"style.css").write_text(css, encoding="utf-8")

spine = []
for letter in "ABCDEFGHIJKLMNOPQRSTUVWXYZ":
    fn = f"{letter}.html"
    path = ROOT/fn
    parts = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!DOCTYPE html>',
        '<html xmlns="http://www.w3.org/1999/xhtml" xmlns:mbp="http://mobipocket.com/ns/mbp" xmlns:idx="http://www.mobipocket.com/ns/idx">',
        '<head><meta charset="utf-8"/><title>English-Hebrew '+letter+'</title><link rel="stylesheet" type="text/css" href="style.css"/></head>',
        '<body><mbp:frameset>',
        f'<h2>{letter}</h2>'
    ]
    eid = 0
    for item in groups[letter]:
        eid += 1
        head = item["head"]
        pos = ", ".join(sorted(item["pos"]))
        inf = inflections(head, item["pos"])
        parts.append(f'<idx:entry name="English" scriptable="yes" spell="yes"><a id="{letter}{eid}"></a>')
        parts.append(f'<idx:orth value="{esc(head)}"><b class="head">{esc(head)}</b>')
        if inf:
            parts.append('<idx:infl>')
            for form in inf:
                parts.append(f'<idx:iform value="{esc(form)}"/>')
            parts.append('</idx:infl>')
        parts.append('</idx:orth>')
        if pos:
            parts.append(f'<div class="meta">{esc(pos)}</div>')
        if item["glosses"]:
            # Keep one concise English sense line; translations are the primary result.
            g = "; ".join(item["glosses"][:3])
            parts.append(f'<div class="gloss">{esc(g)}</div>')
        parts.append('<div class="he" dir="rtl">' + esc(" ; ".join(item["he"][:8])) + '</div>')
        parts.append('<hr/></idx:entry>')
    parts.append('</mbp:frameset></body></html>')
    path.write_text("\\n".join(parts), encoding="utf-8")
    spine.append(letter)

opf = '''<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="uid" version="2.0">
<metadata>
<dc-metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:title>English-Hebrew Expanded Dictionary</dc:title>
<dc:language>en</dc:language>
<dc:identifier id="uid">eng-he-expanded-20261009</dc:identifier>
<dc:creator>Wiktionary-derived bilingual dictionary</dc:creator>
<dc:subject>Dictionary</dc:subject>
<dc:description>English to Hebrew offline Kindle dictionary. Headwords, multiple Hebrew translations, English inflection lookup, and Wiktionary attribution.</dc:description>
</dc-metadata>
<meta name="DictionaryInLanguage" content="en"/>
<meta name="DictionaryOutLanguage" content="he"/>
</metadata>
<manifest>
<item id="css" href="style.css" media-type="text/css"/>
'''
for letter in spine:
    opf += f'<item id="{letter}" href="{letter}.html" media-type="application/xhtml+xml"/>\\n'
opf += '</manifest><spine toc="ncx">\\n'
for letter in spine:
    opf += f'<itemref idref="{letter}"/>\\n'
opf += '</spine><guide><reference type="text" title="A" href="A.html"/></guide></package>\\n'
(ROOT/"dictionary.opf").write_text(opf, encoding="utf-8")

(ROOT/"BUILD-REPORT.txt").write_text(
    "English-Hebrew Expanded Kindle Dictionary\\n"
    f"Headwords with at least one Hebrew translation: {len(items):,}\\n"
    f"Source: Wiktionary User:Matthias Buchmeier/en-he-a, Version 20200901\\n"
    "Source license: Creative Commons Attribution-ShareAlike 3.0 Unported License; GNU Free Documentation License\\n"
    "Format: Kindle MOBI 7 dictionary via Amazon KindleGen 2.9\\n"
    "Lookup: idx:orth + idx:infl/idx:iform\\n"
    "Hebrew display: explicit RTL container; natural Hebrew Unicode order\\n",
    encoding="utf-8"
)

print(f"Parsed {len(items)} translated headwords")
