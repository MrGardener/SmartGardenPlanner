# SGP-SDP-001 — Software Development Plan (SDP)

| Field | Value |
|---|---|
| Document ID | SGP-SDP-001 |
| Revision | A (Draft) |
| DO-178C reference | §11.2 (supersedes the development/integration content of `Smart_Garden_Planner_Integration_Plan_v20_19.md`) |
| Control category | CC1 |

## 1. Purpose

This plan defines the software development processes for SGP-APP: requirements, design, coding and
integration. It also defines the development environment and the transition criteria that satisfy
DO-178C Table A-1 (development aspects) and Table A-2.

## 2. Standards

| Process | Standard |
|---|---|
| Requirements | SGP-SRS-STD-001 |
| Design | SGP-SDS-001 |
| Code | SGP-SCS-001 (Kotlin), enforced partly by detekt/ktlint/Android Lint configurations kept in the repository |

## 3. Software life cycle model

Iterative and incremental, following the REVAMP phases. Each **increment** delivers a set of requirements
that are fully developed and verified. Within an increment the order is:

```
system req (T2) ─► HLR ─► design/LLR ─► code ─► integration ─► verification ─► baseline
      ▲                │         │          │                     │
      └── safety assessment feedback (derived requirements) ──────┘
```

Because the software already exists (DO-178C §12.1.4), increments in Phases 1–3 mostly **re-engineer**
existing code:
1. Write or correct the requirements.
2. Write the design.
3. Change the code to match.
4. Verify.

Code written before its requirements is never baselined until the requirements exist and have been
reviewed.

## 4. Transition criteria

| Activity | Entry criteria | Exit criteria |
|---|---|---|
| HLR development | Relevant T2 requirements baselined; ConOps revision current | HLRs written to the standard; derived HLRs flagged and sent to the safety assessment; HLR review passed (SVP §4.1) |
| Design / LLR | HLRs reviewed | SDD section + LLRs written; design review passed (SVP §4.2); coupling tables updated |
| Coding | LLRs reviewed | Code compiles with zero warnings in `:core`; static analysis clean (or deviations recorded); code review passed (SVP §4.3); trace annotations present |
| Integration | Code reviewed | Builds reproducibly; unit tests green; release APK built and installed on the target |
| Baseline | All of the above for the increment's scope | Verification results recorded; open PRs dispositioned; SCI/SECI generated; SQA conformity check done |

Transitions may overlap for different requirements. SQA audits whether the criteria were met (SQAP §5).

## 5. Software development processes

### 5.1 Requirements process
- **Inputs:** T2 system requirements, ConOps, safety assessment outputs, FEATURE_ROADMAP items accepted for
  implementation.
- **Outputs:** HLRs, including interface requirements (UI behaviour, data formats, platform services used),
  performance/timing requirements, robustness requirements, Parameter Data Item requirements, and ranges
  for user-modifiable data.
- **Tooling:** Markdown requirement files under `docs/requirements/`, one file per subsystem, using the
  attribute format in SGP-SRS-STD-001 §4. A script generates the trace report (tool assessment:
  SGP-TQP-001).
- Derived requirements (those not traceable to a T2 requirement) are marked `Derived: yes` with a rationale,
  and are given to the safety assessment (SGP-SAP-001 §8).

### 5.2 Design process
- **Outputs:**
  - the architecture (module structure, component responsibilities, data flow, threading and coroutine
    model, error handling, persistence schema)
  - the LLRs
  - interface descriptions
  - data and control coupling tables
  - the deactivated-code design (tier gating)
  - the DO-332 analyses
- Recorded in the SDD (`docs/design/SGP-SDD-001`).
- The design must keep all domain rules in `:core` (pure Kotlin) so that they can be verified on the host
  JVM with full coverage, and repeated on the target.

### 5.3 Coding process
- Kotlin per SGP-SCS-001. Each function in `:core` carries KDoc with the LLR IDs it implements
  (`@implements LLR-…`), which the trace generator reads.
- Every change goes through a pull request with a review checklist (SCMP §8). Direct pushes to the protected
  branch are not allowed.
- AI-generated code is labelled in the PR description and reviewed like any other code (SGP-CB-001 §6).

### 5.4 Integration process
Integration replaces the "SSIP" document. Order (bottom-up, matching module dependencies):

1. `:core`: build + host unit tests.
2. `:data` against `:core`: host tests with an in-memory DB where possible, then instrumented DB tests on
   the target (SQLCipher, migrations).
3. `:app` against `:data`/`:core`: ViewModel tests on the host; Compose UI tests on the emulator and the
   target.
4. **Hardware/software integration:** the release APK (R8-processed, signed) is installed on the physical
   Pixel 10 Pro, and the HW/SW integration test suite runs there (SVP §5).

Integration problems are raised as Problem Reports. Integration results form part of the verification
results.

### 5.5 Parameter data development
Catalog and climate data files are produced by a documented, repeatable procedure:
- a source list for the values
- a generation script, if one is used
- structural validation at build time (DW-0813)

Changes to data files follow the same PR/review process as code.

## 6. Software development environment

| Element | Selection (Revision A — exact versions recorded per baseline in the SECI) |
|---|---|
| Host OS | Windows (current developer workstation); CI on Linux |
| IDE | Android Studio (current stable), with Gemini/other AI features treated as unqualified tools |
| Build | Gradle wrapper (version pinned, checksum verified), Android Gradle Plugin 9.x, Kotlin 2.x, KSP |
| Compiler chain | Kotlin compiler → JVM bytecode → D8/R8 → DEX; ART on the device (DEV-02) |
| Static analysis | Android Lint, detekt, ktlint (configurations under CM) |
| Test frameworks | JUnit 4/5, kotlinx-coroutines-test, AndroidX Test, Compose UI Test, Room MigrationTestHelper |
| Coverage | JaCoCo (statement/branch at bytecode level) + MC/DC worksheets (DEV-07) |
| Target | Physical Google Pixel 10 Pro (Android 16+); emulator "Pixel 10 Pro" AVD for development testing |
| CM | Git + GitHub (issues as PRs, pull requests, protected branch, tags) |

Environment changes (a tool version upgrade) are changes under CM. They are recorded in the SECI and trigger
the regression analysis in SVP §9.

## 7. Development of the existing code base (upgrade plan)

The work is defined in SGP-DWR-001 and scheduled by the REVAMP phases. Development-process priorities:

1. **Phase 0–1:** build environment (WP-05), plans approved (WP-01).
2. **Phase 2:** stop crashes and data loss (WP-06/08/09 blockers). These are allowed **before** the full
   requirements re-baseline, as corrective maintenance. Each fix is raised as a Problem Report and gets a
   regression test, and its requirements are formalised in Phase 3.
3. **Phase 3:** requirements and design re-baseline (WP-03/04) together with the architecture refactor
   (WP-07). From this point on, no code without requirements.
4. **Phases 4–8:** feature completion by increment.
5. **Phases 9–10:** verification campaigns, coverage closure, SAS.
