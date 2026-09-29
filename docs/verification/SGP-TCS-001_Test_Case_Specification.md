# SGP-TCS-001 — Software Test Case Specification (LLR-based)

| Field | Value |
|---|---|
| Document ID | SGP-TCS-001 |
| Revision | A — Proposed (2026-09-28) |
| Level | Low-level requirements-based test cases (DO-178C §6.4.2, §6.4.4.1) |
| Parent documents | SGP-SW-LLR-001 (requirements), SGP-SVP-001 (verification plan) |
| Procedures | SGP-TPR-001 |
| Checked by | `python3 tools/tc_trace.py --check` (CI job "Requirements trace") |
| Control category | CC1 |

## 1. How to read this document

Every LLR in SGP-SW-LLR-001 has **at least three** test cases: a nominal case, at least one limit case (minimum, maximum or the exact boundary) and at least one invalid, unexpected, alphanumeric or random case. `tools/tc_trace.py` fails the build if an LLR is missing, has fewer than three cases, lacks a limit or an invalid/random case, or names a test that does not exist.

**Kind**

| Code | Meaning |
|---|---|
| NOM | Nominal (normal) input |
| MIN / MAX / BND | Minimum, maximum, or exactly at / just across a boundary |
| INV | Invalid or unexpected value (NaN, infinity, negative, out of range, missing, damaged) |
| ALN | Alphanumeric or odd text input (letters in numbers, symbols, other scripts, very long text) |
| RND | Random or property-based input (seeded, repeatable), including random positions and random behavior |

**Method** (how the case is run)

| Method | Meaning | Procedure |
|---|---|---|
| AUTO `Class.method` | JVM unit test in `Android App/app/src/test`, run on every pull request and every merge to main | TP-01, TP-07 |
| WEB `script` «check» | Browser check in `web/tests/` (Playwright, Chromium), run on every pull request and merge | TP-03, TP-04 |
| CI `workflow` «step» | A CI step whose success is the evidence | TP-02, TP-05, TP-06 |
| DEVICE `Class.method` | Instrumented test in `Android App/app/src/androidTest`, run on a device or emulator | TP-08 |
| MANUAL TP-nn.m | Step m of manual procedure TP-nn on an Android phone | SGP-TPR-001 |
| REVIEW TP-11.m | Inspection item of the code review checklist | TP-11 |
| Planned | No test yet: the LLR is not implemented, or the case is still to be automated | — |

**Result** (as of this revision)

| Result | Meaning |
|---|---|
| PASS | Automated case passing on the current head |
| MANUAL | Run by a person per SGP-TPR-001; result recorded in the campaign log (SGP-SVR) |
| PLANNED | Not yet runnable |
| BLOCKED | Feature deferred (camera, sensors, location: DW-1101/1104/1105) |
| DEV | The case follows the LLR text, which the code does not do: see §4, owner decision needed |

**LLR status**: IMPL implemented as written · PART partly · DEV implemented differently (decision needed) · TGT target design, not implemented · OBS describes removed code (delete or rewrite).

## 2. Summary

| | Count |
|---|---|
| LLRs | 302 |
| Test cases | 950 |
| Regression entries | 31 |
| LLR status | IMPL 130 · PART 107 · DEV 31 · TGT 29 · OBS 5 |
| Case results | PASS 479 · MANUAL 306 · PLANNED 68 · BLOCKED 54 · DEV 43 |
| Case kinds | NOM 322 · MIN 10 · MAX 42 · BND 260 · INV 242 · ALN 24 · RND 50 |
| Case methods | AUTO 348 · WEB 130 · CI 4 · DEVICE 8 · MANUAL 342 · REVIEW 12 · Planned 106 |

| LLR status | IMPL 130 · PART 107 · DEV 31 · TGT 29 · OBS 5 |
| Case results | PASS 479 · MANUAL 306 · PLANNED 68 · BLOCKED 54 · DEV 43 |
| Case kinds | NOM 322 · MIN 10 · MAX 42 · BND 260 · INV 242 · ALN 24 · RND 50 |
| Case methods | AUTO 348 · WEB 130 · CI 4 · DEVICE 8 · MANUAL 342 · REVIEW 12 · Planned 106 |

## 3. Test cases by LLR

### 3.1 `:core` — domain rules and calculations

#### LLR-LIM-010 — DEV

No single constants object; the limits live next to their users (e.g. `AutoPopulateEngine.MAX_POINTS` = 20 000, plan file 5000 plants/plot, name 200 characters, plot 0.5–1000 m on the web).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LIM-010.1 | NOM | Inspect `:core` for each constant in the LLR table | Each value is defined once and referenced by name | REVIEW TP-11.1 | DEV |
| TC-LIM-010.2 | MAX | Plan file with 5001 plants on one plot | First 5000 kept, the rest skipped with a warning | AUTO `PlanFileLimitsTest.plantLimitPerPlotAndItemsOutsideThePlot` | PASS |
| TC-LIM-010.3 | MAX | Area fill asked for more than MAX_POINTS positions | Empty result, no memory exhaustion | AUTO `AdviceRobustnessTest.areaFillerRejectsBadInputs_andCapsHugeGrids` | PASS |
| TC-LIM-010.4 | INV | Plot name of 250 characters, description of 600, address of 300 in a plan file | Cut to 200 / 500 / 200 | AUTO `PlanFileLimitsTest.namesAndTextsAreCutToTheirLimits` | PASS |

#### LLR-LIM-020 — PART

`core` is a package inside `:app`, not a separate module; the web build compiles it without Android, which fails on any Android import.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LIM-020.1 | NOM | Build the web planner from `core/*.kt` | Compiles with no Android classes | CI `web-planner.yml` «Build the single-file planner» | PASS |
| TC-LIM-020.2 | BND | Add `import android.util.Log` to a `core` file and build the web planner | Build fails | MANUAL TP-11.2 | MANUAL |
| TC-LIM-020.3 | INV | Add a dependency from `core` to `data` | A build check fails (no such check yet: dependency rule by review only) | Planned | PLANNED |

#### LLR-LIM-030 — PART

Most model classes carry field comments; no systematic KDoc of unit and range.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LIM-030.1 | NOM | Review each public model class in `core` | Every field has meaning, type, unit and range | REVIEW TP-11.3 | MANUAL |
| TC-LIM-030.2 | BND | Review fields with limits (radius, zone, sizes) | Minimum and maximum stated | REVIEW TP-11.3 | MANUAL |
| TC-LIM-030.3 | INV | Review a field with no unit (e.g. counts, codes) | Stated as unitless / format given | REVIEW TP-11.3 | MANUAL |

#### LLR-UNIT-010 — DEV

`DistanceUnit` {METERS, INCHES}; the factor is 39.3701 in/m, not exactly 1/0.0254.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-UNIT-010.1 | NOM | 1 m in inches | 39.37 in (±0.01) | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |
| TC-UNIT-010.2 | BND | 0 m, 0.01 m, 2000 m in both units | Round trip within 0.01 % | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |
| TC-UNIT-010.3 | INV | Exactly 0.0254 m per inch required by the LLR | Differs by 3×10⁻⁶ relative; decision needed | Planned | DEV |

#### LLR-UNIT-020 — IMPL

`metersToDisplay` / `displayToMeters`.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-UNIT-020.1 | NOM | 2.5 m → display → meters | 2.5 m | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |
| TC-UNIT-020.2 | MIN | 0 m | 0 in both units | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |
| TC-UNIT-020.3 | MAX | 2000 m (largest plot setting) | Round trip within 0.01 % | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |
| TC-UNIT-020.4 | RND | 5,000 random values 0–2,000 m in both units | Text round trip within 0.01 % | AUTO `LlrGapTest.units_randomValuesRoundTripInBothUnits` | PASS |

#### LLR-UNIT-030 — DEV

`format` writes no space before the suffix ("3.25m") and always uses "." as the decimal separator; inches use 2 decimals unless asked.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-UNIT-030.1 | NOM | format(1 m, METERS) | "1.00m" (LLR says "1.00 m") | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | DEV |
| TC-UNIT-030.2 | BND | Rounding half-up at 0.005 m | "0.01 m" | Planned | PLANNED |
| TC-UNIT-030.3 | INV | Locale with decimal comma | Comma separator per LLR; implementation keeps "." | Planned | DEV |

#### LLR-UNIT-040 — DEV

`parseToMeters` trims, accepts one "," as the decimal sign and any finite number; it accepts signs and exponents ("1e3", "-2") the LLR rejects.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-UNIT-040.1 | NOM | " 2,5 " in meters | 2.5 m | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |
| TC-UNIT-040.2 | INV | "", "abc", "1.2.3", "12m", "NaN", "Infinity", "1e40" | null | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |
| TC-UNIT-040.3 | ALN | Random text, comma and dot mixes | null or a finite number, never an exception | AUTO `InputRobustnessTest.distances_invalidTextGivesNull_andCommaDecimalsAreAccepted` | PASS |
| TC-UNIT-040.4 | BND | 13-character number; "-2"; "1e3" | Rejected per LLR; implementation accepts | Planned | DEV |

#### LLR-UNIT-050 — TGT

No `formatArea`; areas are shown in m² only.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-UNIT-050.1 | NOM | formatArea(10 m², INCHES) | "107.6 ft²" | Planned | PLANNED |
| TC-UNIT-050.2 | MIN | formatArea(0, METERS) | "0.00 m²" | Planned | PLANNED |
| TC-UNIT-050.3 | MAX | formatArea(4 000 000 m², INCHES) | No exponent notation | Planned | PLANNED |
| TC-UNIT-050.4 | INV | formatArea of NaN or a negative area | Refused, never shown as NaN | Planned | PLANNED |

#### LLR-GEO-010 — IMPL

`PlotPoint(x, y)` in Float meters from the top-left corner.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEO-010.1 | NOM | Point parsed from "1.5,2" | x = 1.5 along the length, y = 2 down the width | AUTO `RoadmapFeaturesTest.pointsRoundTrip_andMalformedPairsSkipped` | PASS |
| TC-GEO-010.2 | BND | Point at (0, 0) and at (length, width) | Both inside the plot | AUTO `EditingAndSitePropertyTest.geometry_randomRectanglesAndDegenerateOutlines` | PASS |
| TC-GEO-010.3 | INV | "1,2,3;x,y;NaN,1" | Malformed pairs skipped | AUTO `InputRobustnessTest.points_malformedPairsAreSkipped` | PASS |

#### LLR-GEO-020 — IMPL

Distance computed in double inside the validator.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEO-020.1 | NOM | Two plants 0.90 m apart with radii 0.6 + 0.3 | Allowed (touching) | AUTO `CompanionPlantingValidatorTest.spacingBoundary_touchingCirclesAreAllowed` | PASS |
| TC-GEO-020.2 | BND | Just inside the sum of radii | Rejected | AUTO `CompanionPlantingValidatorTest.spacingBoundary_justInsideIsRejected` | PASS |
| TC-GEO-020.3 | RND | Same pair rotated in random directions | Same result on every axis | AUTO `CompanionPlantingValidatorTest.spacingBoundary_ruleIsAxisIndependent` | PASS |

#### LLR-GEO-030 — PART

No `distanceToRect`; rectangle paths are handled as polygons in the planner's path check.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEO-030.1 | NOM | Point 1 m left of a 2×2 rectangle | 1.0 | Planned | PLANNED |
| TC-GEO-030.2 | BND | Point on a corner / on an edge | 0 | Planned | PLANNED |
| TC-GEO-030.3 | INV | Degenerate rectangle (width 0) | Distance to the segment, no NaN | Planned | PLANNED |

#### LLR-GEO-040 — DEV

`distanceToPolyline` returns `Float.MAX_VALUE` (not +∞) for an empty list.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEO-040.1 | NOM | Point 1 m from a two-segment line | 1.0 | AUTO `EditingAndSitePropertyTest.irrigation_coverageRulesAtTheLimits` | PASS |
| TC-GEO-040.2 | BND | One-point list; zero-length segment | Distance to that point | AUTO `EditingAndSitePropertyTest.geometry_randomRectanglesAndDegenerateOutlines` | PASS |
| TC-GEO-040.3 | INV | Empty list | +∞ per LLR; implementation Float.MAX_VALUE | Planned | DEV |

#### LLR-GEO-050 — DEV

`pointInPolygon` is plain even-odd ray casting with no on-edge rule; `PlotShape.contains` adds the rectangle check.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEO-050.1 | NOM | L-shaped outline, point in the cut-out | Outside | AUTO `RoadmapFeaturesTest.lShapedOutline_containsOnlyTheL` | PASS |
| TC-GEO-050.2 | BND | Fewer than 3 corners; point exactly on an edge | false; true per LLR (edge rule not implemented) | AUTO `EditingAndSitePropertyTest.geometry_randomRectanglesAndDegenerateOutlines` | DEV |
| TC-GEO-050.3 | INV | NaN or infinite coordinates | Outside | AUTO `EditingAndSitePropertyTest.geometry_randomRectanglesAndDegenerateOutlines` | PASS |

#### LLR-GEO-060 — TGT

No `clipRect`; drawn rectangles are clamped by the UI.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEO-060.1 | NOM | Rectangle half outside the plot | The inside half | Planned | PLANNED |
| TC-GEO-060.2 | BND | Rectangle touching the plot only on an edge | null (zero area) | Planned | PLANNED |
| TC-GEO-060.3 | INV | Rectangle entirely outside | null | Planned | PLANNED |

#### LLR-GEO-070 — PART

Bounding boxes are computed inline by the planners.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEO-070.1 | NOM | Triangle outline | Smallest box holding the 3 corners | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |
| TC-GEO-070.2 | BND | All points equal | Zero-size box, no division by zero | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | PASS |
| TC-GEO-070.3 | RND | Random polygons | Every vertex inside the box | Planned | PLANNED |

#### LLR-GEO-080 — PART

Drag rectangles are normalized in the canvas code (web and Android).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEO-080.1 | NOM | Drag from bottom-right to top-left on the web | Rectangle with positive width and height | WEB `smoke.mjs` «plan dialog suggests» | PASS |
| TC-GEO-080.2 | BND | Zero-size drag (click) | No area created | MANUAL TP-15.2 | MANUAL |
| TC-GEO-080.3 | RND | Drags in all four directions (Android) | Same rectangle each time | MANUAL TP-15.2 | MANUAL |

#### LLR-VALD-010 — IMPL

`PlotShape.contains` (rectangle then outline).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-010.1 | NOM | Plant in the middle of the plot | Inside | AUTO `EditingAndSitePropertyTest.geometry_randomRectanglesAndDegenerateOutlines` | PASS |
| TC-VALD-010.2 | BND | Plant at x = 0 and x = length | Inside | AUTO `EditingAndSitePropertyTest.geometry_randomRectanglesAndDegenerateOutlines` | PASS |
| TC-VALD-010.3 | INV | x = −0.0001, x = length + 0.0001, NaN | Outside | AUTO `EditingAndSitePropertyTest.geometry_randomRectanglesAndDegenerateOutlines` | PASS |

#### LLR-VALD-020 — IMPL

`(rA + rB) × margin`, tolerance `SPACING_TOLERANCE_M` = 0.0001.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-020.1 | NOM | Two plants far apart | No conflict | AUTO `CompanionPlantingValidatorTest.validatePlacement_passesWhenFarEnoughApart` | PASS |
| TC-VALD-020.2 | BND | Exactly the required distance; 1 mm less | Allowed; rejected | AUTO `CompanionPlantingValidatorTest.spacingBoundary_justInsideIsRejected` | PASS |
| TC-VALD-020.3 | MAX | Margin 0.3 and 2.0 | Required distance scales | AUTO `CompanionPlantingValidatorTest.spacingBoundary_marginScalesRequiredDistance` | PASS |
| TC-VALD-020.4 | RND | Random pairs, margins and positions | Symmetric and monotonic in distance | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | PASS |

#### LLR-VALD-030 — PART

No-plant paths are checked by the callers' `isBlocked(x, y, r)` (web and Android), not a core `pathConflict`.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-030.1 | NOM | Plan an area that crosses a path | No plant on the path | AUTO `AutoPlanAndPlanFileTest.everyPlacementPassesTheNormalRules` | PASS |
| TC-VALD-030.2 | BND | Plant whose circle just touches a path edge | Allowed | MANUAL TP-15.3 | MANUAL |
| TC-VALD-030.3 | INV | Line path with one point | Distance to that point used | MANUAL TP-15.3 | MANUAL |

#### LLR-VALD-040 — IMPL

Species prefix before the first "-"; codes trimmed and compared ignoring case (LLR revised 2026-09-28 by owner decision).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-040.1 | NOM | TOM-001 vs antagonist list "CAB,FEN" | Species TOM compared | AUTO `CompanionPlantingValidatorTest.validatePlacement_antagonistMatchingIsSpeciesPrefixBased_notExactCode` | PASS |
| TC-VALD-040.2 | ALN | " cab , fen ,," and "tom-001" | Matches CAB and TOM (case-insensitive) | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | PASS |
| TC-VALD-040.3 | BND | Code with no "-"; empty list | Whole code; no antagonists | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | PASS |

#### LLR-VALD-050 — IMPL

Either direction flags the pair.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-050.1 | NOM | Tomato near cabbage | Antagonists | AUTO `CompanionPlantingValidatorTest.validatePlacement_flagsAntagonistConflictNearby` | PASS |
| TC-VALD-050.2 | BND | Listed only on one side | Still antagonists | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | PASS |
| TC-VALD-050.3 | RND | Random pairs A,B and B,A | Same answer | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | PASS |

#### LLR-VALD-060 — IMPL

Antagonists within 2 × required spacing conflict.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-060.1 | NOM | Antagonist at 1.5 × spacing | Conflict | AUTO `CompanionPlantingValidatorTest.validatePlacement_flagsAntagonistConflictNearby` | PASS |
| TC-VALD-060.2 | BND | Antagonist at exactly 2 × spacing | No conflict | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | PASS |
| TC-VALD-060.3 | INV | Companion rules off | No antagonist conflict, spacing still enforced | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | PASS |

#### LLR-VALD-070 — PART

The setting exists; the Pro-tier lock is applied in Settings.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-070.1 | NOM | Pro tier, setting off | Rules not enforced | MANUAL TP-10.4 | MANUAL |
| TC-VALD-070.2 | BND | Basic tier with setting stored off | Enforced | MANUAL TP-10.4 | MANUAL |
| TC-VALD-070.3 | INV | Stored value "maybe" | Default (on) | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-VALD-080 — DEV

`validatePlacement` returns lists of spacing and antagonist conflicts; boundary and path are checked by callers; no ordered single reason.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-080.1 | NOM | Candidate close to one plant | Spacing conflict with that plant | AUTO `CompanionPlantingValidatorTest.validatePlacement_flagsSpacingViolationWhenTooClose` | PASS |
| TC-VALD-080.2 | BND | Candidate compared with itself (same id) | Ignored | AUTO `CompanionPlantingValidatorTest.validatePlacement_ignoresSelf` | PASS |
| TC-VALD-080.3 | RND | Unsaved candidates of one batch (id 0) | Compared with each other | AUTO `CompanionPlantingValidatorTest.unsavedCandidatesInTheSameBatchAreComparedWithEachOther` | PASS |
| TC-VALD-080.4 | INV | Boundary, path and spacing all violated | Single reason BOUNDARY first per LLR; implementation reports lists | Planned | DEV |

#### LLR-VALD-090 — IMPL

A nearby plant with an unknown variety gets a 0.3 m safe-guess radius and blocks placement, with the fixes offered (correct the catalog, or delete that plant and plant a substitute). LLR revised 2026-09-28 by owner decision.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-090.1 | NOM | Candidate 9 m from an unknown plant | Allowed | AUTO `LlrGapTest.unknownVariety_nearbyPlantIsNeverSkipped` | PASS |
| TC-VALD-090.2 | BND | On top of it; just inside and exactly at (candidate radius + 0.3 m) × margin; margins 0.5 and 2 | Refused with its id; allowed at the distance; the margin scales it | AUTO `LlrGapTest.unknownVariety_nearbyPlantIsNeverSkipped` | PASS |
| TC-VALD-090.3 | RND | 2,000 random positions around an unknown plant | Never allowed inside the distance, always allowed outside | AUTO `LlrGapTest.unknownVariety_nearbyPlantIsNeverSkipped` | PASS |
| TC-VALD-090.4 | INV | Known neighbor at the same spot | Ordinary spacing conflict, not an unknown-variety one | AUTO `LlrGapTest.unknownVariety_nearbyPlantIsNeverSkipped` | PASS |
| TC-VALD-090.5 | NOM | Phone: place, move and change variety next to an unknown plant; Delete that plant; Undo | Dialog with the two fixes; plant deleted in one undo step; Undo restores it | MANUAL TP-14.7 | MANUAL |

#### LLR-VALD-100 — PART

Conflicts are listed by `HarmonyAnalyzer` (Plot insights) rather than `conflictFlags`.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VALD-100.1 | NOM | Two antagonists close together | Both reported, lower score | AUTO `RoadmapFeaturesTest.harmony_reportsCloseAntagonistsAndScoresLower` | PASS |
| TC-VALD-100.2 | BND | Plant outside the outline | Reported | AUTO `RoadmapFeaturesTest.harmony_flagsPlantsOutsideOutline` | PASS |
| TC-VALD-100.3 | MIN | Empty and one-plant plots | No conflicts, no exception | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |
| TC-VALD-100.4 | RND | Harmony on random small and large plots | No exception, no NaN in texts | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |

#### LLR-FILL-010 — IMPL

`AutoPopulateEngine` spacing = 2 × radius × margin.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILL-010.1 | NOM | 2 m × 2 m with 0.5 m spacing | Grid inside the area | AUTO `AutoPopulateEngineTest.lineGrid_allPointsWithinBounds` | PASS |
| TC-FILL-010.2 | MIN | Zero area / spacing ≤ 0 | No positions | AUTO `AutoPopulateEngineTest.generatePositions_zeroArea_returnsEmpty` | PASS |
| TC-FILL-010.3 | INV | NaN or infinite inputs | No positions, no endless loop | AUTO `AdviceRobustnessTest.areaFillerRejectsBadInputs_andCapsHugeGrids` | PASS |

#### LLR-FILL-020 — PART

Line and hexagon grids exist; the exact offsets differ from the LLR formula.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILL-020.1 | NOM | Hexagon vs line on the same area | Hexagon holds at least as many | AUTO `AutoPopulateEngineTest.hexGrid_packsMoreOrEqualPointsThanLineGrid` | PASS |
| TC-FILL-020.2 | BND | Area smaller than one spacing | At most one position | AUTO `AutoPopulateEngineTest.areaSmallerThanSpacing_returnsAtMostOnePoint` | PASS |
| TC-FILL-020.3 | RND | Random areas | No two positions closer than the spacing | AUTO `AutoPopulateEngineTest.lineGrid_respectsMinimumSpacing` | PASS |

#### LLR-FILL-030 — PART

Polygon fill is done by the planners (`PlotShape.contains`).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILL-030.1 | NOM | Fill an L-shaped plot | Only positions in the L | AUTO `RoadmapFeaturesTest.lShapedOutline_containsOnlyTheL` | PASS |
| TC-FILL-030.2 | BND | Positions on the outline edge | Need half a radius clearance | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | PASS |
| TC-FILL-030.3 | RND | Random outlines | Every plant inside | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |

#### LLR-FILL-040 — DEV

Cap is `MAX_POINTS` = 20 000 (returns empty), not 100 000 with a TooManyCandidates error.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILL-040.1 | NOM | Normal plot | Positions in row-major order | AUTO `AutoPopulateEngineTest.lineGrid_allPointsWithinBounds` | PASS |
| TC-FILL-040.2 | MAX | 1000 m × 1000 m at 1 cm spacing | Empty result, estimate still given | AUTO `AdviceRobustnessTest.areaFillerRejectsBadInputs_andCapsHugeGrids` | PASS |
| TC-FILL-040.3 | INV | Error E-DATA-006 shown | Not implemented | Planned | DEV |

#### LLR-FILL-050 — PART

Fill accepts positions with `validatePlacement` against existing and accepted plants.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILL-050.1 | NOM | Fill next to existing plants | No overlap | AUTO `PlannerPropertyTest.plantsAlreadyThereAreNeverOverlapped` | PASS |
| TC-FILL-050.2 | MAX | Very large counts | Finishes in reasonable time | AUTO `PlannerPropertyTest.largePlotAndLargeCountsFinishInReasonableTime` | PASS |
| TC-FILL-050.3 | BND | Fill reaching the plant limit | Stops and says so (limit snackbar) | MANUAL TP-15.5 | MANUAL |
| TC-FILL-050.4 | INV | Fill across a no-plant path and over existing plants | Candidates on the path or on plants skipped and counted | MANUAL TP-15.5 | MANUAL |

#### LLR-FILL-060 — PART

Preview and stored counts come from the same call on Android.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILL-060.1 | NOM | Preview count then Fill | Same number placed | MANUAL TP-15.5 | MANUAL |
| TC-FILL-060.2 | BND | Area holding exactly one plant | Preview 1, 1 placed | MANUAL TP-15.5 | MANUAL |
| TC-FILL-060.3 | INV | Change the variety after the preview | Preview recomputed before Fill | MANUAL TP-15.5 | MANUAL |

#### LLR-SCHED-010 — DEV

Dates are epoch milliseconds from `PlatformClock`; no injected Clock.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SCHED-010.1 | NOM | Harvest date shown for a plant | Planting date + days | MANUAL TP-14.6 | MANUAL |
| TC-SCHED-010.2 | BND | Time-zone change while open | Dates recomputed | MANUAL TP-14.6 | MANUAL |
| TC-SCHED-010.3 | INV | Clock injection for tests | Not possible; time passed as a parameter instead | AUTO `LlrGapTest.germination_isOverdueOnlyAfterTheWholeWindow` | DEV |

#### LLR-SCHED-020 — IMPL

Harvest date = planting date + days to harvest.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SCHED-020.1 | NOM | Tomato planted today | Today + 73 days | MANUAL TP-14.6 | MANUAL |
| TC-SCHED-020.2 | BND | Days to harvest 1 and 3650 | Correct dates, no overflow | Planned | PLANNED |
| TC-SCHED-020.3 | INV | Planting date 29 Feb | Correct calendar date | Planned | PLANNED |

#### LLR-SCHED-030 — IMPL

Elapsed days by integer division of milliseconds.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SCHED-030.1 | NOM | Planted 7 days ago | 7 | AUTO `GerminationContingencyEngineTest.isGerminationOverdue_trueWhenElapsedExceedsWindow` | PASS |
| TC-SCHED-030.2 | INV | Planted in the future | Negative, never overdue | AUTO `LlrGapTest.germination_isOverdueOnlyAfterTheWholeWindow` | PASS |
| TC-SCHED-030.3 | MAX | Planted at epoch 0, now far in the future | No overflow | AUTO `LlrGapTest.germination_isOverdueOnlyAfterTheWholeWindow` | PASS |

#### LLR-SCHED-040 — IMPL

Overdue when elapsed > germination days and not resolved.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SCHED-040.1 | NOM | Within the window | Not overdue | AUTO `GerminationContingencyEngineTest.isGerminationOverdue_falseWithinWindow` | PASS |
| TC-SCHED-040.2 | BND | Exactly the window; one day more | Not overdue; overdue | AUTO `LlrGapTest.germination_isOverdueOnlyAfterTheWholeWindow` | PASS |
| TC-SCHED-040.3 | INV | Already resolved | Never overdue | AUTO `GerminationContingencyEngineTest.isGerminationOverdue_falseWhenAlreadyResolved` | PASS |

#### LLR-SCHED-050 — PART

Web plant dates are ISO dates checked on input; the Android date is set to now.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SCHED-050.1 | NOM | 2026-05-01 on the web edit card | Saved | WEB `smoke.mjs` «plant variety changed» | PASS |
| TC-SCHED-050.2 | BND | 1970-01-01; today + 365 | Accepted | Planned | PLANNED |
| TC-SCHED-050.3 | INV | 1969-12-31; today + 366; "2026-02-30"; "abc" | Rejected | AUTO `InputRobustnessTest.isoDates_edgeCases` | PASS |

#### LLR-SEASON-010 — PART

`GrowingSeason.date(dayOfYear)` formats a day of year (non-leap calendar).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SEASON-010.1 | NOM | Day 135 | May 15 | AUTO `SeasonPlanBSunTest.frostDatesComeFromTheNearestNoaaStation` | PASS |
| TC-SEASON-010.2 | BND | Day 0, 1, 365, 366 | Valid dates, no exception | AUTO `InputRobustnessTest.frostStations_malformedLinesAreRejected_andDistanceWrapsAt180` | PASS |
| TC-SEASON-010.3 | INV | Station line with day 367 or text | Station rejected | AUTO `InputRobustnessTest.frostStations_malformedLinesAreRejected_andDistanceWrapsAt180` | PASS |

#### LLR-SEASON-020 — PART

Remaining season is computed inside `BackupPlanner` from the frost dates.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SEASON-020.1 | NOM | Mid-season failure | Faster varieties offered | AUTO `SeasonPlanBSunTest.planBOffersFasterVarietiesThatCatchUpWithTheSurvivors` | PASS |
| TC-SEASON-020.2 | BND | Frost-free place | Options without the frost limit | AUTO `LlrGapTest.planB_respectsTheFirstFrostAndOddLimits` | PASS |
| TC-SEASON-020.3 | INV | Today after the first frost | Nothing offered that would be ready after the frost (+21 days hardy) | AUTO `LlrGapTest.planB_respectsTheFirstFrostAndOddLimits` | PASS |

#### LLR-PLANB-010 — PART

Superseded by LLR-PLANB-060 (`BackupPlanner`), which ranks same-species varieties by timing.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PLANB-010.1 | NOM | Failed tomato | Faster tomato varieties first | AUTO `SeasonPlanBSunTest.planBOffersFasterVarietiesThatCatchUpWithTheSurvivors` | PASS |
| TC-PLANB-010.2 | BND | Failed plant on the first-frost day | Nothing that would be ready after the frost | AUTO `LlrGapTest.planB_respectsTheFirstFrostAndOddLimits` | PASS |
| TC-PLANB-010.3 | RND | Every catalog crop as the failed plant | No exception, ≤ 6 options | AUTO `AdviceRobustnessTest.pestRisksAndPlanBForEveryPestAndCrop` | PASS |

#### LLR-PLANB-020 — PART

Nursery option from `GerminationContingencyEngine`.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PLANB-020.1 | NOM | Seed with transplant data | Nursery offered | AUTO `GerminationContingencyEngineTest.buildContingencyOptions_returnsAllThreePathsWhenDataAvailable` | PASS |
| TC-PLANB-020.2 | INV | Seed missing the data | Option omitted | AUTO `GerminationContingencyEngineTest.buildContingencyOptions_omitsPathsWithMissingData` | PASS |
| TC-PLANB-020.3 | BND | Empty catalog | No options, no exception | AUTO `LlrGapTest.planB_respectsTheFirstFrostAndOddLimits` | PASS |

#### LLR-PLANB-030 — PART

Catch crops come from `BackupPlanner` (food crops ready within 25–60 days).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PLANB-030.1 | NOM | Failed late crop | Quick crops offered | AUTO `SeasonPlanBSunTest.planBOffersFasterVarietiesThatCatchUpWithTheSurvivors` | PASS |
| TC-PLANB-030.2 | MAX | Limit of 6 options | At most 6 | AUTO `AdviceRobustnessTest.pestRisksAndPlanBForEveryPestAndCrop` | PASS |
| TC-PLANB-030.3 | INV | Catch crop that would fail validation at the spot | Not offered per LLR; not checked by `BackupPlanner` | Planned | DEV |

#### LLR-PLANB-040 — PART

After the season, options carry the frost note instead of SEASON_OVER.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PLANB-040.1 | NOM | Early season | Options without warnings | AUTO `SeasonPlanBSunTest.planBOffersFasterVarietiesThatCatchUpWithTheSurvivors` | PASS |
| TC-PLANB-040.2 | BND | Today = first frost (day 280) | Only crops ready by the frost, hardy ones by day 301 | AUTO `LlrGapTest.planB_respectsTheFirstFrostAndOddLimits` | PASS |
| TC-PLANB-040.3 | INV | Season over | SEASON_OVER reason per LLR | Planned | DEV |

#### LLR-PLANB-050 — IMPL

Plan B keeps the id and position, sets today's date (web `Store.change`, Android transaction).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PLANB-050.1 | NOM | Apply Plan B to a lost plant | Same spot, new variety | WEB `smoke.mjs` «Plan B replaced the lost plant» | PASS |
| TC-PLANB-050.2 | BND | Undo after Plan B | Old plant back | WEB `smoke.mjs` «undo reverts Plan B» | PASS |
| TC-PLANB-050.3 | INV | Plan B variety too wide for the spot | Refused with the spacing message | MANUAL TP-18.4 | MANUAL |

#### LLR-WIN-010 — DEV

Replaced by LLR-FROST-020 (`GrowingSeason.window`), which uses hardy and start-indoors weeks.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WIN-010.1 | NOM | Tomato at a zone-6 station | Plant out after the last frost | AUTO `SeasonPlanBSunTest.plantingWindowsFollowTheFrostDates` | PASS |
| TC-WIN-010.2 | BND | Frost-free station (all zeros) | 365-day season | AUTO `EditingAndSitePropertyTest.growingSeason_windowsForEveryCatalogCropAndAnySeason` | PASS |
| TC-WIN-010.3 | RND | Every catalog crop × random seasons | Valid window or the "short season" note | AUTO `EditingAndSitePropertyTest.growingSeason_windowsForEveryCatalogCropAndAnySeason` | PASS |

#### LLR-WIN-020 — IMPL

Zone labels are exactly those of the ZIP table (1a–13b); a bare "7" is not a zone (LLR revised 2026-09-28 by owner decision; HardinessZones.number fixed to agree).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WIN-020.1 | NOM | Every zone in zip_data.txt | Accepted; all 26 labels 1a–13b occur | AUTO `ZipDataTest.zoneLabelsFollowTheZipTable` | PASS |
| TC-WIN-020.2 | BND | "1a", "13b"; "0a", "14a" | Accepted; refused | AUTO `InputRobustnessTest.zones_onlyRealLabels` | PASS |
| TC-WIN-020.3 | ALN | "7", "0"…"14", "7c", "7A", " 7a", "07a", "" | Refused; number("7") is null (it returned 7 before the fix) | AUTO `ZipDataTest.zoneLabelsFollowTheZipTable` | PASS |

#### LLR-REC-010 — PART

`RecommendationEngine` filters by zone and flood/shade, preferring companions; no window filter.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REC-010.1 | NOM | Plot in zone 6 | Only zone-6 crops | AUTO `RoadmapFeaturesTest.recommend_excludesAntagonistsAndPrefersCompanions` | PASS |
| TC-REC-010.2 | BND | Flood and shade zones | Respected | AUTO `RoadmapFeaturesTest.recommend_respectsFloodAndShadeZones` | PASS |
| TC-REC-010.3 | INV | Unknown zone | No zone filter, no exception | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |

#### LLR-REC-020 — IMPL

`HardinessZones.blocksPlacement` / `describe` for perennials.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REC-020.1 | NOM | Tender perennial outside its zone | Warning | AUTO `RoadmapFeaturesTest.hardiness_blocksOnlyPerennialsOutsideZone` | PASS |
| TC-REC-020.2 | BND | Zone equal to zoneMin / zoneMax | No warning | AUTO `RoadmapFeaturesTest.hardiness_blocksOnlyPerennialsOutsideZone` | PASS |
| TC-REC-020.3 | INV | Annual outside its zone; invalid zone | No block | AUTO `RoadmapFeaturesTest.pickerConflict_flagsAntagonistOnPlotAndTenderPerennial` | PASS |

#### LLR-IRR-010 — DEV

Route starts at the plant nearest (0, 0), not at a water source; ties go to the first in list order, not the lowest id.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-IRR-010.1 | NOM | Plants on a line | Visited in order | AUTO `IrrigationRouteCalculatorTest.calculateDripRoute_startsFromNodeClosestToOrigin` | PASS |
| TC-IRR-010.2 | MIN | No plants; one plant | Empty; single stop | AUTO `IrrigationRouteCalculatorTest.calculateDripRoute_singleNode_returnsSingleStop` | PASS |
| TC-IRR-010.3 | RND | Random plants, all on one spot | Each visited once; zero length | AUTO `LlrGapTest.irrigationRoute_randomPlantsAreEachVisitedOnce` | PASS |

#### LLR-IRR-020 — DEV

Length starts at the first plant (no start leg).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-IRR-020.1 | NOM | Known 3-4-5 geometry | Exact length | AUTO `IrrigationRouteCalculatorTest.totalRouteLengthM_matchesKnownGeometry` | PASS |
| TC-IRR-020.2 | MIN | Empty or single route | 0 | AUTO `IrrigationRouteCalculatorTest.calculateDripRoute_emptyInput_returnsEmptyRoute` | PASS |
| TC-IRR-020.3 | RND | Random routes | Sum of the legs | AUTO `LlrGapTest.irrigationRoute_randomPlantsAreEachVisitedOnce` | PASS |

#### LLR-WEED-010 — TGT

No weed-risk grid.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WEED-010.1 | NOM | Plot half covered by circles | About half the area | Planned | PLANNED |
| TC-WEED-010.2 | MIN | Empty plot | Whole plot area | Planned | PLANNED |
| TC-WEED-010.3 | MAX | 1000 m plot | Computed with 400 × 400 cells in time | Planned | PLANNED |
| TC-WEED-010.4 | INV | Plant with a zero or NaN radius | Treated as no cover, no NaN area | Planned | PLANNED |

#### LLR-WEED-020 — TGT

No weed-risk estimate.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WEED-020.1 | NOM | SVP reference case | Within 1 % of the exact area | Planned | PLANNED |
| TC-WEED-020.2 | BND | Circle tangent to the edge | Within 1 % | Planned | PLANNED |
| TC-WEED-020.3 | RND | Random circles vs Monte-Carlo | Within 1 % | Planned | PLANNED |

#### LLR-HIST-010 — PART

Snapshots: Android `CanvasSnapshot` (plants, paths, features, outline, history); web `Snap`.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-HIST-010.1 | NOM | Snapshot, change, undo | Previous state back | WEB `smoke.mjs` «undo puts the tree back» | PASS |
| TC-HIST-010.2 | BND | Undo a whole plan (many plants) | One step | WEB `smoke.mjs` «one undo removes the whole plan» | PASS |
| TC-HIST-010.3 | RND | 40 undos then 40 redos | Same plan | WEB `adversarial.mjs` «undo 40× then redo 40×» | PASS |

#### LLR-HIST-020 — IMPL

`BoundedHistoryStack` drops the oldest; a new change clears redo.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-HIST-020.1 | NOM | Push 5 into a stack of 3 | Newest 3 kept | AUTO `LlrGapTest.historyStack_keepsTheNewestStepsUpToItsLimit` | PASS |
| TC-HIST-020.2 | MIN | Pop an empty stack; limit 0 or negative | null; keeps nothing (fixed: kept one) | AUTO `LlrGapTest.historyStack_keepsTheNewestStepsUpToItsLimit` | PASS |
| TC-HIST-020.3 | RND | 2000 random pushes and pops against a model | Same contents, never over the limit | AUTO `LlrGapTest.historyStack_keepsTheNewestStepsUpToItsLimit` | PASS |

#### LLR-HIST-030 — IMPL

`updateLimit` drops the oldest entries.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-HIST-030.1 | NOM | Lower the limit to 2 | Newest 2 kept | AUTO `LlrGapTest.historyStack_keepsTheNewestStepsUpToItsLimit` | PASS |
| TC-HIST-030.2 | MAX | Raise the limit to 100 | Nothing lost | AUTO `LlrGapTest.historyStack_keepsTheNewestStepsUpToItsLimit` | PASS |
| TC-HIST-030.3 | INV | Stored depth −1 or 1000 | Default 25 used | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-HIST-040 — IMPL

Undo/Redo buttons enabled from the stack sizes.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-HIST-040.1 | NOM | After one change | Undo on, Redo off | MANUAL TP-14.5 | MANUAL |
| TC-HIST-040.2 | BND | After undoing everything | Undo off, Redo on | WEB `smoke.mjs` «redo brings it back» | PASS |
| TC-HIST-040.3 | INV | New change after undo | Redo off | MANUAL TP-14.5 | MANUAL |

#### LLR-SETS-010 — DEV

Settings are the `AppSettings` fields with keys `settings.*`; several LLR keys differ or are absent (season_end, owner_name, battery_pct, last_variety).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETS-010.1 | NOM | Save and load every field | Same values | AUTO `StorageViewModelTest.saveThenLoadConfiguration_returnsSavedValue` | PASS |
| TC-SETS-010.2 | BND | Each slider at its minimum and maximum | Kept | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |
| TC-SETS-010.3 | INV | Missing key | Default | AUTO `StorageViewModelTest.loadConfiguration_missingKey_resultsInNullState` | PASS |

#### LLR-SETS-020 — IMPL

Each value parsed on its own; bad or out-of-range values fall back to the default (`sanitized`, added after a failure: NaN and out-of-range values were accepted).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETS-020.1 | NOM | Valid stored values | Used unchanged | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |
| TC-SETS-020.2 | INV | NaN, Infinity, −1, 1e9, unknown tier/language/layout | Defaults for those keys only | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |
| TC-SETS-020.3 | RND | 500 random damaged sets | Every value within its range | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |
| TC-SETS-020.4 | BND | Every slider exactly at its minimum and maximum | Kept unchanged | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-SETS-030 — PART

Sliders quantize by their step; stored values are range-checked on load.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETS-030.1 | NOM | Move a slider | Value on a step | MANUAL TP-10.3 | MANUAL |
| TC-SETS-030.2 | BND | Slider at both ends | Exact limits | MANUAL TP-10.3 | MANUAL |
| TC-SETS-030.3 | INV | Stored value between steps | Kept (no re-quantizing on load) | Planned | DEV |

#### LLR-SETS-040 — PART

Slider ranges keep zoom min ≤ 1 ≤ zoom max and plot min ≤ 5 < 10 ≤ plot max; load resets min = max.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETS-040.1 | NOM | Zoom 0.5–3 | Accepted | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |
| TC-SETS-040.2 | BND | Zoom min = max = 1 | Both back to defaults | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |
| TC-SETS-040.3 | INV | E-SET-001 message | Not needed while ranges can't overlap | Planned | DEV |

#### LLR-SETS-050 — DEV

Reset writes every default (including the disclaimer flag and last plan list).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETS-050.1 | NOM | Reset all settings | Every slider at its default | MANUAL TP-10.5 | MANUAL |
| TC-SETS-050.2 | BND | Reset twice | Same result | MANUAL TP-10.5 | MANUAL |
| TC-SETS-050.3 | INV | Keys kept by the LLR (owner name, last variety) | Reset too; decision needed | Planned | DEV |

#### LLR-CATP-010 — DEV

Catalog files are 14-field v1 lines with no header line; fields 15–21 are not used.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATP-010.1 | NOM | Every line of the three tiers | 14 fields, parsed into a seed | AUTO `CatalogDataTest.theCatalogLoaderReadsEveryLineOfEveryTier` | PASS |
| TC-CATP-010.2 | BND | Line with empty companion and antagonist lists | Accepted | AUTO `CatalogDataTest.everyLineIsWellFormedAndInRange` | PASS |
| TC-CATP-010.3 | INV | Header line `#SGP-CATALOG v2 …`, 21-field lines | Required by the LLR; not present | Planned | DEV |

#### LLR-CATP-020 — PART

The bundled data is checked by a unit test (added with this spec); the variety editor checks a subset.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATP-020.1 | NOM | All 2,939 Pro lines | Every rule of the table holds | AUTO `CatalogDataTest.everyLineIsWellFormedAndInRange` | PASS |
| TC-CATP-020.2 | BND | Zones 1..13, radius 0.02..10, germination 1..365, harvest 1..3650 | Limits enforced | AUTO `CatalogDataTest.everyLineIsWellFormedAndInRange` | PASS |
| TC-CATP-020.3 | ALN | Code with lower-case letters, spaces or a trailing "-" | Reported with its line | AUTO `CatalogDataTest.everyLineIsWellFormedAndInRange` | PASS |
| TC-CATP-020.4 | INV | Custom variety with spacing 0 or 60 m in a plan file | Skipped with a warning | AUTO `PlanFileLimitsTest.customVarietyLimits` | PASS |

#### LLR-CATP-030 — DEV

No `species.txt` / `families.txt`; species are the code prefixes of the Pro catalog.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATP-030.1 | NOM | Every companion/antagonist code | Names a species present in Pro | AUTO `CatalogDataTest.companionAndAntagonistCodesNameAKnownSpecies` | PASS |
| TC-CATP-030.2 | BND | Code used only in a higher tier | Shown as a raw code in lower tiers (known issue DW-0809) | MANUAL TP-18.8 | MANUAL |
| TC-CATP-030.3 | INV | Bundled species/family files | Not present | Planned | DEV |

#### LLR-CATP-040 — PART

Checked by unit tests in CI instead of a build task.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATP-040.1 | NOM | Tier counts | 253, 603, 2,939 | AUTO `CatalogDataTest.everyLineIsWellFormedAndInRange` | PASS |
| TC-CATP-040.2 | BND | Basic ⊆ Standard ⊆ Pro with identical lines | Holds | AUTO `CatalogDataTest.eachTierContainsTheSmallerOneWithIdenticalEntries` | PASS |
| TC-CATP-040.3 | INV | Duplicate code in a tier | Test fails | AUTO `CatalogDataTest.everyLineIsWellFormedAndInRange` | PASS |

#### LLR-CATP-050 — TGT

No asset manifest with SHA-256.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATP-050.1 | NOM | Build the app | Manifest lists each asset's SHA-256 | Planned | PLANNED |
| TC-CATP-050.2 | BND | Empty asset | Hash of the empty string | Planned | PLANNED |
| TC-CATP-050.3 | INV | Asset changed after the build | Mismatch detected at install | Planned | PLANNED |

#### LLR-CATP-060 — PART

`VarietyCatalogTraits` and the i18n variety rule split "Species - Cultivar".

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATP-060.1 | NOM | "Tomato - Brandywine" | Species Tomato, cultivar Brandywine | AUTO `SeasonsRotationTest.tomatoesSayCherryOrLarge_onionsSaySpringOrBulb` | PASS |
| TC-CATP-060.2 | BND | Name with no " - " | Whole name | AUTO `SpanishDictionaryTest.namesListsBulletsAndDates` | PASS |
| TC-CATP-060.3 | ALN | Name with two " - " | Split at the first | AUTO `I18nPropertyTest.dictionariesWithTrickyEntries` | PASS |

#### LLR-TIER-010 — PART

Tier switch replaces bundled rows and keeps custom ones (`isCustom`); planted-but-removed rows are not tracked separately.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-TIER-010.1 | NOM | Switch Basic → Pro | 2,939 bundled varieties | MANUAL TP-10.6 | MANUAL |
| TC-TIER-010.2 | BND | Switch Pro → Basic with a Pro-only variety planted | Planted variety kept | MANUAL TP-10.6 | MANUAL |
| TC-TIER-010.3 | INV | Custom variety with a bundled code | Never overwritten | MANUAL TP-10.6 | MANUAL |

#### LLR-COLR-010 — PART

Colors: `SeedEntity.colorHex` (any #RRGGBB[AA], checked on import), not a 16-color list.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-COLR-010.1 | NOM | Custom variety color "#ff6b35" in a plan file | Kept | AUTO `PlanFileLimitsTest.customVarietyLimits` | PASS |
| TC-COLR-010.2 | ALN | "red", "#12345", "#GGGGGG", "#FF6B35FF00" | Dropped on import (the last was cut to a valid color before the fix) | AUTO `PlanFileLimitsTest.customVarietyLimits` | PASS |
| TC-COLR-010.3 | BND | 16 preset colors only | Not enforced | Planned | DEV |

#### LLR-COLR-020 — PART

Automatic colors come from the variety traits (fruit color) or a hash; exact HSV rule not verified.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-COLR-020.1 | NOM | Red bell pepper | Red dot | AUTO `SeasonsRotationTest.peppersSaySweetOrSpicyAndTheirColour` | PASS |
| TC-COLR-020.2 | BND | Same code twice | Same color | Planned | PLANNED |
| TC-COLR-020.3 | RND | Random codes | Hue in 0–360, saturation 0.65, value 0.90 | Planned | PLANNED |

#### LLR-LTR-010 — DEV

Plants are labeled with short kind names ("Bell red", "Cherry") instead of 3-letter codes.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LTR-010.1 | NOM | Plant names shown on the layout | Short names drawn | WEB `smoke.mjs` «plant names shown on the layout» | PASS |
| TC-LTR-010.2 | BND | Two varieties with the same short name | Both drawn; letter-code uniqueness not required | MANUAL TP-13.4 | MANUAL |
| TC-LTR-010.3 | INV | Unique 3-character codes per plot | Not implemented | Planned | DEV |

#### LLR-AUD-010 — DEV

`SecurityAuditLogger` stores free-text lines (e.g. "KEY_LOSS: …"), not typed JSON entries with seq/time.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUD-010.1 | NOM | Key loss at start | Audit line written | MANUAL TP-21.4 | MANUAL |
| TC-AUD-010.2 | BND | First entry ever | Chains from 64 zeros | MANUAL TP-21.4 | MANUAL |
| TC-AUD-010.3 | INV | Details over 1024 bytes | Limit per LLR; not enforced | Planned | DEV |

#### LLR-AUD-020 — PART

SHA-256 hash chain over the previous hash and the message.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUD-020.1 | NOM | Two appends | Second chains to the first | MANUAL TP-21.4 | MANUAL |
| TC-AUD-020.2 | BND | Empty log | First hash from the zero string | MANUAL TP-21.4 | MANUAL |
| TC-AUD-020.3 | INV | Unicode message | UTF-8 hashed | Planned | PLANNED |

#### LLR-AUD-030 — PART

`verifyChainIntegrity` returns true/false, not the first failing seq.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUD-030.1 | NOM | Untouched log | Intact | MANUAL TP-21.4 | MANUAL |
| TC-AUD-030.2 | INV | One line edited | Not intact | MANUAL TP-21.4 | MANUAL |
| TC-AUD-030.3 | BND | Report the first failing entry | Not implemented | Planned | DEV |

#### LLR-CLIMQ-010 — IMPL

`ZipTable.find` / `findZone` on the bundled `zip_data.txt`.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CLIMQ-010.1 | NOM | 48104 | Ann Arbor, MI, zone 6a/6b | AUTO `ZipDataTest.zoneTable_givesPublishedZones` | PASS |
| TC-CLIMQ-010.2 | BND | First and last ZIP in the table | Found by binary search | AUTO `AutoPlanAndPlanFileTest.zipTable_findsByBinarySearch` | PASS |
| TC-CLIMQ-010.3 | ALN | "4810a", "481040", " 48104", "", "00000" | Not found | AUTO `InputRobustnessTest.zip_alphanumericAndOddInputsFindNothing` | PASS |

#### LLR-CLIMQ-020 — DEV

Nearest frost station uses an equirectangular distance within 250 km (LLR-FROST-010), not haversine within 50 km.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CLIMQ-020.1 | NOM | Ann Arbor coordinates | Nearest NOAA station | AUTO `SeasonPlanBSunTest.frostDatesComeFromTheNearestNoaaStation` | PASS |
| TC-CLIMQ-020.2 | BND | Longitude −179.9 vs +179.9 | Distance wraps at 180° | AUTO `InputRobustnessTest.frostStations_malformedLinesAreRejected_andDistanceWrapsAt180` | PASS |
| TC-CLIMQ-020.3 | INV | Mid-ocean point > 250 km from any station | No season | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |

#### LLR-CLIMQ-030 — IMPL

Latitude/longitude ranges checked on plan files and in the dialogs.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CLIMQ-030.1 | NOM | 42.28, −83.74 | Accepted | AUTO `PlanFileRoundTripTest.randomPlansComeBackUnchanged` | PASS |
| TC-CLIMQ-030.2 | BND | Latitude 90, longitude −180 | Accepted | AUTO `PlanFileLimitsTest.locationSoilAndDirectionOutOfRangeAreDropped` | PASS |
| TC-CLIMQ-030.3 | INV | Latitude 90.0001, longitude 180.5 in a plan file | Dropped | AUTO `PlanFileLimitsTest.locationSoilAndDirectionOutOfRangeAreDropped` | PASS |

### 3.2 `:data` — persistence, keys and files

#### LLR-DB-010 — IMPL

Schema 12 tables and columns as listed (LLR rewritten 2026-09-28 by owner decision).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DB-010.1 | NOM | Migrations replayed from schema 2 | Exactly the entities' tables, columns, types and nullability | AUTO `DatabaseSchemaTest.migratedTablesMatchTheEntitiesColumnForColumn` | PASS |
| TC-DB-010.2 | INV | Entity field added without a migration; a migrated column with the wrong nullability | Detected as a difference | AUTO `DatabaseSchemaTest.theSchemaCheckItselfCatchesAMissingMigration` | PASS |
| TC-DB-010.3 | BND | Database opened on a device | user_version 12, all 10 tables present | DEVICE `AppDatabaseTest.schema12_hasEveryTableAndForeignKeysOn` | MANUAL |
| TC-DB-010.4 | NOM | Create a plot and plants on a device, reopen | Same rows | DEVICE `AppDatabaseTest.insertAndRetrievePlot_returnsCommittedRecord` | MANUAL |

#### LLR-DB-020 — IMPL

Dates as UTC epoch milliseconds; points as "x,y;x,y" (LLR rewritten 2026-09-28).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DB-020.1 | NOM | Line path with 3 points | Stored and read back | AUTO `RoadmapFeaturesTest.pointsRoundTrip_andMalformedPairsSkipped` | PASS |
| TC-DB-020.2 | ALN | Stored points with letters, 3 values or NaN | Those pairs skipped | AUTO `InputRobustnessTest.points_malformedPairsAreSkipped` | PASS |
| TC-DB-020.3 | BND | Planting date at epoch 0 and far in the future | No overflow in elapsed days | AUTO `LlrGapTest.germination_isOverdueOnlyAfterTheWholeWindow` | PASS |

#### LLR-DB-030 — IMPL

Room enables foreign keys.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DB-030.1 | NOM | Delete a plot | Its plants, paths, features and history go too | DEVICE `AppDatabaseTest.deletingPlot_cascadesToPlantedNodes` | MANUAL |
| TC-DB-030.2 | INV | Insert a plant with an unknown plot id | Rejected by the constraint | MANUAL TP-21.6 | MANUAL |
| TC-DB-030.3 | BND | Delete the last plot | Empty tables, no orphan rows | MANUAL TP-21.6 | MANUAL |

#### LLR-DB-040 — IMPL

Schema version 12, every entity registered, migrations checked by the host test (LLR rewritten 2026-09-28 by owner decision).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DB-040.1 | NOM | AppDatabase | version = 12; the 10 entities registered | AUTO `DatabaseSchemaTest.schemaVersionIs12_andEveryEntityIsRegistered` | PASS |
| TC-DB-040.2 | BND | Device database after opening | user_version 12, foreign keys on | DEVICE `AppDatabaseTest.schema12_hasEveryTableAndForeignKeysOn` | MANUAL |
| TC-DB-040.3 | INV | A migration step removed from the chain or ALL_MIGRATIONS | Test fails | AUTO `DatabaseSchemaTest.migrationsFormOneUnbrokenChainTo12_andAreAllRegistered` | PASS |

#### LLR-DB-050 — PART

SQLCipher with a keystore-protected passphrase; file name differs from `sgp.db`.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DB-050.1 | NOM | Pull the database file from a device | Not readable without the key | MANUAL TP-21.3 | MANUAL |
| TC-DB-050.2 | BND | First start with no key | Key created, empty database | MANUAL TP-21.1 | MANUAL |
| TC-DB-050.3 | INV | Database copied to another device | Data-unreadable screen | MANUAL TP-21.4 | MANUAL |

#### LLR-DB-060 — PART

`lastModifiedTimestamp` is updated by most plot edits.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DB-060.1 | NOM | Rename a plot | Modified time updated | MANUAL TP-12.4 | MANUAL |
| TC-DB-060.2 | BND | Add a plant | Modified time updated | MANUAL TP-12.4 | MANUAL |
| TC-DB-060.3 | INV | Open a plot without changing it | Modified time unchanged | MANUAL TP-12.4 | MANUAL |

#### LLR-DB-070 — PART

DAOs expose Flow queries for plots and plants.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DB-070.1 | NOM | Plot list after adding a plot | Updates without reopening | MANUAL TP-12.1 | MANUAL |
| TC-DB-070.2 | BND | No plots | Empty list, empty-state text | MANUAL TP-12.1 | MANUAL |
| TC-DB-070.3 | INV | Plots using a deleted variety | Query returns none | MANUAL TP-12.1 | MANUAL |

#### LLR-DB-080 — IMPL

Database work runs on `SgpExecutors.dbDispatcher`; StrictMode not enabled.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DB-080.1 | NOM | Open a plot with 500 plants | No UI freeze | MANUAL TP-23.1 | MANUAL |
| TC-DB-080.2 | BND | Rotate the device during a save | Save completes once | MANUAL TP-23.1 | MANUAL |
| TC-DB-080.3 | INV | StrictMode disk-on-main-thread check | No violations | Planned | PLANNED |

#### LLR-MIG-010 — IMPL

One chain of single additive steps 2→12 (LLR rewritten 2026-09-28 by owner decision).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-MIG-010.1 | NOM | Migration list | MIGRATION_2_3 … MIGRATION_11_12, each one step, all registered | AUTO `DatabaseSchemaTest.migrationsFormOneUnbrokenChainTo12_andAreAllRegistered` | PASS |
| TC-MIG-010.2 | BND | Every NOT NULL column added by ALTER TABLE | Has a DEFAULT (existing rows stay valid) | AUTO `DatabaseSchemaTest.migratedTablesMatchTheEntitiesColumnForColumn` | PASS |
| TC-MIG-010.3 | INV | Upgrade a real schema-8 and schema-10 database with data on a device | Every row kept | MANUAL TP-21.2 | MANUAL |

#### LLR-MIG-020 — IMPL

Room runs each migration in a transaction.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-MIG-020.1 | NOM | Migration succeeds | New version | MANUAL TP-21.2 | MANUAL |
| TC-MIG-020.2 | INV | Migration throws half-way | Version unchanged, error shown | Planned | PLANNED |
| TC-MIG-020.3 | BND | Two migrations in a row (10→12) | Both applied | MANUAL TP-21.2 | MANUAL |

#### LLR-MIG-030 — PART

Room refuses a newer database; the app shows the unreadable-data screen.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-MIG-030.1 | NOM | Database at version 13 | Not opened for writing | Planned | PLANNED |
| TC-MIG-030.2 | BND | Version 12 (current) | Opened | MANUAL TP-21.1 | MANUAL |
| TC-MIG-030.3 | INV | Garbage file in place of the database | Unreadable-data screen | MANUAL TP-21.4 | MANUAL |

#### LLR-MIG-040 — PART

Versions below 8 have no migration path.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-MIG-040.1 | NOM | Version 7 database | E-DATA-003 | Planned | PLANNED |
| TC-MIG-040.2 | BND | Version 8 | Migrated | MANUAL TP-21.2 | MANUAL |
| TC-MIG-040.3 | INV | Version 0 / missing version | Treated as unreadable | Planned | PLANNED |

#### LLR-KEY-010 — PART

Keystore AES-GCM and HMAC keys with StrongBox preferred; aliases differ.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-KEY-010.1 | NOM | Key generation on a device | Key created and retrieved | DEVICE `SecurityKeyManagerTest.testKeystoreKeyGenerationAndRetrieval` | MANUAL |
| TC-KEY-010.2 | BND | Device without StrongBox | Falls back to the TEE | MANUAL TP-21.3 | MANUAL |
| TC-KEY-010.3 | INV | HMAC of the same data twice / tampered data | Same; verification fails | DEVICE `SecurityKeyManagerTest.testHmac_isNotConstant_andVerifiesCorrectly` | MANUAL |

#### LLR-KEY-020 — PART

Passphrase created once and kept wrapped; file layout differs from the LLR.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-KEY-020.1 | NOM | Restart the app | Same passphrase | DEVICE `SecurityKeyManagerTest.testPassphrasePersistsAcrossCalls` | MANUAL |
| TC-KEY-020.2 | BND | First start | 32 random bytes | MANUAL TP-21.1 | MANUAL |
| TC-KEY-020.3 | INV | Wrapped key file truncated | Key loss reported | MANUAL TP-21.4 | MANUAL |

#### LLR-KEY-030 — PART

Key loss is detected when the database can't be opened.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-KEY-030.1 | NOM | Keystore key deleted | Data-unreadable screen | MANUAL TP-21.4 | MANUAL |
| TC-KEY-030.2 | BND | Neither key nor database exists | Fresh install | MANUAL TP-21.1 | MANUAL |
| TC-KEY-030.3 | INV | Wrong version byte | KEY_LOSS | Planned | PLANNED |

#### LLR-KEY-040 — PART

Passphrase bytes are passed to SQLCipher; zeroing not verified.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-KEY-040.1 | NOM | Review key handling code | Array zeroed after use, never logged | REVIEW TP-11.4 | MANUAL |
| TC-KEY-040.2 | INV | Search logs for key material | None | REVIEW TP-11.4 | MANUAL |
| TC-KEY-040.3 | BND | Exception during open | Array still zeroed | REVIEW TP-11.4 | MANUAL |

#### LLR-KEY-050 — TGT

No v20.20 key-file migration.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-KEY-050.1 | NOM | Old key file present | Re-wrapped, then deleted after migration | Planned | PLANNED |
| TC-KEY-050.2 | BND | Old and new files both present | New one used | Planned | PLANNED |
| TC-KEY-050.3 | INV | Old file corrupt | KEY_LOSS | Planned | PLANNED |

#### LLR-PHOTO-010 — TGT

Camera photos of plots are deferred (HLR-CAM, DW-1101); satellite photos use LLR-SAT-010/030 instead. Target: decode at ≤ 1080 px.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PHOTO-010.1 | NOM | Typical 12 MP photo | Processed per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-010.2 | MAX | 50 MP photo / 1 MB limit exceeded at quality 60 | Handled per the LLR (E-IMG-002) | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-010.3 | INV | Corrupt image or out of memory | E-IMG-001, previous photo kept | MANUAL TP-22.1 | BLOCKED |

#### LLR-PHOTO-020 — TGT

Camera photos of plots are deferred (HLR-CAM, DW-1101); satellite photos use LLR-SAT-010/030 instead. Target: JPEG quality steps to ≤ 1 MB.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PHOTO-020.1 | NOM | Typical 12 MP photo | Processed per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-020.2 | MAX | 50 MP photo / 1 MB limit exceeded at quality 60 | Handled per the LLR (E-IMG-002) | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-020.3 | INV | Corrupt image or out of memory | E-IMG-001, previous photo kept | MANUAL TP-22.1 | BLOCKED |

#### LLR-PHOTO-030 — TGT

Camera photos of plots are deferred (HLR-CAM, DW-1101); satellite photos use LLR-SAT-010/030 instead. Target: encrypted temp-then-rename storage.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PHOTO-030.1 | NOM | Typical 12 MP photo | Processed per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-030.2 | MAX | 50 MP photo / 1 MB limit exceeded at quality 60 | Handled per the LLR (E-IMG-002) | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-030.3 | INV | Corrupt image or out of memory | E-IMG-001, previous photo kept | MANUAL TP-22.1 | BLOCKED |

#### LLR-PHOTO-040 — TGT

Camera photos of plots are deferred (HLR-CAM, DW-1101); satellite photos use LLR-SAT-010/030 instead. Target: decode errors keep the old photo.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PHOTO-040.1 | NOM | Typical 12 MP photo | Processed per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-040.2 | MAX | 50 MP photo / 1 MB limit exceeded at quality 60 | Handled per the LLR (E-IMG-002) | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-040.3 | INV | Corrupt image or out of memory | E-IMG-001, previous photo kept | MANUAL TP-22.1 | BLOCKED |

#### LLR-PHOTO-050 — TGT

Camera photos of plots are deferred (HLR-CAM, DW-1101); satellite photos use LLR-SAT-010/030 instead. Target: remove/delete photo files.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PHOTO-050.1 | NOM | Typical 12 MP photo | Processed per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-050.2 | MAX | 50 MP photo / 1 MB limit exceeded at quality 60 | Handled per the LLR (E-IMG-002) | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-050.3 | INV | Corrupt image or out of memory | E-IMG-001, previous photo kept | MANUAL TP-22.1 | BLOCKED |

#### LLR-PHOTO-060 — TGT

Camera photos of plots are deferred (HLR-CAM, DW-1101); satellite photos use LLR-SAT-010/030 instead. Target: orphan photo cleanup.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PHOTO-060.1 | NOM | Typical 12 MP photo | Processed per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-060.2 | MAX | 50 MP photo / 1 MB limit exceeded at quality 60 | Handled per the LLR (E-IMG-002) | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-060.3 | INV | Corrupt image or out of memory | E-IMG-001, previous photo kept | MANUAL TP-22.1 | BLOCKED |

#### LLR-PHOTO-070 — TGT

Camera photos of plots are deferred (HLR-CAM, DW-1101); satellite photos use LLR-SAT-010/030 instead. Target: decrypt for display.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PHOTO-070.1 | NOM | Typical 12 MP photo | Processed per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-070.2 | MAX | 50 MP photo / 1 MB limit exceeded at quality 60 | Handled per the LLR (E-IMG-002) | MANUAL TP-22.1 | BLOCKED |
| TC-PHOTO-070.3 | INV | Corrupt image or out of memory | E-IMG-001, previous photo kept | MANUAL TP-22.1 | BLOCKED |

#### LLR-AUDS-010 — DEV

`SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: encrypted audit files.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUDS-010.1 | NOM | Key loss, then open About | Entry present, chain intact | MANUAL TP-21.4 | MANUAL |
| TC-AUDS-010.2 | MAX | Log over 1 MB | Rotated per the LLR | Planned | PLANNED |
| TC-AUDS-010.3 | INV | Log file edited by hand | Chain reported broken | MANUAL TP-21.4 | MANUAL |

#### LLR-AUDS-020 — PART

`SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: append and flush.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUDS-020.1 | NOM | Key loss, then open About | Entry present, chain intact | MANUAL TP-21.4 | MANUAL |
| TC-AUDS-020.2 | MAX | Log over 1 MB | Rotated per the LLR | Planned | PLANNED |
| TC-AUDS-020.3 | INV | Log file edited by hand | Chain reported broken | MANUAL TP-21.4 | MANUAL |

#### LLR-AUDS-030 — DEV

`SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: rotation at 1 MB.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUDS-030.1 | NOM | Key loss, then open About | Entry present, chain intact | MANUAL TP-21.4 | MANUAL |
| TC-AUDS-030.2 | MAX | Log over 1 MB | Rotated per the LLR | Planned | DEV |
| TC-AUDS-030.3 | INV | Log file edited by hand | Chain reported broken | MANUAL TP-21.4 | MANUAL |

#### LLR-AUDS-040 — DEV

`SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: read and verify all files.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUDS-040.1 | NOM | Key loss, then open About | Entry present, chain intact | MANUAL TP-21.4 | MANUAL |
| TC-AUDS-040.2 | MAX | Log over 1 MB | Rotated per the LLR | Planned | PLANNED |
| TC-AUDS-040.3 | INV | Log file edited by hand | Chain reported broken | MANUAL TP-21.4 | MANUAL |

#### LLR-AUDS-050 — DEV

`SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: new chain after key loss.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUDS-050.1 | NOM | Key loss, then open About | Entry present, chain intact | MANUAL TP-21.4 | MANUAL |
| TC-AUDS-050.2 | MAX | Log over 1 MB | Rotated per the LLR | Planned | PLANNED |
| TC-AUDS-050.3 | INV | Log file edited by hand | Chain reported broken | MANUAL TP-21.4 | MANUAL |

#### LLR-AUDS-060 — PART

`SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: log outside the database.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUDS-060.1 | NOM | Key loss, then open About | Entry present, chain intact | MANUAL TP-21.4 | MANUAL |
| TC-AUDS-060.2 | MAX | Log over 1 MB | Rotated per the LLR | Planned | PLANNED |
| TC-AUDS-060.3 | INV | Log file edited by hand | Chain reported broken | MANUAL TP-21.4 | MANUAL |

#### LLR-SETST-010 — IMPL

`SettingsRepository.load` reads each key and sanitizes the result.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETST-010.1 | NOM | Saved settings after restart | Same values | AUTO `StorageViewModelTest.saveThenLoadConfiguration_returnsSavedValue` | PASS |
| TC-SETST-010.2 | INV | Damaged stored values | Defaults for those keys | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |
| TC-SETST-010.3 | BND | Missing keys (first start) | Defaults | AUTO `StorageViewModelTest.loadConfiguration_missingKey_resultsInNullState` | PASS |

#### LLR-SETST-020 — PART

Each change saves every key (not one transaction).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETST-020.1 | NOM | Change the spacing margin | Saved and used by the canvas | MANUAL TP-10.3 | MANUAL |
| TC-SETST-020.2 | BND | Change two settings quickly | Both kept | MANUAL TP-10.3 | MANUAL |
| TC-SETST-020.3 | INV | App killed during a save | Every key readable (defaults for damaged ones) | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-SETST-030 — IMPL

Reset writes the defaults only to the settings keys.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETST-030.1 | NOM | Reset all settings | Defaults | MANUAL TP-10.5 | MANUAL |
| TC-SETST-030.2 | BND | Reset with plots present | Plots untouched | MANUAL TP-10.5 | MANUAL |
| TC-SETST-030.3 | INV | Delete a config row | Default on next load | AUTO `StorageViewModelTest.deleteConfiguration_clearsState` | PASS |

#### LLR-CATI-010 — IMPL

`SeedCatalogLoader` inserts the active tier at start when rows are missing.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATI-010.1 | NOM | First start | 253 Basic varieties | MANUAL TP-10.1 | MANUAL |
| TC-CATI-010.2 | BND | Seed table with fewer rows than the tier | Missing rows inserted (IGNORE) | MANUAL TP-10.1 | MANUAL |
| TC-CATI-010.3 | INV | Seeding the dictionary on a device | Inserts succeed | DEVICE `AppDatabaseTest.seedDictionary_seedsInsertSuccessfully` | MANUAL |

#### LLR-CATI-020 — DEV

No hash check; bundled data is validated by unit tests in CI instead.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATI-020.1 | NOM | Bundled catalogs valid | Tests pass | AUTO `CatalogDataTest.everyLineIsWellFormedAndInRange` | PASS |
| TC-CATI-020.2 | INV | Asset hash mismatch at install | E-CAT-001 per LLR; not implemented | Planned | DEV |
| TC-CATI-020.3 | BND | Tier content identical across tiers | Holds | AUTO `CatalogDataTest.eachTierContainsTheSmallerOneWithIdenticalEntries` | PASS |

#### LLR-CATI-030 — PART

Tier switch replaces bundled rows; no audit entry or batching rule.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATI-030.1 | NOM | Switch to Standard | 603 bundled rows | MANUAL TP-10.6 | MANUAL |
| TC-CATI-030.2 | MAX | Switch Basic → Pro | 2,939 rows, completes | MANUAL TP-10.6 | MANUAL |
| TC-CATI-030.3 | INV | TIER_SWITCH audit entry | Not written | Planned | DEV |

#### LLR-CATI-040 — IMPL

Custom (`isCustom`) rows are never replaced.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATI-040.1 | NOM | Custom variety, then tier switch | Kept | MANUAL TP-10.6 | MANUAL |
| TC-CATI-040.2 | BND | Edited bundled variety (becomes custom) | Kept | MANUAL TP-10.6 | MANUAL |
| TC-CATI-040.3 | INV | Import a plan with a custom code equal to a bundled one | Existing kept | MANUAL TP-19.3 | MANUAL |

#### LLR-CATI-050 — DEV

No species/families files (see LLR-CATP-030).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATI-050.1 | NOM | Species names shown | From the catalog names | AUTO `SpanishDictionaryTest.namesListsBulletsAndDates` | PASS |
| TC-CATI-050.2 | BND | Family list for the editor | Free text | MANUAL TP-18.8 | MANUAL |
| TC-CATI-050.3 | INV | Files loaded at start | Not present | Planned | DEV |

#### LLR-CATI-060 — DEV

Climate data is the text table `zip_data.txt` plus `frost_stations.txt`, not `climate.db`.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CATI-060.1 | NOM | Table integrity | Sorted, well formed, 42,277 lines | AUTO `ZipDataTest.zipTable_isSortedWellFormedAndComplete` | PASS |
| TC-CATI-060.2 | BND | Published zones for sample ZIPs | Match | AUTO `ZipDataTest.zoneTable_givesPublishedZones` | PASS |
| TC-CATI-060.3 | INV | Malformed zone field | Rejected | AUTO `ZipDataTest.findZone_rejectsMalformedValues` | PASS |

#### LLR-REPO-010 — PART

Storage errors are caught at the call sites (snackbars); no uniform Result type.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REPO-010.1 | NOM | Normal save | No message | MANUAL TP-14.1 | MANUAL |
| TC-REPO-010.2 | INV | Disk full during a save | Message, app keeps running | MANUAL TP-21.5 | MANUAL |
| TC-REPO-010.3 | BND | Database locked by another operation | Retries or message | Planned | PLANNED |

#### LLR-REPO-020 — PART

Multi-row changes use `withTransaction` (plan, keep/replace, seasons, Plan B, duplicate, swap).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REPO-020.1 | NOM | Keep a plan of 50 plants | All or nothing | MANUAL TP-16.3 | MANUAL |
| TC-REPO-020.2 | BND | Keep a plan that replaces plants | Delete and insert in one transaction | MANUAL TP-16.4 | MANUAL |
| TC-REPO-020.3 | INV | App killed during the transaction | Old state intact | Planned | PLANNED |

#### LLR-REPO-030 — PART

Resizing checks plants outside on the web; Android resize not offered.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REPO-030.1 | NOM | Shrink a plot with no plants outside | Saved | MANUAL TP-12.3 | MANUAL |
| TC-REPO-030.2 | BND | Plant exactly on the new edge | Allowed | MANUAL TP-12.3 | MANUAL |
| TC-REPO-030.3 | INV | Plant outside the new size | Refused with the list | MANUAL TP-12.3 | MANUAL |

#### LLR-REPO-040 — PART

Validation runs before the insert on the UI side; a plant limit is shown as a snackbar.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REPO-040.1 | NOM | Place a valid plant | Stored | MANUAL TP-14.1 | MANUAL |
| TC-REPO-040.2 | MAX | Fill that would exceed the limit | Snackbar, nothing over the limit | MANUAL TP-15.5 | MANUAL |
| TC-REPO-040.3 | INV | Two quick taps at the same spot | Second refused | MANUAL TP-14.2 | MANUAL |

#### LLR-REPO-050 — IMPL

`restoreSnapshot` replaces plants, paths, features, outline and history in one transaction.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REPO-050.1 | NOM | Undo a move | Plant back | MANUAL TP-14.5 | MANUAL |
| TC-REPO-050.2 | BND | Undo the only plant | Empty plot | MANUAL TP-14.5 | MANUAL |
| TC-REPO-050.3 | RND | Undo storm (web model of the same rule) | Same plan after undo/redo | WEB `adversarial.mjs` «undo 40× then redo 40×» | PASS |

#### LLR-REPO-060 — DEV

Undo history is in memory only (lost when leaving the plot).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REPO-060.1 | NOM | Leave and reopen a plot | Undo empty (matches H-TBC-04 intent) | MANUAL TP-14.5 | MANUAL |
| TC-REPO-060.2 | BND | Depth 5 and 100 | Kept to the depth | AUTO `LlrGapTest.historyStack_keepsTheNewestStepsUpToItsLimit` | PASS |
| TC-REPO-060.3 | INV | Undo after process death | Not possible (not stored) | Planned | DEV |

#### LLR-REPO-070 — PART

Custom varieties can be deleted from the encyclopedia; use check to be confirmed.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REPO-070.1 | NOM | Delete an unused custom variety | Deleted | MANUAL TP-18.8 | MANUAL |
| TC-REPO-070.2 | INV | Delete a planted variety | Refused with the plots | MANUAL TP-18.8 | MANUAL |
| TC-REPO-070.3 | BND | Delete a bundled variety | Not offered | MANUAL TP-18.8 | MANUAL |

#### LLR-REPO-080 — IMPL

Editing a bundled variety stores it as custom.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-REPO-080.1 | NOM | Edit a bundled variety's spacing | Saved as custom | MANUAL TP-18.8 | MANUAL |
| TC-REPO-080.2 | BND | Tier switch afterwards | Edit kept | MANUAL TP-10.6 | MANUAL |
| TC-REPO-080.3 | INV | Spacing 0 or blank | Save refused | MANUAL TP-18.8 | MANUAL |

#### LLR-START-010 — PART

Start-up opens the database off the main thread and seeds the catalog; no capability step.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-START-010.1 | NOM | Cold start | Plot list shown | MANUAL TP-10.1 | MANUAL |
| TC-START-010.2 | BND | First start after install | Disclaimer, then the list | MANUAL TP-10.1 | MANUAL |
| TC-START-010.3 | INV | Database unreadable | Unreadable-data screen | MANUAL TP-21.4 | MANUAL |

#### LLR-START-020 — IMPL

Key loss logs KEY_LOSS and shows the unreadable screen.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-START-020.1 | NOM | Key removed | KEY_LOSS logged, screen shown | MANUAL TP-21.4 | MANUAL |
| TC-START-020.2 | INV | Database file damaged | Same screen | MANUAL TP-21.4 | MANUAL |
| TC-START-020.3 | BND | No write before the choice | Database file unchanged | MANUAL TP-21.4 | MANUAL |

#### LLR-START-030 — PART

"Start with empty data" sets the old data aside and logs DATA_RESET.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-START-030.1 | NOM | Choose Start with empty data | Empty list, old files renamed | MANUAL TP-21.4 | MANUAL |
| TC-START-030.2 | BND | Choose it twice on two starts | Both sets kept separately | MANUAL TP-21.4 | MANUAL |
| TC-START-030.3 | INV | "Delete unreadable data" in Settings | Not implemented | Planned | DEV |

#### LLR-BKP-010 — IMPL

`allowBackup=false`, extraction and backup rules set.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-BKP-010.1 | NOM | Inspect the merged manifest | allowBackup=false, rules referenced | REVIEW TP-11.5 | MANUAL |
| TC-BKP-010.2 | BND | adb backup / cloud backup | No app data | MANUAL TP-21.7 | MANUAL |
| TC-BKP-010.3 | INV | Device-to-device transfer | No app data | MANUAL TP-21.7 | MANUAL |

#### LLR-BKP-020 — PART

`NetworkGateway.getText` is the only network entry; allow-list tested; no build check for blocked SDKs.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-BKP-020.1 | NOM | Allowed HTTPS host | Accepted | AUTO `RoadmapFeaturesTest.onlineData_onlyAllowsListedHttpsHosts` | PASS |
| TC-BKP-020.2 | INV | http://, other hosts, user-info tricks | Refused | AUTO `RoadmapFeaturesTest.onlineData_onlyAllowsListedHttpsHosts` | PASS |
| TC-BKP-020.3 | BND | Online features off | No connection attempted | MANUAL TP-21.8 | MANUAL |
| TC-BKP-020.4 | ALN | Grep the source for URL/socket use outside the gateway | None | REVIEW TP-11.6 | MANUAL |

### 3.3 `:app` — navigation, state and layout mechanics

#### LLR-NAVG-010 — PART

Compose navigation with string routes: plot list, creator, canvas, encyclopedia, settings, insights; no camera or data-unreadable route (the latter is a start-up state).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-NAVG-010.1 | NOM | Visit every screen from the plot list | Each opens and returns | MANUAL TP-10.2 | MANUAL |
| TC-NAVG-010.2 | BND | Deep link to a deleted plot id | Back to the list, no crash | MANUAL TP-10.2 | MANUAL |
| TC-NAVG-010.3 | INV | Typed routes of the LLR | String routes used | Planned | DEV |

#### LLR-NAVG-020 — PART

Back pops the stack; predictive back enabled; no discard prompt for unfinished drawing.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-NAVG-020.1 | NOM | Back from the canvas | Plot list | MANUAL TP-10.2 | MANUAL |
| TC-NAVG-020.2 | BND | Back on the plot list | App closes | MANUAL TP-10.2 | MANUAL |
| TC-NAVG-020.3 | INV | Back while drawing a path | "Discard unfinished …?" per LLR; not implemented | Planned | DEV |

#### LLR-NAVG-030 — TGT

`hasUnfinishedWork` not implemented.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-NAVG-030.1 | NOM | Points being drawn | true | Planned | PLANNED |
| TC-NAVG-030.2 | BND | Dialog open with unedited text | false | Planned | PLANNED |
| TC-NAVG-030.3 | INV | Plant drag canceled | false afterwards | Planned | PLANNED |

#### LLR-STATE-010 — PART

Canvas state is `remember`ed; some state is lost on process death.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-STATE-010.1 | NOM | Rotate the device on the canvas | Tool, zoom and plot kept | MANUAL TP-13.6 | MANUAL |
| TC-STATE-010.2 | MAX | 200 points in progress then rotate | Points kept (target) | MANUAL TP-13.6 | MANUAL |
| TC-STATE-010.3 | INV | Process killed in the background | State restored per LLR; partly lost | Planned | DEV |

#### LLR-STATE-020 — PART

Plot data reloads from the database on re-creation; undo history is lost.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-STATE-020.1 | NOM | Rotate after edits | Same plants | MANUAL TP-13.6 | MANUAL |
| TC-STATE-020.2 | BND | Rotate right after Undo | Undone state kept | MANUAL TP-13.6 | MANUAL |
| TC-STATE-020.3 | INV | Undo history after re-creation | Kept per LLR; lost | Planned | DEV |

#### LLR-STATE-030 — PART

Opening a plot starts in the default mode with zoom 1.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-STATE-030.1 | NOM | Open a plot | Zoom 1, whole plot visible | MANUAL TP-13.1 | MANUAL |
| TC-STATE-030.2 | BND | Open a 1000 m plot | Fits the screen | MANUAL TP-13.1 | MANUAL |
| TC-STATE-030.3 | INV | Open after leaving with overlays on | Overlays off | MANUAL TP-13.1 | MANUAL |

#### LLR-XFORM-010 — IMPL

Both clients draw the plot at one scale for both axes (Android since DW-0901, canvas sized to the plot's aspect).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-XFORM-010.1 | NOM | Square plot on the web | Drawn square | WEB `smoke.mjs` «no horizontal scroll at phone width» | PASS |
| TC-XFORM-010.2 | BND | 10 × 1 m plot on Android, portrait and landscape | Circles stay round | MANUAL TP-13.2 | MANUAL |
| TC-XFORM-010.3 | RND | Random plot shapes on the web | Circles stay round | MANUAL TP-13.2 | MANUAL |

#### LLR-XFORM-020 — PART

Taps are converted to meters with the inverse of the drawing scale.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-XFORM-020.1 | NOM | Tap the plot center | Plant at the center | MANUAL TP-14.1 | MANUAL |
| TC-XFORM-020.2 | BND | Tap each corner | Plant at the corner (or refused by the edge rule) | MANUAL TP-14.1 | MANUAL |
| TC-XFORM-020.3 | RND | Random taps on the web (monkey run) | Every stored plant inside the plot | WEB `adversarial.mjs` «every plant is inside its plot after the monkey run» | PASS |

#### LLR-XFORM-030 — PART

Pan is limited to keep the plot on screen.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-XFORM-030.1 | NOM | Pan to the right | Moves | MANUAL TP-13.3 | MANUAL |
| TC-XFORM-030.2 | BND | Pan far beyond the edge | Stops with the plot on screen | MANUAL TP-13.3 | MANUAL |
| TC-XFORM-030.3 | INV | Pinch and pan at once | No jump | MANUAL TP-13.3 | MANUAL |

#### LLR-XFORM-040 — IMPL

Pinch zoom clamped to the zoom settings; + / − by the zoom step.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-XFORM-040.1 | NOM | Pinch to zoom in | Zooms about the fingers | MANUAL TP-13.3 | MANUAL |
| TC-XFORM-040.2 | BND | Pinch past zoom max and min | Stops at the limits | MANUAL TP-13.3 | MANUAL |
| TC-XFORM-040.3 | INV | Stored zoom min ≥ max | Defaults used | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-XFORM-050 — PART

A reset-view action exists; visibility rule not verified.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-XFORM-050.1 | NOM | Zoom in, press reset | Zoom 1, centered | MANUAL TP-13.3 | MANUAL |
| TC-XFORM-050.2 | BND | At zoom 1 and centered | Reset hidden | MANUAL TP-13.3 | MANUAL |
| TC-XFORM-050.3 | INV | Reset during a drag | Drag canceled first | MANUAL TP-13.3 | MANUAL |

#### LLR-XFORM-060 — PART

Plant hit test uses a fixed pixel tolerance converted to meters.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-XFORM-060.1 | NOM | Tap on a plant | Its details open | MANUAL TP-14.3 | MANUAL |
| TC-XFORM-060.2 | BND | Tap just outside the tolerance | Placement instead | MANUAL TP-14.3 | MANUAL |
| TC-XFORM-060.3 | INV | Tap between two touching plants | The nearer one | MANUAL TP-14.3 | MANUAL |

#### LLR-GEST-010 — PART

Two pointers pan/zoom; a one-finger drag in progress is canceled.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEST-010.1 | NOM | Two-finger pan | Pans, no plant moved | MANUAL TP-13.3 | MANUAL |
| TC-GEST-010.2 | BND | Second finger during a plant drag | Drag canceled, plant back | MANUAL TP-14.4 | MANUAL |
| TC-GEST-010.3 | INV | Three fingers | Treated as a transform | MANUAL TP-13.3 | MANUAL |

#### LLR-GEST-020 — IMPL

Tap: open the nearest plant within tolerance, else place, else ignore.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEST-020.1 | NOM | Tap empty ground | Plant placed | WEB `smoke.mjs` «planted one tomato» | PASS |
| TC-GEST-020.2 | BND | Tap on top of an existing plant | Refused by the spacing rule | WEB `smoke.mjs` «spacing rule blocks a second plant on top» | PASS |
| TC-GEST-020.3 | INV | Tap outside the plot | Ignored | MANUAL TP-14.1 | MANUAL |

#### LLR-GEST-030 — IMPL

Move tool drags a plant; a refused move puts it back with the message.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEST-030.1 | NOM | Drag a plant to free ground | Moved | WEB `smoke.mjs` «tree moved» | PASS |
| TC-GEST-030.2 | BND | Drag onto another plant | Refused, plant back | MANUAL TP-14.4 | MANUAL |
| TC-GEST-030.3 | INV | Drag off the plot | Refused, plant back | MANUAL TP-14.4 | MANUAL |

#### LLR-GEST-040 — PART

Rectangle paths are drawn by drag and clamped.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEST-040.1 | NOM | Drag a path rectangle | Path added | MANUAL TP-15.1 | MANUAL |
| TC-GEST-040.2 | MIN | Rectangle 4 cm wide | Not added | MANUAL TP-15.1 | MANUAL |
| TC-GEST-040.3 | INV | Drag starting outside the plot | Clipped to the plot | MANUAL TP-15.1 | MANUAL |

#### LLR-GEST-050 — IMPL

Taps append points for lines, areas and outlines; outside taps ignored.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEST-050.1 | NOM | Tap 3 points and Finish | Line with 3 points | WEB `smoke.mjs` «drip line added with its 3 points» | PASS |
| TC-GEST-050.2 | BND | Two taps within 5 cm | Second dropped | MANUAL TP-15.1 | MANUAL |
| TC-GEST-050.3 | INV | Tap outside the plot | Ignored | MANUAL TP-15.1 | MANUAL |

#### LLR-GEST-060 — PART

Tapping a path or feature opens its dialog.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEST-060.1 | NOM | Tap a path | Edit dialog | MANUAL TP-15.3 | MANUAL |
| TC-GEST-060.2 | BND | Tap overlapping paths | Topmost chosen | MANUAL TP-15.3 | MANUAL |
| TC-GEST-060.3 | INV | Tap with points in progress | Adds a point instead | MANUAL TP-15.3 | MANUAL |

#### LLR-GEST-070 — IMPL

Area rectangles by drag for planning and filling.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEST-070.1 | NOM | Drag an area and plan | Plan dialog | WEB `smoke.mjs` «plan dialog suggests» | PASS |
| TC-GEST-070.2 | MIN | Area smaller than 5 cm | Ignored | MANUAL TP-16.1 | MANUAL |
| TC-GEST-070.3 | INV | Drag off the plot | Clipped | MANUAL TP-16.1 | MANUAL |

#### LLR-GEST-080 — TGT

No system-gesture exclusion.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GEST-080.1 | NOM | Draw near the left edge | Back not triggered | Planned | PLANNED |
| TC-GEST-080.2 | BND | Exclusion larger than the platform limit | Limited | Planned | PLANNED |
| TC-GEST-080.3 | INV | Tool not active | No exclusion | Planned | PLANNED |

#### LLR-DRAW-010 — PART

Drawing order: paper, photo, grid, sun bands, features, paths, circles, plants, labels, compass.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DRAW-010.1 | NOM | Plot with photo, paths and plants | Order as listed | MANUAL TP-13.4 | MANUAL |
| TC-DRAW-010.2 | BND | Photo covering the whole plot | Grid and plants above it | WEB `smoke.mjs` «photo drawn under the plot» | PASS |
| TC-DRAW-010.3 | INV | Weed-risk overlay | Not implemented | Planned | DEV |

#### LLR-DRAW-020 — PART

Markers with a variety color and spacing circles; sizes differ from the LLR.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DRAW-020.1 | NOM | Plants on the layout | Circle at the spacing radius, center dot | MANUAL TP-13.4 | MANUAL |
| TC-DRAW-020.2 | BND | Tiny radius (2 cm) | Marker still visible | MANUAL TP-13.4 | MANUAL |
| TC-DRAW-020.3 | INV | Variety missing from the catalog | Drawn with a default color | MANUAL TP-13.4 | MANUAL |

#### LLR-DRAW-030 — PART

Overdue ring drawn; conflict symbols shown in insights instead.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DRAW-030.1 | NOM | Overdue plant | Ring | MANUAL TP-14.6 | MANUAL |
| TC-DRAW-030.2 | BND | Germinated plant | No ring | MANUAL TP-14.6 | MANUAL |
| TC-DRAW-030.3 | INV | Conflict triangle on the marker | Not drawn | Planned | DEV |

#### LLR-DRAW-040 — TGT

No weed-risk overlay.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DRAW-040.1 | NOM | Overlay on | Uncovered ground tinted | Planned | PLANNED |
| TC-DRAW-040.2 | BND | No plants | Whole plot tinted | Planned | PLANNED |
| TC-DRAW-040.3 | INV | Scale change | Cache recomputed | Planned | PLANNED |

#### LLR-DRAW-050 — DEV

Ruler ticks every `rulerTickIntervalM` (setting), numbers only with the unit once (LLR-RULER-010); no 1-2-5 rounding by zoom.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DRAW-050.1 | NOM | Ruler on a 10 m plot | Numbers only, unit once | WEB `smoke.mjs` «ruler ticks show numbers only, unit once» | PASS |
| TC-DRAW-050.2 | BND | Tick 0.25 m on a 1000 m plot | Readable (not overlapping) | MANUAL TP-13.5 | MANUAL |
| TC-DRAW-050.3 | INV | Stored tick 0 | Default 1 m | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-DRAW-060 — IMPL

Grid lines at the tick positions inside the plot.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DRAW-060.1 | NOM | 10 × 5 m plot | Grid every meter | MANUAL TP-13.5 | MANUAL |
| TC-DRAW-060.2 | BND | Plot smaller than one tick | No inner grid lines | MANUAL TP-13.5 | MANUAL |
| TC-DRAW-060.3 | INV | L-shaped outline | Grid still on the rectangle; outside shaded | MANUAL TP-13.5 | MANUAL |

#### LLR-DRAW-070 — PART

Paints are remembered; text layout per frame not measured.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DRAW-070.1 | NOM | Review drawing code | No per-plant allocations in the draw loop | REVIEW TP-11.7 | MANUAL |
| TC-DRAW-070.2 | MAX | 500 plants, pan | Smooth (see LLR-PERF-020) | MANUAL TP-23.2 | MANUAL |
| TC-DRAW-070.3 | INV | Labels on/off during pan | No stutter | MANUAL TP-23.2 | MANUAL |

#### LLR-DRAW-080 — PART

Layout colors come from `LayoutPalette` (same in light and dark), plant colors from the variety.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DRAW-080.1 | NOM | Palette values | Match the specification | AUTO `LayoutPaletteTest.hexAndAlpha` | PASS |
| TC-DRAW-080.2 | BND | Dark theme | Layout colors unchanged | WEB `smoke.mjs` «shade legend shown» | PASS |
| TC-DRAW-080.3 | INV | Color-blind reading of sun bands | Bands differ in lightness | AUTO `LayoutPaletteTest.bandsDifferInLightness_soTheyReadWithoutColourVision` | PASS |

### 3.4 `:app` — screens

#### LLR-LIST-010 — PART

Plot list shows name, size and plant count; archived plots not supported.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LIST-010.1 | NOM | Three plots | Three rows with sizes | MANUAL TP-12.1 | MANUAL |
| TC-LIST-010.2 | BND | Plot 0.5 × 0.5 m and 1000 × 1000 m | Sizes shown correctly | MANUAL TP-12.1 | MANUAL |
| TC-LIST-010.3 | INV | "Show archived" switch | Not implemented | Planned | DEV |

#### LLR-LIST-020 — IMPL

Empty state with a create button.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LIST-020.1 | NOM | No plots | Empty-state text and button | MANUAL TP-12.1 | MANUAL |
| TC-LIST-020.2 | BND | Delete the last plot | Empty state appears | MANUAL TP-12.1 | MANUAL |
| TC-LIST-020.3 | INV | Plot list query fails | Message, not a crash | MANUAL TP-21.5 | MANUAL |

#### LLR-LIST-030 — PART

Delete with confirmation; rename/resize/archive partly available.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LIST-030.1 | NOM | Delete a plot | Confirmation, then gone | MANUAL TP-12.5 | MANUAL |
| TC-LIST-030.2 | BND | Delete a plot with 500 plants | Counts stated | MANUAL TP-12.5 | MANUAL |
| TC-LIST-030.3 | INV | Cancel the confirmation | Nothing deleted | MANUAL TP-12.5 | MANUAL |

#### LLR-EDIT-010 — IMPL

Name and sizes validated; Create enabled only when valid (web and Android).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-EDIT-010.1 | NOM | Name "Bed", 8 × 5 m | Accepted | WEB `adversarial.mjs` «minimum size 0.5 m» | PASS |
| TC-EDIT-010.2 | BND | 0.49 m and 1000.01 m; 0.5 m and 1000 m | Refused; accepted | WEB `adversarial.mjs` «length 1000.01 m» | PASS |
| TC-EDIT-010.3 | INV | Empty or blank name, "abc", NaN, Infinity, −5 | Refused with a reason | WEB `adversarial.mjs` «length NaN» | PASS |
| TC-EDIT-010.4 | ALN | Name with HTML | Shown as text, never run | WEB `adversarial.mjs` «HTML in a plot name is shown as text» | PASS |

#### LLR-EDIT-020 — TGT

No unusual-size confirmation.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-EDIT-020.1 | NOM | Plot 0.2 m | Confirmation dialog | Planned | PLANNED |
| TC-EDIT-020.2 | BND | Exactly 0.30 m and 200 m | No dialog | Planned | PLANNED |
| TC-EDIT-020.3 | INV | Cancel in the dialog | Form unchanged | Planned | PLANNED |

#### LLR-EDIT-030 — IMPL

Saving a new plot opens its layout.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-EDIT-030.1 | NOM | Create a plot | Layout opens | WEB `smoke.mjs` «plot created and autosaved» | PASS |
| TC-EDIT-030.2 | BND | Create two plots with the same name | Both kept | MANUAL TP-12.2 | MANUAL |
| TC-EDIT-030.3 | INV | Back after creating | Plot list, not the creator | MANUAL TP-12.2 | MANUAL |

#### LLR-EDIT-040 — PART

Web plot details edit name and size; Android edits name, direction, soil.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-EDIT-040.1 | NOM | Rename a plot | Saved | MANUAL TP-12.3 | MANUAL |
| TC-EDIT-040.2 | BND | Shrink so a plant is exactly on the edge | Allowed | MANUAL TP-12.3 | MANUAL |
| TC-EDIT-040.3 | INV | Shrink so plants fall outside | Refused with the list | MANUAL TP-12.3 | MANUAL |

#### LLR-EDIT-050 — PART

ZIP fills zone and coordinates; no "Use my location".

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-EDIT-050.1 | NOM | ZIP 48104 | Zone and latitude filled | WEB `smoke.mjs` «ZIP 48104 fills zone» | PASS |
| TC-EDIT-050.2 | ALN | ZIP "abcde", "1234" | Refused | WEB `adversarial.mjs` «ZIP abcde» | PASS |
| TC-EDIT-050.3 | BND | ZIP not in the table (00000) | No zone, no crash | AUTO `InputRobustnessTest.zip_alphanumericAndOddInputsFindNothing` | PASS |
| TC-EDIT-050.4 | INV | "Use my location" | Not implemented (LOCS deferred) | Planned | DEV |

#### LLR-LAYV-010 — PART

Top bar with the plot name (not translated) and an overflow menu; header shows size and plant count.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-010.1 | NOM | Open a plot | Name, size and count shown | MANUAL TP-13.1 | MANUAL |
| TC-LAYV-010.2 | ALN | Plot named "Tomato" in Spanish | Name stays as typed | AUTO `SpanishDictionaryTest.quotedNamesStayAsTyped` | PASS |
| TC-LAYV-010.3 | MAX | 200-character name | Ellipsized, no layout break | MANUAL TP-13.1 | MANUAL |

#### LLR-LAYV-020 — PART

Mode chips (plant, move, paths, site, outline …) plus Undo/Redo.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-020.1 | NOM | Switch between tools | One active at a time | MANUAL TP-13.1 | MANUAL |
| TC-LAYV-020.2 | BND | Undo with an empty history | Disabled | MANUAL TP-14.5 | MANUAL |
| TC-LAYV-020.3 | INV | Switch tools mid-drag | Drag canceled | MANUAL TP-14.4 | MANUAL |

#### LLR-LAYV-030 — IMPL

Undo/Redo restore a snapshot in one transaction.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-030.1 | NOM | Undo a placement | Plant removed | WEB `smoke.mjs` «undo removes the tree» | PASS |
| TC-LAYV-030.2 | BND | Redo after undo | Plant back | WEB `smoke.mjs` «redo brings it back» | PASS |
| TC-LAYV-030.3 | RND | 40 undos then 40 redos | Same plan | WEB `adversarial.mjs` «undo 40× then redo 40×» | PASS |

#### LLR-LAYV-040 — IMPL

Every change pushes one snapshot (plants, paths, features, outline, history).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-040.1 | NOM | Move a tree, undo | Tree back | WEB `smoke.mjs` «undo puts the tree back» | PASS |
| TC-LAYV-040.2 | BND | Keep a whole plan | One undo step | WEB `smoke.mjs` «one undo removes the whole plan» | PASS |
| TC-LAYV-040.3 | INV | Change the outline, undo | Outline restored | WEB `smoke.mjs` «undo restores the outline» | PASS |

#### LLR-LAYV-050 — PART

Refusals show a status/snackbar explaining the rule.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-050.1 | NOM | Place on top of a plant | Message names the rule | WEB `smoke.mjs` «status explains the block» | PASS |
| TC-LAYV-050.2 | BND | Place exactly at the allowed distance | Accepted | AUTO `CompanionPlantingValidatorTest.spacingBoundary_touchingCirclesAreAllowed` | PASS |
| TC-LAYV-050.3 | INV | Place outside the outline | "Outside the plot" message | MANUAL TP-14.2 | MANUAL |

#### LLR-LAYV-060 — PART

Conflicts are listed in insights (Harmony) rather than flagged on the canvas.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-060.1 | NOM | Antagonists close together with rules off | Listed in Harmony | AUTO `RoadmapFeaturesTest.harmony_reportsCloseAntagonistsAndScoresLower` | PASS |
| TC-LAYV-060.2 | BND | Antagonists at exactly 2 × spacing | Not listed | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | PASS |
| TC-LAYV-060.3 | INV | Canvas conflict flags | Not drawn | Planned | DEV |

#### LLR-LAYV-070 — PART

Overdue state computed on load and after changes; date-change listener not implemented.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-070.1 | NOM | Plant past its germination window | Ring shown | MANUAL TP-14.6 | MANUAL |
| TC-LAYV-070.2 | BND | Exactly at the window | No ring | AUTO `LlrGapTest.germination_isOverdueOnlyAfterTheWholeWindow` | PASS |
| TC-LAYV-070.3 | INV | Midnight passes while open | Ring appears (target); needs reopen today | Planned | DEV |

#### LLR-LAYV-080 — PART

Placing without a variety opens the picker.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-080.1 | NOM | First tap with no variety chosen | Picker opens | MANUAL TP-14.1 | MANUAL |
| TC-LAYV-080.2 | BND | Pick and place again | Same variety used | MANUAL TP-14.1 | MANUAL |
| TC-LAYV-080.3 | INV | Close the picker without choosing | Nothing placed | MANUAL TP-14.1 | MANUAL |

#### LLR-LAYV-090 — PART

Units switched in Settings (not a header chip).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-090.1 | NOM | Switch to inches | Ruler and dialogs in inches | MANUAL TP-10.3 | MANUAL |
| TC-LAYV-090.2 | BND | Switch back to meters | Values unchanged | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |
| TC-LAYV-090.3 | INV | Stored unit "FEET" | Default meters | MANUAL TP-10.3 | MANUAL |

#### LLR-LAYV-100 — DEV

Overlays: sun/shade and water coverage; no weed-risk or route overlay, no water-source marker.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-100.1 | NOM | Water map on | Covered cells tinted | WEB `smoke.mjs` «water map shows the sprinkler» | PASS |
| TC-LAYV-100.2 | BND | Plants out of reach | Circled red | WEB `smoke.mjs` «plants out of reach are circled» | PASS |
| TC-LAYV-100.3 | INV | Weed-risk switch | Not implemented | Planned | DEV |

#### LLR-LAYV-110 — IMPL

Legend lists planted varieties with counts and kinds.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-110.1 | NOM | Plot with corn | Legend lists corn with its count | WEB `smoke.mjs` «legend lists what is planted» | PASS |
| TC-LAYV-110.2 | BND | Empty plot | Empty legend | MANUAL TP-13.4 | MANUAL |
| TC-LAYV-110.3 | MAX | 40 varieties | Scrollable legend | MANUAL TP-13.4 | MANUAL |
| TC-LAYV-110.4 | INV | Plant whose variety is missing from the catalog | Listed by its code, no crash | MANUAL TP-13.4 | MANUAL |

#### LLR-LAYV-120 — PART

Zone and frost dates shown in the plot tab; prompt when missing.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-120.1 | NOM | Plot with ZIP | Growing season shown | WEB `smoke.mjs` «growing season and frost dates shown» | PASS |
| TC-LAYV-120.2 | BND | Frost-free location | 365-day season text | AUTO `EditingAndSitePropertyTest.growingSeason_windowsForEveryCatalogCropAndAnySeason` | PASS |
| TC-LAYV-120.3 | INV | No location | "Set a location" hint | MANUAL TP-18.1 | MANUAL |

#### LLR-LAYV-130 — DEV

Perennials outside the zone are blocked (`blocksPlacement`) rather than warned after placement.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LAYV-130.1 | NOM | Tender perennial outside its zone | Blocked with the zone message | AUTO `RoadmapFeaturesTest.hardiness_blocksOnlyPerennialsOutsideZone` | PASS |
| TC-LAYV-130.2 | BND | Zone at the edge of the range | Allowed | AUTO `RoadmapFeaturesTest.hardiness_blocksOnlyPerennialsOutsideZone` | PASS |
| TC-LAYV-130.3 | INV | Warn-but-allow per LLR | Blocked instead; decision needed | Planned | DEV |

#### LLR-DET-010 — PART

Details show name, kind, dates, spacing and germination state; web edit card adds position.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DET-010.1 | NOM | Tap a plant | Name, planting and harvest dates | MANUAL TP-14.3 | MANUAL |
| TC-DET-010.2 | BND | Planted today | Expected-by date = today + germination days | MANUAL TP-14.3 | MANUAL |
| TC-DET-010.3 | INV | Plant whose variety was deleted | Details without a crash | MANUAL TP-14.3 | MANUAL |

#### LLR-DET-020 — PART

Web edit card has a date field; Android date is set at placement.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DET-020.1 | NOM | Change the date on the web | Saved | WEB `smoke.mjs` «plant variety changed» | PASS |
| TC-DET-020.2 | BND | Date at the limits | See LLR-SCHED-050 | AUTO `InputRobustnessTest.isoDates_edgeCases` | PASS |
| TC-DET-020.3 | INV | Android date picker | Not implemented | Planned | DEV |

#### LLR-DET-030 — IMPL

Change variety validates against the other plants.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DET-030.1 | NOM | Change a plant's variety | Saved | WEB `smoke.mjs` «plant variety changed» | PASS |
| TC-DET-030.2 | BND | Undo the change | Old variety back | WEB `smoke.mjs` «undo reverts the variety change» | PASS |
| TC-DET-030.3 | INV | New variety too wide for the spot | Refused, old variety kept | MANUAL TP-14.3 | MANUAL |

#### LLR-DET-040 — PART

Germination dialog offers germinated/failed choices.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DET-040.1 | NOM | Overdue plant: "It germinated" | Ring gone | MANUAL TP-14.6 | MANUAL |
| TC-DET-040.2 | BND | Not overdue | No germination buttons | MANUAL TP-14.6 | MANUAL |
| TC-DET-040.3 | INV | Close without choosing | Nothing changed | MANUAL TP-14.6 | MANUAL |

#### LLR-DET-050 — PART

Plan B options with days to harvest and timing (LLR-PLANB-060).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DET-050.1 | NOM | Plan B for a lost plant | Options listed | WEB `smoke.mjs` «Plan B suggests replacements» | PASS |
| TC-DET-050.2 | BND | Days and timing shown | Each option has both | WEB `smoke.mjs` «Plan B shows days to harvest and timing» | PASS |
| TC-DET-050.3 | INV | No suitable option | Explains why | AUTO `AdviceRobustnessTest.pestRisksAndPlanBForEveryPestAndCrop` | PASS |

#### LLR-DET-060 — IMPL

Delete a plant (one undo step).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DET-060.1 | NOM | Delete a plant | Gone | MANUAL TP-14.3 | MANUAL |
| TC-DET-060.2 | BND | Delete the last plant | Empty plot | MANUAL TP-14.3 | MANUAL |
| TC-DET-060.3 | INV | Undo the delete | Plant back with its id | MANUAL TP-14.5 | MANUAL |

#### LLR-PICK-010 — PART

Picker groups by category and species; the web uses search with kinds.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PICK-010.1 | NOM | Search "onion" | Spring onions listed | WEB `smoke.mjs` «three spring onions in the catalog» | PASS |
| TC-PICK-010.2 | BND | Variety kind shown | Kind in the list | WEB `smoke.mjs` «variety kind shown in the list» | PASS |
| TC-PICK-010.3 | INV | Category with no varieties in the tier | Hidden | MANUAL TP-14.1 | MANUAL |

#### LLR-PICK-020 — IMPL

Case-insensitive search; Back clears it.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PICK-020.1 | NOM | "TOMATO" and "tomato" | Same results | MANUAL TP-14.1 | MANUAL |
| TC-PICK-020.2 | ALN | "%", "(", emoji, 200 characters | No crash, no results | MANUAL TP-14.1 | MANUAL |
| TC-PICK-020.3 | BND | Empty search | All items | MANUAL TP-14.1 | MANUAL |

#### LLR-PICK-030 — PART

Recommendations shown in insights; picker greys out antagonists.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PICK-030.1 | NOM | Plot with a zone | Recommended crops fit the zone | AUTO `RoadmapFeaturesTest.recommend_excludesAntagonistsAndPrefersCompanions` | PASS |
| TC-PICK-030.2 | BND | Antagonist already on the plot | Greyed out with the reason | AUTO `RoadmapFeaturesTest.pickerConflict_flagsAntagonistOnPlotAndTenderPerennial` | PASS |
| TC-PICK-030.3 | INV | No location | No recommendation filter | MANUAL TP-14.1 | MANUAL |

#### LLR-FILLV-010 — PART

Fill sheet with variety, pattern and preview count.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILLV-010.1 | NOM | Select an area and a variety | Preview count | MANUAL TP-15.5 | MANUAL |
| TC-FILLV-010.2 | MAX | Huge area, tiny spacing | Capped, message (no freeze) | AUTO `AdviceRobustnessTest.areaFillerRejectsBadInputs_andCapsHugeGrids` | PASS |
| TC-FILLV-010.3 | INV | Switch pattern quickly many times | Last choice shown | MANUAL TP-15.5 | MANUAL |

#### LLR-FILLV-020 — PART

Fill stores the positions; limit snackbar when too many.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILLV-020.1 | NOM | Fill | Plants placed, count shown | MANUAL TP-15.5 | MANUAL |
| TC-FILLV-020.2 | MAX | More than the plant limit | Snackbar, nothing over the limit | MANUAL TP-15.5 | MANUAL |
| TC-FILLV-020.3 | INV | Undo after Fill | All filled plants removed | MANUAL TP-15.5 | MANUAL |

#### LLR-FILLV-030 — DEV

Tier gating differs: features follow `FeatureFlags` minimum tiers.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILLV-030.1 | NOM | Basic tier | Gated items shown disabled with the tier | MANUAL TP-10.6 | MANUAL |
| TC-FILLV-030.2 | BND | Standard tier | Standard items enabled | MANUAL TP-10.6 | MANUAL |
| TC-FILLV-030.3 | INV | Stored tier "GOLD" | Basic | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-FILLV-040 — PART

Line path width dialog validates the width.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILLV-040.1 | NOM | Width 0.5 m | Saved | MANUAL TP-15.1 | MANUAL |
| TC-FILLV-040.2 | BND | Width 0; width larger than the plot | Refused | MANUAL TP-15.1 | MANUAL |
| TC-FILLV-040.3 | ALN | "abc", "1,5", "NaN" | Refused; 1.5 accepted | AUTO `LlrGapTest.units_convertAndParseAtTheLimits` | PASS |

#### LLR-FILLV-050 — TGT

No overlap warning before storing a path.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILLV-050.1 | NOM | Path over a plant | Lists the plant, asks Keep/Cancel | Planned | PLANNED |
| TC-FILLV-050.2 | BND | Path touching a circle | No warning | Planned | PLANNED |
| TC-FILLV-050.3 | INV | Cancel | Path not stored | Planned | PLANNED |

#### LLR-ENCV-010 — IMPL

Encyclopedia list filtered by category and text.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ENCV-010.1 | NOM | Filter "Herb" | Only herbs | MANUAL TP-18.8 | MANUAL |
| TC-ENCV-010.2 | MAX | Pro tier (2,939 items), fast scroll | Smooth | MANUAL TP-18.8 | MANUAL |
| TC-ENCV-010.3 | ALN | Search "ñ", "'", "%" | No crash | MANUAL TP-18.8 | MANUAL |

#### LLR-ENCV-020 — PART

Details show every catalog field; companion codes resolved to names.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ENCV-020.1 | NOM | Open tomato | All fields, companions by name | MANUAL TP-18.8 | MANUAL |
| TC-ENCV-020.2 | BND | Empty field | "Not on file" | MANUAL TP-18.8 | MANUAL |
| TC-ENCV-020.3 | INV | Code missing from the tier | Raw code shown (DW-0809) | MANUAL TP-18.8 | MANUAL |

#### LLR-ENCV-030 — PART

Variety editor checks name, spacing and days; family free text.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ENCV-030.1 | NOM | Valid custom variety | Saved | MANUAL TP-18.8 | MANUAL |
| TC-ENCV-030.2 | BND | Spacing 0.02 and 10 m | Accepted | MANUAL TP-18.8 | MANUAL |
| TC-ENCV-030.3 | INV | Duplicate code, blank name, spacing 0 | Refused | MANUAL TP-18.8 | MANUAL |

#### LLR-ENCV-040 — PART

Delete offered for custom varieties.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ENCV-040.1 | NOM | Delete an unused custom variety | Deleted | MANUAL TP-18.8 | MANUAL |
| TC-ENCV-040.2 | INV | Delete a planted one | Refused with plots | MANUAL TP-18.8 | MANUAL |
| TC-ENCV-040.3 | BND | Bundled variety | No Delete | MANUAL TP-18.8 | MANUAL |

#### LLR-SETV-010 — PART

Settings grouped by topic; sensor items shown with a note (not hidden).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETV-010.1 | NOM | Open Settings | Groups in order | MANUAL TP-10.3 | MANUAL |
| TC-SETV-010.2 | BND | Device without a light sensor | Light item hidden per LLR; shown with a note | MANUAL TP-10.3 | DEV |
| TC-SETV-010.3 | INV | Language set to an unknown code | English | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-SETV-020 — IMPL

Sliders with ranges and steps.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETV-020.1 | NOM | Move each slider | Value shown and saved | MANUAL TP-10.3 | MANUAL |
| TC-SETV-020.2 | BND | Each slider at both ends | Limits kept after restart | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |
| TC-SETV-020.3 | INV | Damaged stored value | Slider shows the default | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | PASS |

#### LLR-SETV-030 — IMPL

Companion enforcement locked on below Pro.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETV-030.1 | NOM | Pro tier | Switch enabled | MANUAL TP-10.4 | MANUAL |
| TC-SETV-030.2 | BND | Standard tier | On and disabled with the note | MANUAL TP-10.4 | MANUAL |
| TC-SETV-030.3 | INV | Downgrade from Pro with it off | Back on | MANUAL TP-10.4 | MANUAL |

#### LLR-SETV-040 — PART

Tier switch shows progress and a result.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETV-040.1 | NOM | Switch to Pro | Progress, then result | MANUAL TP-10.6 | MANUAL |
| TC-SETV-040.2 | BND | Switch to the current tier | No change | MANUAL TP-10.6 | MANUAL |
| TC-SETV-040.3 | INV | Rotate during the switch | Completes once | MANUAL TP-10.6 | MANUAL |

#### LLR-SETV-050 — IMPL

Reset asks for confirmation.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SETV-050.1 | NOM | Reset and confirm | Defaults | MANUAL TP-10.5 | MANUAL |
| TC-SETV-050.2 | INV | Reset and cancel | Unchanged | MANUAL TP-10.5 | MANUAL |
| TC-SETV-050.3 | BND | Reset with defaults already | No change | MANUAL TP-10.5 | MANUAL |

#### LLR-CAMV-010 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: camera preview and in-memory capture.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CAMV-010.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-010.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-010.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-CAMV-020 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: permission request and rationale.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CAMV-020.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-020.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-020.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-CAMV-030 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: gallery picker to the photo pipeline.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CAMV-030.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-030.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-030.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-CAMV-040 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: storage floor banner.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CAMV-040.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-040.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-040.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-CAMV-050 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: battery warning.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CAMV-050.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-050.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-050.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-CAMV-060 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: low-light banner.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CAMV-060.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-060.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-CAMV-060.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-SENS-010 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: light sensor averaging.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SENS-010.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-SENS-010.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-SENS-010.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-SENS-020 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: storage ratio.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SENS-020.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-SENS-020.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-SENS-020.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-SENS-030 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: battery level.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SENS-030.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-SENS-030.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-SENS-030.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-LOCS-010 — TGT

Camera, sensor and location screens are deferred (DW-1101/1104/1105). Target: use my location.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LOCS-010.1 | NOM | Normal conditions | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-LOCS-010.2 | BND | Threshold exactly reached | Per the LLR | MANUAL TP-22.1 | BLOCKED |
| TC-LOCS-010.3 | INV | Permission denied / sensor missing / timeout | Error code of the LLR, no crash | MANUAL TP-22.1 | BLOCKED |

#### LLR-CAPS-010 — PART

Capabilities are not probed at start; network and permission checks happen when used.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CAPS-010.1 | NOM | Device with camera and GPS | Recorded | MANUAL TP-22.1 | BLOCKED |
| TC-CAPS-010.2 | BND | Permission revoked while paused | Re-checked on resume | MANUAL TP-22.1 | BLOCKED |
| TC-CAPS-010.3 | INV | Device without a camera | Camera entry disabled | MANUAL TP-22.1 | BLOCKED |

#### LLR-CAPS-020 — PART

Online features disabled with a reason when off.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CAPS-020.1 | NOM | Online off | Online buttons explain they're off | MANUAL TP-21.8 | MANUAL |
| TC-CAPS-020.2 | BND | Online on, no network | Error message | MANUAL TP-21.8 | MANUAL |
| TC-CAPS-020.3 | INV | Missing capability | Never a crash | MANUAL TP-21.8 | MANUAL |

#### LLR-A11Y-010 — PART

Material components give 48 dp targets; some icons lack descriptions.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-A11Y-010.1 | NOM | Accessibility Scanner on each screen | No touch-target or label issues | MANUAL TP-13.7 | MANUAL |
| TC-A11Y-010.2 | BND | Smallest icon buttons | 48 × 48 dp | MANUAL TP-13.7 | MANUAL |
| TC-A11Y-010.3 | INV | TalkBack on the canvas toolbar | Every button named | MANUAL TP-13.7 | MANUAL |

#### LLR-A11Y-020 — TGT

No semantic nodes per plant on the canvas.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-A11Y-020.1 | NOM | TalkBack on a plant | "Tomato, planted …" | Planned | PLANNED |
| TC-A11Y-020.2 | MAX | 500 plants | All reachable | Planned | PLANNED |
| TC-A11Y-020.3 | INV | Plant with an unknown variety | Labeled with its code | Planned | PLANNED |

#### LLR-DISP-010 — PART

Edge-to-edge enabled; no locked orientation.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DISP-010.1 | NOM | Phone in portrait and landscape | No controls under system bars | MANUAL TP-13.6 | MANUAL |
| TC-DISP-010.2 | BND | Display cutout / gesture navigation | Content inset | MANUAL TP-13.6 | MANUAL |
| TC-DISP-010.3 | INV | Web page at phone width | No horizontal scroll | WEB `smoke.mjs` «no horizontal scroll at phone width» | PASS |

#### LLR-DISP-020 — PART

Text in sp; forms scroll.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DISP-020.1 | NOM | Font scale 1.0 | Normal layout | MANUAL TP-13.7 | MANUAL |
| TC-DISP-020.2 | MAX | Font scale 2.0 | Every control usable | MANUAL TP-13.7 | MANUAL |
| TC-DISP-020.3 | ALN | Spanish (longer texts) at scale 1.3 | Nothing cut off | MANUAL TP-20.2 | MANUAL |

#### LLR-DISP-030 — PART

Rotation keeps the canvas state (see LLR-STATE-010).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DISP-030.1 | NOM | Rotate on the canvas | Zoom and center kept | MANUAL TP-13.6 | MANUAL |
| TC-DISP-030.2 | BND | Rotate twice quickly | Same state | MANUAL TP-13.6 | MANUAL |
| TC-DISP-030.3 | INV | Rotate with a dialog open | Dialog and text kept | MANUAL TP-13.6 | MANUAL |

#### LLR-ERRV-010 — PART

Errors shown as snackbars/dialogs with text; no error codes.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ERRV-010.1 | NOM | Refused placement | Snackbar with the reason | MANUAL TP-14.2 | MANUAL |
| TC-ERRV-010.2 | BND | Refused plan file on the web | "Couldn't open" with the reason | WEB `adversarial.mjs` «hostile file» | PASS |
| TC-ERRV-010.3 | INV | Error codes in brackets | Not shown | Planned | DEV |

#### LLR-ABOUT-010 — PART

"About this planner" shows the disclaimer and data sources; audit view not present.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ABOUT-010.1 | NOM | Open About | Version and sources | MANUAL TP-10.7 | MANUAL |
| TC-ABOUT-010.2 | BND | Disclaimer text | Same as at first start | WEB `smoke.mjs` «help repeats the disclaimer» | PASS |
| TC-ABOUT-010.3 | INV | Audit list and Verify | Not implemented | Planned | DEV |

#### LLR-UNRD-010 — IMPL

Data-unreadable screen with "Start with empty data" and "Close app".

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-UNRD-010.1 | NOM | Key lost | Screen with both choices | MANUAL TP-21.4 | MANUAL |
| TC-UNRD-010.2 | BND | Second confirmation | Required before resetting | MANUAL TP-21.4 | MANUAL |
| TC-UNRD-010.3 | INV | Close app | Nothing changed | MANUAL TP-21.4 | MANUAL |

#### LLR-PERF-010 — PART

Start-up is off the main thread; no automated benchmark.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PERF-010.1 | NOM | Cold start on the target phone | Plot list within the HLR time | MANUAL TP-23.1 | MANUAL |
| TC-PERF-010.2 | MAX | Pro tier first start | Catalog installed with progress | MANUAL TP-23.1 | MANUAL |
| TC-PERF-010.3 | INV | Benchmark in CI | Not set up | Planned | PLANNED |

#### LLR-PERF-020 — PART

No frame benchmark; planner speed covered by host tests.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PERF-020.1 | NOM | 500 plants, pan and zoom | No visible stutter | MANUAL TP-23.2 | MANUAL |
| TC-PERF-020.2 | MAX | Largest plans | Planner finishes in reasonable time | AUTO `PlannerPropertyTest.largePlotAndLargeCountsFinishInReasonableTime` | PASS |
| TC-PERF-020.3 | INV | Overlays on while dragging | Still responsive | MANUAL TP-23.2 | MANUAL |

#### LLR-PERF-030 — PART

Tier switch timed by hand.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PERF-030.1 | NOM | Basic → Pro | Within the HLR time | MANUAL TP-23.3 | MANUAL |
| TC-PERF-030.2 | BND | Pro → Basic | Within the HLR time | MANUAL TP-23.3 | MANUAL |
| TC-PERF-030.3 | INV | Switch while plans are open | No lost plants | MANUAL TP-23.3 | MANUAL |

### 3.5 `:core`, `:app` and web — features added 2026-09-27 onward

#### LLR-AUTOP-010 — OBS

Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: square candidate grid.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUTOP-010.1 | NOM | Plan an area (either layout) | Every placement obeys the rules | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |
| TC-AUTOP-010.2 | BND | Minimum and degenerate inputs | No exception, sensible notes | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | PASS |
| TC-AUTOP-010.3 | RND | Extreme latitudes, directions and margins | Tall plants on the far side, no exception | AUTO `PlannerPropertyTest.extremeLatitudesDirectionsAndMargins` | PASS |

#### LLR-AUTOP-020 — OBS

Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: sunward depth targets.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUTOP-020.1 | NOM | Plan an area (either layout) | Every placement obeys the rules | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |
| TC-AUTOP-020.2 | BND | Minimum and degenerate inputs | No exception, sensible notes | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | PASS |
| TC-AUTOP-020.3 | RND | Extreme latitudes, directions and margins | Tall plants on the far side, no exception | AUTO `PlannerPropertyTest.extremeLatitudesDirectionsAndMargins` | PASS |

#### LLR-AUTOP-030 — OBS

Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: plant-by-plant placement order.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUTOP-030.1 | NOM | Plan an area (either layout) | Every placement obeys the rules | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |
| TC-AUTOP-030.2 | BND | Minimum and degenerate inputs | No exception, sensible notes | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | PASS |
| TC-AUTOP-030.3 | RND | Extreme latitudes, directions and margins | Tall plants on the far side, no exception | AUTO `PlannerPropertyTest.extremeLatitudesDirectionsAndMargins` | PASS |

#### LLR-AUTOP-040 — OBS

Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: score terms.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUTOP-040.1 | NOM | Plan an area (either layout) | Every placement obeys the rules | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |
| TC-AUTOP-040.2 | BND | Minimum and degenerate inputs | No exception, sensible notes | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | PASS |
| TC-AUTOP-040.3 | RND | Extreme latitudes, directions and margins | Tall plants on the far side, no exception | AUTO `PlannerPropertyTest.extremeLatitudesDirectionsAndMargins` | PASS |

#### LLR-AUTOP-050 — OBS

Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: result notes.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUTOP-050.1 | NOM | Plan an area (either layout) | Every placement obeys the rules | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |
| TC-AUTOP-050.2 | BND | Minimum and degenerate inputs | No exception, sensible notes | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | PASS |
| TC-AUTOP-050.3 | RND | Extreme latitudes, directions and margins | Tall plants on the far side, no exception | AUTO `PlannerPropertyTest.extremeLatitudesDirectionsAndMargins` | PASS |

#### LLR-AUTOP-060 — IMPL

Proposal drawn translucent with a card: Plant them / Change selections / Discard.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-AUTOP-060.1 | NOM | Keep a plan | Plants added, one undo step | WEB `smoke.mjs` «plan kept» | PASS |
| TC-AUTOP-060.2 | BND | Change selections | Dialog reopens with the same rows | WEB `smoke.mjs` «change selections keeps the edited list» | PASS |
| TC-AUTOP-060.3 | INV | Discard | Plot unchanged, says so | WEB `smoke.mjs` «discard says nothing changed» | PASS |

#### LLR-VAR-010 — IMPL

`VarietyCatalogTraits` for peppers, tomatoes and onions.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VAR-010.1 | NOM | Bell pepper, cherry tomato, spring onion | Kind, heat, color | AUTO `SeasonsRotationTest.peppersSaySweetOrSpicyAndTheirColour` | PASS |
| TC-VAR-010.2 | BND | Every catalog pepper, tomato and onion | All have details | AUTO `SeasonsRotationTest.everyCatalogPepperTomatoAndOnionHasDetails` | PASS |
| TC-VAR-010.3 | INV | Other crops | null (no details) | AUTO `SeasonsRotationTest.tomatoesSayCherryOrLarge_onionsSaySpringOrBulb` | PASS |

#### LLR-VAR-020 — IMPL

Spring onions in every tier; labels on the layout.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-VAR-020.1 | NOM | Search onion | Three spring onions | WEB `smoke.mjs` «three spring onions in the catalog» | PASS |
| TC-VAR-020.2 | BND | Every tier | ONI-101..103 present | AUTO `CatalogDataTest.eachTierContainsTheSmallerOneWithIdenticalEntries` | PASS |
| TC-VAR-020.3 | INV | Labels switched off | No names drawn | MANUAL TP-13.4 | MANUAL |

#### LLR-ROT-010 — IMPL

Rotation groups by family; conflict within the wait; warning after place/move.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ROT-010.1 | NOM | Tomato family | NIGHTSHADES, 3 years | AUTO `SeasonsRotationTest.rotationGroupsFollowTheFamily_andSkipFlowersAndPerennials` | PASS |
| TC-ROT-010.2 | BND | Same family exactly at the wait limit and just inside | No conflict; conflict | AUTO `SeasonsRotationTest.conflictFindsTheSameFamilyInTheSameSpotWithinTheWait` | PASS |
| TC-ROT-010.3 | INV | Flowers and perennials | No group | AUTO `SeasonsRotationTest.rotationGroupsFollowTheFamily_andSkipFlowersAndPerennials` | PASS |
| TC-ROT-010.4 | NOM | Tomato placed where tomatoes grew (web) | Rotation note shown | WEB `smoke.mjs` «rotation note when a tomato goes where tomatoes grew» | PASS |

#### LLR-ROT-020 — IMPL

Rotation advice text.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ROT-020.1 | NOM | One closed season | Last year's groups and spots | AUTO `SeasonsRotationTest.archiveKeepsNameFamilyAndGroup_andAdviceDescribesLastSeason` | PASS |
| TC-ROT-020.2 | BND | Long rows | Row note | AUTO `SeasonsRotationTest.longRowsAreDetected_forTheRotationTip` | PASS |
| TC-ROT-020.3 | INV | No history | Hint only | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |
| TC-ROT-020.4 | ALN | Advice in Spanish | Translated | WEB `smoke.mjs` «rotation advice names last year» | PASS |

#### LLR-SEAS-010 — IMPL

History table and current season.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SEAS-010.1 | NOM | Close a season | Plants archived with name, family, group | AUTO `SeasonsRotationTest.archiveKeepsNameFamilyAndGroup_andAdviceDescribesLastSeason` | PASS |
| TC-SEAS-010.2 | BND | No plants, no history | This year | AUTO `SeasonsRotationTest.currentSeasonComesAfterTheLastClosedSeason` | PASS |
| TC-SEAS-010.3 | INV | History year after the plants' year | History year + 1 | AUTO `SeasonsRotationTest.currentSeasonComesAfterTheLastClosedSeason` | PASS |

#### LLR-SEAS-020 — IMPL

Start a new season in one change, undoable.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SEAS-020.1 | NOM | Close the season | History grows, trees kept | WEB `smoke.mjs` «tree kept for next season» | PASS |
| TC-SEAS-020.2 | BND | Undo, then redo | Season reopened, closed again | WEB `smoke.mjs` «undo reopens the closed season» | PASS |
| TC-SEAS-020.3 | INV | Year 1899 or 3001 | Refused | MANUAL TP-17.1 | MANUAL |

#### LLR-SEAS-030 — IMPL

History in plan files; limits on read.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SEAS-030.1 | NOM | Save and reopen | Same history | AUTO `SeasonsRotationTest.planFileRoundTripsHistory` | PASS |
| TC-SEAS-030.2 | MAX | 20,001 history entries; seasons 1900 and 3000 | First 20,000 kept; both years kept | AUTO `PlanFileLimitsTest.historyLimits` | PASS |
| TC-SEAS-030.3 | INV | Season 1899, 3001, "2025"; no code; outside the plot | Skipped and counted; missing radius 0.3 m | AUTO `PlanFileLimitsTest.historyLimits` | PASS |

#### LLR-MEM-010 — IMPL

Last plan list and usual varieties.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-MEM-010.1 | NOM | Plan once, reopen the dialog | Same rows | WEB `smoke.mjs` «plan list remembered» | PASS |
| TC-MEM-010.2 | BND | No history | Top species by count | AUTO `SeasonsRotationTest.usualVarietiesAreTheMostPlantedSpecies` | PASS |
| TC-MEM-010.3 | ALN | Stored list "TOM-001:x,bad,:3,ZZZ-9:2" | Only well-formed known rows | Planned | PLANNED |

#### LLR-BLK-010 — IMPL

Block planner: order, anchors, cheap checks, score, validation.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-BLK-010.1 | NOM | 20 corn | 4 rows of 5, evenly spaced | AUTO `BlockPlannerTest.twentyCornAreFourRowsOfFive_evenlySpaced` | PASS |
| TC-BLK-010.2 | BND | 7 tomatoes | Row of 4 and row of 3 | AUTO `BlockPlannerTest.sevenTomatoesAreARowOfFourAndARowOfThree` | PASS |
| TC-BLK-010.3 | RND | Random plots, lists and directions | Every placement obeys the rules | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |
| TC-BLK-010.4 | INV | Plants already there | Never overlapped | AUTO `PlannerPropertyTest.plantsAlreadyThereAreNeverOverlapped` | PASS |

#### LLR-BLK-020 — IMPL

Vine runways and climbers.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-BLK-020.1 | NOM | Squash | Sunny edge, runway toward the sun | AUTO `BlockPlannerTest.vinesGoToTheSunnyEdgeWithARunwayTowardTheSun_climbersToTheBack` | PASS |
| TC-BLK-020.2 | BND | Runway next to a fence | Kept clear | AUTO `OptionsGroupsI18nTest.vinesKeepTheirRunwayClearOfOtherPlantsAndFences` | PASS |
| TC-BLK-020.3 | RND | Every layout option | Runways never cross plants, runways or the edge | AUTO `RunwaysAndRowsTest.runwaysNeverCrossPlantsOtherRunwaysOrThePlotEdge_inEveryLayout` | PASS |

#### LLR-FIND-010 — IMPL

Legend find on the web and Android.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FIND-010.1 | NOM | Find corn | Every corn plant circled | WEB `smoke.mjs` «find circles every corn plant» | PASS |
| TC-FIND-010.2 | BND | Esc | Find cleared | WEB `smoke.mjs` «Esc clears find» | PASS |
| TC-FIND-010.3 | NOM | Harvest line | Finds its plants | WEB `smoke.mjs` «harvest line finds its plants» | PASS |
| TC-FIND-010.4 | INV | Find a variety, then delete all its plants | Find cleared, no ring left | MANUAL TP-13.4 | MANUAL |

#### LLR-FIND-020 — IMPL

Edit plant card.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FIND-020.1 | NOM | Change the variety | Saved | WEB `smoke.mjs` «plant variety changed» | PASS |
| TC-FIND-020.2 | BND | Undo | Old variety | WEB `smoke.mjs` «undo reverts the variety change» | PASS |
| TC-FIND-020.3 | INV | Variety that conflicts at the spot | Refused with the reason | MANUAL TP-14.3 | MANUAL |

#### LLR-OUTL-010 — IMPL

Outline corners editable; delete outline.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-OUTL-010.1 | NOM | Drag a corner | Moved | WEB `smoke.mjs` «outline corner moved» | PASS |
| TC-OUTL-010.2 | BND | Delete the outline, undo | Deleted; restored | WEB `smoke.mjs` «undo restores the outline» | PASS |
| TC-OUTL-010.3 | INV | Corner moved to make edges cross | Refused | AUTO `RoadmapFeaturesTest.outlineValidation_rejectsFigureEightAndTooFewCorners` | PASS |

#### LLR-CMP-010 — IMPL

Compass with four arrowheads; N red when set.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CMP-010.1 | NOM | Direction set | Four arrowheads, N red | WEB `smoke.mjs` «compass has four arrowheads» | PASS |
| TC-CMP-010.2 | BND | Direction not set | Gray, "N?" | MANUAL TP-13.8 | MANUAL |
| TC-CMP-010.3 | RND | Bearings 0, 90, 359 | N points the right way | MANUAL TP-13.8 | MANUAL |

#### LLR-SEAS-040 — IMPL

View a past season read-only.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SEAS-040.1 | NOM | Choose last season | Shown read-only | WEB `smoke.mjs` «shown read-only» | PASS |
| TC-SEAS-040.2 | BND | Try to edit | Refused | WEB `smoke.mjs` «past season cannot be edited» | PASS |
| TC-SEAS-040.3 | INV | Past season with no plants | Empty layout, back button | MANUAL TP-17.2 | MANUAL |

#### LLR-SEAS-050 — IMPL

Plan next season.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SEAS-050.1 | NOM | Plan next season | Starts from this season's list | WEB `smoke.mjs` «next season starts from this season» | PASS |
| TC-SEAS-050.2 | BND | Keep it | This season archived, next planted | WEB `smoke.mjs` «this season archived, next season planted» | PASS |
| TC-SEAS-050.3 | INV | Crops where the same crop grew | None too close | WEB `smoke.mjs` «no corn where corn grew last season» | PASS |

#### LLR-SEAS-060 — IMPL

Multi-year rotation plan with variety changes.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SEAS-060.1 | NOM | Five seasons | No family on last year's spot | AUTO `RotationPlannerTest.fiveSeasonsNeverPutAFamilyWhereItGrewTheYearBefore` | PASS |
| TC-SEAS-060.2 | BND | 1 and 30 seasons; 0 and 31 | Accepted; clamped | AUTO `EditingAndSitePropertyTest.rotationPlan_yearLimitsAndEmptyLists` | PASS |
| TC-SEAS-060.3 | NOM | Change a variety from year 3 | Applied from that year on | AUTO `ShapesSwapRotationTest.rotationPlanCanSwapAVarietyFromAChosenYearOn` | PASS |
| TC-SEAS-060.4 | INV | Looking at the plan | Plot unchanged | WEB `smoke.mjs` «looking at the rotation plan changes nothing» | PASS |

#### LLR-ROT-030 — IMPL

Strict rotation relaxed only when needed.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ROT-030.1 | NOM | Room elsewhere | Crop moved off last year's spot | AUTO `SeasonsRotationTest.autoPlannerKeepsCropsAwayFromLastYearsFamily` | PASS |
| TC-ROT-030.2 | BND | No room elsewhere | Relaxed with the note | AUTO `RotationPlannerTest.strictRuleIsRelaxedOnlyWhenThereIsNoRoom_andSaysSo` | PASS |
| TC-ROT-030.3 | INV | History with no rotation group | Same species kept away | Planned | PLANNED |

#### LLR-SHD-010 — IMPL

Shade at a time; day presets; slider.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SHD-010.1 | NOM | Tree at noon | Shadow to the north | AUTO `ShadeIrrigationTest.shadowAtATimeAndSunWindowsFollowTheSun` | PASS |
| TC-SHD-010.2 | BND | Every latitude and day | Values in range | AUTO `EditingAndSitePropertyTest.sun_valuesStayInRangeAtEveryLatitudeAndDay` | PASS |
| TC-SHD-010.3 | INV | Grid of 0 or negative size | Empty, no crash | AUTO `EditingAndSitePropertyTest.sun_valuesStayInRangeAtEveryLatitudeAndDay` | PASS |
| TC-SHD-010.4 | NOM | Web slider | Shade at the chosen time drawn | WEB `smoke.mjs` «shade at the chosen time drawn» | PASS |

#### LLR-SHD-020 — IMPL

Tall plants cast shade.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SHD-020.1 | NOM | Corn south of lettuce | Lettuce shaded | AUTO `ShadeIrrigationTest.tallPlantsShadeTheirNorthernNeighbours` | PASS |
| TC-SHD-020.2 | BND | Plant 0.49 m / 0.5 m tall | No barrier; barrier | Planned | PLANNED |
| TC-SHD-020.3 | INV | Unknown variety | No barrier | Planned | PLANNED |

#### LLR-SHD-030 — IMPL

Sun windows on hover.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SHD-030.1 | NOM | Open ground | Sun hours window text | AUTO `ShadeIrrigationTest.shadowAtATimeAndSunWindowsFollowTheSun` | PASS |
| TC-SHD-030.2 | BND | Full shade spot | "No direct sun" | AUTO `ShadeIrrigationTest.shadowAtATimeAndSunWindowsFollowTheSun` | PASS |
| TC-SHD-030.3 | INV | Polar night latitude | No sun, no exception | AUTO `EditingAndSitePropertyTest.sun_valuesStayInRangeAtEveryLatitudeAndDay` | PASS |

#### LLR-WATER-010 — IMPL

Sprinklers, drip lines and hose bibs.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WATER-010.1 | NOM | Add a sprinkler and a drip line | Stored with their points | WEB `smoke.mjs` «drip line added with its 3 points» | PASS |
| TC-WATER-010.2 | BND | Arc 2° and missing; throw 99 m in a plan file | Arc 10° and 360°; throw 60 m | AUTO `PlanFileLimitsTest.siteFeaturesAndIrrigationLimits` | PASS |
| TC-WATER-010.3 | INV | 1-point drip line, sprinkler with no point, huge arc direction | Skipped; direction 0 (was NaN) | AUTO `PlanFileLimitsTest.siteFeaturesAndIrrigationLimits` | PASS |
| TC-WATER-010.4 | NOM | Plan file round trip of a sprinkler and a hose tap | Same values | AUTO `ShadeIrrigationTest.irrigationTravelsInPlanFiles` | PASS |

#### LLR-WATER-020 — IMPL

Coverage rules and grid.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WATER-020.1 | NOM | Sprinkler, drip and hose | Correct cells covered | AUTO `ShadeIrrigationTest.sprinklerDripAndHoseCoverage` | PASS |
| TC-WATER-020.2 | BND | Exactly at the throw and at the arc edge | Covered | AUTO `EditingAndSitePropertyTest.irrigation_coverageRulesAtTheLimits` | PASS |
| TC-WATER-020.3 | INV | Grid size 0 or negative | Empty | AUTO `EditingAndSitePropertyTest.irrigation_coverageRulesAtTheLimits` | PASS |

#### LLR-WATER-030 — IMPL

Irrigation report.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WATER-030.1 | NOM | Plants partly covered | Report lists hand-watered plants | AUTO `PestsChecksBackdropTest.wateringAdviceNamesUncoveredAndThirstyPlants` | PASS |
| TC-WATER-030.2 | BND | No irrigation | Hint only | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |
| TC-WATER-030.3 | NOM | Care tab | Shows watering with the tools | WEB `smoke.mjs` «Care shows watering with the irrigation tools» | PASS |
| TC-WATER-030.4 | INV | Sprinkler or drip line with no or garbage points | Covers nothing; report still readable | AUTO `EditingAndSitePropertyTest.irrigation_coverageRulesAtTheLimits` | PASS |

#### LLR-FILLP-010 — IMPL

Fill the whole plot / how many fit.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FILLP-010.1 | NOM | Mixed list | Counts keep the proportions | AUTO `ShadeIrrigationTest.howManyFitKeepsTheProportions` | PASS |
| TC-FILLP-010.2 | BND | Web | Estimates the numbers | WEB `smoke.mjs` «how many fit estimates the numbers» | PASS |
| TC-FILLP-010.3 | INV | Plot too small for any | Zero counts, no exception | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | PASS |

#### LLR-TPL-010 — IMPL

Duplicate a plot.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-TPL-010.1 | NOM | Duplicate | Site and history copied | WEB `smoke.mjs` «duplicate carries the site and history» | PASS |
| TC-TPL-010.2 | BND | Photo and pests | Copied | WEB `smoke.mjs` «duplicate carries the photo and pests» | PASS |
| TC-TPL-010.3 | INV | Delete the copy | Original kept | WEB `smoke.mjs` «copy deleted, original kept» | PASS |

#### LLR-FROST-010 — IMPL

Frost stations.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FROST-010.1 | NOM | Nearest station | Its dates | AUTO `SeasonPlanBSunTest.frostDatesComeFromTheNearestNoaaStation` | PASS |
| TC-FROST-010.2 | BND | Dateline | Distance wraps at 180° | AUTO `InputRobustnessTest.frostStations_malformedLinesAreRejected_andDistanceWrapsAt180` | PASS |
| TC-FROST-010.3 | INV | Malformed station lines | Rejected | AUTO `InputRobustnessTest.frostStations_malformedLinesAreRejected_andDistanceWrapsAt180` | PASS |

#### LLR-FROST-020 — IMPL

Planting windows.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-FROST-020.1 | NOM | Tomato and pea | Windows follow the frost dates | AUTO `SeasonPlanBSunTest.plantingWindowsFollowTheFrostDates` | PASS |
| TC-FROST-020.2 | RND | Every crop, any season | Valid window or note | AUTO `EditingAndSitePropertyTest.growingSeason_windowsForEveryCatalogCropAndAnySeason` | PASS |
| TC-FROST-020.3 | NOM | Web calendar | Lists planting windows | WEB `smoke.mjs` «planting calendar lists planting windows» | PASS |
| TC-FROST-020.4 | BND | Frost-free place (365 days) and a 1-day season | Every crop gets a window or the short-season note | AUTO `LlrGapTest.planningWindows_frostFreeAndOneDaySeasons` | PASS |

#### LLR-SUN-010 — IMPL

Sun over the season.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SUN-010.1 | NOM | Planner run in winter | Uses season days | AUTO `SeasonPlanBSunTest.plannerJudgesSunOverTheGrowingSeasonNotOnTheDayItIsRun` | PASS |
| TC-SUN-010.2 | BND | No frost data; south | Default days (+182) | AUTO `EditingAndSitePropertyTest.sun_valuesStayInRangeAtEveryLatitudeAndDay` | PASS |
| TC-SUN-010.3 | INV | Latitude ±90 | No exception | AUTO `PlannerPropertyTest.extremeLatitudesDirectionsAndMargins` | PASS |

#### LLR-SUN-020 — IMPL

Sun-first ordering and scoring.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SUN-020.1 | NOM | Sun lovers and shade | Sunny spots to sun lovers | AUTO `AutoPlanAndPlanFileTest.shadedSpotsAreAvoidedForSunLovers` | PASS |
| TC-SUN-020.2 | BND | Starred plant | Sunniest spots | AUTO `PestsChecksBackdropTest.mostImportantPlantsGetTheSunniestSpots` | PASS |
| TC-SUN-020.3 | RND | Random plots | Rules obeyed | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |

#### LLR-PLANB-060 — IMPL

Backup planner.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PLANB-060.1 | NOM | Lost plant mid-season | Faster varieties that catch up | AUTO `SeasonPlanBSunTest.planBOffersFasterVarietiesThatCatchUpWithTheSurvivors` | PASS |
| TC-PLANB-060.2 | RND | Every pest and crop | No exception, ≤ 6 options | AUTO `AdviceRobustnessTest.pestRisksAndPlanBForEveryPestAndCrop` | PASS |
| TC-PLANB-060.3 | NOM | Web Plan B | Replaces the lost plant | WEB `smoke.mjs` «Plan B replaced the lost plant» | PASS |
| TC-PLANB-060.4 | BND | Days 121–300 around a first frost on day 280; frost-free place; limit −1 and 0; empty catalog | Nothing ready after the frost (+21 hardy); no limit when frost-free; no options and no exception (−1 used to throw) | AUTO `LlrGapTest.planB_respectsTheFirstFrostAndOddLimits` | PASS |

#### LLR-RULER-010 — IMPL

Ruler numbers only, unit once.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-RULER-010.1 | NOM | Web ruler | Numbers only | WEB `smoke.mjs` «ruler ticks show numbers only, unit once» | PASS |
| TC-RULER-010.2 | BND | Inches | Numbers in inches | MANUAL TP-13.5 | MANUAL |
| TC-RULER-010.3 | INV | Tick spacing larger than the plot | Only 0 and the end | MANUAL TP-13.5 | MANUAL |

#### LLR-BLK-030 — IMPL

Runway samples; shade is only a penalty (review finding).

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-BLK-030.1 | NOM | Free runway | Kept, no note | AUTO `RunwaysAndRowsTest.runwaysNeverCrossPlantsOtherRunwaysOrThePlotEdge_inEveryLayout` | PASS |
| TC-BLK-030.2 | BND | Runway in shade only | Not blocked | AUTO `RunwaysAndRowsTest.shadeOnTheRunwayDoesNotBlockIt` | PASS |
| TC-BLK-030.3 | INV | No free ground for a runway | Note names the crop | AUTO `OptionsGroupsI18nTest.vinesKeepTheirRunwayClearOfOtherPlantsAndFences` | PASS |

#### LLR-BLK-050 — IMPL

Long rows variant.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-BLK-050.1 | NOM | Rows layout | Real rows with walkways | AUTO `RunwaysAndRowsTest.longRowsAreRealRowsWithWalkways` | PASS |
| TC-BLK-050.2 | BND | Clumps vs rows | Clumps more compact; tall plants behind in both | AUTO `SeasonsRotationTest.clumpsAreMoreCompactThanRows_andBothKeepTallPlantsBehind` | PASS |
| TC-BLK-050.3 | RND | Random inputs, rows layout | Rules obeyed | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |

#### LLR-BLK-040 — IMPL

Rejected cells and whole-block retries.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-BLK-040.1 | NOM | Tomatoes near their antagonist | One block away from it | AUTO `OptionsGroupsI18nTest.tomatoesFindRoomAwayFromTheirAntagonist_inOneBlock` | PASS |
| TC-BLK-040.2 | BND | Area that fits only a split | Split with the note | AUTO `ShapesSwapRotationTest.theChosenArrangementIsUsed_andASplitIsExplained` | PASS |
| TC-BLK-040.3 | MAX | Large plots and counts | Finishes in reasonable time | AUTO `PlannerPropertyTest.largePlotAndLargeCountsFinishInReasonableTime` | PASS |
| TC-BLK-040.4 | RND | Random plots and lists with obstacles | Every placement obeys the rules | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |

#### LLR-OPT-010 — IMPL

Several layout options.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-OPT-010.1 | NOM | Plan | Several different options | AUTO `OptionsGroupsI18nTest.severalDifferentLayoutsAreOffered` | PASS |
| TC-OPT-010.2 | RND | Each option | Distinct and valid | AUTO `PlannerPropertyTest.optionsAreDistinctAndEachObeysTheRules` | PASS |
| TC-OPT-010.3 | NOM | Web ◀ ▶ | Moves between options | WEB `smoke.mjs` «Option ▶ shows another layout» | PASS |
| TC-OPT-010.4 | BND | Ask for −5, 0, 1, 10 and 50 options | 1 to 10 options (−5 and 0 used to give none) | AUTO `LlrGapTest.layoutOptions_countLimitsAndEveryoneStarred` | PASS |

#### LLR-KEEP-010 — IMPL

Keep or replace plants in the area.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-KEEP-010.1 | NOM | Area with plants | Keep/replace asked | WEB `smoke.mjs` «plan dialog asks to keep or replace» | PASS |
| TC-KEEP-010.2 | BND | Before keeping | Nothing removed | WEB `smoke.mjs` «nothing is removed before the plan is kept» | PASS |
| TC-KEEP-010.3 | NOM | Replace | Old plants replaced | WEB `smoke.mjs` «blank area: new plan replaced the old plants» | PASS |
| TC-KEEP-010.4 | INV | Choose Replace, then Discard | Old plants unchanged | MANUAL TP-16.4 | MANUAL |

#### LLR-CARD-010 — IMPL

Movable, foldable proposal card.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CARD-010.1 | NOM | Drag the card | Moves | WEB `smoke.mjs` «proposal card can be dragged out of the way» | PASS |
| TC-CARD-010.2 | BND | Fold | Header only | WEB `smoke.mjs` «proposal card folds to its header» | PASS |
| TC-CARD-010.3 | INV | Drag beyond the page | Kept on screen | MANUAL TP-16.2 | MANUAL |

#### LLR-GRP-010 — IMPL

Groups of the same variety.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GRP-010.1 | NOM | Select a clump's group | Whole clump | WEB `smoke.mjs` «Select its group selects the whole clump» | PASS |
| TC-GRP-010.2 | RND | Random plants | Membership symmetric | AUTO `EditingAndSitePropertyTest.groups_membershipIsSymmetric_andRearrangeKeepsCountCentreAndSpacing` | PASS |
| TC-GRP-010.3 | BND | Two clumps just apart | Separate groups | AUTO `OptionsGroupsI18nTest.aGroupCanBeFoundMovedAndRearranged` | PASS |

#### LLR-GRP-020 — IMPL

Move a group.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GRP-020.1 | NOM | Move a group | All moved, rules checked | AUTO `OptionsGroupsI18nTest.aGroupCanBeFoundMovedAndRearranged` | PASS |
| TC-GRP-020.2 | INV | Move out of the plot | Every plant reported | AUTO `EditingAndSitePropertyTest.groups_movingOutOfThePlotIsReportedForEveryPlant` | PASS |
| TC-GRP-020.3 | BND | Move Mode off | Group selection cleared | MANUAL TP-16.5 | MANUAL |

#### LLR-GRP-030 — IMPL

Rearrange a group.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-GRP-030.1 | NOM | Rearrange corn | Other rows and columns offered | WEB `smoke.mjs` «rearrange offers other rows and columns» | PASS |
| TC-GRP-030.2 | RND | Random groups | Count, center and spacing kept | AUTO `EditingAndSitePropertyTest.groups_membershipIsSymmetric_andRearrangeKeepsCountCentreAndSpacing` | PASS |
| TC-GRP-030.3 | INV | Rows not adding up | null | AUTO `EditingAndSitePropertyTest.clumpShapes_everyOptionAddsUpForAnyCount` | PASS |
| TC-GRP-030.4 | BND | Groups of 1, 120 and 5,000 plants | Every offered arrangement adds up | AUTO `EditingAndSitePropertyTest.clumpShapes_everyOptionAddsUpForAnyCount` | PASS |

#### LLR-LANG-010 — IMPL

US spelling in interface strings.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LANG-010.1 | NOM | Interface text | US spelling | REVIEW TP-11.8 | MANUAL |
| TC-LANG-010.2 | BND | Plan file units | "metres" kept | AUTO `WebPlanFileCompatTest.fileSavedByTheWebPlanner_decodesOnAndroid` | PASS |
| TC-LANG-010.3 | INV | British spellings reintroduced | Found by the review script | REVIEW TP-11.8 | MANUAL |

#### LLR-LANG-030 — IMPL

Complete Spanish; names typed by the user stay as typed.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LANG-030.1 | NOM | Planner notes | Spanish | AUTO `SpanishDictionaryTest.plannerNotesForTheOwnersListComeOutInSpanish` | PASS |
| TC-LANG-030.2 | ALN | Random text, quotes, braces | Never throws; quoted names kept | AUTO `I18nPropertyTest.englishIsAlwaysUnchanged_andRandomTextNeverThrows` | PASS |
| TC-LANG-030.3 | MAX | Deep and long inputs | Finish quickly | AUTO `I18nPropertyTest.deepAndLongInputFinishQuickly` | PASS |
| TC-LANG-030.4 | NOM | Main screens in Spanish | No English sentence left | CI `web-planner.yml` «Spanish coverage» | PASS |

#### LLR-LANG-020 — IMPL

Dictionary engine and language switch.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LANG-020.1 | NOM | Switch to Spanish | Interface from the dictionary | WEB `smoke.mjs` «interface switches to Spanish» | PASS |
| TC-LANG-020.2 | BND | And back to English | English | WEB `smoke.mjs` «and back to English» | PASS |
| TC-LANG-020.3 | INV | Missing entry | Falls back to English | AUTO `OptionsGroupsI18nTest.theInterfaceTranslatesFromADictionary_andFallsBackToEnglish` | PASS |
| TC-LANG-020.4 | RND | Switching many times | No leftovers from the old language | AUTO `I18nPropertyTest.switchingLanguagesNeverLeaksOldTranslations` | PASS |

#### LLR-SWAP-010 — IMPL

Replace all of one variety.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SWAP-010.1 | NOM | Replace corn | Every corn plant changed | WEB `smoke.mjs` «replace all changes every corn plant at once» | PASS |
| TC-SWAP-010.2 | RND | Random mixes | Positions and counts kept | AUTO `EditingAndSitePropertyTest.replaceAll_keepsPositionsAndCountsForAnyMix` | PASS |
| TC-SWAP-010.3 | INV | Same variety / none planted / blocked by zone | Unchanged with a message | AUTO `ShapesSwapRotationTest.replaceAllSwapsEveryPlantOfAVarietyInOneStep` | PASS |
| TC-SWAP-010.4 | BND | Replace with a wider variety on a full bed | Crowded count between 0 and the number changed, with a message | AUTO `EditingAndSitePropertyTest.replaceAll_keepsPositionsAndCountsForAnyMix` | PASS |

#### LLR-LEG-010 — IMPL

Draggable legend.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-LEG-010.1 | NOM | Drag the legend | Moved and remembered | WEB `smoke.mjs` «On this plot box dragged and its place remembered» | PASS |
| TC-LEG-010.2 | BND | Drag past the stage edge | Clamped | MANUAL TP-13.4 | MANUAL |
| TC-LEG-010.3 | INV | Stored position "abc" | Default place | MANUAL TP-13.4 | MANUAL |

#### LLR-ARR-010 — IMPL

Arrangement options.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ARR-010.1 | NOM | 50 plants | Several arrangements | AUTO `ShapesSwapRotationTest.fiftyPlantsCanBeArrangedTheWayTheGardenerLikes` | PASS |
| TC-ARR-010.2 | RND | Any count 1..500 | Every option adds up | AUTO `EditingAndSitePropertyTest.clumpShapes_everyOptionAddsUpForAnyCount` | PASS |
| TC-ARR-010.3 | NOM | Web | Arrangements offered | WEB `smoke.mjs` «clump arrangements offered» | PASS |
| TC-ARR-010.4 | BND | 0, 1, 997 and 5,000 plants | None for 0; at most 10 distinct options otherwise | AUTO `EditingAndSitePropertyTest.clumpShapes_everyOptionAddsUpForAnyCount` | PASS |

#### LLR-ARR-020 — IMPL

Other arrangements and split notes.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ARR-020.1 | NOM | Chosen arrangement | Used | AUTO `ShapesSwapRotationTest.theChosenArrangementIsUsed_andASplitIsExplained` | PASS |
| TC-ARR-020.2 | BND | Only a split fits | Split explained | AUTO `ShapesSwapRotationTest.theChosenArrangementIsUsed_andASplitIsExplained` | PASS |
| TC-ARR-020.3 | RND | Random inputs | Rules obeyed | AUTO `PlannerPropertyTest.randomPlotsListsAndDirections_everyLayoutObeysTheRules` | PASS |

#### LLR-HELP-010 — IMPL

Hover help on every button.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-HELP-010.1 | NOM | Every button | Has hover help | WEB `smoke.mjs` «every button has hover help» | PASS |
| TC-HELP-010.2 | BND | Plan dialog buttons | Have hover help | WEB `smoke.mjs` «every button in the plan dialog has hover help» | PASS |
| TC-HELP-010.3 | ALN | In Spanish | Still present | WEB `smoke.mjs` «hover help still present in Spanish» | PASS |

#### LLR-ADDR-010 — IMPL

Address and Google Maps.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ADDR-010.1 | NOM | Plot tab | Open in Google Maps button | WEB `smoke.mjs` «Open in Google Maps button on the Plot tab» | PASS |
| TC-ADDR-010.2 | MAX | Address of 300 characters in a file | Cut to 200 | AUTO `PlanFileLimitsTest.namesAndTextsAreCutToTheirLimits` | PASS |
| TC-ADDR-010.3 | ALN | Address with spaces and a comma | Percent-encoded in the URL | AUTO `PestsChecksBackdropTest.satellitePhotoCalibratesAndTravelsInThePlanFile` | PASS |

#### LLR-YARD-010 — IMPL

Pests on the plot.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-YARD-010.1 | NOM | Tick deer | Saved, round trip | AUTO `PestsChecksBackdropTest.pestsRoundTripOnThePlotAndPointAtThePlantsAtRisk` | PASS |
| TC-YARD-010.2 | ALN | Unknown pest names | Ignored | AUTO `PestsChecksBackdropTest.pestsRoundTripOnThePlotAndPointAtThePlantsAtRisk` | PASS |
| TC-YARD-010.3 | NOM | New plot | Asks which pests visit | WEB `smoke.mjs` «new plot asks which pests visit the yard» | PASS |
| TC-YARD-010.4 | BND | No pest ticked; the same pest three times | Stored as null; once | AUTO `PestsChecksBackdropTest.pestsRoundTripOnThePlotAndPointAtThePlantsAtRisk` | PASS |

#### LLR-YARD-020 — IMPL

Pest guide.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-YARD-020.1 | NOM | Raccoons | Care names the plants | WEB `smoke.mjs` «Care names the plants raccoons go for» | PASS |
| TC-YARD-020.2 | RND | Every pest × every crop | No exception | AUTO `AdviceRobustnessTest.pestRisksAndPlanBForEveryPestAndCrop` | PASS |
| TC-YARD-020.3 | BND | Change pests on Care | Saved on the plot | WEB `smoke.mjs` «pests can be changed on the Care tab» | PASS |

#### LLR-PRIO-010 — IMPL

Most important plants first.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PRIO-010.1 | NOM | Starred crop | Sunniest spots | AUTO `PestsChecksBackdropTest.mostImportantPlantsGetTheSunniestSpots` | PASS |
| TC-PRIO-010.2 | BND | Web | Starred plant listed | WEB `smoke.mjs` «starred plant listed as most important» | PASS |
| TC-PRIO-010.3 | NOM | Proposal | Says the important plants went first | WEB `smoke.mjs` «proposal says the most important plants went first» | PASS |
| TC-PRIO-010.4 | INV | Every crop starred | Valid plan, every plant placed or reported | AUTO `LlrGapTest.layoutOptions_countLimitsAndEveryoneStarred` | PASS |

#### LLR-CHK-010 — IMPL

Checks before planning.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-CHK-010.1 | NOM | Too many plants, little sun, pests | Warnings sorted by severity | AUTO `PestsChecksBackdropTest.checksBeforePlanningWarnAboutSpaceSunAndPests` | PASS |
| TC-CHK-010.2 | BND | Empty list / empty plot | No exception | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |
| TC-CHK-010.3 | NOM | Web | Checks shown | WEB `smoke.mjs` «checks shown before planning» | PASS |
| TC-CHK-010.4 | RND | Random plots, lists and pests | No exception, no NaN in the texts | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |

#### LLR-DISC-010 — IMPL

Disclaimer.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-DISC-010.1 | NOM | First use | Disclaimer shown | WEB `smoke.mjs` «disclaimer shown before first use» | PASS |
| TC-DISC-010.2 | BND | Text | Says results are not guaranteed | WEB `smoke.mjs` «disclaimer says results are not guaranteed» | PASS |
| TC-DISC-010.3 | NOM | Care tab | Short disclaimer | WEB `smoke.mjs` «Care repeats the short disclaimer» | PASS |
| TC-DISC-010.4 | INV | Accept, restart the app; reload the web page | Not shown again | MANUAL TP-10.1 | MANUAL |

#### LLR-WADV-010 — IMPL

Watering advice.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WADV-010.1 | NOM | Thirsty and uncovered plants | Named | AUTO `PestsChecksBackdropTest.wateringAdviceNamesUncoveredAndThirstyPlants` | PASS |
| TC-WADV-010.2 | BND | No plants | General advice only | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |
| TC-WADV-010.3 | NOM | Web Care | Watering shown | WEB `smoke.mjs` «Care shows watering with the irrigation tools» | PASS |
| TC-WADV-010.4 | RND | Random plots with random irrigation | No exception, no NaN in the texts | AUTO `AdviceRobustnessTest.everyAdviceFeatureHandlesEmptySmallAndLargePlots` | PASS |

#### LLR-SAT-010 — IMPL

Google Maps URL and picked image.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SAT-010.1 | NOM | Coordinates | Satellite URL | AUTO `PestsChecksBackdropTest.satellitePhotoCalibratesAndTravelsInThePlanFile` | PASS |
| TC-SAT-010.2 | BND | Button present | Opens Google Maps | WEB `smoke.mjs` «button to open Google Maps in satellite view» | PASS |
| TC-SAT-010.3 | INV | Image over 4,000,000 characters | Re-encoded at 60 % or refused | MANUAL TP-19.4 | MANUAL |

#### LLR-SAT-020 — IMPL

Calibrate, move and turn the photo.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SAT-020.1 | NOM | Two points 10 m apart | Width scaled | WEB `smoke.mjs` «scale set from two points» | PASS |
| TC-SAT-020.2 | BND | Turn to 180° and beyond | Stays within ±180 | AUTO `ShapesSwapRotationTest.photoTurnStaysWithin180EitherWayAndTurnsAboutItsCentre` | PASS |
| TC-SAT-020.3 | INV | Infinite or NaN turn | 0°, never NaN | AUTO `InputRobustnessTest.backdrop_parseAndTurnNeverProduceNonFiniteValues` | PASS |

#### LLR-SAT-030 — IMPL

Photo storage and plan files.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-SAT-030.1 | NOM | Save with a photo | Photo in the file | WEB `smoke.mjs` «saved file has the satellite photo and pests» | PASS |
| TC-SAT-030.2 | BND | Width 1 / 2000 m, aspect 0.05 / 20 | Accepted; beyond refused | AUTO `InputRobustnessTest.backdrop_parseAndTurnNeverProduceNonFiniteValues` | PASS |
| TC-SAT-030.3 | INV | Photo not an image data URL | Dropped with a warning | AUTO `PestsChecksBackdropTest.satellitePhotoCalibratesAndTravelsInThePlanFile` | PASS |

#### LLR-ORNT-010 — IMPL

Plot direction stored.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ORNT-010.1 | NOM | Set direction | Saved | WEB `smoke.mjs` «orientation saved» | PASS |
| TC-ORNT-010.2 | BND | 360° and −90° in a file | 0° and 270° | AUTO `PlanFileLimitsTest.locationSoilAndDirectionOutOfRangeAreDropped` | PASS |
| TC-ORNT-010.3 | INV | 1e40 or text in a file | 0°, never NaN (fixed) | AUTO `PlanFileLimitsTest.locationSoilAndDirectionOutOfRangeAreDropped` | PASS |

#### LLR-ORNT-020 — IMPL

Direction arrow and warning.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ORNT-020.1 | NOM | Direction set | Red N | WEB `smoke.mjs` «compass has four arrowheads» | PASS |
| TC-ORNT-020.2 | BND | Rotated plot | Tall plants follow the compass | AUTO `AutoPlanAndPlanFileTest.tallPlantsFollowTheCompass_whenPlotIsRotated` | PASS |
| TC-ORNT-020.3 | INV | Not set | "N?" and header warning | MANUAL TP-13.8 | MANUAL |

#### LLR-ORNT-030 — IMPL

ZIP table lookups.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-ORNT-030.1 | NOM | Published zones for sample ZIPs | Match | AUTO `ZipDataTest.zoneTable_givesPublishedZones` | PASS |
| TC-ORNT-030.2 | BND | Table sorted and complete | 42,277 lines | AUTO `ZipDataTest.zipTable_isSortedWellFormedAndComplete` | PASS |
| TC-ORNT-030.3 | ALN | Letters, 4 or 6 digits, blanks | Nothing found | AUTO `InputRobustnessTest.zip_alphanumericAndOddInputsFindNothing` | PASS |

#### LLR-OBST-010 — IMPL

Move site features.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-OBST-010.1 | NOM | Move a tree | Moved | WEB `smoke.mjs` «tree moved» | PASS |
| TC-OBST-010.2 | BND | Move past the plot edge; feature outside the plot in a file | Clamped inside; skipped on import (was accepted) | AUTO `PlanFileLimitsTest.siteFeaturesAndIrrigationLimits` | PASS |
| TC-OBST-010.3 | RND | Monkey run | Every item inside | WEB `adversarial.mjs` «every plant is inside its plot after the monkey run» | PASS |

#### LLR-OBST-020 — IMPL

Undo for features and outline.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-OBST-020.1 | NOM | Undo a tree move | Tree back | WEB `smoke.mjs` «undo puts the tree back» | PASS |
| TC-OBST-020.2 | BND | Undo outline delete | Outline back | WEB `smoke.mjs` «undo restores the outline» | PASS |
| TC-OBST-020.3 | RND | Undo storm | Same plan | WEB `adversarial.mjs` «undo 40× then redo 40×» | PASS |

#### LLR-OBST-030 — IMPL

Sun bands and palette.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-OBST-030.1 | NOM | Band thresholds | 6 h / 3 h | AUTO `LayoutPaletteTest.bandsFollowTheSunHourThresholds` | PASS |
| TC-OBST-030.2 | BND | Exactly 6 h and 3 h | Full sun; part shade | AUTO `LayoutPaletteTest.bandThresholdsMatchTheMarkedSunAreas` | PASS |
| TC-OBST-030.3 | NOM | Web overlay | Open ground full sun, tree shade | WEB `smoke.mjs` «open ground shows as full sun» | PASS |
| TC-OBST-030.4 | INV | Sun hours −1, 0, NaN, −∞; 24 and +∞ | Full shade for the odd values, full sun at 24 and above | AUTO `LlrGapTest.sunBands_oddHourValues` | PASS |

#### LLR-PFILE-010 — IMPL

Plan file writer.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PFILE-010.1 | NOM | Random plans | Come back unchanged | AUTO `PlanFileRoundTripTest.randomPlansComeBackUnchanged` | PASS |
| TC-PFILE-010.2 | BND | Smallest and largest plot | Round trip | AUTO `PlanFileRoundTripTest.anEmptyPlotAndTheLargestAllowedPlotRoundTrip` | PASS |
| TC-PFILE-010.3 | NOM | Numbers | 4 decimals | AUTO `WebPlanFileCompatTest.numbersAreRoundedToFourDecimals` | PASS |
| TC-PFILE-010.4 | INV | NaN plant position, NaN latitude, infinite pH in the database | File still opens; that plant skipped, values dropped | AUTO `LlrGapTest.planFile_damagedNumbersInTheAppNeverMakeAnUnreadableFile` | PASS |

#### LLR-PFILE-020 — IMPL

Plan file reader limits.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PFILE-020.1 | MAX | 101 plots; 5001 plants; > 20 MB | Rejected / capped with a warning | AUTO `PlanFileLimitsTest.plotCountAndFileSizeLimits` | PASS |
| TC-PFILE-020.2 | INV | Every truncation; random mutations; deep nesting | Rejected cleanly, never out-of-plot data | AUTO `InputRobustnessTest.planFile_randomMutationsNeverThrowAndNeverYieldOutOfPlotData` | PASS |
| TC-PFILE-020.3 | BND | Sizes 0.0001 and 1000; 0, −1, 1000.0001 | Kept; skipped | AUTO `InputRobustnessTest.planFile_wrongTypesAndExtremeNumbersAreSkippedWithWarnings` | PASS |
| TC-PFILE-020.4 | ALN | Hostile files in the browser | Refused with a reason | WEB `adversarial.mjs` «hostile file» | PASS |

#### LLR-PFILE-030 — PART

Import in one transaction; name suffix; code/name mapping.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-PFILE-030.1 | NOM | Import a web file on Android | Plants mapped | AUTO `WebPlanFileCompatTest.fileSavedByTheWebPlanner_decodesOnAndroid` | PASS |
| TC-PFILE-030.2 | BND | Plot name already exists | " (imported)" added | MANUAL TP-19.2 | MANUAL |
| TC-PFILE-030.3 | INV | Failure half-way | Nothing imported | MANUAL TP-19.2 | MANUAL |

#### LLR-WEB-010 — IMPL

Single-file build.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WEB-010.1 | NOM | Build | One HTML file | CI `web-planner.yml` «Build the single-file planner» | PASS |
| TC-WEB-010.2 | BND | Committed file equals a fresh build | Identical | CI `web-planner.yml` «Committed HTML matches the source» | PASS |
| TC-WEB-010.3 | INV | Page loads nothing from the network | No requests (offline test) | WEB `smoke.mjs` «no script errors» | PASS |

#### LLR-WEB-020 — IMPL

Save and open.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WEB-020.1 | NOM | Save | Plan-file header | WEB `smoke.mjs` «saved file has the plan-file header» | PASS |
| TC-WEB-020.2 | BND | Reopen | Same history | WEB `smoke.mjs` «re-opened file has the same history» | PASS |
| TC-WEB-020.3 | INV | Hostile file | "Couldn't open", plan unchanged | WEB `adversarial.mjs` «hostile file» | PASS |

#### LLR-WEB-030 — IMPL

Store.change, undo, draft.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WEB-030.1 | NOM | Reload the page | Draft restored | WEB `smoke.mjs` «draft restored after reload» | PASS |
| TC-WEB-030.2 | RND | 400 random actions | Plan still readable | WEB `adversarial.mjs` «after 400 random actions the plan is still readable» | PASS |
| TC-WEB-030.3 | BND | 40 undo / 40 redo | Same plan | WEB `adversarial.mjs` «undo 40× then redo 40×» | PASS |

#### LLR-WEB-040 — IMPL

Plan dialog and side panel.

| TC | Kind | Input / action | Expected | Method | Result |
|---|---|---|---|---|---|
| TC-WEB-040.1 | NOM | Plan an area | Preview with plants | WEB `smoke.mjs` «preview shows placed plants» | PASS |
| TC-WEB-040.2 | BND | Each tab | Renders | WEB `smoke.mjs` «tab renders» | PASS |
| TC-WEB-040.3 | INV | Monkey run | No script errors | WEB `adversarial.mjs` «no script errors» | PASS |

## 4. Regression register

Every failure found by testing, review or use becomes a regression case here. Each is automated where the
failure can be reproduced off-device, and runs after every merge (CI on pull requests and on pushes to main).
A register entry is never deleted; if the behavior is later changed on purpose, the entry records the decision.

| REG | Found by | Failure | Fix | Regression test | Affected LLR |
|---|---|---|---|---|---|
| REG-01 | Adversarial JVM suite | A plan file nested 200,000 levels deep crashed the JSON reader (stack overflow) | Depth limit 64 (`MiniJson.MAX_DEPTH`) | AUTO `InputRobustnessTest.json_deepNestingIsRejectedNotCrashing` | LLR-PFILE-020 |
| REG-02 | Adversarial JVM suite | Distances "NaN", "Infinity", "1e40" were accepted; "2,5" was refused | Finite values only; one decimal comma accepted | AUTO `InputRobustnessTest.distances_invalidTextGivesNull_andCommaDecimalsAreAccepted` | LLR-UNIT-040 |
| REG-03 | Adversarial JVM suite | Zones "7" and "7zz" were accepted | Only 1a–13b (`HardinessZones.isValid`) | AUTO `InputRobustnessTest.zones_onlyRealLabels` | LLR-WIN-020, LLR-ORNT-030 |
| REG-04 | Adversarial JVM suite | Turning the photo by infinity gave NaN; a stored photo with infinite values was accepted | `normalizeDeg` returns 0 for non-finite; `parse` rejects non-finite fields | AUTO `InputRobustnessTest.backdrop_parseAndTurnNeverProduceNonFiniteValues` | LLR-SAT-020, LLR-SAT-030 |
| REG-05 | Adversarial JVM suite | Frost-station search didn't wrap at ±180° longitude | Longitude difference wrapped | AUTO `InputRobustnessTest.frostStations_malformedLinesAreRejected_andDistanceWrapsAt180` | LLR-FROST-010 |
| REG-06 | Adversarial JVM suite | Plan files kept invalid hardiness zones | Zone checked on read | AUTO `InputRobustnessTest.planFile_hardinessZoneMustBeARealZone` | LLR-PFILE-020 |
| REG-07 | Adversarial JVM suite | A squash was planted in a 0.5 m plot, half outside | Plants keep half a radius from the area and plot edges | AUTO `PlannerPropertyTest.minimumAndDegenerateInputs` | LLR-BLK-010 |
| REG-08 | Adversarial JVM suite | Negative grid sizes crashed the sun, shade and water grids | Size ≤ 0 gives an empty grid | AUTO `EditingAndSitePropertyTest.sun_valuesStayInRangeAtEveryLatitudeAndDay` | LLR-SHD-010, LLR-WATER-020 |
| REG-09 | Adversarial JVM suite | NaN positions counted as inside the plot | `PlotShape.contains` rejects non-finite coordinates | AUTO `EditingAndSitePropertyTest.geometry_randomRectanglesAndDegenerateOutlines` | LLR-VALD-010 |
| REG-10 | Adversarial JVM suite | Lower-case companion codes in a user file were ignored | Codes trimmed and compared case-insensitively | AUTO `EditingAndSitePropertyTest.validator_isSymmetricMonotonicAndCaseInsensitive` | LLR-VALD-040 |
| REG-11 | Adversarial JVM suite | Area fill ran out of memory on a huge plot and looped forever on NaN | Inputs checked; capped at 20,000 positions | AUTO `AdviceRobustnessTest.areaFillerRejectsBadInputs_andCapsHugeGrids` | LLR-FILL-010, LLR-FILL-040 |
| REG-12 | Adversarial JVM suite | Soil texture accepted NaN | Non-finite values refused with a message | AUTO `AdviceRobustnessTest.soilTextureClassifiesEveryCombination` | LLR-EDIT-010 |
| REG-13 | Adversarial JVM suite | Plot names over 80 characters were cut on reload | Limit 200 everywhere | AUTO `PlanFileLimitsTest.namesAndTextsAreCutToTheirLimits` | LLR-PFILE-020 |
| REG-14 | Browser adversarial suite | A refused plan file said "Opened" and hid the reason | `Store.load` returns success and messages; "Couldn't open" shown | WEB `adversarial.mjs` «hostile file» | LLR-WEB-020 |
| REG-15 | Browser adversarial suite | Web number boxes accepted NaN; ZIP, pH and organic matter weren't checked | `Numbers.parse`; ZIP 5 digits; pH 3–10; organic 0–100; soil all or none | WEB `adversarial.mjs` «length NaN» | LLR-EDIT-010 |
| REG-16 | Review of web dialogs | Web allowed barriers up to 150 m, plan files only 100 m | Web limited to 100 m | AUTO `PlanFileLimitsTest.siteFeaturesAndIrrigationLimits` (file side); MANUAL TP-15.4 (web dialog) | LLR-WATER-010, LLR-OBST-010 |
| REG-17 | Dictionary property suite | Template "{0} and {1}" turned "rows and columns" into "rows y columns" | Loose templates need every part translated | AUTO `I18nPropertyTest.dictionariesWithTrickyEntries` | LLR-LANG-030 |
| REG-18 | Dictionary property suite | Templates matched across list items; planner notes half translated | Templates starting with {0} never match across "; " | AUTO `SpanishDictionaryTest.plannerNotesForTheOwnersListComeOutInSpanish` | LLR-LANG-030 |
| REG-19 | Spanish coverage check | "Prevent:" labels stopped translating after the colon rule was tightened | Colon labels added to the dictionary | CI `web-planner.yml` «Spanish coverage» | LLR-LANG-030 |
| REG-20 | /code-review | Shade alone blocked a vine's runway | Shade is a score penalty only | AUTO `RunwaysAndRowsTest.shadeOnTheRunwayDoesNotBlockIt` | LLR-BLK-030 |
| REG-21 | /code-review | Plot names and descriptions typed by the user were translated | Names marked `translate="no"` (web) and not passed to `tr` (Android) | AUTO `SpanishDictionaryTest.quotedNamesStayAsTyped` | LLR-LANG-030, LLR-LAYV-010 |
| REG-22 | Test case review (this spec) | Stored settings were read without range checks: "NaN", undo depth −1, zoom min = max were accepted | `AppSettings.sanitized()` on every load | AUTO `LlrGapTest.settings_sanitizedKeepsValidValuesAndReplacesDamagedOnes` | LLR-SETS-020 |
| REG-23 | Test case review (this spec) | An undo stack with limit 0 still kept one step | Limit ≤ 0 keeps nothing; stack moved to pure Kotlin (`History.kt`) | AUTO `LlrGapTest.historyStack_keepsTheNewestStepsUpToItsLimit` | LLR-HIST-020 |
| REG-24 | Test case review (this spec) | A plan file with direction 1e40 gave the plot a NaN direction | Non-finite direction read as 0° | AUTO `PlanFileLimitsTest.locationSoilAndDirectionOutOfRangeAreDropped` | LLR-ORNT-010, LLR-PFILE-020 |
| REG-25 | Test case review (this spec) | Color "#FF6B35FF00" was cut to 9 characters and became a different valid color | Whole text checked before use | AUTO `PlanFileLimitsTest.customVarietyLimits` | LLR-COLR-010 |
| REG-26 | Test case review (this spec) | Trees, fences and sprinklers outside the plot were accepted from a plan file; a huge sprinkler direction gave NaN | Features must lie inside the plot (skipped with a warning); non-finite direction read as 0° | AUTO `PlanFileLimitsTest.siteFeaturesAndIrrigationLimits` | LLR-PFILE-020, LLR-WATER-010 |
| REG-27 | Test case review (this spec) | The three seed catalogs had no automated check (a bad line would only show on a phone) | Catalog data test | AUTO `CatalogDataTest.everyLineIsWellFormedAndInRange` | LLR-CATP-020, LLR-CATP-040 |
| REG-28 | Test case review (this spec) | Plan B with a negative option limit threw an exception | Limit below 0 treated as 0 | AUTO `LlrGapTest.planB_respectsTheFirstFrostAndOddLimits` | LLR-PLANB-060 |
| REG-29 | Test case review (this spec) | Asking for 0 or a negative number of layouts gave none at all | Count limited to 1–10 before planning | AUTO `LlrGapTest.layoutOptions_countLimitsAndEveryoneStarred` | LLR-OPT-010 |
| REG-30 | Owner decision on LLR-WIN-020 | `HardinessZones.number("7")` returned 7, so a bare zone not in the ZIP table still counted in the hardiness check | `number` only for labels 1a–13b | AUTO `ZipDataTest.zoneLabelsFollowTheZipTable` | LLR-WIN-020 |
| REG-31 | LLR review (VALD-090) | A plant with an unknown variety was skipped by the spacing check, so a new plant could be placed right on top of it | 0.3 m safe-guess radius; placement refused with the fixes offered | AUTO `LlrGapTest.unknownVariety_nearbyPlantIsNeverSkipped` | LLR-VALD-090 |


## 5. LLRs that do not match the code (owner decision: keep / improve / delete)

| LLR | Status | What differs |
|---|---|---|
| LLR-LIM-010 | DEV | No single constants object; the limits live next to their users (e.g. `AutoPopulateEngine.MAX_POINTS` = 20 000, plan file 5000 plants/plot, name 200 characters, plot 0.5–1000 m on the web). |
| LLR-UNIT-010 | DEV | `DistanceUnit` {METERS, INCHES}; the factor is 39.3701 in/m, not exactly 1/0.0254. |
| LLR-UNIT-030 | DEV | `format` writes no space before the suffix ("3.25m") and always uses "." as the decimal separator; inches use 2 decimals unless asked. |
| LLR-UNIT-040 | DEV | `parseToMeters` trims, accepts one "," as the decimal sign and any finite number; it accepts signs and exponents ("1e3", "-2") the LLR rejects. |
| LLR-GEO-040 | DEV | `distanceToPolyline` returns `Float.MAX_VALUE` (not +∞) for an empty list. |
| LLR-GEO-050 | DEV | `pointInPolygon` is plain even-odd ray casting with no on-edge rule; `PlotShape.contains` adds the rectangle check. |
| LLR-VALD-080 | DEV | `validatePlacement` returns lists of spacing and antagonist conflicts; boundary and path are checked by callers; no ordered single reason. |
| LLR-FILL-040 | DEV | Cap is `MAX_POINTS` = 20 000 (returns empty), not 100 000 with a TooManyCandidates error. |
| LLR-SCHED-010 | DEV | Dates are epoch milliseconds from `PlatformClock`; no injected Clock. |
| LLR-WIN-010 | DEV | Replaced by LLR-FROST-020 (`GrowingSeason.window`), which uses hardy and start-indoors weeks. |
| LLR-IRR-010 | DEV | Route starts at the plant nearest (0, 0), not at a water source; ties go to the first in list order, not the lowest id. |
| LLR-IRR-020 | DEV | Length starts at the first plant (no start leg). |
| LLR-SETS-010 | DEV | Settings are the `AppSettings` fields with keys `settings.*`; several LLR keys differ or are absent (season_end, owner_name, battery_pct, last_variety). |
| LLR-SETS-050 | DEV | Reset writes every default (including the disclaimer flag and last plan list). |
| LLR-CATP-010 | DEV | Catalog files are 14-field v1 lines with no header line; fields 15–21 are not used. |
| LLR-CATP-030 | DEV | No `species.txt` / `families.txt`; species are the code prefixes of the Pro catalog. |
| LLR-LTR-010 | DEV | Plants are labeled with short kind names ("Bell red", "Cherry") instead of 3-letter codes. |
| LLR-AUD-010 | DEV | `SecurityAuditLogger` stores free-text lines (e.g. "KEY_LOSS: …"), not typed JSON entries with seq/time. |
| LLR-CLIMQ-020 | DEV | Nearest frost station uses an equirectangular distance within 250 km (LLR-FROST-010), not haversine within 50 km. |
| LLR-AUDS-010 | DEV | `SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: encrypted audit files. |
| LLR-AUDS-030 | DEV | `SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: rotation at 1 MB. |
| LLR-AUDS-040 | DEV | `SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: read and verify all files. |
| LLR-AUDS-050 | DEV | `SecurityAuditLogger` keeps one plain hash-chained file in app storage (not encrypted, no rotation). Target: new chain after key loss. |
| LLR-CATI-020 | DEV | No hash check; bundled data is validated by unit tests in CI instead. |
| LLR-CATI-050 | DEV | No species/families files (see LLR-CATP-030). |
| LLR-CATI-060 | DEV | Climate data is the text table `zip_data.txt` plus `frost_stations.txt`, not `climate.db`. |
| LLR-REPO-060 | DEV | Undo history is in memory only (lost when leaving the plot). |
| LLR-DRAW-050 | DEV | Ruler ticks every `rulerTickIntervalM` (setting), numbers only with the unit once (LLR-RULER-010); no 1-2-5 rounding by zoom. |
| LLR-LAYV-100 | DEV | Overlays: sun/shade and water coverage; no weed-risk or route overlay, no water-source marker. |
| LLR-LAYV-130 | DEV | Perennials outside the zone are blocked (`blocksPlacement`) rather than warned after placement. |
| LLR-FILLV-030 | DEV | Tier gating differs: features follow `FeatureFlags` minimum tiers. |
| LLR-AUTOP-010 | OBS | Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: square candidate grid. |
| LLR-AUTOP-020 | OBS | Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: sunward depth targets. |
| LLR-AUTOP-030 | OBS | Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: plant-by-plant placement order. |
| LLR-AUTOP-040 | OBS | Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: score terms. |
| LLR-AUTOP-050 | OBS | Describes the plant-by-plant planner removed 2026-09-28; both layouts now run `BlockPlanner` (LLR-BLK-010/050). Recommend delete or rewrite. Former subject: result notes. |

LLRs with status TGT (29) describe the Phase-1 target design and have no code yet; their cases stay PLANNED until the LLR is implemented, rewritten or deleted (SGP-GAP-001 §4.3, SGP-DWR-001 WP-03).

