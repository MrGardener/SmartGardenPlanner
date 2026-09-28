#!/usr/bin/env python3
"""Test case trace checker (LLR -> test case -> test / procedure).

Usage:
  python3 tools/tc_trace.py --check   fail (exit 1) on any gap below
  python3 tools/tc_trace.py --write   also rewrite the summary table (section 2) of SGP-TCS-001

Checks, for SGP-TCS-001 against SGP-SW-LLR-001, the test sources and SGP-TPR-001:
  - every LLR has exactly one test case block, and no block names an unknown LLR
  - every LLR has at least 3 test cases, at least one limit case (MIN, MAX or BND) and at least one
    invalid, alphanumeric or random case (INV, ALN or RND)
  - test case ids are unique and numbered after their LLR
  - every AUTO / DEVICE reference is a @Test method of that class, every WEB reference is a check label in
    that script, every CI reference is a step of that workflow, every MANUAL / REVIEW reference is a step of
    SGP-TPR-001 (this applies to the regression register too)
  - results agree with the method (an automated case is PASS or DEV; a planned one PLANNED or DEV)
  - every regression register entry names at least one automated test

Tool assessment: SGP-TQP-001 (TQL-5 if verification credit is taken from this tool, DW-1403).
"""
import collections
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
LLR_DOC = ROOT / "docs" / "requirements" / "SGP-SW-LLR-001_Software_Low_Level_Requirements.md"
TCS_DOC = ROOT / "docs" / "verification" / "SGP-TCS-001_Test_Case_Specification.md"
TPR_DOC = ROOT / "docs" / "verification" / "SGP-TPR-001_Test_Procedures.md"
UNIT_TESTS = ROOT / "Android App" / "app" / "src" / "test"
DEVICE_TESTS = ROOT / "Android App" / "app" / "src" / "androidTest"
WEB_TESTS = ROOT / "web" / "tests"
WORKFLOWS = ROOT / ".github" / "workflows"

LIMIT_KINDS = {"MIN", "MAX", "BND"}
BAD_KINDS = {"INV", "ALN", "RND"}
KINDS = {"NOM"} | LIMIT_KINDS | BAD_KINDS
RESULTS = {"PASS", "MANUAL", "PLANNED", "BLOCKED", "DEV"}
STATUSES = ["IMPL", "PART", "DEV", "TGT", "OBS"]


def test_methods(folder):
    """{class name: set of @Test method names} for every Kotlin file under folder."""
    found = collections.defaultdict(set)
    for f in folder.rglob("*.kt"):
        text = f.read_text(encoding="utf-8")
        for cls in re.findall(r"^class (\w+)", text, re.M):
            found[cls]  # a class with no tests still exists
        cls = f.stem
        for m in re.finditer(r"@Test\s+(?:@\w+(?:\([^)]*\))?\s+)*fun\s+(\w+)\s*\(", text):
            found[cls].add(m.group(1))
    return found


def main(argv):
    write = "--write" in argv
    errors = []
    llrs = []
    for m in re.finditer(r"^\*\*(LLR-[A-Z0-9]+-\d+)\*\*", LLR_DOC.read_text(encoding="utf-8"), re.M):
        if m.group(1) not in llrs:
            llrs.append(m.group(1))
    tcs_text = TCS_DOC.read_text(encoding="utf-8")
    tpr_steps = set(re.findall(r"\*\*(TP-\d+\.\d+)\*\*", TPR_DOC.read_text(encoding="utf-8")))
    unit = test_methods(UNIT_TESTS)
    device = test_methods(DEVICE_TESTS)
    web = {f.name: f.read_text(encoding="utf-8") for f in WEB_TESTS.glob("*.mjs")}
    flows = {f.name: f.read_text(encoding="utf-8") for f in WORKFLOWS.glob("*.yml")}

    def check_ref(cell, where):
        """Validate every reference in a method cell; return the list of method kinds found."""
        kinds = []
        for kind, ref in re.findall(r"\b(AUTO|DEVICE) `([\w.]+)`", cell):
            kinds.append(kind)
            cls, _, meth = ref.partition(".")
            table = unit if kind == "AUTO" else device
            if cls not in table:
                errors.append(f"{where}: {kind} class {cls} not found")
            elif meth not in table[cls]:
                errors.append(f"{where}: {kind} {cls}.{meth} is not a @Test method")
        for kind, f, label in re.findall(r"\b(WEB|CI) `([\w.\-]+)` «([^»]+)»", cell):
            kinds.append(kind)
            src = (web if kind == "WEB" else flows).get(f)
            if src is None:
                errors.append(f"{where}: {kind} file {f} not found")
            elif kind == "WEB" and label not in src:
                errors.append(f"{where}: WEB check «{label}» not found in {f}")
            elif kind == "CI" and not re.search(r"name:\s*" + re.escape(label), src):
                errors.append(f"{where}: CI step «{label}» not found in {f}")
        for kind, step in re.findall(r"\b(MANUAL|REVIEW) (TP-\d+\.\d+)", cell):
            kinds.append(kind)
            if step not in tpr_steps:
                errors.append(f"{where}: procedure step {step} not in SGP-TPR-001")
        if "Planned" in cell:
            kinds.append("PLANNED")
        return kinds

    blocks = collections.OrderedDict()
    for m in re.finditer(r"^#### (LLR-[A-Z0-9]+-\d+) — (\w+)\n(.*?)(?=^#{2,4} )", tcs_text, re.S | re.M):
        if m.group(1) in blocks:
            errors.append(f"{m.group(1)}: more than one test case block")
        blocks[m.group(1)] = (m.group(2), m.group(3))
    for llr in llrs:
        if llr not in blocks:
            errors.append(f"{llr}: no test cases")
    for llr in blocks:
        if llr not in llrs:
            errors.append(f"{llr}: test cases for an LLR that doesn't exist")

    status_count = collections.Counter()
    result_count = collections.Counter()
    kind_count = collections.Counter()
    method_count = collections.Counter()
    tc_ids = set()
    for llr, (status, body) in blocks.items():
        if status not in STATUSES:
            errors.append(f"{llr}: unknown status {status}")
        status_count[status] += 1
        rows = [r for r in body.splitlines() if r.startswith("| TC-")]
        if len(rows) < 3:
            errors.append(f"{llr}: {len(rows)} test case(s), at least 3 needed")
        kinds = set()
        for row in rows:
            cells = [c.strip() for c in re.split(r"(?<!\\)\|", row)[1:-1]]
            if len(cells) != 6:
                errors.append(f"{llr}: malformed row {row[:60]}")
                continue
            tc, kind, _, _, meth, result = cells
            if not tc.startswith(llr.replace("LLR-", "TC-") + "."):
                errors.append(f"{tc}: id doesn't match {llr}")
            if tc in tc_ids:
                errors.append(f"{tc}: duplicate id")
            tc_ids.add(tc)
            if kind not in KINDS:
                errors.append(f"{tc}: unknown kind {kind}")
            kinds.add(kind)
            kind_count[kind] += 1
            if result not in RESULTS:
                errors.append(f"{tc}: unknown result {result}")
            result_count[result] += 1
            used = check_ref(meth, tc)
            if len(used) != 1:
                errors.append(f"{tc}: needs exactly one method, found {used or 'none'}")
                continue
            method_count[used[0]] += 1
            allowed = {"AUTO": {"PASS", "DEV"}, "WEB": {"PASS", "DEV"}, "CI": {"PASS", "DEV"},
                       "DEVICE": {"MANUAL", "DEV"}, "MANUAL": {"MANUAL", "BLOCKED", "DEV"},
                       "REVIEW": {"MANUAL", "DEV"}, "PLANNED": {"PLANNED", "DEV"}}[used[0]]
            if result not in allowed:
                errors.append(f"{tc}: result {result} doesn't fit method {used[0]}")
        if not kinds & LIMIT_KINDS:
            errors.append(f"{llr}: no limit case (MIN, MAX or BND)")
        if not kinds & BAD_KINDS:
            errors.append(f"{llr}: no invalid, alphanumeric or random case (INV, ALN or RND)")

    reg = re.search(r"^## 4\. Regression register\n(.*?)(?=^## )", tcs_text, re.S | re.M)
    regs = 0
    if not reg:
        errors.append("regression register (section 4) not found")
    else:
        for row in reg.group(1).splitlines():
            if row.startswith("| REG-"):
                regs += 1
                rid = row.split("|")[1].strip()
                used = check_ref(row, rid)
                if not {"AUTO", "WEB", "CI"} & set(used):
                    errors.append(f"{rid}: no automated regression test")

    total = sum(result_count.values())
    names = [("AUTO", "AUTO"), ("WEB", "WEB"), ("CI", "CI"), ("DEVICE", "DEVICE"), ("MANUAL", "MANUAL"), ("REVIEW", "REVIEW"), ("PLANNED", "Planned")]
    summary = "\n".join([
        "| | Count |", "|---|---|",
        f"| LLRs | {len(blocks)} |",
        f"| Test cases | {total} |",
        f"| Regression entries | {regs} |",
        "| LLR status | " + " · ".join(f"{k} {status_count[k]}" for k in STATUSES) + " |",
        "| Case results | " + " · ".join(f"{k} {result_count[k]}" for k in ["PASS", "MANUAL", "PLANNED", "BLOCKED", "DEV"]) + " |",
        "| Case kinds | " + " · ".join(f"{k} {kind_count[k]}" for k in ["NOM", "MIN", "MAX", "BND", "INV", "ALN", "RND"]) + " |",
        "| Case methods | " + " · ".join(f"{label} {method_count[k]}" for k, label in names) + " |",
    ])
    current = re.search(r"^## 2\. Summary\n\n(.*?)\n\n", tcs_text, re.S | re.M)
    if not current:
        errors.append("summary (section 2) not found")
    elif current.group(1) != summary:
        if write:
            TCS_DOC.write_text(tcs_text.replace(current.group(1), summary), encoding="utf-8")
            print("summary rewritten")
        else:
            errors.append("summary table is stale: run python3 tools/tc_trace.py --write")

    for e in errors:
        print("ERROR:", e)
    print(f"{len(blocks)} LLRs, {total} test cases, {regs} regression entries; "
          f"{result_count['PASS']} automated passing, {result_count['MANUAL']} manual, "
          f"{result_count['PLANNED']} planned, {result_count['BLOCKED']} blocked, {result_count['DEV']} deviations")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
