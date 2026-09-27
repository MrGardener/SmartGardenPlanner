#!/usr/bin/env python3
"""Requirements trace generator and checker (T2 -> HLR -> LLR).

Usage:
  python3 tools/req_trace.py --write   regenerate the trace tables inside the HLR and LLR documents
  python3 tools/req_trace.py --check   fail (exit 1) if a table is stale or the trace has gaps

Checks:
  - every active or suspended T2 requirement is implemented by at least one HLR
  - every active HLR is implemented by at least one LLR ([Future]/[Suspended] HLRs are deferred)
  - every HLR has a T2 parent and every LLR has an HLR parent
  - no link points to an unknown ID, and no ID is defined twice

Tool assessment: SGP-TQP-001 (TQL-5 if trace credit is taken from this tool, DW-1403).
"""
import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
REQ = ROOT / "docs" / "requirements"
T2_DOC = REQ / "SGP-SYS-REQ-001_System_Requirements_T2.md"
HLR_DOC = REQ / "SGP-SW-HLR-001_Software_High_Level_Requirements.md"
LLR_DOC = REQ / "SGP-SW-LLR-001_Software_Low_Level_Requirements.md"
HLR_MARK = "<!-- TRACE-TABLE -->"
LLR_MARK = "<!-- LLR-TRACE -->"


def read(path):
    return path.read_text(encoding="utf-8")


def parse_t2(text):
    """Return {id: (title, status)} in document order."""
    reqs = collections.OrderedDict()
    pattern = r"^### (T2-[A-Z]+-\d+) — (.+?)\n(.*?)(?=^### |^## )"
    for m in re.finditer(pattern, text, re.S | re.M):
        block = m.group(3)
        status_text = block.split("Status:")[1][:60] if "Status:" in block else ""
        if "Merged into" in block:
            status = "Merged"
        elif "Suspended" in status_text:
            status = "Suspended"
        else:
            status = "Active"
        title = re.sub(r" \(NEW[^)]*\)", "", m.group(2))
        reqs[m.group(1)] = (title, status)
    return reqs


def split_blocks(body, id_regex):
    """Split text into blocks that each start with **ID (or a heading, which ends a block)."""
    parts = re.split(r"(?=^\*\*%s)|(?=^#{2,3} )" % id_regex, body, flags=re.M)
    for part in parts:
        m = re.match(r"\*\*(%s)([^\n]*)" % id_regex, part)
        if m:
            yield m.group(1), m.group(2), part


def links(block, arrow, target_regex):
    ids = []
    for chunk in block.split(arrow)[1:]:
        ids += re.findall(target_regex, chunk)
    return list(dict.fromkeys(ids))


def parse_level(text, id_regex, arrow, target_regex, stop):
    body = text.split(stop)[0]
    defined = re.findall(r"^\*\*(%s)" % id_regex, body, flags=re.M)
    dups = sorted(k for k, v in collections.Counter(defined).items() if v > 1)
    fwd, flags = collections.OrderedDict(), {}
    for rid, first_line, block in split_blocks(body, id_regex):
        fwd[rid] = links(block, arrow, target_regex)
        if "[Future]" in first_line:
            flags[rid] = "Future"
        elif "[Suspended]" in first_line:
            flags[rid] = "Suspended"
        else:
            flags[rid] = "Active"
    return fwd, flags, dups


def reverse(fwd):
    rev = collections.defaultdict(list)
    for src, targets in fwd.items():
        for t in targets:
            rev[t].append(src)
    return rev


def hlr_table(t2, rev):
    rows = ["| T2 requirement | Title | T2 status | Implemented by HLR |", "|---|---|---|---|"]
    for rid, (title, status) in t2.items():
        if rev.get(rid):
            cell = ", ".join(rev[rid])
        elif status == "Merged":
            cell = "— (merged into T2-FUN-080)"
        else:
            cell = "**NOT COVERED**"
        rows.append(f"| {rid} | {title} | {status} | {cell} |")
    return "\n".join(rows)


def llr_table(hlr_flags, rev):
    rows = ["| HLR | Status | Implemented by LLR |", "|---|---|---|"]
    for rid, status in hlr_flags.items():
        if rev.get(rid):
            cell = ", ".join(rev[rid])
        elif status != "Active":
            cell = "deferred (§6)"
        else:
            cell = "**NOT COVERED**"
        rows.append(f"| {rid} | {status} | {cell} |")
    return "\n".join(rows)


def listed(items):
    return (" — " + ", ".join(items)) if items else ""


def main(mode):
    t2_text, hlr_text, llr_text = read(T2_DOC), read(HLR_DOC), read(LLR_DOC)
    t2 = parse_t2(t2_text)
    hlr_fwd, hlr_flags, hlr_dups = parse_level(
        hlr_text, r"HLR-[A-Z]+-\d+", "→", r"T2-[A-Z]+-\d+", "## 20. HLR-level values")
    llr_fwd, _, llr_dups = parse_level(
        llr_text, r"LLR-[A-Z0-9]+-\d+", "↑", r"HLR-[A-Z]+-\d+", LLR_MARK)
    t2_rev, hlr_rev = reverse(hlr_fwd), reverse(llr_fwd)

    problems = []
    t2_uncovered = [r for r, (_, s) in t2.items() if s != "Merged" and r not in t2_rev]
    hlr_orphans = [r for r, v in hlr_fwd.items() if not v]
    hlr_bad = sorted(r for r in t2_rev if r not in t2)
    hlr_uncovered = [r for r, s in hlr_flags.items() if s == "Active" and r not in hlr_rev]
    hlr_deferred = [r for r, s in hlr_flags.items() if s != "Active"]
    llr_orphans = [r for r, v in llr_fwd.items() if not v]
    llr_bad = sorted(r for r in hlr_rev if r not in hlr_flags)
    for label, items in [
        ("T2 requirements without HLR", t2_uncovered), ("HLRs without T2 parent", hlr_orphans),
        ("HLR links to unknown T2 IDs", hlr_bad), ("Duplicate HLR IDs", hlr_dups),
        ("Active HLRs without LLR", hlr_uncovered), ("LLRs without HLR parent", llr_orphans),
        ("LLR links to unknown HLR IDs", llr_bad), ("Duplicate LLR IDs", llr_dups),
    ]:
        if items:
            problems.append(f"{label}: {', '.join(items)}")

    counts = collections.Counter(s for _, s in t2.values())
    hlr_cov = f"""## 22. Coverage check

- HLRs: **{len(hlr_fwd)}**.
- System requirements: {len(t2)} in SGP-SYS-REQ-001 ({counts['Active']} active, {counts['Suspended']} suspended, {counts['Merged']} merged).
- Active or suspended system requirements with no HLR: **{len(t2_uncovered)}**{listed(t2_uncovered)}.
- HLRs with no system parent: **{len(hlr_orphans)}**{listed(hlr_orphans)}.
- Trace links to unknown system IDs: **{len(hlr_bad)}**{listed(hlr_bad)}.

Generated by `tools/req_trace.py`.
"""
    hflags = collections.Counter(hlr_flags.values())
    llr_cov = f"""## 10. Coverage check

- LLRs: **{len(llr_fwd)}**. Duplicate LLR IDs: **{len(llr_dups)}**{listed(llr_dups)}.
- HLRs: {len(hlr_flags)} ({hflags['Active']} active, {hflags['Future']} future, {hflags['Suspended']} suspended).
- Active HLRs with no LLR: **{len(hlr_uncovered)}**{listed(hlr_uncovered)}.
- HLRs intentionally deferred (§6): {', '.join(hlr_deferred)}.
- LLRs with no HLR parent: **{len(llr_orphans)}**{listed(llr_orphans)}.
- Links to unknown HLR IDs: **{len(llr_bad)}**{listed(llr_bad)}.

Generated by `tools/req_trace.py`.
"""
    new_hlr = hlr_text.split(HLR_MARK)[0] + HLR_MARK + "\n" + hlr_table(t2, t2_rev) + "\n\n" + hlr_cov
    new_llr = llr_text.split(LLR_MARK)[0] + LLR_MARK + "\n" + llr_table(hlr_flags, hlr_rev) + "\n\n" + llr_cov

    if mode == "--write":
        HLR_DOC.write_text(new_hlr, encoding="utf-8")
        LLR_DOC.write_text(new_llr, encoding="utf-8")
        print(f"Trace tables written: {len(t2)} T2, {len(hlr_fwd)} HLR, {len(llr_fwd)} LLR.")
    else:
        if new_hlr != hlr_text:
            problems.append(f"{HLR_DOC.name}: trace table is stale (run tools/req_trace.py --write)")
        if new_llr != llr_text:
            problems.append(f"{LLR_DOC.name}: trace table is stale (run tools/req_trace.py --write)")

    if problems:
        print("TRACE CHECK FAILED:")
        for p in problems:
            print(" -", p)
        return 1
    print(f"Trace OK: {len(t2)} T2, {len(hlr_fwd)} HLR, {len(llr_fwd)} LLR; no gaps.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("--write", "--check"):
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
