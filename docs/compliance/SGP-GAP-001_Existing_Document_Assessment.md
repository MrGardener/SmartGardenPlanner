# SGP-GAP-001 — Assessment of Existing Plans, Documents and Requirements

| Field | Value |
|---|---|
| Document ID | SGP-GAP-001 |
| Revision | A (Draft) |
| Purpose | Decide which existing documents can be used as DO-178C / ARP4754A life cycle data, which must be rewritten, and which must be withdrawn. |
| Method | Each document was read in full (the 2.6 MB Verification Plan was sampled and structurally analysed) and compared with the content DO-178C §11 / ARP4754A §4–5 require for that type of document, and with the actual source code at commit `074117f`. |

Verdict codes: **KEEP** (usable as is, minor edits), **REVISE** (usable content, needs correction),
**REWRITE** (replaced by a new document), **WITHDRAW** (not valid as life cycle data; keep in git history
or `docs/archive/` for reference only).

---

## 1. Summary table

| Existing file | Claimed role | Verdict | Replaced by / action |
|---|---|---|---|
| `smart_garden_planner_master_plan.md` | Requirements (T2/HLR/LLR) + governance rules | **REVISE** (requirements) / **WITHDRAW** (governance directives) | Requirements re-baselined per SGP-SRS-STD-001 into a controlled requirements document (DWR WP-03). Governance rules replaced by SGP-SCMP-001 / SGP-SQAP-001. |
| `Android App/SGP_Knowledge_Base_11_…Requirements.md` | Requirements for Settings, Zoom/Pan, Catalog | **REVISE** | Merge into the re-baselined requirements (WP-03). |
| `Android App/FEATURE_ROADMAP.md` | Feature backlog | **KEEP** | Linked from SGP-DWR-001 WP-16. Each FR item needs T2/HLR requirements before implementation. |
| `Smart_Garden_Planner_Concept_of_Operations_v20_19.md` | ConOps | **REVISE** | Keep as the ARP4754A operational concept input, after removing untrue statements (§3.1). |
| `Interface Design Document & Interface Conrol Document.md` (+ `.docx`) and `Smart_Garden_Planner_Interface_Design_Document_v20_19.md` | IDD/ICD | **REWRITE** | Interfaces become part of the Software Design Description (DWR WP-04) and the HLR set. The "5-iteration review" section is **WITHDRAWN**. |
| `Smart_Garden_Planner_Integration_Plan_v20_19.md` | "SSIP" | **REWRITE** | Integration is covered by SGP-SDP-001 §6 (integration process) and SGP-SVP-001 §5 (integration testing). |
| `Smart_Garden_Planner_Verification_Plan_and_Test_Specification_v20_19.md` (2.6 MB) | Verification plan + test cases | **WITHDRAW** | SGP-SVP-001 (plan). Test cases/procedures are written against the re-baselined requirements (DWR WP-13). |
| `Smart_Garden_Planner_Review_Board_Audit_Record_v20_19.md` | Review/SQA records | **WITHDRAW** | Real review records per SGP-SVP-001 §4 and SGP-SQAP-001 §5. |
| `smart_garden_planner_audit_report.md` (11-perspective audit) | Audit | **WITHDRAW** | SQA audits per SGP-SQAP-001. |
| `smart_garden_planner_traceability_t2_t3.md`, `…_t3_t4.md` | Trace data | **REWRITE** | Trace data (DO-178C §11.21) is generated from requirement attributes (DWR WP-13, DW-1306). The current files cover 9 and 6 rows respectively out of ~130 T2/HLR and ~200 LLR. |
| `smart_garden_planner_risk_register.md` | Risk register | **REVISE** | Project risks move to the SDCP §9. Safety-related entries move to the safety assessment (SGP-SAP-001). |
| `Smart_Garden_Planner_Development_Log_and_Debt_Ledger_v20_19.md` | Dev log / debt | **REVISE** | Debt items move into SGP-DWR-001 WP-18. The log becomes SCM records (git history + PRs). |
| `Smart_Garden_Planner_Development_Notes_Progress_Journal.md` | Journal | **KEEP (history)** | Historical. Contains a factual error (it says the imports were changed to `net.sqlcipher` "instead of legacy `net.zetetic`"; the reverse is true — `net.zetetic:sqlcipher-android` is the current library). |
| `Test_Environment_Compliance_Data_and_HardwareEmulator_Variant_Log.md` | Environment log | **REVISE** | Becomes the Software Life Cycle Environment Configuration Index (SECI) + emulator/target equivalence analysis (SVP §6). Update from Pixel 8 Pro / API 34 to Pixel 10 Pro / API 36+. Remove claims not true in the code (complementary filter "bounds drift to ±5%", `WARN_ENCLAVE_FALLBACK` log entry — not implemented). |
| `smart_garden_planner_transcript.md` | Design rationale | **KEEP (history)** | Useful rationale. Not life cycle data. |
| `smart_garden_planner_automated_handoff.md`, `session_restart_prompt.txt`, `rename_gemini_files.py` | AI session procedures | **WITHDRAW** (as CM/governance) | Not CM procedures (SGP-CB-001 §6). They may remain as personal tooling, but must not claim to "seal" baselines. |
| `Android App/USER_MANUAL.md` | User documentation | **KEEP** | Update after the revamp. Its "Known Gaps" section is accurate and useful. |
| `Android App/README_v20.20_CHANGES.md` | Change notes | **REVISE** | It says `SecurityKeyManager.kt` (package `security`) was deleted; the file still exists (DWR DW-1201). |
| `REVAMP_PLAN_Pixel10Pro.md` | Engineering plan | **KEEP** | Its issue IDs are referenced from SGP-DWR-001. Decision D-01 is now answered: keep the DAL A framing with rewritten plans. |

## 2. Why the key plans cannot be used

### 2.1 Verification Plan and Test Specification v20.19 — WITHDRAW
- It contains 1,620 test cases (5 per T2 requirement across 324 sections). Every one is the **same
  generated template** with only the requirement ID and a "vector index 1…5" changed. Example: TC-T2-FUN-010-01 …
  "Inject safety-critical multi-variant test input parameter matrix configuration vector index 1".
- There are **no inputs, no expected results, no procedures** that a tester could execute, and no link to
  source code, HLR or LLR.
- It asks for things that don't exist or can't be done on this platform: "low-level hardware registers
  response payloads", "bit-level telemetry", "object-code paths".
- It is written against T2 (system) requirements only. DO-178C requires tests based on HLR and LLR
  (Table A-6), with test cases and procedures (§11.13) and results (§11.14).
- It has none of the content a DO-178C SVP must have (§11.3): organisation and independence, verification
  methods per objective, verification environment, transition criteria, partitioning considerations,
  compiler assumptions, re-verification, previously developed software, multiple-version software.

### 2.2 Integration Plan (SSIP) v20.19 — REWRITE
- The toolchain versions it "locks" (API 34, Kotlin 1.9.22, Room 2.6.1, Compose 1.5.4, R8 8.2.24) do not
  match the build files exactly and are now obsolete. No mechanism enforces them.
- It describes integration steps that don't match the code: "rejects 8/16-byte IVs" (no such check),
  `onSaveInstanceState` serialisation (not implemented), "un-dismissible 3-second calibration" (not wired),
  and ViewModel state isolation (not used by any screen).
- The risk matrix claims mitigations that do not exist (orientation lock, encrypted JSON state cache).
- The §4.2 "low-voltage rollback" is not how SQLite/Room works: transactions are atomic regardless of battery
  level, and there is no battery monitoring code.
- The §7 "5-iteration review" and "FINAL COMPLIANCE STAMP" are narratives with no reviewers, dates, findings
  records or checklists.
- DO-178C has no separate "integration plan". Integration belongs in the SDP (integration process) and the
  SVP (HW/SW integration and software integration testing).

### 2.3 Review Board Audit Record and 11-Perspective Audit — WITHDRAW
- They name no people, have no dates, checklists, findings or closure evidence. The "Sign-Off Hash IDs" are
  labels, not hashes of anything.
- One says it verifies "DO-178C **Level D**" while the rest of the project claims Level A.
- It states "every individual test case defines clear, objective pass/fail metrics", which is untrue of
  the Verification Plan (§2.1).

### 2.4 IDD/ICD — REWRITE
- It describes Fragments (`CreatorFragment`, `CanvasDesignFragment`, `NavHostFragment`), `LiveData` and
  `View.isEnabled` / text-watchers. The app is pure Jetpack Compose with no Fragments.
- The variable routing matrix (§3) names variables that don't exist (`varRawLengthStr`,
  `valPixelsPerMeterScale`, …), with "byte limits" that have no meaning for Kotlin/JVM objects.
- It describes error messages ERR-VAL-001 … ERR-MEM-005 whose strings do not appear in the code.
- Some tolerances (§4) contradict the implemented, user-configurable ranges (e.g. 0.05–1000 m fixed vs
  0.01–2000 m configurable; undo depth fixed at 25 vs configurable 5–100).
- Several tolerance values are embedded images (`![][image2]`) and cannot be read or traced as text.

## 3. Content that is kept and why

### 3.1 ConOps — REVISE (keep structure)
The problem statement, operational concept, the offline/no-telemetry constraint, the Plan B and weed-mask
concepts, and the growth tracks are good ARP4754A inputs. The following must be corrected:
- Remove statements that the system "is" safety-critical DAL A by nature, and replace them with the elected
  DAL rationale (SGP-CB-001 §2).
- Remove "every calculation … under rigorous DAL A structural envelope guarantees" and similar unverified
  claims.
- The IMU double-integration concept is not physically workable on a phone (REVAMP HW-04). Mark it as
  pending decision D-03.
- "Locks the window to landscape or portrait" becomes a decision (REVAMP D-04).
- "Encrypted local folders" / "encrypted SharedPreferences": replace with the design actually chosen.

### 3.2 Requirement IDs
The T2/HLR/LLR identifiers are kept for continuity. IDs are never reused. A deleted requirement keeps its ID
with status **Deleted** and a rationale.

## 4. Requirements quality review (input to WP-03)

Checked against SGP-SRS-STD-001 (requirements standard) and DO-178C Table A-3/A-4 objectives (accuracy,
consistency, verifiability, conformance to standards, traceability, algorithm accuracy).

### 4.1 Inconsistencies (requirements contradict each other or the implementation)

| ID | Conflict |
|---|---|
| RQ-01 | **T2-VAL-030** requires a "single-step" undo/redo buffer. **LLR-UI-070-C** requires depth 25. **T2-CFG-080** makes depth user-configurable (5–100). |
| RQ-02 | **T2-ERR-020** warns at "95% of allocated application storage". **HLR-SEN-160 / LLR-SEN-160-C** block at "≤5% free disk blocks". These are different quantities. Settings make the floor 1–25%. |
| RQ-03 | **HLR-SEN-100 / LLR-SEN-100-C** say image *height* exactly 1080 px. The ConOps and the code use the *major axis* ≤1080 px. |
| RQ-04 | **T2-ENV-010 / HLR-UI-030** require an orientation lock. The ConOps mentions landscape *or* portrait. Android 16 ignores orientation locks on large screens. |
| RQ-05 | ICD LIM-CORE-01/02 fix plot bounds at 0.05–1000 m. **T2-CFG-090** makes them configurable (0.01–2000 m sliders). |
| RQ-06 | **HLR-SEN-040** fixes 10 lux, and **HLR-SEN-150** fixes 5°. Settings make both configurable (1–50 lux, 1–20°). |
| RQ-07 | **T2-INT-030** requires a "visual conflict *warning*". The implementation *rejects* the placement. The companion toggle (**T2-CFG-050**) can disable antagonist checks, which **T2-INT-030** doesn't allow for. |
| RQ-08 | **HLR-UI-010, LLR-UI-010-A/B/C, LLR-UI-020-A** require Fragments, NavHostFragment and XML navigation. The app is Compose. |
| RQ-09 | **LLR-UI-040-C** requires LiveData, while **LLR-UI-040-B** requires StateFlow. Both are design choices, not requirements. |
| RQ-10 | **HLR-DAT-110** says ON DELETE CASCADE. The code uses CASCADE for plot→nodes but **RESTRICT** for seed→nodes (correct), and that RESTRICT causes the tier-switch crash (REVAMP DAT-01). The requirement doesn't cover the seed relation at all. |

### 4.2 Non-verifiable or ambiguous wording
"correct physical spacing" (T2-FUN-050), "optimized layout grid" (T2-FUN-060), "comprehensive suite"
(T2-FUN-080), "best companion plants" (T2-DAT-030), "continuously evaluate/monitor" (T2-ENV-030,
T2-INT-030), "instantly", "unrealistically small or large" (T2-VAL-015 — no values), "dynamically
optimized" (T2-DAT-100), "gracefully degrade" (T2-CON-060), all T2-SEC-* and T2-DAT-110…140 (one-line
statements with no measurable criteria), **LLR-DAT-190-D** "throw compilation structural exception"
(a runtime check can't cause a compilation exception).

### 4.3 Design or implementation embedded in requirements
Many LLRs prescribe API calls rather than required behaviour, and several prescribe APIs that are wrong
for the chosen platform:
- `System.gc()` (LLR-SEN-110-C) — has no guaranteed effect, and is discouraged on Android.
- `SENSOR_DELAY_FASTEST` (LLR-SEN-050-C) — capped at 200 Hz on Android 12+ unless the
  `HIGH_SAMPLING_RATE_SENSORS` permission is declared.
- `View.VISIBLE`, `isEnabled = false`, text-watchers (LLR-SEN-040-D/E, LLR-SEN-160-D, LLR-UI-120-A/D) — View
  system APIs, not Compose.
- Encrypted SharedPreferences (LLR-UI-050-C) — `androidx.security:security-crypto` is deprecated.
- `onSaveInstanceState` overrides (LLR-UI-050-A) — Compose uses `rememberSaveable`/`SavedStateHandle`.
- WindowManager metrics (LLR-SEN-010-A) — not needed for CameraX.
- "Single-thread loops" (LLR-DAT-020-B) — Room manages its own executors.

Under the requirements standard, **LLRs describe the design at the level needed to write code without
further information**, but they are still verifiable behavioural statements. API choices belong in the
Software Design Description.

### 4.4 Process statements placed in the product specification
T2-GOV-010/020, HLR-GOV-010/020, LLR-GOV-010-A, LLR-GOV-020-A ("execute 11-perspective audits", "generate
handoff package artifacts") are process obligations. Move them to SGP-SQAP-001 and mark them **Deleted** in the
requirements with that rationale.

### 4.5 Missing requirements (gaps found against the code and the platform)
- **Back navigation / exit behaviour** in general (only "dirty workspace" is covered by HLR-UI-080).
- **Backup/restore behaviour** and key loss handling (REVAMP PLT-06).
- **Schema migration / upgrade compatibility** (the code has migrations, but no requirement covers them).
- **Performance/timing**: startup time, canvas frame time with N nodes, tier-switch duration.
- **Plot bounds for moved plants** (drag-move can currently place a plant outside the plot; see DWR DW-0901).
- **Units** (covered only in KB11), **accessibility**, **text scaling**, **edge-to-edge/insets**.
- **Catalog data correctness** (Parameter Data Item requirements: valid ranges, referential integrity of
  companion codes, provenance of spacing/germination values).
- **Time handling**: time zones, clock changes and DST for germination/harvest calculations.
- **Derived requirements** are not flagged anywhere, yet DO-178C requires derived requirements to be
  identified and given to the safety assessment.

### 4.6 Traceability gaps
- T2→HLR trace rows: 9 of ~70 T2 requirements (plus a fuller table in KB11 for its own subsystems).
- HLR→LLR trace rows: 6 of ~80 HLR.
- LLR→source code: none. Requirement→test: none. Code→requirement (reverse trace, to find untraced
  code): none. There is significant untraced code (e.g. paths, auto-populate, colours, units existed
  before their requirements were written).

## 5. Actions

All actions from this assessment are logged in SGP-DWR-001 (WP-01, WP-03, WP-04, WP-13, WP-17). Superseded
documents get a supersession notice at the top of the file (done in this revision for the Verification
Plan, Integration Plan, Review Board Audit Record and 11-Perspective Audit). Moving them into
`docs/archive/` is a CM action to be done at the first formal baseline (DW-1505).
