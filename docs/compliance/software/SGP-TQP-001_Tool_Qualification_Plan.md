# SGP-TQP-001 — Tool Assessment and Qualification Plan

| Field | Value |
|---|---|
| Document ID | SGP-TQP-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| References | DO-178C §12.2; DO-330 |
| Control category | CC1 |

## 1. Method

For each tool, determine whether it **eliminates, reduces or automates** a DO-178C process **without its
output being verified**. If it does, assign the qualification criterion and TQL for software Level A:

| Criterion | Meaning | TQL at Level A |
|---|---|---|
| 1 | Its output is part of the airborne software and could insert an error | TQL-1 |
| 2 | Automates verification and its output is used to justify eliminating or reducing other verification or development processes | TQL-4 |
| 3 | Automates verification and could fail to detect an error (within its intended use) | TQL-5 |

If the tool's output is verified by another means, **no qualification is needed**, and the verification is
recorded instead.

## 2. Policy

- The project avoids needing TQL-1 or TQL-4 qualification.
- TQL-5 qualification is done only where credit is actually taken (DW-1402…1404). For a TQL-5 tool,
  the project produces: Tool Operational Requirements, qualification test cases with known-good and
  known-bad inputs, results, and a tool configuration record in the SECI.

## 3. Tool assessment table

| Tool | Use | Could insert/miss errors? | Output verified otherwise? | Decision |
|---|---|---|---|---|
| Kotlin compiler | Source → JVM bytecode | Yes (criterion 1) | Partly: tests run on the EOC; compiler-added code analysis (SVP §8.4) | **Not qualifiable. Deviation DEV-02** with those mitigations |
| D8 / R8 | Bytecode → DEX; shrinking/optimisation | Yes (criterion 1) | Credit tests run on the R8-processed release APK; R8 config under CM | Not qualified (DEV-02) |
| ART AOT/JIT (on device) | DEX → machine code | Yes | Tests run on the target | Not qualified (DEV-02) |
| KSP + Room compiler | Generates DAO implementation code | Yes (generated code is part of the EOC) | Generated code is covered by `:data` requirements-based tests; generated sources are kept for review | Not qualified; verified via tests + reviewed generated code samples |
| Compose compiler plugin | Transforms composables | Yes | UI tests on the target | Not qualified (DEV-02 / DEV-01) |
| Gradle + AGP | Build orchestration, resource merging, signing | Could produce a wrong APK | APK contents checked (APK Analyzer checklist), EOC hash recorded, reproducible build (DW-0509) | Not qualified; output verified |
| JUnit / AndroidX Test runners | Execute tests, report pass/fail | Could report a false pass (criterion 3) | Test procedures include a sampling review of the raw results; intentional failure checks (mutated expected values) are part of the qualification tests | **TQL-5 if the automated verdict is taken without review**. Otherwise record the review. |
| JaCoCo | Structural coverage measurement | Could over-report coverage (criterion 3) | Not otherwise verified | **TQL-5 if coverage credit is taken** (DW-1402) |
| Trace report generator (project script) | Checks bidirectional trace and orphans | Could miss a gap (criterion 3) | — | **TQL-5 if credit is taken** (DW-1403). Develop it under this plan. |
| detekt / ktlint / Android Lint | Code standard checks | Could miss a violation (criterion 3) | Code review still checks the standard | TQL-5 only if review of those rules is removed (DW-1404). Default: no credit, review remains. |
| Git / GitHub | CM | Integrity protected by content hashes | Baseline retrieval test at each `rel-` baseline | No qualification (CM tool); retrieval check recorded |
| Android Emulator | Development testing | Could behave differently from the target | Credit only per SVP §6.2 | No qualification; equivalence analysis AN-08 |
| Android Studio (IDE), including AI features | Editing, refactoring, code generation | Could insert errors | All output reviewed and tested like hand-written code | No qualification (SGP-CB-001 §6) |
| AI assistants (Claude, Gemini, …) | Drafting code, requirements, documents | Could insert errors or false claims | All output reviewed; no credit | No qualification; outputs verified (SGP-CB-001 §6) |
| Catalog generation scripts (if any) | Produce PDI files | Could insert wrong data | PDI structural checks + content audit (AN-10) | No qualification; output verified |

## 4. Maintenance

The table is re-assessed whenever a tool is added or its version or configuration changes. The result is
recorded in the SECI for the baseline.
