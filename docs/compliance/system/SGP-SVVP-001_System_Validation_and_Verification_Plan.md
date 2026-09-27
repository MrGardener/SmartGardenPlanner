# SGP-SVVP-001 — System Requirements Validation and Verification Plan (ARP4754A)

| Field | Value |
|---|---|
| Document ID | SGP-SVVP-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| References | ARP4754A §5.4 (requirements validation), §5.5 (implementation verification) |
| Control category | CC1 |

## 1. Purpose

- **Validation:** show that the T2 system requirements (and the assumptions) are **correct and complete**,
  i.e. the right product is being built.
- **Verification:** show that the implemented system on the target **meets** the T2 requirements, i.e. the
  product is built right.

Software-level verification against HLR/LLR is in SGP-SVP-001. This plan covers the system level and reuses
software results where they apply.

## 2. Independence

At the elected Level A, validation of the requirements needs independence from their author (a reviewer
other than the person who wrote the requirement). The same open deviation applies (SGP-CB-001 DEV-04).

## 3. Inputs

ConOps (revised), T2 requirements, FHA/PSSA, the assumption list (SDCP §4), FEATURE_ROADMAP items accepted
into scope.

## 4. Requirements validation

### 4.1 Methods
| Method | Used for |
|---|---|
| Traceability | Every T2 traces to the ConOps, the FHA, or a recorded user need; every ConOps function is covered by T2 |
| Review (checklist §4.2) | All T2 requirements and assumptions |
| Analysis | Consistency (no conflicts), completeness against the functions list, safety requirement coverage |
| Prototyping / simulation | UI and interaction requirements: emulator prototypes and walkthroughs on the target with the user (the Project Owner acts as a representative user) |
| Similarity / experience | Agronomic data requirements: comparison with published seed-supplier data (catalog provenance) |

### 4.2 Validation checklist (per T2 requirement)
- [ ] Traces to a ConOps function, the FHA or a recorded user need
- [ ] Correct: states what the user actually needs (confirmed by walkthrough/prototype where UI-related)
- [ ] Complete: normal, abnormal and boundary behaviour are specified
- [ ] Unambiguous and verifiable (per SGP-SRS-STD-001)
- [ ] Consistent with other T2 requirements and the assumptions
- [ ] Feasible on the target platform (Pixel 10 Pro, Android 16+)
- [ ] Safety requirements from the PSSA included

### 4.3 Assumption validation
Each assumption in SDCP §4 (clock accuracy, sensor units, keystore properties, camera resolution, GNSS
accuracy reporting) is validated by a documented source (Android documentation, a published device spec) or by
measurement on the target. The evidence is recorded in the validation matrix.

### 4.4 Validation matrix
`docs/validation/validation-matrix.md`, with columns: T2 ID, source/trace, method(s), evidence reference,
reviewer (independence), result, open issues.

## 5. Implementation verification (system level)

### 5.1 Methods
| Method | Used for |
|---|---|
| Test on the target | All T2 requirements with observable behaviour: executed as HW/SW integration procedures (SVP §5) on the physical Pixel 10 Pro with the release APK |
| Analysis | T2 requirements that can't be fully tested (e.g. "no network dependency": merged-manifest and dependency analysis + test in airplane mode; privacy requirements) |
| Inspection | Documentation-type requirements (user manual content), labels/disclaimers |
| Demonstration | Usability-oriented requirements, via walkthrough on the target with recorded video/screenshots |

### 5.2 System verification matrix
`docs/verification/system-verification-matrix.md`, with columns: T2 ID, method, procedure ID (HW/SW
integration test or analysis), result reference, SECI reference, status.

### 5.3 Target configurations
Every system verification campaign records:
- device model
- Android version, build number and security patch
- display size and font scale settings
- gesture navigation mode
- light/dark theme
- battery state

Minimum configurations per campaign:
- default settings (portrait + landscape)
- largest font + largest display size
- "Don't keep activities" enabled
- airplane mode (T2-CON-010)

## 6. Completion criteria

- Validation is complete when every T2 requirement and assumption has a passed validation record, and every
  ConOps function is covered.
- Verification is complete when every T2 requirement has a passed verification record on the target (or an
  approved analysis/inspection). Failures are raised as Problem Reports and dispositioned.
- Results feed the SSA (SAP §3) and the summary (DW-0108).
