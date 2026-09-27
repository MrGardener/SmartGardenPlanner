# SGP-SDCP-001 — System Development and Certification Plan (ARP4754A)

| Field | Value |
|---|---|
| Document ID | SGP-SDCP-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| References | ARP4754A §3–5 (planning, development, integral processes); combines the ARP4754A *Certification Plan* and *Development Plan* |
| Related | SGP-SAP-001 (safety), SGP-SVVP-001 (validation & verification), SGP-SCMP-001 (CM, system + software), SGP-SQAP-001 (process assurance, system + software), SGP-PSAC-001 (software) |
| Control category | CC1 |

## 1. System description

**Function:** help a user plan and manage a garden plot on a phone, fully offline.

**System boundary:**

```
            ┌──────────────────────────── SGP System ─────────────────────────────┐
 User ◄────►│  Touch UI / display                                                 │
            │        │                                                             │
            │  SGP-APP software item (Level A elected)                             │
            │        │                         │                                   │
            │  Android 16+ platform (COTS)     Bundled data (PDI: catalog, climate)│
            │        │                                                             │
            │  Pixel 10 Pro hardware: display, touch, camera, light sensor, IMU,   │
            │  GNSS, storage, battery, TEE/StrongBox keystore                      │
            └──────────────────────────────────────────────────────────────────────┘
 External (not relied on): network (not used), Google backup (data excluded), other devices (future export only)
```

**Operating environment:** outdoors or indoors. Variable light (bright sun reduces screen readability).
One user. May be used with gloves (touch accuracy). Interrupted by calls and notifications (process death).

## 2. Development assurance levels

| Level | Value | Rationale |
|---|---|---|
| FDAL (SGP System) | **A (elected)** | The FHA gives a maximum severity of *Minor* (SAP §6), which would require D. Level A is a programme decision (SGP-CB-001 §2). |
| IDAL (SGP-APP software item) | **A (elected)** | Same. |
| IDAL (COTS platform, hardware) | Not assured | SGP-CB-001 DEV-01, DEV-05 |

If the FHA ever finds a more severe classification, this table is revisited. The elected level already
covers any outcome.

## 3. Development process (ARP4754A)

| Process | How it is done | Output |
|---|---|---|
| Planning | This plan + SAP, SVVP, SCMP, SQAP, PSAC | Approved plans (DW-0101…0105) |
| Aircraft/"product" function → system requirements | From the ConOps to T2 requirements, per SGP-SRS-STD-001 | T2 set (WP-03) |
| Safety assessment | FHA → PSSA → SSA per SAP | Safety requirements fed into T2/HLR |
| System architecture | Allocation: software item SGP-APP, PDI, COTS platform, phone hardware | Allocation table (§4) |
| Allocation to items | T2 → HLR (software) or → assumptions on hardware/COTS | Trace T2→HLR; assumption list |
| Implementation | DO-178C process for SGP-APP (PSAC/SDP) | Software baselines |
| Integration | Release APK on the target (SVP §5 HW/SW integration) | Integration results |
| Requirements validation | SVVP §4 | Validation matrix |
| Implementation verification | SVVP §5 | System verification matrix |
| Configuration management | SGP-SCMP-001 | Baselines, indexes |
| Process assurance | SGP-SQAP-001 | Records |
| Certification coordination | Project Owner in the authority role (DEV-06) | Approvals, SAS |

## 4. Allocation of system functions

| System function (ConOps) | Allocated to | Assumptions on COTS/hardware (validated per SVVP §4.3) |
|---|---|---|
| Plot definition and to-scale layout | SGP-APP (`:core`, `:app`) | Display density and insets are reported correctly by Android |
| Placement rules (spacing/companion) | SGP-APP (`:core`) + PDI (catalog values) | — |
| Scheduling (harvest, germination, Plan B) | SGP-APP (`:core`) + PDI | Device clock is correct to within ±1 day; time zone from the OS |
| Overlays (weed mask, irrigation) | SGP-APP (`:core`, `:app`) | — |
| Encrypted local persistence | SGP-APP (`:data`) + COTS SQLCipher + TEE/StrongBox | Android Keystore keeps keys non-exportable; storage is non-volatile |
| Plot photo | SGP-APP (`:app`) + camera hardware + CameraX (COTS) | Camera delivers images at ≥1080 px on the major axis |
| Environment checks (light, storage, battery) | SGP-APP + sensors/OS services | Sensor values have the documented units (lux, %) |
| Location / climate lookup | SGP-APP + PDI (climate data) + GNSS (optional) | GNSS reports horizontal accuracy |
| Measurement (pending D-03) | TBD | TBD |

## 5. Life cycle data (system level)

| ARP4754A data | Document |
|---|---|
| Certification plan / development plan | This document |
| Safety program plan and safety assessments | SGP-SAP-001, FHA/PSSA/SSA records |
| Requirements (system) | T2 requirements (WP-03) |
| Validation plan / records | SGP-SVVP-001 / validation matrix |
| Verification plan / records | SGP-SVVP-001 / system verification matrix |
| CM plan / records | SGP-SCMP-001 |
| Process assurance plan / records | SGP-SQAP-001 |
| Configuration index | SCI (system = software item + PDI + platform/hardware identification) |
| Development assurance summary | SAS (DW-0108) extended with the system-level results |

## 6. Transition criteria (system)

- T2 requirements are validated (SVVP §4) before HLR development for them is considered complete.
- The FHA is complete before T2 requirements are baselined. The PSSA is complete before design
  baselines. The SSA is complete before the final baseline.

## 7. Schedule

Follows REVAMP phases:
- plans (Phase 0)
- FHA + T2 re-baseline + validation (Phases 0–3)
- PSSA (Phase 3)
- implementation (Phases 1–8)
- system verification on the target (Phase 9)
- SSA + summary (Phase 10)

## 8. Roles

As in PSAC §7. At system level the Project Owner is also the "applicant" and the "authority" (DEV-06).

## 9. Project risks (replaces `smart_garden_planner_risk_register.md` project entries)

| ID | Risk | Mitigation |
|---|---|---|
| PRJ-01 | No independent verifier/SQA person, so Level A objectives cannot be closed | Recruit a reviewer (DW-1507); record the deviation until then |
| PRJ-02 | Toolchain churn (AGP/Kotlin/Android releases) invalidates verification | Pin versions per baseline; regression per SVP §9 |
| PRJ-03 | The effort of full Level A verification (MC/DC, coupling) exceeds capacity | Keep logic in `:core`; limit decision complexity (R-CPX-3); automate trace/coverage |
| PRJ-04 | Sensor-based measurement is infeasible as specified | Decision D-03 |
| PRJ-05 | Catalog values lack authoritative provenance | Provenance work DW-0813; disclose uncertainty to the user |
| OPP-01 | Platform-neutral `:core` enables future desktop/web clients (T2-PLT-*) | Keep `:core` free of Android dependencies |
