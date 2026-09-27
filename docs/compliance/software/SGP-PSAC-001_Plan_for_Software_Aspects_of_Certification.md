# SGP-PSAC-001 — Plan for Software Aspects of Certification (PSAC)

| Field | Value |
|---|---|
| Document ID | SGP-PSAC-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| DO-178C reference | §11.1 |
| Software item | SGP-APP (Smart Garden Planner Android application) |
| Software level | **A (elected — see SGP-CB-001 §2)** |
| Approval | Project Owner (acting as the certification authority role, SGP-CB-001 DEV-06) |
| Control category | CC1 |

> The word "certification" in this title follows DO-178C naming. As stated in SGP-CB-001, no authority is
> involved. This plan defines how the project **conforms** to the DO-178C Level A objectives.

---

## 1. System overview

Smart Garden Planner (SGP) is an offline Android application for planning a garden plot:
- define plot dimensions (and optionally photograph the plot)
- place plants on a to-scale canvas, with spacing and companion-planting checks
- mark no-plant paths and auto-fill areas
- see harvest dates, germination alerts and recovery ("Plan B") options
- browse a bundled plant catalog, and view weed-risk and irrigation overlays

It runs on a Google Pixel 10 Pro (target computer), stores all data locally in an encrypted database, and
makes no network connections. System requirements (T2) and the operational concept are in the ConOps and the
re-baselined system requirements (SGP-DWR-001 WP-03). The system-level process is in SGP-SDCP-001.

## 2. Software overview

| Aspect | Description |
|---|---|
| Language | Kotlin (JVM target), Android resources (XML) |
| Architecture | Three Gradle modules: `:core` (pure Kotlin domain logic), `:data` (Room/SQLCipher persistence, catalog loading), `:app` (Jetpack Compose UI, ViewModels, navigation, platform services). See SGP-SDS-001 and the SDD. |
| Runtime | Android Runtime (ART) on Android 16 (API 36) or later, single process, multiple threads (main thread + coroutine dispatchers owned by AndroidX/Room). |
| COTS / previously developed software | Android platform, ART, Kotlin stdlib, kotlinx-coroutines, AndroidX (Compose, Lifecycle, Navigation, Room, CameraX, Activity), SQLCipher for Android. Handled per SGP-CB-001 DEV-01. |
| Parameter data | Bundled seed catalog tiers and climate lookup data (Parameter Data Items, §6.3). |
| User-modifiable data | Settings (§6.4). |
| Option-selectable functions | Basic/Standard/Pro tier gating, which leaves some code deactivated in some configurations (§6.5). |
| Partitioning | None. One software item at one level. |
| Multiple-version dissimilar software | Not used. |

## 3. Certification considerations

- **Basis:** DO-178C Level A objectives (Annex A Tables A-1…A-10), with the DO-330 and DO-332 supplements
  (SGP-CB-001 §1).
- **Software level rationale:** elected Level A. The preliminary FHA (SGP-SAP-001) classifies no failure
  condition above *Minor*. The elected level is **higher** than the minimum, so the safety assessment does
  not constrain the level further.
- **Means of compliance:** by the processes in SDP/SVP/SCMP/SQAP, the standards, and the life cycle data
  listed in §5. Compliance status per objective is summarised in §8 and reported in the SAS.
- **Deviations:** SGP-CB-001 §5 (DEV-01 … DEV-07), repeated in §10.

## 4. Software life cycle

Processes: planning, development (requirements, design, coding, integration), and the integral processes
(verification, configuration management, quality assurance, certification liaison).

The life cycle is **iterative and incremental**. Development follows the phases of REVAMP_PLAN_Pixel10Pro.md
(0–10). Because the software already exists, the project applies **DO-178C §12.1.4 (upgrading a development
baseline)**: existing code is kept where it is sound, and the missing life cycle data (requirements, design,
trace, verification) is produced for it, by reverse engineering where necessary. Any code whose requirements
cannot be established is removed.

Transition criteria between processes are in SGP-SDP-001 §4. No code enters a baseline unless:
- its requirements are baselined and reviewed
- it has been reviewed against the code standard
- its tests trace to its requirements

## 5. Software life cycle data

| DO-178C §11 item | Project document / artifact | CC |
|---|---|---|
| 11.1 PSAC | This document | CC1 |
| 11.2 Software Development Plan | SGP-SDP-001 | CC1 |
| 11.3 Software Verification Plan | SGP-SVP-001 | CC1 |
| 11.4 SCM Plan | SGP-SCMP-001 | CC1 |
| 11.5 SQA Plan | SGP-SQAP-001 | CC1 |
| 11.6 Software Requirements Standards | SGP-SRS-STD-001 | CC1 |
| 11.7 Software Design Standards | SGP-SDS-001 | CC1 |
| 11.8 Software Code Standards | SGP-SCS-001 | CC1 |
| 11.9 Software Requirements Data | Re-baselined requirements (`docs/requirements/`, to be created: DW-0301) | CC1 |
| 11.10 Design Description | `docs/design/SGP-SDD-001` (to be created: DW-0401) | CC1 |
| 11.11 Source Code | `Android App/` source tree at a tagged baseline | CC1 |
| 11.12 Executable Object Code | Signed release APK/AAB built from the baseline (hash recorded in the SCI) | CC1 |
| 11.13 Verification Cases and Procedures | Test source (`src/test`, `src/androidTest`, `:core` tests) + manual procedures in `docs/verification/procedures/` | CC1 |
| 11.14 Verification Results | `docs/verification/results/<baseline>/`: test reports, coverage, analyses, review records | CC2 |
| 11.15 Life Cycle Environment Configuration Index (SECI) | `docs/cm/SECI-<baseline>.md` | CC1 |
| 11.16 Software Configuration Index (SCI) | `docs/cm/SCI-<baseline>.md` | CC1 |
| 11.17 Problem Reports | Issue tracker, "PR" label/template (SGP-SCMP-001 §7) | CC2 |
| 11.18 SCM Records | Git history, tags, PR/merge records, baseline records | CC2 |
| 11.19 SQA Records | `docs/sqa/records/` | CC2 |
| 11.20 Software Accomplishment Summary | `docs/compliance/SGP-SAS-001` (end of revamp: DW-0108) | CC1 |
| 11.21 Trace Data | Generated trace report per baseline (DW-1306) | CC1 |
| 11.22 Parameter Data Item File | `assets/seed_catalog_*.txt`, climate data asset, with a PDI specification | CC1 |

## 6. Additional considerations

### 6.1 Previously developed software (the existing code base)
Existing project code at commit `074117f` is treated as a development baseline being upgraded (§4). The
deviations and gaps are recorded in SGP-GAP-001 and SGP-RSM-001, and every gap is tracked in SGP-DWR-001.

### 6.2 COTS software
See SGP-CB-001 DEV-01. For each COTS component the project records:
- the exact version
- the functions used (usage domain)
- the requirements-based and robustness tests that exercise those functions on the target
- a review of its release notes / known issues at each upgrade

A COTS upgrade is a change that triggers regression per SVP §9.

### 6.3 Parameter Data Items
The seed catalog and climate data change the software's outputs, so they are verified as data:
- a PDI specification defines the format, ranges and referential integrity
- automated structural checks run at build time
- the content (spacing, germination and harvest values) is checked against a documented source, by a
  sample audit
- every PDI has a version and a hash recorded in the SCI

### 6.4 User-modifiable data (Settings)
Each Setting has a requirement giving its range and default. The software enforces the range when it reads
the value. Tests cover the minimum, the maximum, the default, and a corrupted stored value.

### 6.5 Option-selectable software and deactivated code
Tier gating means some features are inactive in some tiers. Per DO-178C §6.4.4.3, the design shows how
deactivated functions cannot be activated inadvertently, and verification shows (by test) that they are
inactive in each tier. Coverage analysis records deactivated code separately from dead code.

### 6.6 Tool qualification
See SGP-TQP-001. Summary:
- The compiler/translation chain is not qualified (DEV-02).
- Verification tools are qualified at TQL-5 only where the project takes credit from their output (JaCoCo,
  the trace generator, static analysers).
- AI assistants are unqualified, and all their output is verified (SGP-CB-001 §6).

### 6.7 Object-oriented technology (DO-332)
DO-332 applies. The design and code standards restrict inheritance depth, require substitutability (LSP)
for any subtype used polymorphically, limit dynamic dispatch in `:core`, forbid reflection, and require
exception handling to be explicit. The memory management analysis is DW-0404.

### 6.8 Field-loadable software
Updates are delivered as whole APK/AAB releases (Play Store or side-load). Each release is a new baseline
with its own SCI. The app's data migration behaviour is a verified requirement.

## 7. Organisation and responsibilities

| Role | Responsibility | Current assignment |
|---|---|---|
| Project Owner | Approves plans, baselines, deviations and the SAS. Acts in the authority role. | Repository owner |
| Developer | Requirements, design, code, tests | Repository owner (+ AI assistants as tools) |
| Independent verifier | Reviews and analyses that require independence | **Open — DEV-04, DW-1507** |
| SQA | Process audits, conformity review | **Open. Must be independent of the developer. Until assigned, SQA records carry "independence not satisfied".** |
| Configuration manager | Baselines, SCI/SECI, PR system | Repository owner |

## 8. Annex A objective compliance summary

"Plan" gives where the objective is satisfied. "Status" is the Revision A status, where **Not met** means
the life cycle data does not yet exist.

| Table | Topic (objectives) | How satisfied | Status |
|---|---|---|---|
| A-1 | Software planning (7) | This PSAC + SDP, SVP, SCMP, SQAP, standards; plans coordinated and approved | Drafts written; approval pending (DW-0101…0105) |
| A-2 | Software development (7) | SDP §5: HLR, derived requirements, architecture, LLR, source code, EOC, PDI | Not met: re-baseline WP-03/WP-04 |
| A-3 | Verification of requirements (7) | SVP §4.1 requirements review checklist, with independence | Not met |
| A-4 | Verification of design (13) | SVP §4.2 design review + architecture/coupling analyses | Not met |
| A-5 | Verification of coding & integration (9) | SVP §4.3 code review, standards checks, PDI verification, source→object analysis (DEV-02) | Not met |
| A-6 | Testing (5) | SVP §5–7: HLR- and LLR-based normal and robustness tests on the target; EOC compatibility with the target | Not met: existing unit tests are partial and not traced |
| A-7 | Verification of verification (9) | SVP §8: test procedure/result reviews, HLR/LLR coverage, statement/decision/MC/DC coverage (DEV-07), data & control coupling, compiler-added code analysis (DEV-02) | Not met |
| A-8 | SCM (6) | SGP-SCMP-001 | Partial: git exists; baselines, PR system and SCI not yet in place |
| A-9 | SQA (5) | SGP-SQAP-001 | Not met: independence open (DEV-04) |
| A-10 | Certification liaison (3) | Project Owner in the authority role (DEV-06) | Partial |

## 9. Schedule

The schedule follows REVAMP_PLAN_Pixel10Pro.md phases:
- planning approval: Phase 0
- requirements and design re-baseline: Phases 0–3
- implementation: Phases 1–8
- verification campaigns and coverage: Phase 9, with continuous unit testing from Phase 1
- SAS: Phase 10

Planned reviews (SOI-style checkpoints, run internally):
1. **Planning review**, end of Phase 0
2. **Development review**, after the requirements/design re-baseline in Phase 3
3. **Verification review**, in Phase 9
4. **Final review / SAS**, in Phase 10

## 10. Deviations

As in SGP-CB-001 §5:
- DEV-01: COTS without life cycle data
- DEV-02: unqualified compiler/R8/ART chain
- DEV-03: garbage-collected memory
- DEV-04: independence not yet available
- DEV-05: consumer target hardware
- DEV-06: no authority
- DEV-07: no MC/DC tool

The SAS reports the final state of each.
