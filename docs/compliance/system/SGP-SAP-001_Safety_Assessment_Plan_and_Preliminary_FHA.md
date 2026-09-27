# SGP-SAP-001 — Safety Assessment Plan and Preliminary Functional Hazard Assessment

| Field | Value |
|---|---|
| Document ID | SGP-SAP-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| References | ARP4754A §5.1; ARP4761A (FHA, PSSA, SSA, CCA) |
| Control category | CC1 |

## 1. Purpose

This document defines how the safety assessment is performed for the SGP system, and records the
**preliminary FHA**. Its outputs:
- justify the assurance level (elected A, SGP-CB-001 §2)
- produce safety requirements that flow into the T2/HLR requirements
- assess derived requirements

## 2. Classification scale (adapted)

ARP4761A classifications are defined for aircraft effects. For this product they are adapted to effects
on the **user, their property and their data**. The mapping is recorded here so that it can be reviewed.

| Class | ARP4761A name | Adapted meaning for SGP | DAL it would require |
|---|---|---|---|
| CAT | Catastrophic | Death or life-threatening injury | A |
| HAZ | Hazardous | Serious injury, or large-scale loss of a livelihood | B |
| MAJ | Major | Minor injury; significant financial loss (e.g. loss of a commercial crop season); disclosure of personal data | C |
| MIN | Minor | Inconvenience; small economic loss (replanting a few plants); loss of planning data the user can re-enter | D |
| NSE | No Safety Effect | No effect on the user beyond cosmetic | E |

## 3. Process

| Step | When | Method | Output |
|---|---|---|---|
| FHA | Phase 0–2 | For each function in the ConOps: failure conditions (loss, erroneous/misleading, unintended), effect, classification, detection by the user | FHA table (§6, to be completed: DW-0201) |
| PSSA | Phase 3 | For each MIN or worse condition: architecture mitigations, safety requirements, allocation to `:core`/`:data`/`:app`/PDI | Safety requirements tagged `SAF` (DW-0202) |
| CCA | Phase 3 | Common causes: single catalog data error affecting many functions; shared clock; shared persistence; COTS platform defects | CCA record (DW-0203) |
| Derived requirement assessment | Continuous | Each derived HLR/LLR is assessed for the conditions it could cause | Disposition in the requirement attributes (DW-0204) |
| SSA | Phase 10 | Confirm the safety requirements are implemented and verified; confirm the classifications still hold | SSA record (DW-0205) |

## 4. Assumptions

- A-01: The user is an adult gardener who uses the app as an **aid**, not as the sole authority. Seed
  packet instructions remain available.
- A-02: The app gives no advice on pesticides/chemicals dosage in its current scope. If FR-018 (pest-management
  plan) adds dosage information, the FHA must be revisited, since toxic exposure could be MAJ or worse.
- A-03: No network, so no remote attack surface (T2-CON-010). Local attack surface: device theft, backups,
  export files.

## 5. Functions analysed

F1 Plot definition · F2 Plant placement & spacing/companion validation · F3 Scheduling (harvest,
germination alert) · F4 Plan B recovery options · F5 Weed-risk overlay · F6 Irrigation route · F7 Catalog &
Encyclopedia information (pests, care) · F8 Persistence & encryption · F9 Plot photo · F10 Climate/location
lookup · F11 Measurement (pending D-03) · F12 Export/import (plan files) · F13 Automatic planting and crop-rotation
planning (Plan an area for me, next season, rotation plans) · F14 Shade estimation (whole day, time of day, plant
shade) · F15 Irrigation coverage · F16 Plot duplication / templates · F17 Computer planner (web client) · F18 Pest and
wildlife guidance · F19 Pre-planning checks and plant priority · F20 Satellite photo layer · F21 Disclaimer

## 6. Preliminary FHA

| ID | Function | Failure condition | Effect on the user | Detectable? | Class | Notes / candidate safety requirement |
|---|---|---|---|---|---|---|
| FC-01 | F2 | Erroneous spacing validation (accepts plants too close together) | Overcrowded plants, lower yield | Partly (visible on the canvas) | MIN | Uniform scale (DW-0901); boundary tolerance (DW-0814); test boundaries |
| FC-02 | F2 | Erroneous rejection of valid placements | Inconvenience | Yes | NSE/MIN | Same as FC-01 |
| FC-03 | F2 | Antagonist pair not flagged | Poorer growth | No | MIN | Companion data integrity (DW-0809/0813) |
| FC-04 | F3 | Erroneous harvest date | Harvest too early/late; some crop loss | Partly | MIN | Clock injection, time zone handling (DW-0712) |
| FC-05 | F3 | Missed germination-overdue alert | Late replanting; loss of part of the season | No | MIN | Alert evaluation tests; clock-change robustness |
| FC-06 | F4 | Misleading Plan B (suggests a crop that can't mature before frost) | Wasted seeds/time | No | MIN | Runtime Plan B uses the remaining season (DW-0808); show the assumptions |
| FC-07 | F5 | Misleading weed mask marks a crop seedling area as "weed zone" | User may pull crop seedlings | No | MIN | Mask derived from the same transform as the plants; the mask is display-only, with a warning text |
| FC-08 | F6 | Erroneous irrigation route | Wasted material; some plants not watered | Yes | MIN | Label as a suggestion; show route length |
| FC-09 | F7 | Erroneous catalog values (spacing, days) | Planning errors across many plants (common cause) | No | MIN | PDI provenance + integrity checks (DW-0813); show "estimate" disclaimer |
| FC-10 | F8 | Loss of all planning data (DB corruption, key loss on restore, crash during migration) | Must re-enter plans | Yes | MIN | Backup exclusion + controlled reset (DW-0605); migration tests (DW-0806); future export (DW-1203) |
| FC-11 | F8 | Disclosure of user data (locations, photos of property) | Privacy loss | No | MAJ? | **To be decided in the FHA review:** if location/photo data are stored, disclosure may be MAJ under the adapted scale. Encryption at rest + backup exclusion + no network |
| FC-12 | F9 | Photo mis-scaled or rotated relative to the plot | Misleading layout | Yes | MIN | EXIF handling (DW-1102) |
| FC-13 | F10 | Wrong hardiness zone / frost dates | Poor crop choice; loss of plantings to frost | No | MIN | Climate PDI provenance; accuracy gate; show the zone used |
| FC-14 | F11 | Erroneous measured dimensions | Whole layout mis-scaled | Partly | MIN | D-03; show uncertainty (T2-PRE-020) |
| FC-15 | F12 | Tampered import accepted | Corrupted plans | Partly | MIN | Signature + schema validation (DW-1203) |
| FC-16 | All | App crash / hang | Inconvenience, possible loss of the last edit | Yes | MIN | Robustness tests; transactional writes |
| FC-17 | F13 | Rotation plan puts a crop where its family grew the previous season, or advises the wrong family | Soil-borne disease and pest build-up; lower yield | No | MIN | Strict rule with explicit note when relaxed (HLR-ROT-040); advice text shows the season and family used; tests RotationPlannerTest |
| FC-18 | F13 | Planner places a vine with no free runway, or a tall crop in front of short ones | Crowding and shading of neighbours | Yes (proposal shown before planting) | MIN | Proposal is a preview with explanation (HLR-AUTO-060); guides drawn; tests BlockPlannerTest |
| FC-19 | F14 | Shade underestimated (sun shown where there is shade) | Sun-loving crops planted in shade; lower yield | No | MIN | Clear-sky and solar-time assumptions stated in the legend and manual (SP-01); plant heights approximate |
| FC-20 | F15 | Water map shows a plant as reached when it is not | Plants left unwatered | Partly (the gardener sees the plants) | MIN | Straight-line hose reach and no pressure model stated (SP-01, SP-03); plants out of reach ringed |
| FC-21 | F16 | Duplicate changes or loses the original plot, or copies incomplete data | Loss of planning data | Yes | MIN | Copy in one transaction with new ids; original untouched (HLR-TPL-010) |
| FC-22 | F17 | Computer planner and phone give different results for the same plot, or unsaved browser work is lost | Inconsistent plans; re-entry of work | Partly | MIN | Same source compiled twice (DEV-02 extended); file round-trip test; draft kept in the browser plus explicit Save |
| FC-23 | F18 | Pest or wildlife advice followed in a way that harms people, pets or wildlife, or breaks a local rule (electric fence, trapping, bait) | Injury, harm to animals, legal trouble | Partly (the gardener chooses what to do) | MAJ | Advice limited to widely published, non-lethal measures first; every relevant tip says to check local rules and choose methods safe for pets, children and wildlife; disclaimer (F21, HLR-DISC-010) before first use; no product dosing given |
| FC-24 | F20 | Satellite photo scaled, placed or turned wrongly, so obstacles are traced in the wrong place | Wrong shade estimate and placement (as FC-19) | Yes (photo shown under the plot; the gardener compares with the yard) | MIN | Scale set from two points of known distance with the result shown; photo only a tracing aid (shade uses the drawn obstacles); nothing fetched from Google automatically, so no location is sent unless the gardener opens Google Maps |
| FC-25 | F19 | Pre-planning checks miss a problem or overstate one (space, sun, clash, zone, rotation, pests) | Plan made on wrong expectations; lower yield | Yes (checks are advice; the proposal is still a preview) | MIN | Checks use the same engines as planning (sun, spacing, companions, zone, rotation); proposal preview and Harmony remain; disclaimer |
| FC-26 | F13 | "Replace all" swaps the wrong variety, or silently leaves plants crowding their neighbours | Wrong crop planted; crowding and lower yield | Yes (the plot shows the result; one undo reverts it) | MIN | Only the chosen variety is changed, positions and dates kept; the message states how many now crowd a neighbour and the new spacing; perennials that can't survive the zone are refused; tests ShapesSwapRotationTest |

**Preliminary conclusion:** the worst classification is **MIN**, with FC-11 possibly **MAJ** depending on
the FHA review. The minimum required level would therefore be D (or C for the privacy aspect). **Level A
is elected** and is kept regardless.

## 7. Safety design principles (for the PSSA)

- SP-01 Show uncertainty and assumptions wherever values are estimates (catalog, measurement, climate).
- SP-02 Fail visible: on internal errors, show a message and keep the stored data unchanged. Never silently
  "fix" data.
- SP-03 Display-only overlays (weed mask, irrigation) are labelled as guidance.
- SP-04 A single source of truth for geometry (the canvas transform) avoids inconsistent views.
- SP-05 Data integrity is protected by transactions, migrations, backup exclusion and controlled key-loss
  recovery.

## 8. Interface with requirements

Each FHA row becomes one or more requirements tagged `Safety: SAF (FC-xx)`. Each derived requirement records
which FC it could affect, or "none".
