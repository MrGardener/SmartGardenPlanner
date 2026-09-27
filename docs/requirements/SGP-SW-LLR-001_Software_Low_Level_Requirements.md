# SGP-SW-LLR-001 — Software Low-Level Requirements (LLR)

| Field | Value |
|---|---|
| Document ID | SGP-SW-LLR-001 |
| Revision | A — Proposed |
| Level | LLR (DO-178C software low-level requirements) |
| Parent document | SGP-SW-HLR-001 |
| Supersedes | All v20.18 LLRs (retired; no trace kept, per Project Owner direction) |
| Control category | CC1 |

## How to read this document

- LLRs are grouped by **design component** (§1). Each LLR is detailed enough to write the code and a
  test without further information.
- **`↑ HLR-…`** at the end of each LLR names the HLR(s) it implements. §9 has the generated table and the
  coverage check.
- LLRs for **[Future]** HLRs (export/import, corner correction) and **[Suspended]** HLRs (measurement) are
  written when those features are scheduled. The coverage check lists them as intentionally deferred.
- `[L-TBC-nn]` refers to this document's confirmation list (§8). `[TBC-nn]` and `[H-TBC-nn]` refer to the
  system and HLR lists. Proposed values apply until changed.
- Units unless stated: metres (m), days (d), UTC milliseconds for timestamps, density-independent pixels
  (dp) for screen sizes. All `:core` arithmetic is double precision.

---

## 1. Architecture summary (context for the LLRs)

Full design description: SGP-SDD-001 (to be written, DW-0401). The LLRs assume this structure:

| Module | Component (code prefix) | Responsibility |
|---|---|---|
| `:core` (pure Kotlin/JVM) | UNIT, GEO, VALD, FILL, SCHED, SEASON, PLANB, WIN, REC, IRR, WEED, HIST, SETS, CATP, TIER, COLR, LTR, AUD, CLIMQ, LIM | Domain model and all rules/calculations. No Android, no I/O. |
| `:data` | DB, MIG, KEY, PHOTO, AUDS, SETST, CATI, REPO, START, BKP | Encrypted persistence, key handling, files, catalog installation, repositories, start-up sequence. |
| `:app` | NAVG, STATE, XFORM, GEST, DRAW, LIST, EDIT, LAYV, DET, PICK, FILLV, ENCV, SETV, CAMV, SENS, LOCS, CAPS, A11Y, DISP, ERRV, ABOUT, UNRD | Compose UI, ViewModels, device services. |

Dependencies are one-way (`:app → :data → :core`). A build check enforces this (LLR-LIM-020).

---

## 2. `:core` — domain rules and calculations

### 2.1 Limits and module rules (LIM)

**LLR-LIM-010** `:core` shall define these constants, and every component shall use them instead of
literals:

| Constant | Value |
|---|---|
| MAX_PLANTS_PER_PLOT | 500 `[TBC-16]` |
| MAX_PATHS_PER_PLOT | 100 `[H-TBC-07]` |
| MAX_POINTS_PER_SHAPE | 200 `[H-TBC-02]` |
| MIN_SHAPE_SIDE_M | 0.05 |
| DEFAULT_PATH_WIDTH_M | 0.5 |
| SPACING_TOLERANCE_M | 0.0001 `[TBC-20]` |
| ANTAGONIST_FACTOR | 2.0 `[TBC-23]` |
| HIT_TOLERANCE_DP | 24 |
| NAME_MAX_CHARS | 60 |
| UNUSUAL_MIN_M | 0.30 `[TBC-18]` |
| UNUSUAL_MAX_M | 200.0 `[TBC-18]` |
| MAX_FILL_CANDIDATES | 100 000 `[L-TBC-01]` |
| PLANTING_DATE_MAX_FUTURE_DAYS | 365 `[TBC-02]` |

↑ HLR-PLC-110, HLR-STOR-060, HLR-RULE-020, HLR-RULE-050, HLR-PLC-040, HLR-PLOT-030, HLR-PLOT-050, HLR-AREA-010, HLR-AREA-020, HLR-PLC-050

**LLR-LIM-020** `:core` shall be a Kotlin/JVM library module with no dependency on Android or AndroidX
artifacts. A build check shall fail the build if one is added, or if `:core` depends on `:data` or `:app`.
↑ HLR-PLTN-010

**LLR-LIM-030** Every public model class in `:core` shall document each field's meaning, type, unit and
allowed range in its KDoc. ↑ HLR-PLTN-020

### 2.2 Units (UNIT)

**LLR-UNIT-010** `DisplayUnit` shall have exactly two values, METERS and INCHES.
`METERS_PER_INCH = 0.0254` (exact). ↑ HLR-NAV-090

**LLR-UNIT-020** `toDisplay(m, unit)` shall return `m` for METERS, and `m / 0.0254` for INCHES.
`toMeters(v, unit)` shall be the exact inverse. ↑ HLR-NAV-090

**LLR-UNIT-030** `formatDistance(m, unit, locale)` shall return the display value rounded half-up, followed
by a space and the suffix:
- METERS: 2 decimals, suffix "m"
- INCHES: 1 decimal, suffix "in"

The decimal separator shall be the locale's. ↑ HLR-NAV-090, HLR-RULE-090, HLR-LAY-040

**LLR-UNIT-040** `parseDistance(text, unit)` shall:
- trim the text
- accept only digits with at most one decimal separator ("." or ",") and at least one digit
- reject signs, exponents, grouping separators, empty text and more than 12 characters

It returns meters (via LLR-UNIT-020), or `null` if rejected. ↑ HLR-PLOT-030, HLR-PLOT-040, HLR-NAV-090

**LLR-UNIT-050** `formatArea(m2, unit)` shall return m² with 2 decimals for METERS, and ft² with 1 decimal
for INCHES, where ft² = m² / 0.09290304. ↑ HLR-VIEW-080

### 2.3 Geometry (GEO)

**LLR-GEO-010** `PointM(x, y)` shall hold double coordinates in metres, relative to the plot's top-left
corner. x runs along the plot length (to the right) and y along the plot width (downwards).
↑ HLR-RULE-010, HLR-LAY-010

**LLR-GEO-020** `distance(a, b)` shall return `hypot(a.x − b.x, a.y − b.y)`. ↑ HLR-RULE-020

**LLR-GEO-030** `distanceToRect(p, rect)` shall return 0 when p is inside or on the rectangle. Otherwise it
returns the distance from p to the closest point of the rectangle, i.e. p clamped to the rectangle's x and y
ranges. ↑ HLR-RULE-030

**LLR-GEO-040** `distanceToPolyline(p, points)` shall return:
- `+∞` for an empty list
- the distance to the single point for a 1-point list
- otherwise, the minimum over consecutive segments of the distance from p to the segment, with a
  zero-length segment treated as a point

↑ HLR-RULE-030, HLR-AREA-030

**LLR-GEO-050** `insidePolygon(p, polygon)` shall return `false` for fewer than 3 vertices. It shall return
`true` if p lies on an edge (distance to the edge ≤ 1×10⁻⁹ m). Otherwise it applies the even-odd
ray-casting rule. ↑ HLR-AREA-080

**LLR-GEO-060** `clipRect(rect, length, width)` shall return the intersection of `rect` with
[0, length] × [0, width], or `null` if the intersection has zero area. ↑ HLR-AREA-010

**LLR-GEO-070** `boundingBox(points)` shall return the smallest axis-aligned rectangle containing all the
points. ↑ HLR-AREA-080

**LLR-GEO-080** `normalizeRect(a, b)` shall build a rectangle from two drag corners in any order: left =
min(x), top = min(y), width = |Δx|, height = |Δy|. ↑ HLR-AREA-010, HLR-AREA-050

### 2.4 Placement validation (VALD)

**LLR-VALD-010** `isInside(p, plot)` shall be `0 ≤ p.x ≤ plot.lengthM && 0 ≤ p.y ≤ plot.widthM`.
↑ HLR-RULE-010

**LLR-VALD-020** `requiredSpacing(rA, rB, margin) = (rA + rB) × margin`.
`spacingConflict(d, required) = d < required − SPACING_TOLERANCE_M`. ↑ HLR-RULE-020

**LLR-VALD-030** `pathConflict(p, r, path)` shall be:
- RECT paths: `distanceToRect(p, path.rect) < r`
- LINE paths: `distanceToPolyline(p, path.points) < path.widthM / 2 + r`

↑ HLR-RULE-030

**LLR-VALD-040** `speciesCode(code)` shall return the part of `code` before the first "-", or the whole code
if there is no "-". An antagonist list shall be parsed by splitting on ",", trimming each item and dropping
empty items. Comparison is exact (case-sensitive). ↑ HLR-RULE-040, HLR-ENC-060

**LLR-VALD-050** `areAntagonists(A, B)` shall be true when `speciesCode(B)` is in A's antagonist list, or
`speciesCode(A)` is in B's. ↑ HLR-RULE-040

**LLR-VALD-060** `companionConflict(A, B, d, margin)` shall be
`areAntagonists(A, B) && d < ANTAGONIST_FACTOR × requiredSpacing(rA, rB, margin)`. ↑ HLR-RULE-050

**LLR-VALD-070** `companionEnforced(tier, setting)` shall return `setting` when tier = PRO, and `true`
otherwise. ↑ HLR-RULE-060, HLR-SET-050

**LLR-VALD-080** `validate(request)` shall take:
- the plot
- its paths, in stored order
- the other plants (optionally excluding one plant id)
- a variety lookup
- the spacing margin
- companion enforcement
- the candidate position and variety

It shall return `Accepted`, or `Rejected(reason, conflictPlantId?, actualM?, requiredM?)`, evaluating in
this order:
1. BOUNDARY, per LLR-VALD-010
2. PATH: the first conflicting path in stored order
3. SPACING: among all plants in spacing conflict, the one with the smallest distance (ties: lowest id)
4. COMPANION, only when enforced: the same selection among plants in companion conflict

↑ HLR-RULE-080, HLR-RULE-090, HLR-PLC-020, HLR-PLC-060, HLR-PLC-080, HLR-TIME-050, HLR-TIME-060, HLR-AREA-070

**LLR-VALD-090** If the variety of the candidate, or of any plant being compared, isn't found by the lookup,
`validate` shall return `Rejected(DATA_INCONSISTENT)` (error E-DATA-005) rather than skip the plant.
↑ HLR-RULE-080, HLR-STOR-050

**LLR-VALD-100** `conflictFlags(plants, lookup, margin)` shall return the set of plant ids that appear in at
least one pair satisfying LLR-VALD-060, evaluating every unordered pair once. ↑ HLR-RULE-070, HLR-LAY-060

### 2.5 Area fill (FILL)

**LLR-FILL-010** The fill spacing shall be `s = 2 × radius × margin`. If `s ≤ 0`, no candidates are
produced. ↑ HLR-AREA-080

**LLR-FILL-020** For a bounding box [x0, x1] × [y0, y1] with width w and height h:
- Column positions are x0 + s/2 + i·s for i ≥ 0 while the position ≤ x1 − s/2 + 1×10⁻⁹.
- If w < s, there is a single column at x0 + w/2.
- Rows are built the same way along y.
- **Rows pattern:** every row uses the same columns.
- **Hexagonal pattern:** row pitch = s·√3/2, starting at y0 + s/2 (or a single row at y0 + h/2 if h < s),
  and odd rows are offset by +s/2 in x (keeping only positions ≤ x1 − s/2 + 1×10⁻⁹).

↑ HLR-AREA-080

**LLR-FILL-030** For a polygon area, the bounding box is the polygon's (LLR-GEO-070), and only candidates
with `insidePolygon` true are kept. ↑ HLR-AREA-080, HLR-AREA-060

**LLR-FILL-040** Candidates shall be produced lazily, in row-major order (increasing y, then increasing x).
If more than MAX_FILL_CANDIDATES candidates would be produced, the planner shall return
`TooManyCandidates` (E-DATA-006) without evaluating any. ↑ HLR-AREA-070, HLR-PERF-020

**LLR-FILL-050** `planFill(...)` shall evaluate the candidates in order with `validate` (LLR-VALD-080),
comparing against the existing plants plus the candidates already accepted in this plan. It shall stop
accepting when existing + accepted = MAX_PLANTS_PER_PLOT. It shall return:
- the accepted positions
- the total candidate count M
- skipped counts per rejection reason
- whether it was truncated by the plant limit

↑ HLR-AREA-070, HLR-AREA-090, HLR-PLC-110, HLR-RULE-080

**LLR-FILL-060** The preview count shown before filling, and the number stored by "Fill", shall both come
from the same `planFill` call with the same inputs. ↑ HLR-AREA-070, HLR-AREA-090

### 2.6 Dates and germination (SCHED)

**LLR-SCHED-010** Dates shall be `java.time.LocalDate`. "Today" shall come only from an injected `Clock`,
evaluated in the device's current time zone. ↑ HLR-TIME-010

**LLR-SCHED-020** `harvestDate(planting, daysToHarvest) = planting.plusDays(daysToHarvest)`. ↑ HLR-TIME-020

**LLR-SCHED-030** `daysSince(planting, today) = ChronoUnit.DAYS.between(planting, today)` (negative for
future dates). ↑ HLR-TIME-030

**LLR-SCHED-040** `isOverdue(plant, variety, today) = daysSince > variety.germinationDays && plant.outcome == NONE`.
The outcome values are NONE and GERMINATED. ↑ HLR-TIME-030, HLR-LAY-050

**LLR-SCHED-050** The allowed planting-date range shall be [1970-01-01, today + PLANTING_DATE_MAX_FUTURE_DAYS]
inclusive. Dates outside it are rejected. ↑ HLR-PLC-050

### 2.7 Remaining season (SEASON)

**LLR-SEASON-010** `dateOfDayOfYear(year, doy)` shall return that date. For doy = 366 in a non-leap year it
returns 31 December, and for a season-end of 29 February in a non-leap year it returns 28 February.
↑ HLR-TIME-080, HLR-TIME-090

**LLR-SEASON-020** `remainingSeasonDays(today, climate?, seasonEnd)`:
- if climate is present and this year's first-frost date ≥ today: the days from today to it
- otherwise, if this year's season-end date ≥ today: the days to it
- otherwise: 0

↑ HLR-TIME-080

### 2.8 Plan B (PLANB)

**LLR-PLANB-010** Fast-track: among varieties V ≠ failed with the same species code,
`V.daysToHarvest < failed.daysToHarvest` and `V.daysToHarvest ≤ remaining`, choose the smallest
daysToHarvest. Ties are broken by name (case-insensitive), then code. If there is none, apply the same
selection to varieties with the same botanical family, unless the family is "Other" `[L-TBC-02]`.
↑ HLR-TIME-050, HLR-TIME-070

**LLR-PLANB-020** Nursery transplant shall be offered when `failed.transplantSuitable` is true.
↑ HLR-TIME-050

**LLR-PLANB-030** Catch crop: among varieties of a different species code with
`daysToHarvest ≤ remaining`, for which `validate` accepts the failed plant's position (excluding the failed
plant), sort by daysToHarvest descending, then name, and take up to 3. Evaluation stops once 3 are found.
↑ HLR-TIME-050, HLR-TIME-070

**LLR-PLANB-040** If remaining ≤ 0, fast-track and catch-crop shall not be computed, and the result carries
the reason SEASON_OVER. ↑ HLR-TIME-050

**LLR-PLANB-050** Applying an option shall produce the same plant (same id and position) with the chosen
variety, planting date = today and outcome = NONE. For fast-track and catch-crop the result must pass
`validate` (excluding itself); otherwise it is rejected with that validation result. ↑ HLR-TIME-060

### 2.9 Planting windows and recommendations (WIN, REC)

**LLR-WIN-010** `plantingWindow(variety, climate, year)`: start = last-frost date + variety.sowOffsetDays;
end = first-frost date − daysToHarvest. The result is:
- EMPTY if end < start
- TOO_LATE if end < today
- OPEN if start ≤ today ≤ end
- UPCOMING if today < start

↑ HLR-TIME-090

**LLR-WIN-020** `zoneNumber(zone)` shall accept strings matching `^(1[0-3]|[1-9])[ab]?$` and return the
number. Anything else returns null, and the zone features treat the location as unknown. ↑ HLR-TIME-100

**LLR-REC-010** `recommend(varieties, zone, climate, today)` shall return the varieties with
zoneMin ≤ zone ≤ zoneMax and a window of OPEN or UPCOMING, sorted by name. ↑ HLR-TIME-100

**LLR-REC-020** `perennialZoneWarning(variety, zone)` shall be true when
`variety.lifecycle == PERENNIAL` and zone is outside [zoneMin, zoneMax]. ↑ HLR-TIME-100

### 2.10 Irrigation route (IRR)

**LLR-IRR-010** `route(start, plants)` shall return the plants in visiting order: from the current point
(initially `start`), repeatedly take the unvisited plant at the smallest distance, with ties broken by the
lowest plant id. An empty plant list gives an empty route. ↑ HLR-VIEW-090

**LLR-IRR-020** `routeLength(start, route)` = distance(start, first) + the sum of distances between
consecutive plants, or 0 for an empty route. ↑ HLR-VIEW-090

### 2.11 Weed-risk area (WEED)

**LLR-WEED-010** `weedRiskArea(plot, plants, paths, lookup)` shall estimate the uncovered area on a grid:
- cell size c = max(length, width) / 400, with cells covering the plot (edge cells clipped to it)
- a cell is covered when its centre is within some plant's spacing circle (d ≤ r), inside a RECT path, or
  within width/2 of a LINE path
- the area = sum of the areas of uncovered cells

Only cells inside each shape's bounding box are tested against that shape. ↑ HLR-VIEW-080

**LLR-WEED-020** The estimate of LLR-WEED-010 shall be within 1 % of the plot area of the exact value, for the
verification cases in the SVP. `[L-TBC-03]` ↑ HLR-VIEW-080

### 2.12 Undo history (HIST)

**LLR-HIST-010** `Snapshot` shall be an immutable pair (plants sorted by id, paths sorted by id), with every
field of each plant and path. ↑ HLR-HIST-020

**LLR-HIST-020** `UndoHistory(capacity)` shall keep two stacks:
- `record(before)`: pushes `before` on the undo stack, drops the oldest entry if the size exceeds capacity,
  and clears the redo stack.
- `undo(current)`: pops the undo stack, pushes `current` on the redo stack, and returns the popped snapshot
  (or null if empty).
- `redo(current)` is symmetric.

↑ HLR-HIST-010, HLR-HIST-020, HLR-HIST-030

**LLR-HIST-030** `setCapacity(n)` shall drop the oldest undo entries until size ≤ n. Redo entries are also
limited to n. ↑ HLR-HIST-030

**LLR-HIST-040** `canUndo` and `canRedo` shall be true exactly when the respective stack is non-empty.
↑ HLR-HIST-030

### 2.13 Settings specification (SETS)

**LLR-SETS-010** `:core` shall define, for every setting in HLR-SET-020: a key, type, minimum, maximum, step
and default. Keys are: `unit`, `tier`, `spacing_margin`, `enforce_companion`, `ruler_tick_m`, `ruler_text_sp`,
`zoom_min`, `zoom_max`, `zoom_step`, `undo_depth`, `plot_min_m`, `plot_max_m`, `loc_accuracy_m`, `light_lux`,
`storage_floor_pct`, `battery_pct`, `season_end` (MM-DD), `owner_name`. The internal key `last_variety` holds the
last selected variety code. ↑ HLR-SET-020, HLR-PLC-090

**LLR-SETS-020** `decode(key, text)` shall return the parsed value when it is well-formed and within
[min, max]. Otherwise it returns the setting's default, independently of every other key. ↑ HLR-SET-030

**LLR-SETS-030** `quantize(key, value)` shall round a numeric value to the nearest multiple of the step
(half-up) from the minimum, then clamp to [min, max]. ↑ HLR-SET-020

**LLR-SETS-040** `checkChange(current, key, value)` shall reject (E-SET-001) a change that would make
`zoom_min ≥ zoom_max` or `plot_min_m ≥ plot_max_m`. ↑ HLR-SET-020

**LLR-SETS-050** `defaults()` shall return the default value of every key except `last_variety` and
`owner_name`, which are left unchanged by a reset. ↑ HLR-SET-040

### 2.14 Catalog format and validation (CATP)

**LLR-CATP-010** A catalog file shall be UTF-8 text, with one variety per line and "|"-separated fields.
The first line is a header `#SGP-CATALOG v2 tier=<BASIC|STANDARD|PRO> source=<text>`. Fields, in order:
1. code
2. name
3. family
4. category
5. lifecycle
6. zoneMin
7. zoneMax
8. radiusM
9. germinationDays
10. daysToHarvest
11. companions
12. antagonists
13. pests
14. care
15. watering
16. soilMinC
17. soilMaxC
18. depthCm
19. light
20. transplant
21. sowOffsetDays

Fields 15–21 may be empty (meaning "not on file"; transplant then = Y and sowOffsetDays = 0). A 14-field line
(format v1) shall be read as if fields 15–21 were empty. ↑ HLR-ENC-010, HLR-ENC-050, HLR-ENC-100

**LLR-CATP-020** Validation rules for an entry:

| Field | Rule |
|---|---|
| code | 2–20 characters matching `^[A-Z0-9]+(-[A-Z0-9]+)*$`, unique |
| name | 1–60 characters |
| family | in `families.txt` |
| category | ∈ {VEGETABLE, FRUIT, HERB, FLOWER, ORNAMENTAL} |
| lifecycle | ∈ {ANNUAL, PERENNIAL, BIENNIAL} |
| zones | integers 1 ≤ zoneMin ≤ zoneMax ≤ 13 |
| radiusM | 0.02–10 |
| germinationDays | integer 1–365 |
| daysToHarvest | integer 1–3650 |
| companions, antagonists | every code in `species.txt` |
| soil temperatures | −10…50 °C, with soilMin ≤ soilMax when both are present |
| depthCm | 0–30 |
| light | ∈ {FULL_SUN, PART_SHADE, SHADE} or empty |
| transplant | ∈ {Y, N} or empty |
| sowOffsetDays | integer −120…120, or empty |

Validation shall report every violation with its line number and field. ↑ HLR-ENC-100, HLR-ENC-070

**LLR-CATP-030** `species.txt` (lines `code|name`) and `families.txt` (one name per line, including "Other")
shall be bundled. Every species code used by any tier, as a variety prefix or in a companion/antagonist list,
shall appear in `species.txt`. ↑ HLR-ENC-060, HLR-ENC-100

**LLR-CATP-040** A build task shall validate the three tier files, `species.txt` and `families.txt`, and fail
the build on any violation. It shall also check that:
- the tier counts are 253, 603 and 2,939
- Basic ⊆ Standard ⊆ Pro, by code
- every entry's content is identical across the tiers containing it

↑ HLR-ENC-100, HLR-ENC-010

**LLR-CATP-050** The build task shall generate a manifest with the SHA-256 of each catalog, species, family
and climate asset. ↑ HLR-ENC-100, HLR-CLIM-010

**LLR-CATP-060** `cultivarName(name)` = the text after the first " - ", or the whole name.
`speciesName(code)` = the name for `speciesCode(code)` in `species.txt`, or the text before " - " if absent.
↑ HLR-PLC-100, HLR-ENC-060

### 2.15 Tier switch planning (TIER)

**LLR-TIER-010** `planTierSwitch(currentBundled, newEntries, plantedCodes, userOwnedCodes)` shall return:
- upserts = new entries whose code is not user-owned
- removals = current bundled codes that are not in the new tier, not planted and not user-owned
- kept = current bundled codes that are not in the new tier but are planted

↑ HLR-ENC-020, HLR-ENC-030

### 2.16 Colours and letter codes (COLR, LTR)

**LLR-COLR-010** The 16 preset colours shall be: #EF4444, #F97316, #F59E0B, #EAB308, #84CC16, #22C55E,
#10B981, #14B8A6, #06B6D4, #0EA5E9, #3B82F6, #6366F1, #8B5CF6, #A855F7, #D946EF, #EC4899.
A user colour is stored as one of these strings. ↑ HLR-VIEW-070

**LLR-COLR-020** The automatic colour shall be HSV(h, 0.65, 0.90), where h = ((H mod 360) + 360) mod 360, and H
is the variety code's hash as defined by `java.lang.String.hashCode` (deterministic across runs). ↑ HLR-VIEW-070

**LLR-LTR-010** `letterCodes(varieties on plot)` shall process the varieties sorted by code. Each gets the
first candidate not already used on the plot, from:
1. S1
2. S1S2
3. S1C1
4. S1C1C2
5. S1 followed by 2, 3 … 99

S = the uppercase letters/digits of the species name, and C = those of the cultivar name. Every candidate is
truncated to 3 characters. ↑ HLR-NAV-080, HLR-VIEW-060

### 2.17 Audit chain (AUD)

**LLR-AUD-010** An audit entry shall contain:
- seq: 1, 2, … continuous across files
- time: UTC, ISO-8601 with milliseconds
- type ∈ {DATA_RESET, KEY_LOSS, BATTERY_OVERRIDE, TIER_SWITCH, IMPORT_ACCEPTED, IMPORT_REJECTED, LOG_ROTATED}
- details: a JSON object of ≤ 1024 bytes
- prevHash
- hash

↑ HLR-PROT-060

**LLR-AUD-020** hash = lowercase hex SHA-256 of the UTF-8 string `prevHash|seq|time|type|details`. The first
entry ever uses 64 × "0" as prevHash. ↑ HLR-PROT-060

**LLR-AUD-030** `verify(entries)` shall recompute each hash in order, and check prevHash linkage and seq
continuity. It returns INTACT, or the seq of the first failing entry. ↑ HLR-PROT-070

### 2.18 Climate lookup (CLIMQ)

**LLR-CLIMQ-010** `byZip(zip)` shall accept exactly 5 ASCII digits, and return the record (zone,
lastFrostDoy, firstFrostDoy, centroid lat/lon) or null. ↑ HLR-PLOT-110, HLR-CLIM-010

**LLR-CLIMQ-020** `nearest(lat, lon)` shall return the record whose centroid has the smallest haversine
distance (Earth radius 6371.0088 km), if that distance ≤ 50 km `[H-TBC-06]`, and otherwise null. ↑ HLR-CLIM-020

**LLR-CLIMQ-030** Latitude outside −90…90 or longitude outside −180…180 shall be rejected before lookup.
↑ HLR-PLOT-110

---

## 3. `:data` — persistence, keys and files

### 3.1 Database schema (DB)

**LLR-DB-010** The database shall contain these tables (SQLite column types):
- `plots(id INTEGER PK AUTOINCREMENT, uuid TEXT UNIQUE NOT NULL, name TEXT NOT NULL, length_m REAL NOT NULL,
  width_m REAL NOT NULL, archived INTEGER NOT NULL DEFAULT 0, zip TEXT, lat REAL, lon REAL,
  water_x_m REAL NOT NULL DEFAULT 0, water_y_m REAL NOT NULL DEFAULT 0, photo_file TEXT, owner_name TEXT,
  role TEXT NOT NULL DEFAULT 'OWNER', created_utc INTEGER NOT NULL, modified_utc INTEGER NOT NULL)`
- `varieties(code TEXT PK, name, family, category, lifecycle TEXT NOT NULL, zone_min, zone_max,
  germination_days, days_to_harvest INTEGER NOT NULL, radius_m REAL NOT NULL, companions, antagonists, pests,
  care, watering TEXT NOT NULL DEFAULT '', soil_min_c REAL, soil_max_c REAL, depth_cm REAL, light TEXT,
  transplant INTEGER NOT NULL DEFAULT 1, sow_offset_days INTEGER NOT NULL DEFAULT 0, color TEXT,
  source TEXT NOT NULL CHECK(source IN ('BUNDLED','USER')), tier TEXT)`
- `plants(id INTEGER PK AUTOINCREMENT, plot_id INTEGER NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
  variety_code TEXT NOT NULL REFERENCES varieties(code) ON DELETE RESTRICT, x_m REAL NOT NULL,
  y_m REAL NOT NULL, planting_date TEXT NOT NULL, outcome TEXT NOT NULL DEFAULT 'NONE',
  created_utc INTEGER NOT NULL)`, with indexes on plot_id and variety_code
- `paths(id INTEGER PK AUTOINCREMENT, plot_id INTEGER NOT NULL REFERENCES plots(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK(kind IN ('RECT','LINE')), x_m, y_m, w_m, h_m REAL, points TEXT, width_m REAL)`,
  with an index on plot_id
- `settings(key TEXT PK, value TEXT NOT NULL)`
- `undo_entries(plot_id INTEGER REFERENCES plots(id) ON DELETE CASCADE, stack TEXT CHECK(stack IN ('UNDO','REDO')),
  seq INTEGER, snapshot TEXT NOT NULL, PRIMARY KEY(plot_id, stack, seq))`

↑ HLR-STOR-020, HLR-STOR-040, HLR-PLOT-060, HLR-VIEW-100, HLR-HIST-050, HLR-PLTN-020

**LLR-DB-020** `planting_date` shall be stored as ISO `yyyy-MM-dd`. LINE `points` shall be stored as
`x,y;x,y;…`, with "." decimals, in metres, and at most MAX_POINTS_PER_SHAPE pairs. ↑ HLR-STOR-020, HLR-TIME-010

**LLR-DB-030** Foreign-key enforcement shall be on for every connection. ↑ HLR-STOR-040

**LLR-DB-040** The schema version shall be 8. Room's schema export shall be enabled, and the exported
schema committed. ↑ HLR-STOR-030

**LLR-DB-050** The database shall be encrypted with SQLCipher 4 using the 32-byte key from LLR-KEY-020. The
file name shall be `sgp.db`. ↑ HLR-PROT-010

**LLR-DB-060** Every statement that changes a plot's name, dimensions, location, water source, photo,
archived flag, plants or paths shall set that plot's `modified_utc` to the current UTC time, in the same
transaction. ↑ HLR-PLOT-010, HLR-PLOT-060

**LLR-DB-070** The DAOs shall provide observable queries (Flow) for:
- non-archived plots with plant counts, ordered by `modified_utc` descending
- all plots including archived
- a plot by id
- the plants of a plot
- the paths of a plot
- all varieties
- a variety by code
- the plots using a variety code

↑ HLR-PLOT-010, HLR-PLOT-100, HLR-ENC-040, HLR-ENC-090

**LLR-DB-080** Database access shall never run on the main thread. ↑ HLR-PERF-010, HLR-PERF-020

### 3.2 Migration (MIG)

**LLR-MIG-010** A migration 7 → 8 shall convert the v20.20 schema, preserving all rows:

| v20.20 (schema 7) | Schema 8 |
|---|---|
| `plots` (lengthM, widthM, description, imagePath, locationZip, ownerRole, timestamps) | `plots`: generate a random UUID per row; zip = locationZip; photo_file = null (old unencrypted photos aren't carried over, as none were ever created by the UI); role = ownerRole |
| `seeds` | `varieties`: isCustom → source (1 → USER, 0 → BUNDLED); colorHex → color; missing new fields → defaults |
| `planted_nodes` | `plants`: datePlantedEpochMillis → local date in the device time zone at migration time; germinationFlagResolved → outcome (1 → GERMINATED); coordinates copied |
| `path_zones` | `paths`: pathType → kind; pointsJson → points |
| `app_configurations` rows `settings.*` | `settings`, with keys renamed per LLR-SETS-010 |
| `climate_zones` | dropped (replaced by the climate asset, LLR-CATI-060) |

↑ HLR-STOR-030

**LLR-MIG-020** The migration shall run in one transaction. On failure, the database shall stay at
version 7, unchanged, and E-DATA-002 shall be reported. ↑ HLR-STOR-030, HLR-STOR-050

**LLR-MIG-030** A stored version higher than 8 shall not be opened for writing. E-DATA-003 shall be reported,
and no file shall be modified. ↑ HLR-STOR-030

**LLR-MIG-040** Versions below 7 shall be treated as unsupported: E-DATA-003, same handling as
LLR-MIG-030. `[TBC-14]` ↑ HLR-STOR-030

### 3.3 Keys (KEY)

**LLR-KEY-010** The software shall use three keystore keys: `sgp_db_wrap_v2`, `sgp_photo_v1` and
`sgp_audit_v1`. Each is AES-256, purpose encrypt/decrypt, GCM, non-exportable, with no user authentication.
StrongBox is requested, with a fallback to the TEE when StrongBox is unavailable. ↑ HLR-PROT-010, HLR-PROT-020

**LLR-KEY-020** On first start, the database key shall be 32 bytes from `SecureRandom.nextBytes`. It shall be
stored in `files/db_key.bin` as: version byte 0x02, a 12-byte IV, then the AES-GCM ciphertext with a 16-byte tag,
using `sgp_db_wrap_v2` and AAD `sgp-db-key-v2`. ↑ HLR-PROT-010

**LLR-KEY-030** Unwrapping shall report KEY_LOSS when any of these happens:
- the alias is missing
- the key is permanently invalidated
- tag verification fails
- the file is shorter than 29 bytes
- the version byte isn't 0x02
- `db_key.bin` is missing while `sgp.db` exists

When neither file exists, it is a fresh install (LLR-KEY-020). ↑ HLR-PROT-040

**LLR-KEY-040** The unwrapped key array shall be overwritten with zeros after it has been passed to the
database. The key shall never be written to a log. ↑ HLR-PROT-010

**LLR-KEY-050** Migration from v20.20: if `files/sgp_db_key.enc` exists (format IV[12] | ciphertext under alias
`SGP_SECURE_ENCLAVE_KEY`), the software shall unwrap it, write `db_key.bin` per LLR-KEY-020 with the same
key bytes, and delete the old file only after LLR-MIG-010 succeeds. ↑ HLR-STOR-030, HLR-PROT-010

### 3.4 Photos (PHOTO)

**LLR-PHOTO-010** Photo processing shall decode the source at a size such that the longest side ≤ 1080 px,
preserving the aspect ratio and applying the stored orientation, without ever holding a full-resolution bitmap
larger than 1080 px on its longest side. ↑ HLR-CAM-030

**LLR-PHOTO-020** Compression shall use JPEG at quality 85, 80, …, 60, stopping at the first result
≤ 1 048 576 bytes. If none fits, it fails with E-IMG-002. ↑ HLR-CAM-030

**LLR-PHOTO-030** Storage shall:
1. encrypt the JPEG with `sgp_photo_v1` (file = 12-byte IV | ciphertext + tag)
2. write it to `files/photos/<uuid>.tmp`
3. rename it to `<uuid>.jpg.enc`
4. set `plots.photo_file` in a transaction
5. then delete the previous photo file

On failure at any step, the temporary file is deleted, the previous photo is kept, and E-IMG-003 is reported.
↑ HLR-CAM-030, HLR-CAM-040, HLR-PROT-020

**LLR-PHOTO-040** Decode errors and `OutOfMemoryError` during processing shall cancel it with E-IMG-001,
keeping the previous photo. ↑ HLR-CAM-040

**LLR-PHOTO-050** Removing a photo shall clear `photo_file` in a transaction, then delete the file.
Deleting a plot shall delete its photo file after the transaction commits. ↑ HLR-CAM-050, HLR-PLOT-090

**LLR-PHOTO-060** At start, files in `files/photos/` not referenced by any plot shall be deleted. ↑ HLR-STOR-040

**LLR-PHOTO-070** For display, the photo shall be decrypted in memory and decoded at no more than the
on-screen plot size. On failure the layout is drawn without it, with a notice (E-IMG-001). ↑ HLR-CAM-090, HLR-LAY-030

### 3.5 Audit log store (AUDS)

**LLR-AUDS-010** Audit files shall be `files/audit/audit-<n>.log`, where n = 1, 2, …. Each line is the Base64
encoding of IV[12] | AES-GCM(JSON of one LLR-AUD-010 entry), using `sgp_audit_v1`. ↑ HLR-PROT-060, HLR-PROT-020

**LLR-AUDS-020** Appending shall read the last entry of the highest-numbered file, build the new entry per
LLR-AUD-020, write the line and flush it to storage before returning. ↑ HLR-PROT-060

**LLR-AUDS-030** Before appending, if the current file exceeds 1 048 576 bytes `[H-TBC-08]`, a new file n + 1
shall be started, with a first entry of type LOG_ROTATED whose details hold the previous file name and its last
hash. ↑ HLR-PROT-080

**LLR-AUDS-040** Reading shall decrypt all files in order and pass the entries to LLR-AUD-030. Lines that
can't be decrypted make the result "unreadable at seq n". ↑ HLR-PROT-070

**LLR-AUDS-050** If `sgp_audit_v1` is lost, a new key and a new file shall be created. Its first entry is
KEY_LOSS with details `{"log":"previous audit files unreadable"}`, and it starts a new chain. ↑ HLR-PROT-060

**LLR-AUDS-060** The audit log is stored outside the database, so that KEY_LOSS and DATA_RESET can be
recorded when the database can't be read. ↑ HLR-PROT-060, HLR-PROT-040

### 3.6 Settings store (SETST)

**LLR-SETST-010** Settings shall be read from the `settings` table at start via LLR-SETS-020, and exposed as
an observable state. ↑ HLR-SET-030

**LLR-SETST-020** A change shall be quantized (LLR-SETS-030), checked (LLR-SETS-040), written to the table in
one transaction, and then published. ↑ HLR-SET-030, HLR-SET-020

**LLR-SETST-030** Reset shall write `defaults()` for every key in one transaction. It shall not touch any other
table. ↑ HLR-SET-040

### 3.7 Catalog and climate installation (CATI)

**LLR-CATI-010** At start, if no BUNDLED variety exists and no `tier` setting exists, the BASIC tier shall be
installed, with progress reported in percent. ↑ HLR-ENC-010, HLR-PERF-010

**LLR-CATI-020** Before installing, the asset's SHA-256 shall be compared with the build manifest
(LLR-CATP-050). On a mismatch or any validation failure, E-CAT-001/E-CAT-002 is raised, and nothing changes.
↑ HLR-ENC-100, HLR-ENC-020

**LLR-CATI-030** A tier switch shall apply `planTierSwitch` (LLR-TIER-010) in one transaction:
- insert-or-replace the upserts, with source = BUNDLED and tier = the new tier
- delete the removals
- keep the kept rows

It then sets `tier` and records a TIER_SWITCH audit entry with details {from, to, kept count}. Rows are written
in batches of at most 500 per statement. ↑ HLR-ENC-020, HLR-ENC-030, HLR-PERF-030, HLR-PROT-060

**LLR-CATI-040** Rows with source = USER shall never be written or deleted by installation or tier switching.
↑ HLR-ENC-030

**LLR-CATI-050** `species.txt` and `families.txt` shall be loaded into memory at start (read-only). ↑ HLR-ENC-060, HLR-ENC-070

**LLR-CATI-060** Climate data shall be a bundled read-only SQLite asset, `climate.db` (not encrypted,
containing no user data), with table `zips(zip TEXT PK, zone TEXT, last_frost_doy INTEGER,
first_frost_doy INTEGER, lat REAL, lon REAL)` and a `meta(source, version)` table. It is opened read-only.
↑ HLR-CLIM-010, HLR-CLIM-020

### 3.8 Repositories (REPO)

**LLR-REPO-010** Every repository operation shall return `Result<T, AppError>`. It shall catch
`SQLiteException`, `IOException`, `GeneralSecurityException` and `IllegalStateException` from storage, and map
them to E-DATA-001 (read) or E-DATA-002 (write). No storage exception may reach the UI uncaught.
↑ HLR-STOR-050

**LLR-REPO-020** Each of these operations shall be exactly one transaction, including any undo_entries update:
- createPlot, renamePlot, resizePlot, deletePlot, setArchived, setLocation, setWaterSource
- addPlant, movePlant, deletePlant, changeVariety, setPlantingDate, setOutcome, applyPlanB
- addPath, updatePath, deletePath
- fill
- restoreSnapshot (undo/redo)
- tier switch
- variety create/update/delete

↑ HLR-STOR-010, HLR-HIST-040, HLR-AREA-090

**LLR-REPO-030** `resizePlot(id, L, W)` shall check the stored plants and paths inside its transaction:
- a plant is outside if x > L or y > W
- a RECT path is outside if x + w > L or y + h > W
- a LINE path is outside if any point is outside

If any is outside, it shall return `OutsideBounds(plantIds, pathIds)` without writing. ↑ HLR-PLOT-080

**LLR-REPO-040** `addPlant`, `fill` and `applyPlanB` shall re-run `validate` against the stored data inside the
transaction. `addPlant` and `fill` shall also enforce MAX_PLANTS_PER_PLOT there (E-DATA-004), and `addPath`
shall enforce MAX_PATHS_PER_PLOT. ↑ HLR-PLC-110, HLR-STOR-060, HLR-RULE-080

**LLR-REPO-050** `restoreSnapshot(plotId, snapshot)` shall delete the plot's plants and paths and re-insert the
snapshot's rows with their original ids. ↑ HLR-HIST-020

**LLR-REPO-060** Undo entries shall be written with every recorded change (the `before` snapshot, serialized
as JSON), trimmed to the undo depth. Leaving a plot shall delete that plot's undo entries `[H-TBC-04]`.
↑ HLR-HIST-050, HLR-HIST-030

**LLR-REPO-070** `deleteVariety(code)` shall refuse (returning the plots using it) when any plant references
the code, and shall refuse varieties with source = BUNDLED. ↑ HLR-ENC-090, HLR-STOR-040

**LLR-REPO-080** Editing a BUNDLED variety shall store it with source = USER. ↑ HLR-ENC-080

### 3.9 Start-up and data reset (START)

**LLR-START-010** Start-up shall run off the main thread, in this order:
1. capabilities (LLR-CAPS-010)
2. key unwrap (LLR-KEY-030) and v20.20 key migration (LLR-KEY-050)
3. open the database and migrate (§3.2)
4. first-run catalog install (LLR-CATI-010)
5. orphan photo cleanup (LLR-PHOTO-060)

The splash screen stays until step 4 completes, or an error state is reached. ↑ HLR-PERF-010, HLR-PROT-040

**LLR-START-020** On KEY_LOSS, or when the database fails to open with the key, start-up shall stop before
any write, record a KEY_LOSS audit entry and show the data-unreadable screen (LLR-UNRD-010). ↑ HLR-PROT-040

**LLR-START-030** "Start with empty data" shall:
- rename `sgp.db` (and its -wal/-shm files) and `db_key.bin` with the suffix `.unreadable-<utc>`
- generate a new key
- create an empty database
- record DATA_RESET
- continue start-up at step 4

Settings shall offer "Delete unreadable data", which deletes the renamed files after confirmation.
↑ HLR-PROT-040

### 3.10 Backup exclusion (BKP)

**LLR-BKP-010** The manifest shall set `android:allowBackup="false"`. `data_extraction_rules.xml` shall
exclude the `root`, `file`, `database`, `sharedpref` and `external` domains from both `<cloud-backup>` and
`<device-transfer>`. `full_backup_content.xml` shall exclude the same domains. ↑ HLR-PROT-030

**LLR-BKP-020** All network access shall go through `NetworkGateway.getText`, which returns without connecting
when the Online features setting is off and refuses any URL not accepted by `OnlineData.isAllowed` (HTTPS and
an allow-listed host); the manifest shall set `android:usesCleartextTraffic="false"`. A build check shall fail
if any other class opens a URL connection or socket, or if the dependency graph contains a group on the blocked list: `com.google.firebase`,
`com.google.android.gms:play-services-analytics`, `com.crashlytics`, `io.sentry`, `com.google.android.gms:play-services-ads`.
↑ HLR-PROT-050

---

## 4. `:app` — navigation, state and layout mechanics

### 4.1 Navigation and state (NAVG, STATE)

**LLR-NAVG-010** Navigation shall use typed routes:
- PlotList (start)
- PlotEditor(plotId?)
- Layout(plotId)
- Camera(plotId)
- Encyclopedia
- VarietyDetail(code)
- VarietyEditor(code?)
- Settings
- About
- DataUnreadable

↑ HLR-NAV-010

**LLR-NAVG-020** The system Back action shall pop the back stack. On PlotList it shall finish the activity.
On Layout, when `hasUnfinishedWork` is true, a Back handler shall show "Discard unfinished …?" and pop only on
confirmation. Predictive back shall be enabled in the manifest. ↑ HLR-NAV-020

**LLR-NAVG-030** `hasUnfinishedWork` shall be true when any of these holds: path or area points are being
drawn, a plant drag is in progress, or a dialog with edited text is open. ↑ HLR-NAV-020

**LLR-STATE-010** Each screen's ViewModel shall keep its state in a `SavedStateHandle`. For Layout, this holds:
- plotId
- tool
- zoom
- view centre (m)
- in-progress points (≤ MAX_POINTS_PER_SHAPE)
- open dialog id and its field texts
- overlay toggles

↑ HLR-NAV-030, HLR-NAV-040

**LLR-STATE-020** On re-creation, the Layout screen shall restore the values of LLR-STATE-010 and reload the
plot data and undo history from the database. ↑ HLR-NAV-040, HLR-HIST-050

**LLR-STATE-030** When a plot is opened by navigation (not restored), the tool shall be Plant, overlays off, zoom 1
and the centre at the plot's middle. ↑ HLR-PLC-010, HLR-VIEW-080, HLR-VIEW-090

### 4.2 Canvas transform (XFORM)

**LLR-XFORM-010** For a viewport of Vw × Vh px (after insets and ruler gutters), a plot L × W m, zoom z and
centre (cx, cy):
- s0 = min(Vw / L, Vh / W)
- s = s0 · z
- screen(x, y) = ((x − cx)·s + Vw/2, (y − cy)·s + Vh/2)

↑ HLR-LAY-010, HLR-VIEW-010

**LLR-XFORM-020** `toPlot(screen)` shall be the exact inverse of LLR-XFORM-010. ↑ HLR-PLC-020, HLR-PLC-080

**LLR-XFORM-030** The centre shall be clamped to [0, L] × [0, W] after every pan and zoom. ↑ HLR-VIEW-020

**LLR-XFORM-040** Pinch zoom shall multiply z by the gesture's scale factor, clamp z to [zoom_min, zoom_max],
and adjust the centre so that the plot point under the gesture centroid stays under it. "+" and "−" shall change
z by ± zoom_step (clamped), keeping the centre. ↑ HLR-VIEW-010

**LLR-XFORM-050** "Recenter" shall set z = 1 and centre = (L/2, W/2). It shall be visible only when z ≠ 1 or
the centre ≠ (L/2, W/2). ↑ HLR-VIEW-010

**LLR-XFORM-060** `hitToleranceM = HIT_TOLERANCE_DP × density / s`. ↑ HLR-PLC-040, HLR-PLC-080, HLR-AREA-030

### 4.3 Gestures (GEST)

**LLR-GEST-010** The pointer handler shall treat the gesture as a transform (pinch/pan) whenever two or more
pointers are down. A one-finger gesture in progress when a second pointer goes down shall be cancelled,
with no change to stored data. ↑ HLR-VIEW-020, HLR-VIEW-030

**LLR-GEST-020** A tap is a single pointer that moves less than the system touch slop and is released within the
long-press timeout. In the Plant tool, the software shall:
- find the plant with the smallest screen distance to the tap
- if that distance ≤ HIT_TOLERANCE_DP, open its details
- otherwise, if `toPlot(tap)` is inside the plot, request placement
- otherwise, ignore the tap

↑ HLR-PLC-020, HLR-PLC-040, HLR-PLC-030

**LLR-GEST-030** In the Move tool, a drag starting within the hit tolerance of a plant shall show that plant at
the finger position. On release it calls `movePlant` (validated). On rejection or cancel it redraws the plant
at its stored position and shows the message. ↑ HLR-PLC-080

**LLR-GEST-040** In the Path tool, rectangle style, a drag shall produce `normalizeRect(start, end)` in metres,
then `clipRect`. The path is added if both sides ≥ MIN_SHAPE_SIDE_M. ↑ HLR-AREA-010

**LLR-GEST-050** In the Path tool, line style, and in the Area tool, polygon shape, each tap inside the plot shall
append a point (up to MAX_POINTS_PER_SHAPE). Taps outside the plot are ignored. ↑ HLR-AREA-020, HLR-AREA-060

**LLR-GEST-060** In the Path tool, with no points in progress, a tap shall open a path for editing when it is:
- inside a RECT path expanded by the hit tolerance, or
- within width/2 + the hit tolerance of a LINE path

The topmost (last stored) matching path is chosen. ↑ HLR-AREA-030

**LLR-GEST-070** In the Area tool, rectangle shape, a drag shall produce a clipped, normalized rectangle. It is
used if both sides ≥ MIN_SHAPE_SIDE_M. ↑ HLR-AREA-050

**LLR-GEST-080** While the Path or Area tool is active, the canvas shall request system-gesture exclusion for
its left and right edges (within the platform limit), so that drawing near an edge doesn't trigger Back.
↑ HLR-NAV-020, HLR-AREA-010

### 4.4 Drawing (DRAW)

**LLR-DRAW-010** The layout shall draw, in this order:
1. plot background
2. photo, fitted to the plot rectangle
3. grid
4. paths
5. weed-risk overlay
6. spacing circles
7. irrigation route and water-source marker
8. plant markers and letter codes
9. overdue rings
10. conflict symbols
11. in-progress points and drag previews

↑ HLR-LAY-020, HLR-LAY-030, HLR-VIEW-080, HLR-VIEW-090

**LLR-DRAW-020** Plant markers shall be circles with a radius of 10 dp, in the variety colour, with the letter
code centred (text ≥ 10 sp). Spacing circles have radius `radiusM · s`, a translucent fill (alpha 0.25) and an
outline (1 dp). ↑ HLR-LAY-020, HLR-NAV-080

**LLR-DRAW-030** Overdue plants shall get a ring of radius 14 dp and stroke 3 dp in the theme's error colour.
Conflict-flagged plants shall get a warning triangle, 12 dp, at the top-right of the marker. ↑ HLR-LAY-050, HLR-LAY-060

**LLR-DRAW-040** The weed-risk overlay shall be drawn as: plot rectangle minus the union of spacing circles
minus the paths (path boolean operations). It is filled with the theme's warning colour at alpha 0.3, and cached,
recomputed only when plants, paths or scale change. ↑ HLR-VIEW-080, HLR-PERF-020

**LLR-DRAW-050** The ruler tick step shall be: raw = ruler_tick_m (converted to the display unit) / z, then the
value from {1, 2, 5} × 10ⁿ closest to raw on a logarithmic scale. Ticks and labels are placed with
LLR-XFORM-010. Labels use LLR-UNIT-030 decimals: 0 when the step ≥ 1, 1 when the step ≥ 0.1, otherwise 2.
Text size = ruler_text_sp. ↑ HLR-VIEW-040

**LLR-DRAW-060** Grid lines shall be drawn at the ruler tick positions, inside the plot only. ↑ HLR-VIEW-050

**LLR-DRAW-070** Text layout and paint objects shall be created once per style and reused across frames.
Nothing shall be allocated per plant per frame beyond primitive values. ↑ HLR-PERF-020

**LLR-DRAW-080** Colours shall come from the theme (light, dark, high-contrast). Plant colours come from
LLR-COLR-010/020. ↑ HLR-VIEW-070

---

## 5. `:app` — screens

### 5.1 Plot list and editor (LIST, EDIT)

**LLR-LIST-010** The plot list shall observe the non-archived query (LLR-DB-070). Each row shows the name,
`formatDistance(L) × formatDistance(W)` and the plant count. A "Show archived" switch changes the query to all
plots, with archived ones labelled. ↑ HLR-PLOT-010, HLR-PLOT-100

**LLR-LIST-020** With zero rows, the list shall show the empty-state text and a "Create plot" button.
↑ HLR-PLOT-020

**LLR-LIST-030** Each row shall have an overflow menu with Rename, Edit size, Archive/Unarchive and Delete.
Delete asks for confirmation, stating the plant and path counts. ↑ HLR-PLOT-070, HLR-PLOT-080, HLR-PLOT-090, HLR-PLOT-100

**LLR-EDIT-010** The editor shall validate on every change:
- name: trimmed length 1–60
- length and width: `parseDistance` ≠ null and within [plot_min_m, plot_max_m]

Invalid fields show an error text with the range formatted in the display unit. Save is enabled only when all
are valid. ↑ HLR-PLOT-030, HLR-PLOT-040

**LLR-EDIT-020** On save, if L or W < UNUSUAL_MIN_M or > UNUSUAL_MAX_M, a confirmation dialog shall be shown.
Cancel returns to the form unchanged. ↑ HLR-PLOT-050

**LLR-EDIT-030** Saving a new plot shall call `createPlot` with a new UUID, the dimensions in metres and
created = modified = now, then navigate to Layout(newId), replacing the editor in the back stack. ↑ HLR-PLOT-060

**LLR-EDIT-040** Saving an existing plot shall call `renamePlot` and/or `resizePlot`. An `OutsideBounds`
result shows the list of affected plant varieties and path numbers, and keeps the form open. ↑ HLR-PLOT-070, HLR-PLOT-080

**LLR-EDIT-050** The editor's location section shall accept a ZIP (validated with `byZip`) or lat/lon
(LLR-CLIMQ-030 + `nearest`), or "Use my location" (LLR-LOCS-010), and show the resulting zone and frost dates.
"Clear" removes the location. ↑ HLR-PLOT-110, HLR-PLOT-120, HLR-CLIM-030

### 5.2 Layout screen (LAYV, DET, PICK, FILLV)

**LLR-LAYV-010** The top bar shall show Back, the plot name and an overflow menu (Legend, Overlays, Photo,
Location, Units). A header line shall show the dimensions, plant count and, in the Plant tool, "Placing:
<variety> (<radius>)". ↑ HLR-LAY-040, HLR-NAV-100

**LLR-LAYV-020** A bottom toolbar shall offer the Plant, Path, Area and Move tools as a single-selection
control, plus Undo and Redo buttons, enabled per LLR-HIST-040. ↑ HLR-PLC-010, HLR-HIST-030

**LLR-LAYV-030** Undo and Redo shall call `restoreSnapshot` with the snapshot from LLR-HIST-020, and update the
history only after the transaction succeeds. ↑ HLR-HIST-020, HLR-HIST-040

**LLR-LAYV-040** Every successful layout change listed in HLR-HIST-010 shall call `record(before)`, with the
snapshot taken from the state before the change. ↑ HLR-HIST-010

**LLR-LAYV-050** A rejected request shall show a snackbar. For SPACING and COMPANION it reads
"<reason>: <variety> is <actual>, needs <required>", with distances from LLR-UNIT-030. For BOUNDARY: "Outside
the plot". For PATH: "Overlaps a no-plant path". ↑ HLR-RULE-090, HLR-PLC-020

**LLR-LAYV-060** When companion rules are not enforced, the ViewModel shall recompute `conflictFlags` after
every change to the plants, and pass the result to drawing. ↑ HLR-RULE-070, HLR-LAY-060

**LLR-LAYV-070** The ViewModel shall evaluate overdue states on load, after each change, and when the local date
changes while the screen is visible (listening for date/time/time-zone changes). ↑ HLR-TIME-030

**LLR-LAYV-080** In the Plant tool with no selected variety, a placement request shall open the variety picker.
After a selection, `last_variety` shall be saved. ↑ HLR-PLC-030, HLR-PLC-090

**LLR-LAYV-090** The Units item and the header unit chip shall toggle the `unit` setting between METERS and
INCHES. ↑ HLR-NAV-100

**LLR-LAYV-100** The Overlays menu shall have independent switches for "Weed-risk area" and "Irrigation route".
When the irrigation route is on, a movable water-source marker is drawn. Dragging it (in any tool, starting on
the marker) calls `setWaterSource` with the position clamped to the plot. Route length and weed-risk area are
shown in a small panel. ↑ HLR-VIEW-080, HLR-VIEW-090, HLR-VIEW-100

**LLR-LAYV-110** The Legend shall list the varieties on the plot, sorted by name, each with colour, letter
code, name and count. ↑ HLR-VIEW-060

**LLR-LAYV-120** When the plot has a location, the header shall show the zone and frost dates. Location-
dependent items without a location shall show "Set a location" with a link to the editor's location section.
↑ HLR-CLIM-030, HLR-PLOT-130

**LLR-LAYV-130** Placing a perennial whose zone range excludes the plot's zone shall show a warning snackbar
after a successful placement, without blocking it. ↑ HLR-TIME-100

**LLR-DET-010** The plant details sheet shall show:
- variety name
- planting date
- harvest date (LLR-SCHED-020)
- spacing radius (display unit)
- germination state: "Overdue", "Germinated", or "Expected by <date>"
- planting window when located (LLR-WIN-010)

It offers "Change date", "Change variety" and "Delete". ↑ HLR-PLC-040, HLR-TIME-020, HLR-TIME-090

**LLR-DET-020** "Change date" shall open a date picker limited to LLR-SCHED-050, and call `setPlantingDate`.
↑ HLR-PLC-050

**LLR-DET-030** "Change variety" shall open the picker, then call `changeVariety`, which validates excluding
the plant and sets planting date = today and outcome = NONE. A rejection keeps the old variety and shows
LLR-LAYV-050. ↑ HLR-PLC-060

**LLR-DET-040** For an overdue plant, the sheet shall show "It germinated" (`setOutcome(GERMINATED)`),
"It failed — show options" and "Close". ↑ HLR-TIME-040

**LLR-DET-050** "Show options" shall compute Plan B (§2.8) with LLR-SEASON-020 and show the available options
side by side, each with the variety, its days to harvest and a harvest date if started today. It shows the
SEASON_OVER reason when applicable. Choosing an option calls `applyPlanB`. ↑ HLR-TIME-050, HLR-TIME-060, HLR-TIME-080

**LLR-DET-060** "Delete" shall call `deletePlant`. ↑ HLR-PLC-070

**LLR-PICK-010** The picker shall show:
- **step 1:** categories with counts, from the variety list
- **step 2:** species (grouped by `speciesCode`, named by LLR-CATP-060) with cultivar counts, sorted by name
- **step 3:** cultivars sorted by `cultivarName`, each with spacing radius (display unit), germination days,
  days to harvest and, when located, the window state

↑ HLR-PLC-100, HLR-TIME-090

**LLR-PICK-020** Steps 2 and 3 shall filter by case-insensitive substring of the species or cultivar name. Every
step after the first shall have a Back action to the previous step, which clears the search text. ↑ HLR-PLC-100

**LLR-PICK-030** When the plot has a location, the picker shall offer a "Recommended" filter using
LLR-REC-010. ↑ HLR-TIME-100

**LLR-FILLV-010** After an area selection, a sheet shall show the variety (default: selected variety), a
Rows/Hexagonal choice and the preview count, from `planFill` run on a background dispatcher, at most 300 ms after
the last input change. TooManyCandidates shows E-DATA-006. ↑ HLR-AREA-070

**LLR-FILLV-020** "Fill" shall call the `fill` repository operation with the accepted positions, then show
"Placed N of M", and "(plant limit reached)" when truncated. The tool then switches to Plant. ↑ HLR-AREA-090

**LLR-FILLV-030** On the Basic tier, the Area tool's polygon option shall be shown disabled, with the text
"Available on Standard and Pro". ↑ HLR-AREA-060

**LLR-FILLV-040** A line path's width dialog shall accept `parseDistance` values > 0 and ≤ min(L, W), defaulting
to DEFAULT_PATH_WIDTH_M shown in the display unit. The path edit dialog shows the width (line paths) and Delete.
↑ HLR-AREA-020, HLR-AREA-030

**LLR-FILLV-050** Before a new or widened path is stored, the software shall list the plants whose spacing
circle would overlap it (LLR-VALD-030) and ask "Keep path" or "Cancel" `[H-TBC-03]`. ↑ HLR-AREA-040

### 5.3 Encyclopedia and variety editor (ENCV)

**LLR-ENCV-010** The encyclopedia shall show a lazily rendered list of all varieties, filtered by a category
chip and a case-insensitive substring over name and family. ↑ HLR-ENC-040

**LLR-ENCV-020** The detail view shall show every field of HLR-ENC-050, with "Not on file" for empty fields,
companion/antagonist names via LLR-CATP-060, and the estimate notice with the catalog source from the file
header. ↑ HLR-ENC-050, HLR-ENC-060, HLR-ENC-110

**LLR-ENCV-030** The editor shall validate with LLR-CATP-020's rules (code uniqueness checked against the
database). It takes the radius in the display unit, and offers the family as a list from `families.txt`,
companions and antagonists as a searchable multi-select from `species.txt`, and the 16 colours plus
"Automatic". Save is enabled only when valid. ↑ HLR-ENC-070, HLR-ENC-080

**LLR-ENCV-040** "Delete" shall appear only for USER varieties. A refusal lists the plots using it. ↑ HLR-ENC-090

### 5.4 Settings (SETV)

**LLR-SETV-010** Settings shall be grouped into Units, Catalog, Placement, Canvas, Plot limits, Camera and
location, and Season. Items for features whose capability is missing shall be hidden:
- light threshold: needs a light sensor
- location limit: needs a location provider
- storage and battery thresholds: need a camera

`[TBC-30]` ↑ HLR-SET-010

**LLR-SETV-020** Numeric settings shall use sliders quantized per LLR-SETS-030. A rejected change (E-SET-001)
shall revert the slider and show the message. ↑ HLR-SET-020

**LLR-SETV-030** Below the Pro tier, "Enforce companion rules" shall be shown on and disabled, with the text
"Can be changed on the Pro tier". ↑ HLR-SET-050

**LLR-SETV-040** A tier switch shall show a progress dialog, then a result: "Switched to <tier>; <k>
varieties kept because they are planted". ↑ HLR-ENC-020

**LLR-SETV-050** "Reset all settings" shall ask for confirmation, then call LLR-SETST-030. ↑ HLR-SET-040

### 5.5 Camera and device services (CAMV, SENS, LOCS, CAPS)

**LLR-CAMV-010** The camera screen shall bind a preview and an in-memory still capture to the rear camera. The
image is never written unencrypted to storage. Binding failures show E-CAM-001. ↑ HLR-CAM-010, HLR-PROT-020

**LLR-CAMV-020** Without camera permission, the screen shall request it. After a denial, it shows the rationale
and an "Open settings" action (E-CAM-002). ↑ HLR-CAM-010

**LLR-CAMV-030** "Choose from gallery" shall use the system photo picker for images, and pass the chosen image
stream to the photo pipeline (§3.4). ↑ HLR-CAM-020

**LLR-CAMV-040** On opening, and before each capture or pick, the storage ratio (LLR-SENS-020) shall be
compared with `storage_floor_pct`. Below it, a warning banner is shown and capture and pick are disabled,
re-checked every 5 s. ↑ HLR-CAM-070

**LLR-CAMV-050** On opening, the battery level and charging state (LLR-SENS-030) shall be read. If the level is
below `battery_pct` and the phone isn't charging, the Continue/Cancel dialog is shown before the preview
starts. Continue records BATTERY_OVERRIDE {level, plotUuid}. The camera is never disabled for battery reasons.
↑ HLR-CAM-080

**LLR-CAMV-060** The light warning banner shall be shown while the 5-sample average (LLR-SENS-010) < `light_lux`.
Capture stays enabled. ↑ HLR-CAM-060

**LLR-SENS-010** While the camera screen is resumed and a light sensor exists, the software shall listen to it at
a 200 000 µs sampling period (5 Hz), keep the last 5 values, and expose their mean. It stops listening when the
screen pauses. ↑ HLR-CAM-060

**LLR-SENS-020** The storage ratio shall be available bytes / total bytes of the app's internal storage volume.
↑ HLR-CAM-070

**LLR-SENS-030** The battery level shall be `level × 100 / scale` from the sticky battery status, with charging =
plugged ≠ 0. ↑ HLR-CAM-080

**LLR-LOCS-010** "Use my location" shall request fine location permission, then request a single current location
with a 30 s timeout `[H-TBC-01]`, from the fused provider if present, otherwise GPS. It is accepted only if it has
an accuracy ≤ `loc_accuracy_m`. Otherwise it reports E-LOC-001 (denied), E-LOC-002 (timeout) or E-LOC-003
(accuracy). A location found but outside the climate data reports E-LOC-004. ↑ HLR-PLOT-120, HLR-CLIM-020

**LLR-CAPS-010** At start, the software shall determine: rear camera present (camera feature and a back-facing
camera); light sensor present; a location provider present; and the camera and location permission states. It
stores these in memory, and re-checks permissions on each resume. ↑ HLR-CAPS-010

**LLR-CAPS-020** A UI entry point whose capability is missing or denied shall be shown disabled, with the reason
text. It never crashes. ↑ HLR-CAPS-020, HLR-CAM-010

### 5.6 Accessibility and display (A11Y, DISP)

**LLR-A11Y-010** Every interactive element shall have a minimum touch target of 48 × 48 dp and a content
description from string resources. ↑ HLR-NAV-070

**LLR-A11Y-020** The canvas shall expose each plant as a semantic node at its marker, labelled
"<variety name>, planted <date>", with a click action that opens its details. ↑ HLR-NAV-070

**LLR-DISP-010** The activity shall enable edge-to-edge. Every screen shall apply safe-drawing insets to its
content and floating controls. No orientation shall be locked `[TBC-21]`. ↑ HLR-NAV-050, HLR-NAV-030

**LLR-DISP-020** Text sizes shall be in sp from the theme typography. Forms and settings shall scroll vertically.
At font scale 2.0, no control shall be clipped so that it can't be used. ↑ HLR-NAV-060

**LLR-DISP-030** A configuration change shall not recreate ViewModels, and shall keep the zoom and view centre
of LLR-STATE-010. ↑ HLR-NAV-030

### 5.7 Errors, about and data-unreadable screens (ERRV, ABOUT, UNRD)

**LLR-ERRV-010** Errors from §7 shall be shown with their text and code in brackets: as a snackbar for errors
affecting one action, and as a dialog for errors blocking a screen. The screen stays usable after dismissal.
↑ HLR-STOR-050, HLR-CAM-040

**LLR-ABOUT-010** The About screen shall show the app version, the catalog source(s), the climate data
source/version from `meta`, and the audit log entries (newest first). A "Verify" action shows the LLR-AUDS-040
result. ↑ HLR-PROT-070, HLR-CLIM-010

**LLR-UNRD-010** The data-unreadable screen shall explain that the stored data can't be read on this device
(for example after restoring a backup), and offer "Start with empty data" (after a second confirmation,
LLR-START-030) and "Close app". ↑ HLR-PROT-040

### 5.8 Performance (PERF)

**LLR-PERF-010** Start-up (LLR-START-010) shall do no database, key or file work on the main thread. The time
from process start to an interactive plot list shall be measured by an automated benchmark on the target in
each verification campaign. ↑ HLR-PERF-010

**LLR-PERF-020** A frame-timing benchmark with 500 plants, zoom and pan, and drag, shall run on the target in each
campaign, with overlays off and on. ↑ HLR-PERF-020

**LLR-PERF-030** A tier switch benchmark (Basic → Pro) shall run on the target in each campaign. ↑ HLR-PERF-030

### 5.9 Automatic planting, direction, obstacles and plan files (AUTOP, ORNT, OBST, PFILE) (added 2026-09-27)

**LLR-AUTOP-010** `AutoPlanner.plan(context, area, requests, isBlocked, margin, orientationKnown, maxCandidates = 3000)`
shall generate candidate positions on a square grid over the area's bounding box with step
`max(0.5 × smallest requested radius, √(bbox area / maxCandidates))`, starting half a step in, keeping only
positions inside the area polygon and inside the plot (`PlotShape.contains`). ↑ HLR-AUTO-010, HLR-AUTO-050

**LLR-AUTOP-020** The sunward unit vector shall be `SunlightEngine.sunDirectionInPlot(az, bearing)` with
az = 180° if latitude ≥ 0, else 0° (latitude defaults to 40°N). A position's depth is
`(pmax − p·s) / (pmax − pmin)` over the area's corners, clamped to [0, 1] (0 = sunny edge, 1 = far edge). Each
variety's target depth is `(h − hmin) / (hmax − hmin)` of the requested varieties' mature heights
(`PlantHeights.heightM`), or 0.5 when they differ by less than 0.05 m. ↑ HLR-AUTO-020

**LLR-AUTOP-030** Plants shall be placed one at a time: non-pollinator varieties first in decreasing height, then
pollinator varieties (`AutoPlanner.POLLINATOR_PLANTS`), each variety's plants consecutively. For each plant, every
candidate is scored and the candidates are tried in decreasing score; the first that is not blocked by a path
(`isBlocked(x, y, radius)`), not a flood area for a flood-intolerant crop, and valid under
`CompanionPlantingValidator.validatePlacement` against existing plus already-proposed plants (with the active
guilds and margin) is taken. A plant with no such candidate is counted as unplaced. ↑ HLR-AUTO-030, HLR-AUTO-050

**LLR-AUTOP-040** The score shall be the sum of:
- −w × |depth − target depth|, w = 1.5 for CLUMPS and 3 for ROWS
- the rotation penalty: the largest 6 × (1 − (yearsAgo − 1) / waitYears) over past plantings of the same rotation
  group within 1..waitYears seasons whose `CropRotation.reach` (max(1.5 × radius, 0.6 m)) plus half the new plant's
  radius covers the position
- CLUMPS, first plant of a species: −1 / (1 + distance) to each other species' centroid
- when sun hours are known: −4 × max(0, need − hours) / need + hours / 24
- when plants of the same species exist: −(distance to their centroid) / (area diagonal) × k, with k = 9 (block
  species, `BLOCK_PLANTED`) or 7 for CLUMPS, and 5 or 2 for ROWS
- when plants with the same watering interval exist: −(distance to their centroid) / diagonal
- +0.5 for each companion species whose centroid is within 2 m
- for pollinator plants: −(distance to the insect-pollinated crops' centroid) / diagonal × 2, and
  −1.5 / (1 + distance to the nearest pollinator plant already proposed)

↑ HLR-AUTO-030, HLR-AUTO-040, HLR-ROT-020

**LLR-AUTOP-050** The result shall list placed plants, unplaced counts per species name, and notes: the far-side
compass name with the two tallest and the shortest species; the missing-direction assumption when
`orientationKnown` is false; whether sun data was used; block planting; pollinators spread or recommended when an
insect-pollinated crop is listed; watering groups when more than one interval is present; what didn't fit.
↑ HLR-AUTO-060

**LLR-AUTOP-060** The layout shall run the planner off the main thread, draw the proposal as translucent circles
at each variety's spacing radius with the area outlined, and show a card with the summary (each variety's `VarietyCatalogTraits.displayName`), notes and the buttons
Plant them / Change selections / Discard. "Plant them" inserts all proposed plants with `insertAll`, reloads, pushes one
undo snapshot and clears the redo stack. "Change selections" clears only the proposal, so the list dialog reopens for
the same area with the same rows. "Discard" clears the proposal and the area and writes nothing; the rows stay in
memory and in settings. ↑ HLR-AUTO-060, HLR-AUTO-010

**LLR-VAR-010** `VarietyCatalogTraits.of(seed)` shall return, by species key and cultivar name: for "pepper" a
shape (bell, horn, banana, mild chile, chile, hot chile, super-hot), a `Heat` (SWEET … EXTREME) and a `FruitColour`
from a table covering all 46 catalog peppers (else keywords "bell", "sweet", "hot"/"chil" in the name or care notes);
"shishito type pepper" mild green; for "tomato" a size (cherry, salad, slicing, beefsteak, paste) and colour from a
table covering all catalog tomatoes (else "cherry/grape/currant/pear", "paste/roma/plum/marzano", "beefsteak"
keywords); "paste tomato" paste; "onion" bulb with a colour; "spring onion" spring; "egyptian walking onion" walking;
null otherwise. `details` joins kind, heat (not for sweet), colour and size note; `tag` is kind and colour for the
layout; `displayName` appends the details in brackets; `dotArgb` is the colour. ↑ HLR-VAR-010, HLR-VAR-020

**LLR-VAR-020** ONI-101..103 (Spring Onion - Evergreen Hardy White, Tokyo Long White, Red Beard) shall be in all three
tier files (253 / 603 / 2,939). At start-up, when the seed table holds fewer rows than the active tier's count, the
tier file shall be inserted with `OnConflictStrategy.IGNORE`. Android draws the tag 34 px below the plant centre in
24 px bold ink over a 6 px paper-coloured halo, and the centre dot in `dotArgb` with an ink outline; the web draws the
same with SVG text sized to the layout. `settings.showPlantLabels` (default true) and the web preference control the
names. ↑ HLR-VAR-020

**LLR-ROT-010** `RotationGroup.forSeed` shall map botanical families (Fabaceae/Leguminosae → LEGUMES 2 y,
Brassicaceae → BRASSICAS 3 y, Solanaceae → NIGHTSHADES 3 y, Cucurbitaceae → CUCURBITS 2 y, Poaceae → GRAINS 2 y,
Amaryllidaceae/Alliaceae → ALLIUMS 3 y, Apiaceae → ROOTS 3 y, Amaranthaceae/Chenopodiaceae → BEETS 2 y,
Asteraceae → LETTUCE 1 y) for ANNUAL VEGETABLE or FRUIT varieties, else null. `CropRotation.conflict(x, y, seed,
history, seasonYear)` returns the most recent past planting of the same group, 1..waitYears seasons earlier, within
`reach` + half the seed's radius. `successor` follows legumes → brassicas → nightshades → alliums → legumes by role.
Placing (tap) and moving (drag) a plant on Android and the web shall show `CropRotation.warning` after a successful
change. ↑ HLR-ROT-010

**LLR-ROT-020** `CropRotation.advice(plot, history, current, lookup, seasonYear)` shall return: a no-history hint;
else "YEAR: group (species) in the SPOT; …" for the last season, where SPOT comes from `describeSpot` (plot centre
offset projected on north and east unit vectors from `sunDirectionInPlot(0|90, bearing)`, thirds of the half-size →
"north-west corner", "east side", "middle" …); one line per group naming the successor and the wait; a row note when
`isRow` (long side ≥ 60 % of the plot side and short side ≤ 25 % of the long side, ≥ 4 plants); one ⚠ line per
species of current plants in conflict, else "✓ … respect the rotation"; and the list of seasons when more than one.
Shown on Android in Plot insights → Harmony (not tier-gated) and on the web Plot tab. ↑ HLR-ROT-030

**LLR-SEAS-010** `PlantingHistoryEntity` (table `planting_history`, schema 10, MIGRATION_9_10) shall hold plotId
(cascade delete), seasonYear, seedCode, varietyName, family, rotationGroup (nullable), coordinates, radiusM and the
planting date. `Seasons.archive` copies the plot's plants with name, family, group and radius from the catalog.
`Seasons.currentSeason(nodes, history)` = max(year most nodes were planted, or this year if none; last history
year + 1). ↑ HLR-SEAS-010

**LLR-SEAS-020** "Start a new season" (Android menu, web Plot tab; enabled when the plot has plants) shall, after the
user confirms a year in 1900..3000, insert the archived rows and delete the plot's plants in one transaction (web: one
`Store.change`), select that year for the layout, and push one undo snapshot. `CanvasSnapshot` (Android) and `Snap`
(web) include the history, and restoring a snapshot replaces the plot's history rows. ↑ HLR-SEAS-010

**LLR-SEAS-030** The plan file shall write, per plot with history, `history`: [{season, code, variety, family,
rotationGroup, x, y, radiusM, plantedAt}] and omit it when empty. Reading keeps up to 20,000 entries per plot, skips
entries without a code, with a season outside 1900..3000 or outside the plot (counted in a warning), and defaults a
missing radius to 0.3 m. Import stores history rows for the new plot regardless of the catalog. The web draws the
selected year as dashed circles with italic species names; Android as dashed circles (8/8 px) with 22 px italic
names. ↑ HLR-SEAS-020

**LLR-MEM-010** `AppSettings` shall add `planLayout` ("CLUMPS" default), `lastPlanList` ("CODE:count,…") and
`showPlantLabels`; the web keeps the same in `localStorage`. Opening "Plan an area for me" with no rows loads
`lastPlanRows` (known codes only), else the top 4 of `Seasons.usualVarieties` with count 3; "Plan it" saves the rows
to `lastPlanList`. `usualVarieties(current, history, lookup, limit = 8)` groups all codes by species key, picks each
species' most used code, and sorts by count then name. The dialog shows them as "+ Species" buttons that add 3 or
increase an existing row by 1. ↑ HLR-MEM-010

**LLR-ORNT-010** `PlotEntity` shall store `northBearingDeg` (0–360, the compass bearing of the plot's top edge)
and `orientationSet` (schema 9, MIGRATION_8_9 adds `orientationSet INTEGER NOT NULL DEFAULT 0`). The creator
shows `CompassChips`; `PlotDirectionDialog` offers the chips and a 0–355° slider with 70 steps; saving sets
`orientationSet = true`. ↑ HLR-ORNT-010

**LLR-ORNT-020** The layout shall draw, 44 px from the canvas's top-right corner, an arrow from the centre toward
bearing −northBearingDeg (red, labelled "N"), grey and labelled "N?" when `orientationSet` is false, and show the
tappable warning text in the plot header while it is false. ↑ HLR-ORNT-020

**LLR-ORNT-030** `ZipLookup.location` shall binary-search the bundled `zip_locations.txt` (lines "zip|lat|lon|state",
sorted by ZIP, loaded once) with `ZipTable.find`. `ZipLookup.zone` shall return, in order: the zone from the
bundled `zip_zones.txt` ("zip|zone", 40,502 ZIPs from the 2023 USDA/PRISM tables, sorted, loaded once) via
`ZipTable.findZone`; the starter climate table's zone; else, only when Online features are on, the result of
`NetworkGateway` with `OnlineData.zoneUrl(zip)`. Only zones `1a`–`13b` are accepted. The data files are checked
by `ZipDataTest` (sorted, well-formed, 40,502 entries, published zones for sample ZIPs). ↑ HLR-ORNT-030

**LLR-OBST-010** `SiteFeatureDialog` shall show Move for existing features. After Move, the next tap in a site
tool mode calls `moveSiteFeature`, which translates all points by (tap − anchor), with the translation clamped so
that every point stays within [0, length] × [0, width]. ↑ HLR-OBST-010

**LLR-OBST-020** `CanvasSnapshot` shall hold plants, paths, site features and the outline text. `restoreSnapshot`
shall, in one transaction, replace the plot's plants, paths and site features with the snapshot's and set the
outline if it differs. Insert, update, move and delete of a site feature and `saveOutline` shall push
`snapshotNow()` after reloading. ↑ HLR-OBST-020

**LLR-OBST-030** `SunBand.of(hours)` shall return FULL_SUN for hours ≥ 6, PART_SHADE for hours ≥ 3, else
FULL_SHADE, with overlay colours 0x40FACC15, 0x6660A5FA and 0xA6312E81 (ARGB). `LayoutPalette` shall hold the
layout colours (paper 0xFFF5F1E6, grid, border, ink, paths, fence, wall, building, tree, trunk, site areas). The
Android canvas and the web SVG shall fill the plot with `PAPER`, draw every cell of `SunlightEngine.sunHoursGrid`
in its band colour while the shade display is on, draw tree crowns as outlines only while it is on, and show a
"Sun today" legend of the three bands. The web page theme (auto, light, dark; kept in the browser) shall not change
the layout colours. ↑ HLR-OBST-030

**LLR-PFILE-010** `PlanFileCodec.encode` shall write a JSON object with `format` = "smart-garden-plan",
`version` = 1, `exportedAt` (ISO-8601 UTC), `app`, `units` = "metres", `plots` (name, description, lengthM,
widthM, outline, orientation {topFacesDeg, set}, location {zip, latitude, longitude, hardinessZone}, soil,
createdAt, modifiedAt, plants, paths, siteFeatures) and `customVarieties` (custom varieties used). Numbers are
rounded to 4 decimals; null fields are omitted. `PlanFileRepository.export` reads the plots from the database
and `PlanFileIo.write` stores the text at the URI from the system "create document" picker. ↑ HLR-PORT-010

**LLR-PFILE-020** `PlanFileCodec.decode` shall reject: text over 20 MB, invalid JSON, a wrong `format`, a missing
or newer `version`, missing `plots`, or more than 100 plots. It shall skip, with a warning: plots with a size
outside (0, 1000] m; outlines that are invalid or leave the plot; plants without a code or outside the plot
(first 5000 per plot); paths and features that are malformed, outside the plot or out of range (first 2000 per
plot); custom varieties with a spacing outside (0, 50] m. Soil percentages outside 0–100, pH outside 3–10,
latitude outside ±90 and longitude outside ±180 are dropped. ↑ HLR-PORT-020

**LLR-PFILE-030** `PlanFileRepository.import` shall, in one database transaction: insert custom varieties whose
code is new; insert each plot as a new row (name suffixed " (imported)" if a plot of that name exists); map each
plant's code to a known variety by code, else by variety name (case-insensitive), else skip and count it; insert
plants, paths and site features with the new plot id. Any exception rolls back everything. ↑ HLR-PORT-020


**LLR-WEB-010** `web/build.sh` shall compile every `core/*.kt` file except `AgriculturalIsolationEngine.kt`,
`CameraCaptureManager.kt` and the JVM `PlatformClock.kt`, together with `web/src/platform` (browser clock, Room
annotation stubs) and `web/src/app`, with the Kotlin 2.2.10 JS compiler (ES2015, dead-code elimination), and
`web/tools/bundle.py` shall inline the styles, the compiled script and the Pro seed catalog, `zip_zones.txt` and
`zip_locations.txt` (as `<script type="text/plain">` blocks) into `web/dist/smart-garden-planner.html`, failing if
a data file contains `</script`. The page shall load nothing from the network. ↑ HLR-PORT-030

**LLR-WEB-020** `Store.encode`/`Store.load` shall use `PlanFileCodec.encode`/`decode`. Loading replaces the open
plots, assigns new ids, maps unknown codes by variety name and skips and counts the rest. Save shall write through
`showSaveFilePicker` (keeping the handle for later saves) when available, otherwise download the file; Open shall
use `showOpenFilePicker` or a file input, or a file dropped on the page. `PlanFileCodec` shall test `Float`/`Double`
before `Int` so numbers are rounded to 4 decimals on both platforms. ↑ HLR-PORT-030, HLR-PORT-010

**LLR-WEB-030** Every change shall go through `Store.change`, which pushes a snapshot (plot, plants, paths,
features; max 100) and clears redo; undo/redo swap snapshots. After every change the whole plan shall be encoded to
`localStorage` ("sgp.draft") and restored on the next visit. Placing and moving a plant shall check the outline,
`HardinessZones.blocksPlacement`, no-plant paths and `CompanionPlantingValidator.validatePlacement` (with the
spacing margin, companion and guild settings); moving a site feature clamps it inside the plot. ↑ HLR-PORT-040

**LLR-WEB-040** "Plan an area for me" shall pass the dragged rectangle, the rows (variety, count 1–200), a path
check, the spacing margin and the plot's `orientationSet` to `AutoPlanner.plan`, show the result as dashed plants
with a keep/discard card, and add all kept plants in one undo step. The side panel shall show
`HarmonyAnalyzer`, `RecommendationEngine`, `CarePlanner` and `FoodPlanner` output for the current plot, and the
shade overlay `SunlightEngine.sunHoursGrid` for today. ↑ HLR-PORT-040
---

## 6. Deferred LLRs

- HLR-EXP-010 … 060 (export/import/roles) and HLR-CAM-100 (corner correction) are **[Future]**. Their LLRs will
  be written when they are scheduled.
- HLR-MEAS-010 is **[Suspended]** pending decision D-03.

## 7. Error catalogue

| Code | Condition | User text (string resource) | Handling |
|---|---|---|---|
| E-DATA-001 | Stored data could not be read | "Couldn't read your garden data. Try again; if it keeps happening, restart the app." | Action cancelled |
| E-DATA-002 | Stored data could not be written | "Couldn't save the change. Nothing was changed. Free some storage and try again." | Transaction rolled back |
| E-DATA-003 | Data from an unsupported (newer/older) version | "This data was created by a different version of the app and can't be opened." | No file modified |
| E-DATA-004 | Plant or path limit reached | "This plot has reached its limit of <n> <plants/paths>." | Change refused |
| E-DATA-005 | Inconsistent data (unknown variety) | "Some plant data is inconsistent; the change was refused." | Change refused |
| E-DATA-006 | Too many fill candidates | "Spacing is too small for this area. Choose a smaller area." | Fill not offered |
| E-KEY-001 | Encryption key lost | Data-unreadable screen | LLR-START-020 |
| E-CAT-001 | Catalog data invalid | "The plant catalog is damaged; reinstall the app." | Nothing changed |
| E-CAT-002 | Catalog checksum mismatch | Same as E-CAT-001 | Nothing changed |
| E-IMG-001 | Photo can't be decoded / out of memory | "Couldn't process this photo. The previous photo was kept." | Previous kept |
| E-IMG-002 | Photo too large after compression | "This photo is too detailed to store. Try another." | Previous kept |
| E-IMG-003 | Photo storage failed | "Couldn't save the photo. The previous photo was kept." | Previous kept |
| E-CAM-001 | Camera unavailable | "The camera isn't available right now." | Screen shows reason |
| E-CAM-002 | Camera permission denied | "Camera permission is needed to take plot photos." | Rationale + settings |
| E-LOC-001 | Location permission denied | "Location permission is needed; you can enter a ZIP instead." | Manual entry |
| E-LOC-002 | No fix within the timeout | "Couldn't get a location in time; enter it manually." | Manual entry |
| E-LOC-003 | Fix not accurate enough | "Location accuracy was <x>; the limit is <y>. Enter it manually." | Manual entry |
| E-LOC-004 | Location outside climate data | "No climate data for this location." | Location stored without zone |
| E-SET-001 | Setting conflict | "Minimum must stay below maximum." | Change refused |
| E-AUD-001 | Audit log unreadable | "Audit log can't be read (key lost at seq <n>)." | Shown in About |

↑ HLR-STOR-050 (catalogue referenced by all error-reporting LLRs)

## 8. LLR-level values to confirm `[L-TBC]`

| L-TBC | LLR | Question | Proposal |
|---|---|---|---|
| 01 | LIM-010, FILL-040 | Maximum fill candidates before refusing | 100 000 |
| 02 | PLANB-010 | Skip the family fallback when the family is "Other"? | Yes |
| 03 | WEED-020 | Accuracy of the weed-risk area figure | Within 1 % of plot area |

## 9. Traceability HLR → LLR

Generated by script from the `↑` links above.

<!-- LLR-TRACE -->
| HLR | Status | Implemented by LLR |
|---|---|---|
| HLR-PLOT-010 | Active | LLR-DB-060, LLR-DB-070, LLR-LIST-010 |
| HLR-PLOT-020 | Active | LLR-LIST-020 |
| HLR-PLOT-030 | Active | LLR-LIM-010, LLR-UNIT-040, LLR-EDIT-010 |
| HLR-PLOT-040 | Active | LLR-UNIT-040, LLR-EDIT-010 |
| HLR-PLOT-050 | Active | LLR-LIM-010, LLR-EDIT-020 |
| HLR-PLOT-060 | Active | LLR-DB-010, LLR-DB-060, LLR-EDIT-030 |
| HLR-PLOT-070 | Active | LLR-LIST-030, LLR-EDIT-040 |
| HLR-PLOT-080 | Active | LLR-REPO-030, LLR-LIST-030, LLR-EDIT-040 |
| HLR-PLOT-090 | Active | LLR-PHOTO-050, LLR-LIST-030 |
| HLR-PLOT-100 | Active | LLR-DB-070, LLR-LIST-010, LLR-LIST-030 |
| HLR-PLOT-110 | Active | LLR-CLIMQ-010, LLR-CLIMQ-030, LLR-EDIT-050 |
| HLR-PLOT-120 | Active | LLR-EDIT-050, LLR-LOCS-010 |
| HLR-PLOT-130 | Active | LLR-LAYV-120 |
| HLR-LAY-010 | Active | LLR-GEO-010, LLR-XFORM-010 |
| HLR-LAY-020 | Active | LLR-DRAW-010, LLR-DRAW-020 |
| HLR-LAY-030 | Active | LLR-PHOTO-070, LLR-DRAW-010 |
| HLR-LAY-040 | Active | LLR-UNIT-030, LLR-LAYV-010 |
| HLR-LAY-050 | Active | LLR-SCHED-040, LLR-DRAW-030 |
| HLR-LAY-060 | Active | LLR-VALD-100, LLR-DRAW-030, LLR-LAYV-060 |
| HLR-PLC-010 | Active | LLR-STATE-030, LLR-LAYV-020 |
| HLR-PLC-020 | Active | LLR-VALD-080, LLR-XFORM-020, LLR-GEST-020, LLR-LAYV-050 |
| HLR-PLC-030 | Active | LLR-GEST-020, LLR-LAYV-080 |
| HLR-PLC-040 | Active | LLR-LIM-010, LLR-XFORM-060, LLR-GEST-020, LLR-DET-010 |
| HLR-PLC-050 | Active | LLR-LIM-010, LLR-SCHED-050, LLR-DET-020 |
| HLR-PLC-060 | Active | LLR-VALD-080, LLR-DET-030 |
| HLR-PLC-070 | Active | LLR-DET-060 |
| HLR-PLC-080 | Active | LLR-VALD-080, LLR-XFORM-020, LLR-XFORM-060, LLR-GEST-030 |
| HLR-PLC-090 | Active | LLR-SETS-010, LLR-LAYV-080 |
| HLR-PLC-100 | Active | LLR-CATP-060, LLR-PICK-010, LLR-PICK-020 |
| HLR-PLC-110 | Active | LLR-LIM-010, LLR-FILL-050, LLR-REPO-040 |
| HLR-RULE-010 | Active | LLR-GEO-010, LLR-VALD-010 |
| HLR-RULE-020 | Active | LLR-LIM-010, LLR-GEO-020, LLR-VALD-020 |
| HLR-RULE-030 | Active | LLR-GEO-030, LLR-GEO-040, LLR-VALD-030 |
| HLR-RULE-040 | Active | LLR-VALD-040, LLR-VALD-050 |
| HLR-RULE-050 | Active | LLR-LIM-010, LLR-VALD-060 |
| HLR-RULE-060 | Active | LLR-VALD-070 |
| HLR-RULE-070 | Active | LLR-VALD-100, LLR-LAYV-060 |
| HLR-RULE-080 | Active | LLR-VALD-080, LLR-VALD-090, LLR-FILL-050, LLR-REPO-040 |
| HLR-RULE-090 | Active | LLR-UNIT-030, LLR-VALD-080, LLR-LAYV-050 |
| HLR-AREA-010 | Active | LLR-LIM-010, LLR-GEO-060, LLR-GEO-080, LLR-GEST-040, LLR-GEST-080 |
| HLR-AREA-020 | Active | LLR-LIM-010, LLR-GEST-050, LLR-FILLV-040 |
| HLR-AREA-030 | Active | LLR-GEO-040, LLR-XFORM-060, LLR-GEST-060, LLR-FILLV-040 |
| HLR-AREA-040 | Active | LLR-FILLV-050 |
| HLR-AREA-050 | Active | LLR-GEO-080, LLR-GEST-070 |
| HLR-AREA-060 | Active | LLR-FILL-030, LLR-GEST-050, LLR-FILLV-030 |
| HLR-AREA-070 | Active | LLR-VALD-080, LLR-FILL-040, LLR-FILL-050, LLR-FILL-060, LLR-FILLV-010 |
| HLR-AREA-080 | Active | LLR-GEO-050, LLR-GEO-070, LLR-FILL-010, LLR-FILL-020, LLR-FILL-030 |
| HLR-AREA-090 | Active | LLR-FILL-050, LLR-FILL-060, LLR-REPO-020, LLR-FILLV-020 |
| HLR-VIEW-010 | Active | LLR-XFORM-010, LLR-XFORM-040, LLR-XFORM-050 |
| HLR-VIEW-020 | Active | LLR-XFORM-030, LLR-GEST-010 |
| HLR-VIEW-030 | Active | LLR-GEST-010 |
| HLR-VIEW-040 | Active | LLR-DRAW-050 |
| HLR-VIEW-050 | Active | LLR-DRAW-060 |
| HLR-VIEW-060 | Active | LLR-LTR-010, LLR-LAYV-110 |
| HLR-VIEW-070 | Active | LLR-COLR-010, LLR-COLR-020, LLR-DRAW-080 |
| HLR-VIEW-080 | Active | LLR-UNIT-050, LLR-WEED-010, LLR-WEED-020, LLR-STATE-030, LLR-DRAW-010, LLR-DRAW-040, LLR-LAYV-100 |
| HLR-VIEW-090 | Active | LLR-IRR-010, LLR-IRR-020, LLR-STATE-030, LLR-DRAW-010, LLR-LAYV-100 |
| HLR-VIEW-100 | Active | LLR-DB-010, LLR-LAYV-100 |
| HLR-HIST-010 | Active | LLR-HIST-020, LLR-LAYV-040 |
| HLR-HIST-020 | Active | LLR-HIST-010, LLR-HIST-020, LLR-REPO-050, LLR-LAYV-030 |
| HLR-HIST-030 | Active | LLR-HIST-020, LLR-HIST-030, LLR-HIST-040, LLR-REPO-060, LLR-LAYV-020 |
| HLR-HIST-040 | Active | LLR-REPO-020, LLR-LAYV-030 |
| HLR-HIST-050 | Active | LLR-DB-010, LLR-REPO-060, LLR-STATE-020 |
| HLR-TIME-010 | Active | LLR-SCHED-010, LLR-DB-020 |
| HLR-TIME-020 | Active | LLR-SCHED-020, LLR-DET-010 |
| HLR-TIME-030 | Active | LLR-SCHED-030, LLR-SCHED-040, LLR-LAYV-070 |
| HLR-TIME-040 | Active | LLR-DET-040 |
| HLR-TIME-050 | Active | LLR-VALD-080, LLR-PLANB-010, LLR-PLANB-020, LLR-PLANB-030, LLR-PLANB-040, LLR-DET-050 |
| HLR-TIME-060 | Active | LLR-VALD-080, LLR-PLANB-050, LLR-DET-050 |
| HLR-TIME-070 | Active | LLR-PLANB-010, LLR-PLANB-030 |
| HLR-TIME-080 | Active | LLR-SEASON-010, LLR-SEASON-020, LLR-DET-050 |
| HLR-TIME-090 | Active | LLR-SEASON-010, LLR-WIN-010, LLR-DET-010, LLR-PICK-010 |
| HLR-TIME-100 | Active | LLR-WIN-020, LLR-REC-010, LLR-REC-020, LLR-LAYV-130, LLR-PICK-030 |
| HLR-ENC-010 | Active | LLR-CATP-010, LLR-CATP-040, LLR-CATI-010 |
| HLR-ENC-020 | Active | LLR-TIER-010, LLR-CATI-020, LLR-CATI-030, LLR-SETV-040 |
| HLR-ENC-030 | Active | LLR-TIER-010, LLR-CATI-030, LLR-CATI-040 |
| HLR-ENC-040 | Active | LLR-DB-070, LLR-ENCV-010 |
| HLR-ENC-050 | Active | LLR-CATP-010, LLR-ENCV-020 |
| HLR-ENC-060 | Active | LLR-VALD-040, LLR-CATP-030, LLR-CATP-060, LLR-CATI-050, LLR-ENCV-020 |
| HLR-ENC-070 | Active | LLR-CATP-020, LLR-CATI-050, LLR-ENCV-030 |
| HLR-ENC-080 | Active | LLR-REPO-080, LLR-ENCV-030 |
| HLR-ENC-090 | Active | LLR-DB-070, LLR-REPO-070, LLR-ENCV-040 |
| HLR-ENC-100 | Active | LLR-CATP-010, LLR-CATP-020, LLR-CATP-030, LLR-CATP-040, LLR-CATP-050, LLR-CATI-020 |
| HLR-ENC-110 | Active | LLR-ENCV-020 |
| HLR-CLIM-010 | Active | LLR-CATP-050, LLR-CLIMQ-010, LLR-CATI-060, LLR-ABOUT-010 |
| HLR-CLIM-020 | Active | LLR-CLIMQ-020, LLR-CATI-060, LLR-LOCS-010 |
| HLR-CLIM-030 | Active | LLR-EDIT-050, LLR-LAYV-120 |
| HLR-CAM-010 | Active | LLR-CAMV-010, LLR-CAMV-020, LLR-CAPS-020 |
| HLR-CAM-020 | Active | LLR-CAMV-030 |
| HLR-CAM-030 | Active | LLR-PHOTO-010, LLR-PHOTO-020, LLR-PHOTO-030 |
| HLR-CAM-040 | Active | LLR-PHOTO-030, LLR-PHOTO-040, LLR-ERRV-010 |
| HLR-CAM-050 | Active | LLR-PHOTO-050 |
| HLR-CAM-060 | Active | LLR-CAMV-060, LLR-SENS-010 |
| HLR-CAM-070 | Active | LLR-CAMV-040, LLR-SENS-020 |
| HLR-CAM-080 | Active | LLR-CAMV-050, LLR-SENS-030 |
| HLR-CAM-090 | Active | LLR-PHOTO-070 |
| HLR-CAM-100 | Future | deferred (§6) |
| HLR-CAPS-010 | Active | LLR-CAPS-010 |
| HLR-CAPS-020 | Active | LLR-CAPS-020 |
| HLR-STOR-010 | Active | LLR-REPO-020 |
| HLR-STOR-020 | Active | LLR-DB-010, LLR-DB-020 |
| HLR-STOR-030 | Active | LLR-DB-040, LLR-MIG-010, LLR-MIG-020, LLR-MIG-030, LLR-MIG-040, LLR-KEY-050 |
| HLR-STOR-040 | Active | LLR-DB-010, LLR-DB-030, LLR-PHOTO-060, LLR-REPO-070 |
| HLR-STOR-050 | Active | LLR-VALD-090, LLR-MIG-020, LLR-REPO-010, LLR-ERRV-010 |
| HLR-STOR-060 | Active | LLR-LIM-010, LLR-REPO-040 |
| HLR-PROT-010 | Active | LLR-DB-050, LLR-KEY-010, LLR-KEY-020, LLR-KEY-040, LLR-KEY-050 |
| HLR-PROT-020 | Active | LLR-KEY-010, LLR-PHOTO-030, LLR-AUDS-010, LLR-CAMV-010 |
| HLR-PROT-030 | Active | LLR-BKP-010 |
| HLR-PROT-040 | Active | LLR-KEY-030, LLR-AUDS-060, LLR-START-010, LLR-START-020, LLR-START-030, LLR-UNRD-010 |
| HLR-PROT-050 | Active | LLR-BKP-020 |
| HLR-PROT-060 | Active | LLR-AUD-010, LLR-AUD-020, LLR-AUDS-010, LLR-AUDS-020, LLR-AUDS-050, LLR-AUDS-060, LLR-CATI-030 |
| HLR-PROT-070 | Active | LLR-AUD-030, LLR-AUDS-040, LLR-ABOUT-010 |
| HLR-PROT-080 | Active | LLR-AUDS-030 |
| HLR-EXP-010 | Future | deferred (§6) |
| HLR-EXP-020 | Future | deferred (§6) |
| HLR-EXP-030 | Future | deferred (§6) |
| HLR-EXP-040 | Future | deferred (§6) |
| HLR-EXP-050 | Future | deferred (§6) |
| HLR-EXP-060 | Future | deferred (§6) |
| HLR-NAV-010 | Active | LLR-NAVG-010 |
| HLR-NAV-020 | Active | LLR-NAVG-020, LLR-NAVG-030, LLR-GEST-080 |
| HLR-NAV-030 | Active | LLR-STATE-010, LLR-DISP-010, LLR-DISP-030 |
| HLR-NAV-040 | Active | LLR-STATE-010, LLR-STATE-020 |
| HLR-NAV-050 | Active | LLR-DISP-010 |
| HLR-NAV-060 | Active | LLR-DISP-020 |
| HLR-NAV-070 | Active | LLR-A11Y-010, LLR-A11Y-020 |
| HLR-NAV-080 | Active | LLR-LTR-010, LLR-DRAW-020 |
| HLR-NAV-090 | Active | LLR-UNIT-010, LLR-UNIT-020, LLR-UNIT-030, LLR-UNIT-040 |
| HLR-NAV-100 | Active | LLR-LAYV-010, LLR-LAYV-090 |
| HLR-SET-010 | Active | LLR-SETV-010 |
| HLR-SET-020 | Active | LLR-SETS-010, LLR-SETS-030, LLR-SETS-040, LLR-SETST-020, LLR-SETV-020 |
| HLR-SET-030 | Active | LLR-SETS-020, LLR-SETST-010, LLR-SETST-020 |
| HLR-SET-040 | Active | LLR-SETS-050, LLR-SETST-030, LLR-SETV-050 |
| HLR-SET-050 | Active | LLR-VALD-070, LLR-SETV-030 |
| HLR-PERF-010 | Active | LLR-DB-080, LLR-CATI-010, LLR-START-010, LLR-PERF-010 |
| HLR-PERF-020 | Active | LLR-FILL-040, LLR-DB-080, LLR-DRAW-040, LLR-DRAW-070, LLR-PERF-020 |
| HLR-PERF-030 | Active | LLR-CATI-030, LLR-PERF-030 |
| HLR-PLTN-010 | Active | LLR-LIM-020 |
| HLR-PLTN-020 | Active | LLR-LIM-030, LLR-DB-010 |
| HLR-AUTO-010 | Active | LLR-AUTOP-010, LLR-AUTOP-060 |
| HLR-AUTO-020 | Active | LLR-AUTOP-020 |
| HLR-AUTO-030 | Active | LLR-AUTOP-030, LLR-AUTOP-040 |
| HLR-AUTO-040 | Active | LLR-AUTOP-040 |
| HLR-AUTO-050 | Active | LLR-AUTOP-010, LLR-AUTOP-030 |
| HLR-AUTO-060 | Active | LLR-AUTOP-050, LLR-AUTOP-060 |
| HLR-VAR-010 | Active | LLR-VAR-010 |
| HLR-VAR-020 | Active | LLR-VAR-010, LLR-VAR-020 |
| HLR-ROT-010 | Active | LLR-ROT-010 |
| HLR-ROT-020 | Active | LLR-AUTOP-040 |
| HLR-ROT-030 | Active | LLR-ROT-020 |
| HLR-SEAS-010 | Active | LLR-SEAS-010, LLR-SEAS-020 |
| HLR-SEAS-020 | Active | LLR-SEAS-030 |
| HLR-MEM-010 | Active | LLR-MEM-010 |
| HLR-ORNT-010 | Active | LLR-ORNT-010 |
| HLR-ORNT-020 | Active | LLR-ORNT-020 |
| HLR-ORNT-030 | Active | LLR-ORNT-030 |
| HLR-OBST-010 | Active | LLR-OBST-010 |
| HLR-OBST-020 | Active | LLR-OBST-020 |
| HLR-OBST-030 | Active | LLR-OBST-030 |
| HLR-PORT-010 | Active | LLR-PFILE-010, LLR-WEB-020 |
| HLR-PORT-020 | Active | LLR-PFILE-020, LLR-PFILE-030 |
| HLR-PORT-030 | Active | LLR-WEB-010, LLR-WEB-020 |
| HLR-PORT-040 | Active | LLR-WEB-030, LLR-WEB-040 |
| HLR-MEAS-010 | Suspended | deferred (§6) |

## 10. Coverage check

- LLRs: **251**. Duplicate LLR IDs: **0**.
- HLRs: 164 (156 active, 7 future, 1 suspended).
- Active HLRs with no LLR: **0**.
- HLRs intentionally deferred (§6): HLR-CAM-100, HLR-EXP-010, HLR-EXP-020, HLR-EXP-030, HLR-EXP-040, HLR-EXP-050, HLR-EXP-060, HLR-MEAS-010.
- LLRs with no HLR parent: **0**.
- Links to unknown HLR IDs: **0**.

Generated by `tools/req_trace.py`.
