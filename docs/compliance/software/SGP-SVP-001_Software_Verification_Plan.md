# SGP-SVP-001 — Software Verification Plan (SVP)

| Field | Value |
|---|---|
| Document ID | SGP-SVP-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| DO-178C reference | §11.3; Annex A Tables A-3 … A-7 |
| Supersedes | `Smart_Garden_Planner_Verification_Plan_and_Test_Specification_v20_19.md` (withdrawn, SGP-GAP-001 §2.1) |
| Control category | CC1 |

## 1. Purpose and scope

This plan defines how SGP-APP is verified at Level A: reviews, analyses and tests, the verification
environment, independence, coverage analysis, and regression. Test cases themselves are **not** in this
plan. They are verification cases and procedures (§11.13), written per §7 and stored with the source code.

## 2. Organisation and independence

| Activity | Performer | Independence required at Level A |
|---|---|---|
| Requirements, design and code reviews (A-3, A-4, A-5) | Independent verifier | Yes, for the objectives Annex A marks for independence |
| Test case / procedure development | Developer or verifier | — |
| Review of test cases, procedures and results (A-7 obj. 1–2) | Independent verifier | Yes |
| Coverage analyses (A-7 obj. 3–9) | Independent verifier | Yes |
| Test execution | Anyone, following the written procedure; automated where possible | — |

Independence means the person is not the author of the item being verified. **Until an independent verifier
is assigned (SGP-CB-001 DEV-04), every record states "Independence: NOT satisfied", and the objective stays
open in the SAS.** AI assistants may run preliminary checks, but their output is recorded as tool output
(SGP-CB-001 §6).

## 3. Verification methods overview

| Method | Used for |
|---|---|
| **Review** (checklist-driven, recorded) | Requirements, design, code, test cases, PDI content, plans |
| **Analysis** | Traceability, coverage (requirements and structural), data/control coupling, memory/DO-332 analyses, floating-point precision, timing, source-to-object (compiler-added code), emulator/target equivalence |
| **Test** | Requirements-based tests (normal range + robustness) at three levels: low-level (unit), software integration, hardware/software integration on the target |

## 4. Reviews and analyses

Each review produces a record in `docs/verification/results/<baseline>/reviews/`. The record contains:
- the item and version (git hash)
- the checklist used
- the reviewer and author, with an independence statement
- the date
- findings, each either raised as a Problem Report or closed with rationale
- the review outcome

### 4.1 Requirements review checklist (Table A-3)
For each HLR (and the T2 requirements under the SVVP):
- [ ] Complies with its parent requirement(s); trace exists and is correct (A-3.1, A-3.6)
- [ ] Accurate and consistent: no contradiction with other requirements (A-3.2)
- [ ] Compatible with the target: uses only capabilities the Pixel 10 Pro / Android 16 provides (A-3.3)
- [ ] Verifiable: a test or analysis can show it is met (A-3.4)
- [ ] Conforms to SGP-SRS-STD-001 (A-3.5)
- [ ] Algorithms are specified accurately (units, ranges, rounding, boundaries) (A-3.7)
- [ ] Derived-requirement flag is correct, and the requirement has been given to the safety assessment

### 4.2 Design review checklist (Table A-4)
- [ ] LLRs comply with and trace to HLRs; accurate, consistent, verifiable, conform to the standard (A-4.1–4.6)
- [ ] Algorithms are accurate (A-4.7)
- [ ] Architecture is compatible with the HLRs, consistent, compatible with the target, verifiable, and
      conforms to SGP-SDS-001 (A-4.8–4.12)
- [ ] Partitioning integrity: N/A, since there is none (A-4.13), recorded
- [ ] Data/control coupling tables are complete for the change
- [ ] Deactivated code (tier gating) is designed per SGP-SDS-001 §7
- [ ] DO-332 items: substitutability, dispatch, exceptions, memory

### 4.3 Code review checklist (Table A-5)
- [ ] Complies with and traces to the LLRs; no untraced code (A-5.1, A-5.5)
- [ ] Complies with the architecture (A-5.2)
- [ ] Verifiable: testable, and no unreachable code without a disposition (A-5.3)
- [ ] Conforms to SGP-SCS-001; static analysis findings are clean or justified (A-5.4)
- [ ] Accurate and consistent: arithmetic, units, null handling, concurrency, resource release, exception
      paths (A-5.6)
- [ ] Output of the integration process is complete and correct: build log clean, APK contents as
      expected (A-5.7)
- [ ] Parameter Data Item files are correct and complete (A-5.8/5.9)

### 4.4 Analyses

| ID | Analysis | Method | Evidence |
|---|---|---|---|
| AN-01 | Requirements-based test coverage (A-7.3, A-7.4) | Generated trace report: every HLR and LLR has ≥1 test (or an approved review/analysis method); every test traces to a requirement | Trace report per baseline |
| AN-02 | Structural coverage (A-7.5–7.7) | §8 | Coverage report + MC/DC worksheets + gap dispositions |
| AN-03 | Data and control coupling (A-7.8) | §8.5 | Coupling analysis record |
| AN-04 | Compiler-added code (A-7.9) | §8.4 | Source-to-bytecode analysis record |
| AN-05 | Memory management / DO-332 | Bounds on growing collections, bitmap sizes, allocation in draw loops; stress tests | Analysis + stress results |
| AN-06 | Timing / performance | Measured on the target: cold start, canvas frame time at the maximum allowed node count, tier switch duration | Macrobenchmark results |
| AN-07 | Floating-point precision | Error bounds for unit conversions and coordinate transforms against the tolerances in the LLRs | Analysis record |
| AN-08 | Emulator/target equivalence | §6.2 | Analysis record |
| AN-09 | COTS usage domain | Every relied-on COTS behaviour is exercised by tests on the target | Usage list with test references |
| AN-10 | PDI integrity and content | Automated structural checks + sample content audit against the documented sources | Check log + audit record |

## 5. Test levels

| Level | What | Where | Credit |
|---|---|---|---|
| Low-level tests | LLR-based tests of `:core` and `:data` functions/classes | Host JVM (and repeated on the target for the credit baseline) | LLR coverage, structural coverage |
| Software integration tests | HLR/LLR-based tests across modules: ViewModel ↔ repository ↔ DB; navigation; state restoration | Host (Robolectric-free JVM where possible) + instrumented on the target | HLR coverage, coupling |
| Hardware/software integration tests | HLR-based tests of the release APK on the physical target: UI flows, camera, sensors, storage, battery, permissions, backup/restore, process death, performance | **Physical Pixel 10 Pro only** | HLR coverage, target compatibility (A-6.5) |

Every test case lists its requirement IDs. A test that traces to no requirement is either given a
requirement or deleted.

## 6. Verification environment

### 6.1 Environments
- **Host:** JVM version from the SECI, with Gradle unit tests.
- **Emulator:** Android Emulator, "Pixel 10 Pro" hardware profile (1280×2856 px, ~495 ppi if the profile has
  to be created manually), with the newest Google APIs system image (API 36+) in both a **4 KB** and a
  **16 KB page-size** variant, and a virtual camera scene.
- **Target:** physical Google Pixel 10 Pro. The SECI records the exact Android version, build number and
  security patch per campaign. Developer options are set as each procedure requires (e.g. "Don't keep
  activities" for the state tests).

### 6.2 Emulator vs target
Credit is taken on the host/emulator **only** where AN-08 shows the environment is representative:
- **Pure `:core` logic:** the host JVM is representative for functional results. The final credit run is
  repeated on the target with the release build (DEV-02).
- **Anything involving:**
  - the camera, sensors, StrongBox/TEE key storage or storage limits
  - the display (insets, cutout, density) or gesture navigation
  - performance or battery

  is **target only**.

## 7. Test case design

### 7.1 Normal range tests
For each requirement, cover:
- valid equivalence classes
- boundary values of every numeric input (min, min+ε, nominal, max−ε, max)
- every state transition the requirement defines
- time-related behaviour with an injected clock (DW-0712)

### 7.2 Robustness tests
Cover:
- invalid equivalence classes: out-of-range, empty, non-numeric and very long inputs
- DB failures (fault injection through the repository interface)
- corrupted stored settings and PDI lines
- storage full / below floor
- permission denied / revoked
- process death at each screen
- configuration change during an operation
- clock set backwards or forwards, time zone and DST change
- the maximum allowed node, point and path counts
- low battery
- key-loss at startup (backup restore)

### 7.3 Test case template (mandatory fields)

```
Test ID:        TC-<requirement-area>-<nnn>
Requirements:   HLR-…, LLR-…            (all traced IDs)
Level:          Low-level | SW integration | HW/SW integration
Environment:    Host | Emulator (profile) | Target (SECI ref)
Preconditions:  exact data/state
Inputs:         exact values
Steps:          numbered, executable by someone else
Expected:       exact values / observable behaviour, with tolerances
Pass/fail:      objective rule
Automation:     test class#method, or "manual procedure <file>"
```

### 7.4 Worked example (shows the level of detail required)

Current code: `CompanionPlantingValidator.validatePlacement`. The spacing rule is
`distance < (rA + rB) × margin`, which gives a violation.

```
Test ID:        TC-PLC-001
Requirements:   T2-FUN-050, HLR-DAT-050, HLR-CFG-040, LLR-DAT-050-B, LLR-DAT-050-C, LLR-CFG-040-A
Level:          Low-level (host), repeated on target for credit
Preconditions:  Existing node N1 at (1.00 m, 1.00 m), seed radius 0.60 m.
                Candidate seed radius 0.30 m. Margin 1.0. Companion rules enabled; no antagonists.
Cases (candidate position → expected):
  a) (1.89, 1.00)  distance 0.89 < 0.90           → invalid, spacingViolations = [N1]
  b) (1.90, 1.00)  distance 0.90, not < 0.90      → valid (boundary: touching circles are allowed)
  c) (1.00, 1.95)  vertical axis, distance 0.95   → valid (checks the rule is axis-independent)
  d) margin 0.5, (1.46, 1.00): 0.46 ≥ 0.45        → valid
  e) margin 2.0, (2.79, 1.00): 1.79 < 1.80        → invalid
Pass/fail:      isValid and violation lists equal the expected values exactly.
Automation:     :core CompanionPlantingValidatorTest#spacingBoundary_*
```

**What this example finds in the current code.** The implementation uses `Float`:
`0.6f + 0.3f = 0.90000004f`, while the computed distance for case (b) is `0.9f` (exactly
0.899999976…). So case (b) **fails** today (confirmed by executing the same arithmetic on a JVM): two plants whose circles exactly touch are rejected. A precise requirement plus a
boundary test exposes this immediately. The fix belongs in the LLR: specify a comparison tolerance (for
example "violation if distance < required − 1×10⁻⁴ m"), or specify the arithmetic type. This is tracked as
DW-0814 (precision analysis, AN-07). The old template test cases could never have caught it, because they
contained no values.

Robustness companion: TC-PLC-002. The candidate has the same id as an existing node, and a seed lookup
returns null. Expected: the self node is ignored, the unknown seed is skipped, and no exception is thrown.
If skipping an unknown seed is judged unsafe, this test finds a requirement gap, and a PR is raised to
decide the behaviour.

## 8. Structural coverage analysis (Level A)

### 8.1 Criteria
Statement coverage, decision coverage and MC/DC for all project-authored code in the EOC. Coverage is
measured while running **requirements-based tests only**. Tests written just to raise coverage are not
allowed. Where coverage is missing, a requirement or a requirements-based test is missing.

### 8.2 Tooling
JaCoCo measures line/instruction/branch coverage at bytecode level, for the host and instrumented test
runs. If credit is taken from its reports, JaCoCo is qualified at TQL-5 (SGP-TQP-001).

### 8.3 MC/DC method (DEV-07)
1. The code standard limits each decision to ≤3 conditions (R-CPX-3), and bans side effects in conditions.
2. A detekt rule or script lists every decision with ≥2 conditions in project code.
3. For each such decision, an **MC/DC worksheet** lists the conditions, the truth table rows required for
   each condition's independent effect (unique-cause or masking MC/DC, stated per worksheet), and the test
   case IDs that execute those rows.
4. JaCoCo branch data supports the worksheet (short-circuit evaluation produces separate bytecode
   branches). It is not treated as MC/DC by itself.

### 8.4 Code not traceable to source (A-7.9, DEV-02)
For a representative sample of `:core` functions (every language construct used), compare the Kotlin source
with the bytecode (`javap -c` / the Kotlin bytecode viewer) and with the post-R8 DEX (`dexdump` / APK Analyzer).
Identify compiler-added code (null checks, intrinsics, `when` switch tables, inline expansion, default-argument
bridges, coroutine state machines, data-class methods) and show that it is either verified by the existing
tests or cannot affect behaviour. Repeat the analysis when the compiler, R8 version or R8 configuration
changes.

### 8.5 Data coupling and control coupling
Using the coupling tables in the SDD:
- **Data coupling:** every data item passed between components (function parameters, StateFlow
  state, DB columns, settings keys, PDI fields) is exercised by requirements-based tests with values from
  each equivalence class.
- **Control coupling:** every call path / event path between components (UI event → ViewModel → repository
  → DAO; navigation routes; coroutine launches; lifecycle callbacks) is executed by at least one test.

Record the gaps and resolve them.

### 8.6 Coverage gap resolution
Each gap is classified as one of:
- a missing requirements-based test (add one)
- a missing requirement (raise a PR)
- deactivated code (show the deactivation mechanism)
- dead code (remove it)
- defensive code that cannot be reached by test (justify by analysis)

## 9. Regression and re-verification

- **Every pull request:** build, lint/static analysis, all host tests, trace report, and an impact analysis
  in the PR description (which requirements, tests and analyses are affected).
- **Every baseline:** full host suite, full instrumented suite on the emulator, and the credit campaign on
  the physical target with the release APK.
- **Toolchain, COTS or PDI changes:** impact analysis. At minimum, the full test suite on the target plus a
  repeat of AN-04 (for compiler/R8 changes) or AN-10 (for PDI changes).

## 10. Transition criteria for verification

Verification of an item starts when the item is under CM at a known version. Verification of a baseline is
complete when:
- all planned reviews, analyses and tests have results
- every failure has a Problem Report
- coverage gaps are resolved
- the results have been reviewed (A-7.1/7.2)

## 11. Records

All verification results live under `docs/verification/results/<baseline>/`. Each campaign records:
- the test report (JUnit XML/HTML)
- coverage reports
- MC/DC worksheets
- analysis records
- review records
- device screenshots/videos where a procedure needs them
- the SECI reference

These records are CC2.
