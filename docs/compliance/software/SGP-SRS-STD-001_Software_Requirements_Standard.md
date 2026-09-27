# SGP-SRS-STD-001 — Software Requirements Standard

| Field | Value |
|---|---|
| Document ID | SGP-SRS-STD-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| DO-178C reference | §11.6 (also used for T2 system requirements under ARP4754A) |
| Control category | CC1 |

## 1. Requirement levels

| Level | Name | Content | Parent |
|---|---|---|---|
| T1 | Customer adaptation | Reserved | — |
| **T2** | System requirements (ARP4754A) | What the SGP system (app on the target phone) must do, as the user and environment see it. No software structure. | ConOps, safety assessment |
| **HLR** | Software high-level requirements (DO-178C) | Software behaviour at its external interfaces: UI behaviour, data formats, platform services, timing, robustness. No internal structure. | T2 (or Derived) |
| **LLR** | Software low-level requirements | Behaviour of design components (classes/functions in `:core`, `:data`, `:app`), detailed enough to code from without further information, and **verifiable by test**. | HLR (or Derived) |

## 2. ID rules

- Format: `T2-<AREA>-<nnn>`, `HLR-<AREA>-<nnn>`, `LLR-<AREA>-<nnn>-<letter>`.
- Areas in use: FUN, DAT, CON, VAL, ENV, INT, PRE, ERR, SEC, SAF, PLT, CFG, SEN, UI, ZOOM, MOVE, CAT, SEL.
  New areas are added by revising this standard.
- IDs are **never reused or renumbered**. A removed requirement keeps its ID with `Status: Deleted` and a
  rationale.
- New requirements take the next free number in steps of 10 (T2/HLR), or the next letter (LLR).

## 3. Writing rules

| Rule | Text |
|---|---|
| R-01 | One requirement per statement, using **"shall"**. "Should/may/will" are not requirements (use them in notes). |
| R-02 | Verifiable: state measurable criteria (values, units, tolerances, time limits). Banned without a quantification: *optimized, best, correct, comprehensive, continuously, instantly, user-friendly, realistic, gracefully, as appropriate, etc.* |
| R-03 | Units are always explicit (m, m², days, lux, %, ms, dp). Internal canonical units: meters, days, milliseconds UTC epoch. |
| R-04 | Numeric ranges state inclusive/exclusive bounds and the behaviour at and beyond each bound. |
| R-05 | T2 and HLR must not name classes, functions, libraries or APIs (e.g. no `System.gc()`, `View.VISIBLE`, `Path.Op.DIFFERENCE`). LLRs may name design components, but API choices belong in the SDD unless the API *is* the required behaviour. |
| R-06 | Every requirement covers both normal and abnormal behaviour (what happens on invalid input or failure), or references the requirement that does. |
| R-07 | Settings-controlled values reference the Setting requirement instead of hardcoding a number (e.g. "the tilt threshold configured per HLR-CFG-0xx"). |
| R-08 | No conflicting requirements; each open conflict is recorded as a Problem Report until resolved. |
| R-09 | User-visible text that must be exact (error messages) is listed in the error catalogue (SDD) and referenced by ID. |
| R-10 | Time-dependent behaviour states the clock source, time zone and the behaviour on clock changes. |
| R-11 | Floating-point comparisons state a tolerance or the arithmetic type (see SVP §7.4 lesson). |
| R-12 | Process obligations (reviews, audits, documentation) are **not** product requirements. They belong in plans. |

## 4. Attributes (mandatory for every requirement)

```
ID:            HLR-DAT-050
Title:         Spacing conflict detection
Statement:     The software shall …
Rationale:     why the requirement exists (and source: ConOps §, safety assessment, user feedback)
Parent(s):     T2-FUN-050, T2-INT-030        (or "Derived")
Derived:       no | yes — with justification; "yes" requires a safety assessment disposition
Safety:        none | SAF (from PSSA) with reference
Verification:  Test | Analysis | Review | Inspection   (Test preferred; others need justification)
Status:        Proposed | Approved | Implemented | Verified | Deleted
Allocation:    :core | :data | :app | PDI              (HLR/LLR only)
Notes:         optional
```

Requirement files: `docs/requirements/<level>-<area>.md`. The trace generator (SGP-TQP-001) parses these
attributes. Code carries `@implements <LLR-ID>` in KDoc. Tests carry `@verifies <ID>` (an annotation or a KDoc
tag).

## 5. Special classes of requirements

- **Derived requirements:** requirements that do not come directly from a parent, e.g. design decisions
  with externally visible effects. They must be flagged and assessed for safety impact.
- **Parameter Data Item requirements:** file format, field ranges, referential integrity (e.g. every
  companion code resolves to a species), and provenance of values.
- **User-modifiable parameter requirements:** range, default, persistence, behaviour on corrupted or
  out-of-range stored values.
- **Deactivated-function requirements:** for tier-gated features, what is active in each tier and how
  activation is prevented.
- **Performance requirements:** measured on the target, with the conditions stated (plot size, node count,
  catalog tier).

## 6. Example (rewrite of an existing requirement)

*Old (T2-VAL-015):* "…trigger a confirmation prompt if values are unrealistically small or large."

*New:*
```
ID:           T2-VAL-015
Statement:    When the user enters a plot length or width that is within the configured allowed range
              (HLR-CFG-0xx) but below 0.30 m or above 200 m, the system shall ask the user to confirm
              the value before creating or updating the plot, and shall not create or update the plot
              unless the user confirms.
Rationale:    Catch unit-entry mistakes (e.g. centimetres typed as metres) without blocking valid
              unusual plots.
Parent(s):    ConOps §5.3
Verification: Test (boundaries 0.29/0.30/200/200.01 m, in both display units)
```
(The 0.30 m / 200 m values are examples to be confirmed during WP-03.)
