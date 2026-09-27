# SGP-CB-001 — Compliance Basis, Applicability and Deviations

| Field | Value |
|---|---|
| Document ID | SGP-CB-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| Status | Provisionally adopted; formal approval pending |
| Applies to | Smart Garden Planner (SGP) Android application, all project-authored software and data |
| Control category | CC1 (see SGP-SCMP-001) |

This document says **what "DO-178C / ARP4754A DAL A" means for this project**, and where the project cannot
conform and why. Every other plan in `docs/compliance/` depends on it. If a later plan conflicts with this
document, this document wins until it is revised.

---

## 1. Standards adopted

| Standard | Role in this project |
|---|---|
| **ARP4754A** *Guidelines for Development of Civil Aircraft and Systems* | System-level development assurance: system requirements (T2), allocation to the software item, requirements validation, system verification, CM, process assurance. Note: ARP4754**B** (2023) supersedes 4754A. This project keeps 4754A as the stated basis and records any adoption of 4754B changes in a revision of this document. |
| **ARP4761A** *Guidelines for Conducting the Safety Assessment Process* | Safety assessment method (FHA → PSSA → SSA, Common Cause Analysis) used by the ARP4754A process. |
| **DO-178C** *Software Considerations in Airborne Systems and Equipment Certification* | Software life cycle processes and objectives (Annex A, Tables A-1 … A-10) at **Level A**. |
| **DO-330** *Software Tool Qualification Considerations* | Qualification of tools whose output is not otherwise verified (SGP-TQP-001). |
| **DO-332** *Object-Oriented Technology and Related Techniques Supplement* | Kotlin is object-oriented and runs on a garbage-collected runtime. DO-332 objectives (type substitutability, dynamic dispatch, memory management, exception management, overloading, inlining, generics) apply. |
| DO-331 (model-based), DO-333 (formal methods) | Not used. If formal methods are later used for credit (e.g. for geometry algorithms), this document is revised. |

## 2. Honest statement of applicability

These points are stated plainly so that nobody (future developers, reviewers, AI assistants) takes a
compliance claim to mean more than it does.

1. **No certification authority is involved.** DO-178C and ARP4754A are written for airborne systems
   approved by an authority (FAA, EASA, TCCA, …). SGP is a consumer smartphone application. No authority
   will review or approve this data. All compliance statements in this project are **self-assessed process
   conformance** ("developed in accordance with the DO-178C Level A objectives, with the deviations listed
   in §5"). They are **never** "certified" or "DAL A approved".
2. **DAL A is elected, not derived.** Under ARP4754A, the development assurance level comes from the most
   severe failure condition found by the Functional Hazard Assessment. The preliminary FHA
   (SGP-SAP-001 §6) finds no failure condition worse than *Minor*, which would give FDAL/IDAL D or E.
   The Project Owner has **elected Level A rigor as a programme decision** (a quality and learning
   objective). ARP4754A allows a higher assurance level than the minimum. All plans therefore target the
   Level A objectives, while the safety assessment keeps recording the true severity of each failure
   condition.
3. **Previously produced "compliance" records are not evidence.** Earlier documents (Review Board Audit
   Record, 11-Perspective Audit, the "5-iteration review" sections inside the IDD/ICD and Integration Plan,
   the 1,620-case Verification Plan) describe reviews, sign-offs and test cases that were not performed or
   that contain no executable content. They are superseded (SGP-GAP-001) and **no credit** is taken from them.
   The requirement IDs they contain are kept.
4. **Requirements were partly written after the code** (stated openly in Knowledge Base Part 11). DO-178C
   expects requirements to drive code. The existing code base is handled as a **development baseline to be
   upgraded** (DO-178C §12.1.4). Missing or inadequate life cycle data is produced by reverse engineering
   and by re-verification, and every such item is logged in the Deferred Work Register (SGP-DWR-001).

## 3. System and software item definition

| Item | Definition |
|---|---|
| System | "SGP System" = the SGP application running on a Google Pixel 10 Pro with the Android release and build number recorded in the SECI, used by one person to plan a garden plot. |
| Software item | **SGP-APP**: all Kotlin source, resources, bundled catalog data and build scripts in this repository that end up in the APK. One software item, Level A (elected). |
| Target computer | **Physical Google Pixel 10 Pro** (exact model, Android version and security patch level recorded per test campaign in the SECI). **Only tests run on the physical target count as target testing for credit.** |
| Development / host environments | Android Emulator ("Pixel 10 Pro" AVD, API 36+, 4 KB and 16 KB page-size images) and the JVM for host unit tests. These can generate verification credit only where SGP-SVP-001 §6 shows the host is representative for that test (e.g. pure-Kotlin `core` logic). |
| Parameter data | The bundled seed catalog files (`assets/seed_catalog_*.txt`) and the climate lookup data change the software's behaviour without code changes. They are handled as **Parameter Data Items** (DO-178C §11.22) with their own requirements, verification and configuration identification. |
| User-modifiable data | Settings values (spacing multiplier, companion-rule toggle, zoom limits, thresholds…) are **user-modifiable parameters** (DO-178C §2.5.2). Their permitted ranges are requirements. Behaviour is verified at range boundaries, and the software must protect itself from out-of-range values. |
| Option-selectable software | Basic/Standard/Pro tier gating (`FeatureFlags.kt`) makes code **deactivated** in some configurations (DO-178C §2.5.4, §6.4.4.3). Deactivation must be designed and verified per SGP-SDS-001 §7. |

## 4. Architecture assurance boundary

To make Level A objectives achievable, the design (SGP-SDS-001) separates the software into layers:

- **`:core` (pure Kotlin/JVM module, no Android imports):** domain model, validation rules, geometry,
  scheduling (harvest/germination), Plan B, auto-populate, irrigation, weed-mask geometry, units, settings
  validation. **This is where the Level A verification effort (requirements-based tests, full structural
  coverage including MC/DC, data/control coupling analysis) is achievable** on the host JVM and repeatable on
  the target.
- **`:data`:** persistence (Room + SQLCipher), catalog loading, repositories.
- **`:app`:** Compose UI, navigation, ViewModels, platform services (camera, sensors, location).

All three are Level A (elected). There is no partitioning between them, because they run in one Android
process. The layering exists to keep complex logic in the part that can be verified most completely.

## 5. Deviations from DO-178C / ARP4754A objectives

Each deviation has an ID, the affected objectives, the reason, the mitigation and its status. Deviations are
**permanent** unless marked otherwise. The PSAC (SGP-PSAC-001 §10) repeats this list.

| ID | Deviation | Affected objectives | Reason | Mitigation |
|---|---|---|---|---|
| **DEV-01** | COTS platform software has no DO-178C life cycle data: Android OS, ART runtime, Kotlin standard library, kotlinx-coroutines, AndroidX (Compose, Lifecycle, Navigation, Room, CameraX), SQLCipher. | A-2…A-7 for those components; §12.1 (previously developed software) | The source and life cycle data cannot be obtained, or are not produced to DO-178C. | (1) Keep COTS use behind project-owned interfaces. (2) Usage-domain analysis: which COTS functions SGP depends on. (3) Requirements-based and robustness tests exercise every relied-on COTS behaviour on the target. (4) Pin exact versions and verify them (SCMP §6). (5) Track COTS problem reports / release notes on every upgrade. |
| **DEV-02** | Compiler/translator chain cannot be qualified: Kotlin compiler, D8/R8 (DEX + shrinking/optimisation), ART AOT/JIT compilation on the device. | A-5 obj. 7 (source to object traceability at Level A), DO-330 TQL-1 would be needed | These tools are not qualifiable by this project. ART compiles on the device at install time and at runtime (profile-guided), so the final machine code is not under project control. | (1) **All credit-taking tests run against the release APK** (R8-processed) on the target. (2) Source-to-bytecode traceability analysis for a sample of `:core` functions, looking for code the compiler adds (Kotlin intrinsics, null checks, `when` tables, inline expansions, coroutine state machines). This is recorded in the SVP §8.4. (3) R8 configuration (keep rules, optimisation level) is under CM, and changes trigger re-verification. (4) No credit is claimed for object-code coverage below DEX level. |
| **DEV-03** | Dynamic memory management and garbage collection (JVM/ART). | DO-332 OO.6.8 (memory management), DO-178C robustness | GC is inherent to Kotlin/Android. | Memory management vulnerability analysis per DO-332 (allocation in hot paths, unbounded collections, bitmap sizes). Explicit bounds on every collection that grows with user input (undo depth, nodes per plot, points per path). Stress tests on the target with the largest allowed plot and catalog (Pro). No reliance on `System.gc()`. |
| **DEV-04** | Independence. Level A requires many verification activities to be done by someone other than the author, and SQA to be independent. The project currently has a single human developer, working with AI assistants. | All Annex A objectives marked for independence (A-3…A-7, A-9) | Only one human is available. **An AI assistant is not accepted as an independent reviewer**: it is a tool, it is not accountable, and it may share the same misunderstanding that produced the artifact. | Open (temporary) deviation. (1) Recruit at least one independent human reviewer for the objectives that require independence. (2) Until then, every review record states "Independence NOT satisfied" and the item stays open in SGP-DWR-001. (3) AI assistants may perform *preliminary* checks, but their output is recorded as tool output, not as review evidence. |
| **DEV-05** | Target hardware is a consumer phone, not qualified equipment. Sensors (camera, light, IMU, GNSS) have unknown accuracy and failure modes. | ARP4754A implementation verification; DO-178C HW/SW integration assumptions | Consumer device. | Assumptions about the hardware are written as system requirements/assumptions with validation evidence (datasheets where published, measurements on the target). Sensor-dependent functions show the uncertainty to the user and fail safe (SAP §7). |
| **DEV-06** | No certification liaison (Table A-10). | A-10 obj. 1–3 | No authority. | The Project Owner plays the authority role: approves the PSAC, reviews the SAS, and agrees any change to this document. Records are kept exactly as if an authority existed. |
| **DEV-07** | Structural coverage tooling for Kotlin does not measure MC/DC. | A-7 obj. 5 (MC/DC) | No mature Kotlin MC/DC tool. | Code standard limits decisions to ≤3 conditions (SGP-SCS-001 R-CPX-3). JaCoCo supplies statement/branch data (bytecode level). MC/DC for every decision with ≥2 conditions is shown by a documented **MC/DC analysis worksheet** that lists the independence pairs and the test cases achieving them (SVP §8.3). If JaCoCo output is used for credit, JaCoCo is qualified at TQL-5 (SGP-TQP-001). |

Adding, removing or changing a deviation requires a revision of this document approved by the Project Owner,
and a matching update to the PSAC and the Deferred Work Register.

## 6. Use of AI assistants

AI assistants (such as Claude or Gemini) have been and will be used to draft code, requirements and documents.
Rules:

1. AI output is treated as if it came from an **unqualified tool**. No objective is satisfied by AI output
   alone. Every AI-produced artifact goes through the same review, analysis and test as hand-written work.
2. AI assistants must not produce text that claims a review, test, approval or sign-off happened unless a
   record of that activity exists in the repository. The old "Review Board" narratives are an example of
   what must not happen again.
3. Session "handoff" or "save files" procedures (`smart_garden_planner_automated_handoff.md`,
   `session_restart_prompt.txt`, `rename_gemini_files.py`) are **not** configuration management procedures.
   CM is defined only by SGP-SCMP-001.

## 7. Document set

See `docs/compliance/README.md` for the index, and for how each document maps to the DO-178C §11 life cycle
data and the ARP4754A plans.
