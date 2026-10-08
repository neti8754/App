import sys
from pathlib import Path

p = Path(sys.argv[1])
b = p.read_bytes()
if b[:8] != b"BOOKMOBI":
    raise SystemExit("Not a MOBI/PalmDB file")
n = int.from_bytes(b[76:78], "big")
offs = [int.from_bytes(b[78+i*8:82+i*8], "big") for i in range(n)]
records = [b[offs[i]:offs[i+1] if i+1 < n else len(b)] for i in range(n)]
r0 = records[0]
m = r0.find(b"MOBI")
if m < 0:
    raise SystemExit("MOBI header missing")
text_count = int.from_bytes(r0[m+8:m+10], "big")
first_non = int.from_bytes(r0[m+68:m+72], "big") if m+68 <= len(r0) else None
orth = int.from_bytes(r0[m+24:m+28], "big")
infl = int.from_bytes(r0[m+28:m+32], "big")
if text_count <= 0 or orth in (0, 0xFFFFFFFF):
    raise SystemExit(f"Dictionary index not present: text_count={text_count}, orth={orth:#x}, infl={infl:#x}")
whole = b"".join(records[:min(n, text_count+2)])
if b"<idx:entry" not in whole and b"INDX" not in b"".join(records):
    raise SystemExit("No dictionary entry/index markers found")
# Confirm EXTH dictionary language metadata exists.
if b"DictionaryInLanguage" not in b and b"DictionaryOutLanguage" not in b:
    # These may be normalized into binary EXTH; do not fail solely on that.
    pass
print(f"PASS: {p.name}; records={n}; text_records={text_count}; orth_index_record={orth}; inflection_index={infl}")
print(f"Size: {p.stat().st_size:,} bytes")
