# SGP-SW-HLR-001 — Software High-Level Requirements (HLR)

| Field | Value |
|---|---|
| Document ID | SGP-SW-HLR-001 |
| Revision | A — Proposed |
| Level | HLR (DO-178C software high-level requirements) for the SGP-APP software item |
| Parent document | SGP-SYS-REQ-001 (system requirements, T2) |
| Supersedes | All HLRs of `smart_garden_planner_master_plan.md` (v20.18) and Knowledge Base Part 11. Those HLR IDs are **retired**. Per Project Owner direction, the new HLRs are written from scratch, with no trace to the old HLRs or LLRs. |
| Control category | CC1 |

## How to read this document

- Each HLR states **what the software must do at its boundary** (screens, touch input, stored data,
  device services) in testable terms. How the code does it belongs in the design and the LLRs.
- **`→ T2-…`** at the end of each HLR is its trace to the system requirement(s) it implements. §21 lists the
  full trace in both directions, and shows that every active system requirement is covered.
- **[Derived]** marks an HLR with no direct system parent, which the design needs. Its rationale is given, and
  it is sent to the safety assessment (SGP-SAP-001 §3).
- **[Future]** marks HLRs for features scheduled after the core revamp (export/sharing, corner correction).
  **[Suspended]** marks HLRs waiting on decision D-03 (measurement method).
- `[TBC-nn]` refers to the system-level confirmation list (SGP-SYS-REQ-001 §15). `[H-TBC-nn]` refers to this
  document's own list (§20). Proposed values apply until you confirm or change them.
- Area codes are new, so they can't be confused with the retired v20.18 HLR IDs.

Terms used:
- **Plant:** a placed instance of a variety on a plot.
- **Spacing radius:** the variety's radius in metres.
- **Species code:** the part of a variety code before the first "-".
- **Display unit:** metres or inches, per the setting.
- **Unfinished work:** points of a path or area being drawn, a plant being dragged, or text typed in an
  open dialog.

---

## 1. Plot management (PLOT)

**HLR-PLOT-010** The plot list screen shall be the start screen. It shall show every non-archived plot with
its name, length × width in the display unit, and plant count, ordered by last modification (newest first).
Selecting a plot shall open its layout screen. → T2-FUN-150, T2-CFG-100

**HLR-PLOT-020** When no plot exists, the plot list shall show an explanation and a "Create plot" action.
→ T2-FUN-150

**HLR-PLOT-030** The create-plot screen shall accept:
- a name of 1–60 characters, not only spaces
- a length and a width as decimal numbers in the display unit

The create action shall be enabled only when the name is valid and both dimensions convert to values within
the plot size limits (HLR-SET-020). → T2-FUN-020, T2-VAL-010, T2-CFG-090

**HLR-PLOT-040** A dimension field that is empty, zero, non-numeric or outside the limits shall be marked
invalid, with a message giving the allowed range in the display unit. → T2-VAL-010

**HLR-PLOT-050** When a valid length or width is below 0.30 m or above 200 m `[TBC-18]`, the software shall
ask for confirmation before creating or resizing the plot. It shall do nothing if the user declines.
→ T2-VAL-015

**HLR-PLOT-060** On creation, the software shall store the dimensions in metres (converted per HLR-NAV-090),
record the creation and last-modified date/time, and open the new plot's layout screen.
→ T2-FUN-020, T2-DAT-120, T2-CFG-100

**HLR-PLOT-070** The software shall let the user rename a plot, with the same name rules as HLR-PLOT-030.
→ T2-FUN-070

**HLR-PLOT-080** The software shall let the user change a plot's length and width (same validation as
creation). It shall refuse the change if any plant centre or any part of a path would lie outside the new
boundary, listing the affected plants and paths and changing nothing. Accepted changes keep all plant and
path positions (in metres), and the layout is redrawn at the new scale.
→ T2-FUN-070, T2-VAL-017, T2-INT-010

**HLR-PLOT-090** The software shall let the user delete a plot after a confirmation that states the number of
plants and paths that will be deleted. Deletion removes the plot, its plants, its paths and its photo.
→ T2-FUN-070

**HLR-PLOT-100** The software shall let the user archive and un-archive a plot. Archived plots are hidden from
the plot list unless "Show archived" is on, and are otherwise unchanged. `[TBC-05]` → T2-FUN-070

**HLR-PLOT-110** The software shall let the user set, change or clear a plot's location in one of two forms:
- a 5-digit ZIP code, accepted only if it exists in the climate data
- latitude −90…90 and longitude −180…180, in decimal degrees

→ T2-FUN-090

**HLR-PLOT-120** On a "Use my location" request, the software shall ask for location permission if it isn't
granted. It shall accept a location fix only if its reported horizontal accuracy is within the configured
limit (HLR-SET-020). Otherwise, or if permission is denied or no fix arrives within 30 s [H-TBC-01], it shall
say why and leave manual entry available. → T2-FUN-090, T2-ENV-025

**HLR-PLOT-130** Any function that needs a location (zone, frost dates, planting windows, recommendations,
season length) shall, for a plot without a location, say that a location is needed and offer to set it.
→ T2-FUN-090, T2-DAT-090, T2-DAT-100

## 2. Layout display (LAY)

**HLR-LAY-010** The layout screen shall draw the plot as a rectangle, using a single scale factor for both
axes: at zoom 1, pixels per metre = min(available width ÷ length, available height ÷ width). The plot is
centred in the available area. → T2-FUN-040

**HLR-LAY-020** Each plant shall be drawn at its position as:
- a marker in its variety colour (HLR-VIEW-070), showing the variety letter code (HLR-NAV-080)
- a spacing circle of radius (spacing radius × scale) around the marker

→ T2-FUN-040, T2-FUN-050, T2-ENV-060

**HLR-LAY-030** When the plot has a photo, it shall be drawn behind the plants, stretched to exactly fill the
plot rectangle. → T2-FUN-010, T2-FUN-040

**HLR-LAY-040** The layout screen shall show:
- the plot name
- its dimensions in the display unit
- the plant count
- the active tool (HLR-PLC-010)
- in the Plant tool, the variety currently selected for placement with its spacing radius in the display unit

→ T2-FUN-040, T2-CFG-100

**HLR-LAY-050** A plant that is germination-overdue (HLR-TIME-030) shall be drawn with a red ring around its
marker. → T2-FUN-080

**HLR-LAY-060** A plant flagged in companion conflict (HLR-RULE-070) shall be drawn with a warning symbol.
→ T2-INT-030

## 3. Tools, placement and editing (PLC)

**HLR-PLC-010** The layout screen shall offer four tools: Plant, Path, Area and Move. Exactly one is active.
When a plot is opened, the active tool shall be Plant (so Move is off). → T2-INT-100

**HLR-PLC-020** In the Plant tool, a tap on a point inside the plot shall request placement of the selected
variety at that point `[TBC — gesture per decision D-06: single tap proposed]`. The request is checked by
HLR-RULE-080. If accepted, the plant is stored with today's date as its planting date. If refused, the
refusal message (HLR-RULE-090) is shown and nothing is stored.
→ T2-FUN-030, T2-FUN-040, T2-VAL-020, T2-VAL-040, T2-VAL-050, T2-INT-030

**HLR-PLC-030** A placement request with no variety selected shall open the variety picker (HLR-PLC-100)
instead of placing. → T2-FUN-030

**HLR-PLC-040** In the Plant tool, a tap within 24 dp (on screen) of a plant marker shall select the nearest
such plant and show its details instead of placing:
- variety name
- planting date
- expected harvest date
- spacing radius in the display unit
- germination state

→ T2-DAT-010, T2-FUN-070, T2-FUN-080

**HLR-PLC-050** From the plant details, the user shall be able to change the planting date to any date from
the past up to today + 365 days `[TBC-02]`. All dependent values are recomputed immediately
(HLR-TIME-020/030). → T2-FUN-030, T2-INT-020

**HLR-PLC-060** From the plant details, the user shall be able to change the plant's variety through the
variety picker. The change is checked by HLR-RULE-080, ignoring the plant itself. If accepted, the variety is
replaced and the planting date is set to today [Derived: a new variety means a new sowing]. If refused, the
old variety stays and the refusal message is shown. → T2-FUN-120

**HLR-PLC-070** From the plant details, the user shall be able to delete the plant. → T2-FUN-070

**HLR-PLC-080** In the Move tool, a drag that starts within 24 dp of a plant marker shall move that plant with
the finger. On release, the new position is checked by HLR-RULE-080, ignoring the plant itself:
- if accepted, the new position is stored
- if refused, the plant returns to its original position and the refusal message is shown

A drag that starts elsewhere does nothing. → T2-INT-090, T2-VAL-070

**HLR-PLC-090** The software shall remember the last variety selected for placement across restarts, and
pre-select it, provided that variety still exists. [Derived: avoids placing an unintended variety, a problem
reported in earlier versions.] → T2-FUN-030

**HLR-PLC-100** Wherever a variety is chosen, the software shall present three steps:
1. Category, showing the number of varieties per category
2. Species, showing the number of cultivars per species
3. Cultivar, showing spacing radius (display unit), germination days and days to harvest

Steps 2 and 3 shall offer a case-insensitive substring search within the current step. Each step after the
first shall offer a way back. → T2-VAL-080, T2-CFG-100

**HLR-PLC-110** A plot shall hold at most 500 plants `[TBC-16]`. A placement or fill that would exceed this
shall be refused, or truncated for a fill (HLR-AREA-090), with a message. → T2-CON-080

## 4. Placement rules (RULE)

**HLR-RULE-010 (boundary)** A plant position (x, y), in metres from the plot's top-left corner, is inside the
plot when 0 ≤ x ≤ length and 0 ≤ y ≤ width `[TBC-19: centre rule]`. → T2-VAL-020

**HLR-RULE-020 (spacing)** Two plants A and B are in spacing conflict when
d < (rA + rB) × m − 0.0001 m `[TBC-20]`, where:
- d is the distance between their centres
- rA and rB are their spacing radii
- m is the spacing margin (HLR-SET-020)

Distances shall be computed in double precision. → T2-VAL-040, T2-CFG-040

**HLR-RULE-030 (path clearance)** A plant is in path conflict when its spacing circle overlaps a path:
- for a rectangular path: the distance from the plant centre to the rectangle is less than its radius
- for a line path: the distance from the plant centre to the path's centre line is less than
  (path width ÷ 2 + radius)

→ T2-VAL-050

**HLR-RULE-040 (antagonist relation)** Varieties A and B are antagonists when B's species code appears in A's
antagonist list, or A's species code appears in B's. This makes every cultivar of a species take part in its
species' relations. → T2-DAT-210, T2-INT-030

**HLR-RULE-050 (antagonist conflict)** Two antagonist plants are in companion conflict when
d < 2 × (rA + rB) × m `[TBC-23]`. → T2-INT-030

**HLR-RULE-060 (enforcement)** Companion conflicts are enforced when the software is on the Pro tier with
"Enforce companion rules" on, and always on the Basic and Standard tiers. The stored setting value shall not
disable enforcement below Pro. → T2-CFG-050

**HLR-RULE-070 (not enforced)** When companion conflicts are not enforced, placements in companion conflict
shall be accepted. Every plant involved in at least one companion conflict shall be flagged (HLR-LAY-060).
Flags are recomputed after every change to the plot's plants. `[TBC-24]` → T2-INT-030

**HLR-RULE-080 (validation)** A requested plant position P for variety V shall be accepted only if all of
these hold:
- P is inside the plot (HLR-RULE-010)
- no path conflict (HLR-RULE-030)
- no spacing conflict with any other plant (HLR-RULE-020)
- if enforced, no companion conflict with any other plant (HLR-RULE-050)

"Any other plant" excludes the plant being moved or changed, and includes plants already accepted earlier in
the same fill. This check shall be used for placing, moving, changing variety, filling an area, and Plan B
replacements. → T2-VAL-020, T2-VAL-040, T2-VAL-050, T2-VAL-070, T2-INT-030, T2-FUN-110, T2-FUN-120, T2-FUN-080

**HLR-RULE-090 (refusal message)** A refusal shall report the first failing check in the order boundary,
path, spacing, companion. For spacing and companion conflicts it shall name the nearest conflicting plant's
variety, the actual distance and the required distance, in the display unit. → T2-VAL-040, T2-INT-030, T2-CFG-100

## 5. No-plant paths and area fill (AREA)

**HLR-AREA-010** In the Path tool, rectangle style, a drag shall define a rectangular path:
- The rectangle is clipped to the plot [Derived: paths outside the plot have no meaning].
- It is stored if both sides are ≥ 0.05 m after clipping.
- It is ignored otherwise.

→ T2-FUN-100

**HLR-AREA-020** In the Path tool, line style, each tap inside the plot shall add a point, up to 200 points
[H-TBC-02]. "Finish" (available from 2 points) shall ask for a width in the display unit (default 0.5 m;
must be > 0 and ≤ the plot's smaller dimension), then store the path. "Cancel" discards the points.
→ T2-FUN-100

**HLR-AREA-030** In the Path tool, a tap on an existing path, when no points are being drawn, shall open it
for editing: change the width (line paths) or delete it (both kinds). → T2-FUN-100

**HLR-AREA-040** When a new or widened path would overlap the spacing circle of existing plants, the software
shall list those plants and let the user either keep the path or cancel [H-TBC-03]. [Derived: the system
requirements don't say what happens to existing plants under a new path.] → T2-VAL-050

**HLR-AREA-050** In the Area tool, rectangle shape, a drag shall select a rectangular area inside the plot.
Areas with a side below 0.05 m are ignored. → T2-FUN-110

**HLR-AREA-060** In the Area tool, polygon shape (Standard and Pro tiers only):
- Each tap adds a point, up to 200 [H-TBC-02].
- "Finish" is available from 3 points.
- On the Basic tier the polygon option is shown as locked, with an explanation.

→ T2-FUN-110

**HLR-AREA-070** After an area is selected, the software shall show:
- the chosen variety (default: the selected placement variety)
- the pattern (Rows or Hexagonal)
- the number of plants that will actually be placed, i.e. candidate positions inside the area that pass
  HLR-RULE-080, recomputed whenever variety or pattern changes

→ T2-FUN-110

**HLR-AREA-080** Candidate positions shall be generated with a centre-to-centre spacing
s = 2 × spacing radius × spacing margin:
- **Rows:** a square grid.
- **Hexagonal:** rows s × √3/2 apart, every second row offset by s/2.
- In both cases the first row and column are s/2 from the area's bounding edges.
- For a polygon area, only candidates inside the polygon are kept.

→ T2-FUN-110, T2-CFG-040

**HLR-AREA-090** "Fill" shall store all accepted positions as one change (one undo step), up to the plot
limit (HLR-PLC-110). It shall then report "placed N of M" and switch to the Plant tool. → T2-FUN-110, T2-VAL-030, T2-CON-030

## 6. Views: zoom, ruler, colours, overlays (VIEW)

**HLR-VIEW-010** The layout shall support zoom levels between the configured minimum and maximum
(HLR-SET-020):
- two-finger pinch `[TBC-26]`
- "+" and "−" buttons that change the zoom by the configured step
- a "Recenter" button, shown when the zoom isn't 1 or the view is panned, restoring zoom 1 and the centred
  view

Zooming changes no stored data. → T2-INT-070, T2-CFG-070

**HLR-VIEW-020** When zoomed in, the layout shall pan with a two-finger drag. Panning changes no stored data.
→ T2-INT-070

**HLR-VIEW-030** One-finger gestures shall never change the zoom or pan. → T2-INT-080

**HLR-VIEW-040** The ruler shall run along the top and left edges of the layout, in the display unit, and
stay aligned with the plot at every zoom and pan position:
- Tick interval = the configured base interval ÷ zoom, rounded to the nearest 1, 2 or 5 × 10ⁿ in the display
  unit.
- Label text size = the configured ruler text size.

→ T2-FUN-140, T2-CFG-060, T2-CFG-100

**HLR-VIEW-050** Grid lines inside the plot shall be drawn at the ruler's tick interval. [Derived: keeps grid
and ruler consistent.] → T2-FUN-140

**HLR-VIEW-060** The software shall offer a legend of the varieties placed on the current plot, with each
variety's colour, letter code, name and plant count. → T2-FUN-130

**HLR-VIEW-070** A variety's colour shall be its user-chosen colour (one of 16 presets) when set. Otherwise it
is an automatic colour derived from the variety code, which is the same on every start. → T2-FUN-130

**HLR-VIEW-080** When "Weed-risk area" is switched on:
- The software shall shade the plot area outside all plants' spacing circles and outside all paths
  `[TBC-25]`, semi-transparently.
- It shall label the overlay "Weed-risk area — guidance only".
- It shall show the shaded area in m² or ft² (following the display unit).

The overlay is off whenever a plot is opened, and it doesn't affect any rule. → T2-INT-040

**HLR-VIEW-090** When "Irrigation route" is switched on and the plot has at least one plant, the software
shall draw a line:
- starting at the water-source point
- going to the nearest plant, then repeatedly to the nearest plant not yet visited, until all are visited;
  ties are broken by the lower plant creation order

It shall show the total route length in the display unit and label the overlay "Suggested route". The overlay
is off whenever a plot is opened and doesn't affect any rule. → T2-FUN-060, T2-CFG-100

**HLR-VIEW-100** The water-source point shall default to the plot's top-left corner. The user shall be able
to move it anywhere inside the plot, and it is stored per plot. `[TBC-03]` → T2-FUN-060

## 7. Undo and redo (HIST)

**HLR-HIST-010** Each completed layout change shall be one undo step:
- place, move, delete or change the variety of a plant
- change a planting date
- apply a Plan B option
- add, edit or delete a path
- one area fill

→ T2-VAL-030

**HLR-HIST-020** Undo shall restore the plot's plants and paths exactly as they were before the last undone
step (same plants, positions, varieties, dates and paths). Redo shall re-apply the step exactly. The number of
plants and paths after undo followed by redo shall equal the number before. → T2-VAL-030

**HLR-HIST-030** The undo history shall hold up to the configured depth (HLR-SET-020). The oldest step is
dropped when the depth is exceeded. Any new change clears the redo history. The Undo and Redo controls shall
be disabled when there is nothing to undo or redo. → T2-VAL-030, T2-CFG-080

**HLR-HIST-040** Each undo or redo shall be stored completely or not at all (HLR-STOR-010). → T2-CON-030

**HLR-HIST-050** The undo history belongs to the open plot, and is kept when the app is interrupted and
restored (HLR-NAV-040). It is cleared when the user leaves the plot [H-TBC-04]. → T2-VAL-030, T2-ENV-020

## 8. Dates, germination and Plan B (TIME)

**HLR-TIME-010** Planting dates shall be stored as calendar dates (year, month, day), not as instants. "Today"
is the current date in the device's time zone. Changing the clock or the time zone shall not change stored
planting dates. → T2-DAT-160

**HLR-TIME-020** The expected harvest date shall be the planting date plus the variety's days to harvest, in
calendar days, displayed in the device's date format. → T2-DAT-010

**HLR-TIME-030** A plant is germination-overdue when (today − planting date) in days > the variety's
germination days, and no germination outcome is recorded for it. The state shall be re-evaluated when a plot
is opened, when the date changes while it is open, and after a planting date or variety change.
→ T2-FUN-080, T2-INT-020, T2-DAT-160

**HLR-TIME-040** Selecting an overdue plant shall offer:
- "It germinated": records the outcome, and the red ring is removed
- "It failed — show options": opens HLR-TIME-050
- "Close"

→ T2-FUN-080

**HLR-TIME-050** On a declared failure, the software shall show side by side each applicable option:
- **(a) Fast-track:** among varieties of the same species code whose days to harvest is less than the failed
  variety's and ≤ the remaining season (HLR-TIME-080), the one with the shortest days to harvest (ties by
  name). If there is none, the same selection among varieties of the same botanical family.
- **(b) Nursery transplant:** the same variety, offered when the variety is marked suitable for
  transplanting (default: suitable).
- **(c) Catch crop:** up to 3 varieties of a different species whose days to harvest ≤ the remaining season
  and that pass HLR-RULE-080 at the plant's position, ordered by days to harvest, longest first
  [H-TBC-05: "longest that still fits" makes the most of the season].

If the remaining season is 0 days or less, (a) and (c) are not offered and the software says why.
→ T2-FUN-080, T2-DAT-065

**HLR-TIME-060** Choosing an option shall replace the plant's variety (for (a) and (c), subject to
HLR-RULE-080), set the planting date to today, and clear the recorded outcome, as one undo step.
→ T2-FUN-080

**HLR-TIME-070** Options (a) and (c) shall be computed from the catalog at the time of the failure. They
don't depend on pre-stored alternates. → T2-FUN-080

**HLR-TIME-080** Remaining season = days from today to the plot's first fall frost date this year (climate
data, HLR-CLIM-010). If the plot has no location, or that date has passed, it is the days to the configured
season-end date (HLR-SET-020, default 31 October `[TBC-06]`). → T2-FUN-080

**HLR-TIME-090** For a plot with a location, the planting window of a variety shall run from the last spring
frost date (plus a per-variety offset, default 0 days `[TBC-10]`) to the first fall frost date minus its days
to harvest. The window shall be shown in the variety picker and in the plant details. An empty window shall be
shown as "too late this season". → T2-DAT-090

**HLR-TIME-100** For a plot with a location, the software shall offer a list of varieties whose zone range
contains the plot's zone and whose planting window (HLR-TIME-090) contains today or a future date this season.
When the user places a perennial whose zone range doesn't contain the plot's zone, the software shall warn but
still allow it. → T2-DAT-100

## 9. Catalog and encyclopedia (ENC)

**HLR-ENC-010** The software shall include three bundled catalog tiers:
- Basic: 250 varieties
- Standard: 600 varieties, containing all of Basic
- Pro: 2,936 varieties, containing all of Standard

Basic is active after first installation. → T2-DAT-190

**HLR-ENC-020** Switching tier shall, as one change:
- add or update the new tier's bundled varieties
- remove bundled varieties that are not in the new tier, are not planted on any plot, and were not edited by
  the user

It shall show progress, report how many varieties were kept because they are planted, and log the switch
(HLR-PROT-060). It shall never fail because a variety is in use. → T2-DAT-200, T2-SEC-030, T2-CON-080

**HLR-ENC-030** Varieties created or edited by the user shall never be changed or removed by a tier switch.
→ T2-DAT-200

**HLR-ENC-040** The encyclopedia shall list all varieties (bundled and user), with:
- case-insensitive substring search over name and botanical family
- a category filter
- smooth scrolling of the full Pro catalog

→ T2-DAT-150

**HLR-ENC-050** A variety's detail view shall show:
- name, category, lifecycle, botanical family
- hardiness zone range
- spacing radius (display unit)
- germination days and germination conditions (soil temperature range, sowing depth, light)
- days to harvest
- companions and antagonists by species name
- pests, care guidance, watering strategy

Fields with no data shall show "Not on file". → T2-DAT-020, T2-DAT-030, T2-DAT-040, T2-DAT-050, T2-DAT-150, T2-CFG-100

**HLR-ENC-060** Companion and antagonist species codes shall be shown as species names, using a species list
shipped with the app, whatever the active tier. Raw codes shall never be shown. → T2-DAT-030, T2-DAT-220

**HLR-ENC-070** The software shall let the user create a variety, with these rules (save is enabled only when
all are valid):
- a code not used by any other variety
- a name of 1–60 characters
- a botanical family chosen from a fixed list including "Other"
- a category
- a spacing radius of 0.02–10 m in the display unit
- germination days 1–365
- days to harvest 1–3650
- a zone range 1–13 with minimum ≤ maximum
- companions and antagonists chosen from the species list
- pest, care and watering text
- an optional colour

→ T2-DAT-060, T2-DAT-065, T2-DAT-220

**HLR-ENC-080** The software shall let the user edit any variety, with the same rules. Editing a bundled
variety marks it as user-owned (HLR-ENC-030). → T2-DAT-060, T2-DAT-200

**HLR-ENC-090** The software shall let the user delete a user-created variety after confirmation. Deletion
shall be refused, naming the plots where it is used, if the variety is planted. Bundled varieties can't be
deleted [Derived: they are restored by the tier]. → T2-DAT-060

**HLR-ENC-100** The build shall fail if any bundled catalog entry breaks the data rules:
- duplicate code
- a missing field
- a value outside HLR-ENC-070's ranges
- a companion or antagonist code not in the species list

→ T2-DAT-220

**HLR-ENC-110** The variety detail view shall state that spacing, germination and harvest values are planning
estimates, and name their source. → T2-DAT-220

## 10. Climate data (CLIM)

**HLR-CLIM-010** The software shall include climate data giving, for each US 5-digit ZIP code, the USDA
hardiness zone (e.g. "7b") and the average last spring and first fall frost dates, with the data source and
version shown in the About screen. `[TBC-09]` → T2-DAT-080

**HLR-CLIM-020** For a latitude/longitude location, the software shall use the climate data of the nearest ZIP
code centroid within 50 km [H-TBC-06]. If there is none, it reports "location not covered". → T2-DAT-080

**HLR-CLIM-030** The layout screen of a plot with a location shall show its zone and frost dates.
→ T2-DAT-080

## 11. Camera and photos (CAM)

**HLR-CAM-010** The camera screen shall show the rear camera's live preview with a capture button:
- It asks for camera permission when not granted.
- If permission is denied, it explains why the camera is needed and offers the system settings.
- On a device without a usable rear camera, the photo actions are replaced by an explanation.

→ T2-FUN-010, T2-CON-060

**HLR-CAM-020** The software shall also let the user choose an existing photo through the system photo picker,
without asking for storage permission. `[TBC-01]` → T2-FUN-010

**HLR-CAM-030** A new photo (captured or picked) shall be:
- rotated upright according to its orientation data
- scaled so its longest side is ≤ 1080 px
- compressed as JPEG, starting at quality 85 and lowering it in steps of 5 (minimum 60) until the file is
  ≤ 1 MB `[TBC-13]`
- stored encrypted (HLR-PROT-020)

The plot's previous photo is replaced only after the new one has been stored. → T2-CON-040, T2-SEC-010

**HLR-CAM-040** If a photo can't be processed or stored, for any reason including insufficient memory, the
software shall keep the previous photo (or none), show a message, and keep running. → T2-ERR-030

**HLR-CAM-050** The software shall let the user replace or remove a plot's photo. → T2-FUN-010

**HLR-CAM-060** While the camera screen is open on a device with a light sensor:
- The software shall sample ambient light at ≥ 5 Hz and average the last 5 samples.
- When the average is below the configured threshold (HLR-SET-020), it shows a low-light warning.
- The warning is removed when the average is at or above the threshold.
- Capture stays enabled.

Without a light sensor, no warning is shown and the capability screen notes it. → T2-ENV-030, T2-CON-060

**HLR-CAM-070** When the camera screen or photo picker is opened, and before each capture, the software shall
check free storage. If free ÷ total device storage is below the configured floor (HLR-SET-020), it shows a
warning and disables capture and picking until the ratio is above the floor. Other functions are unaffected.
→ T2-ERR-020

**HLR-CAM-080** When the camera screen is opened with the battery below the configured threshold
(HLR-SET-020) and not charging, the software shall show a warning explaining the risk, with "Continue"
(opens the camera) and "Cancel" (returns). Battery level shall never disable the camera by itself. Each
"Continue" is logged with date/time, battery % and plot (HLR-PROT-060).
→ T2-SAF-010, T2-SAF-020, T2-SAF-030, T2-SAF-040

**HLR-CAM-090** If the photo can't be drawn on the layout, the layout shall be shown without it, with a
notice. → T2-ERR-030

**HLR-CAM-100 [Future]** The software shall let the user mark the four corners of the plot on its photo, and
shall then display the photo perspective-corrected, so that the marked corners map to the plot rectangle's
corners. The original photo and the corner points are stored. `[TBC-27]` → T2-PRE-040

## 12. Device capabilities (CAPS)

**HLR-CAPS-010** At every start, the software shall determine whether these are present and usable: a rear
camera, a light sensor, a location provider, and the camera and location permission states. It stores the
result for use by the other functions. → T2-CON-050

**HLR-CAPS-020** Functions that need a missing, unusable or denied capability shall be shown disabled, with the
reason. All other functions remain available. → T2-CON-060

## 13. Storage and data integrity (STOR)

**HLR-STOR-010** Every completed change (plot, plant, path, variety, setting, undo/redo) shall be written to
permanent storage before the software shows it as done. A change that affects several records shall be
written as one transaction, so that after a forced stop at any moment the stored data holds either all of it
or none of it. → T2-CON-030

**HLR-STOR-020** Stored values shall use:
- distances in metres
- durations in days
- planting dates as calendar dates
- timestamps in UTC

→ T2-CFG-100, T2-DAT-160, T2-PLT-010

**HLR-STOR-030** On first start after an update, the software shall migrate stored data from any schema
version released since v20.20 `[TBC-14]`, keeping all plots, plants, paths, photos, user varieties and
settings. If the stored data is from a newer, unknown version, the software shall say so and not modify it
[Derived: prevents data loss on downgrade]. → T2-CON-070

**HLR-STOR-040** Deleting a plot shall delete its plants, paths and photo. A variety used by any plant shall
not be deletable. → T2-FUN-070, T2-DAT-060

**HLR-STOR-050** If reading or writing stored data fails, the software shall:
- cancel the action in progress
- leave the stored data as it was before the action
- show a message with an error code and a suggested action (e.g. free storage, restart)
- keep running

→ T2-ERR-010

**HLR-STOR-060** Besides the plant limit (HLR-PLC-110), a plot shall hold at most 100 paths [H-TBC-07]. A
path or polygon has at most 200 points [H-TBC-02]. Attempts beyond these limits are refused with a message.
[Derived: bounds memory use.] → T2-CON-080

## 14. Protection: encryption, backup, audit, privacy (PROT)

**HLR-PROT-010** The stored database shall be encrypted with a 256-bit key generated randomly on the device.
That key shall be stored only in wrapped form, encrypted by a key held in the device's hardware-backed
keystore (a dedicated security chip where available, otherwise the trusted execution environment). The
unwrapped key shall never be written to storage or logs. → T2-SEC-010

**HLR-PROT-020** Plot photos and the audit log shall be stored encrypted with keystore-protected keys.
`[TBC-28]` → T2-SEC-010

**HLR-PROT-030** The database, the wrapped key file, photos and the audit log shall be excluded from cloud
backup and from device-to-device transfer. → T2-SEC-050

**HLR-PROT-040** If at start-up the keystore key is missing, the wrapped key can't be decrypted, or the
database can't be opened with the key, the software shall show a screen explaining that the stored data can't
be read on this device. It offers two choices:
- "Start with empty data": the old files are renamed and kept until the user deletes them from Settings
- "Close app"

It shall not crash and shall not delete data without that confirmation. → T2-SEC-050, T2-ERR-010

**HLR-PROT-050** The software shall not declare the Internet permission and shall make no network
connections. The built app shall contain no analytics, telemetry, crash-reporting or advertising components.
→ T2-CON-010, T2-CON-020

**HLR-PROT-060** The software shall append an audit log entry for each of these events, with UTC date/time,
event type and details:
- data reset (HLR-PROT-040)
- key-loss detection
- battery override (HLR-CAM-080)
- catalog tier switch
- import accepted or rejected

Each entry shall include a SHA-256 hash of the previous entry's hash plus its own content. → T2-SEC-030, T2-SAF-040

**HLR-PROT-070** The About screen shall show the audit log. It shall also verify the hash chain on request,
reporting either "intact" or the first entry that fails. → T2-SEC-030

**HLR-PROT-080** When the audit log exceeds 1 MB [H-TBC-08], the software shall start a new file whose first
entry records the last hash of the previous file. The previous file is kept. [Derived: bounds storage.]
→ T2-SEC-030

## 15. Export, import and sharing (EXP) [Future]

**HLR-EXP-010 [Future]** The software shall export one or all plots to a single file chosen through the
system file picker. The file contains plots, plants, paths, the user varieties they use, photos, owner name,
role and timestamps, in a documented, versioned format (JSON + images). → T2-DAT-110, T2-DAT-120, T2-PLT-020

**HLR-EXP-020 [Future]** Export files shall be signed with a per-installation signing key held in the
keystore. The file includes the public key and owner name, so that any device can verify integrity and origin.
→ T2-SEC-040

**HLR-EXP-030 [Future]** On import, the software shall verify the format version, the signature and every
value range before changing anything. Any failure rejects the whole file, with the reason shown and logged.
→ T2-SEC-020, T2-SEC-030

**HLR-EXP-040 [Future]** If an imported plot has the same identity as a local plot, the software shall show
the differences (plants, paths, dimensions, dates) and apply the user's choice: keep local, take imported, or
keep both. → T2-DAT-130

**HLR-EXP-050 [Future]** Imported plots shall carry a role (Owner, Contributor or Viewer), enforced as:
- **Viewer:** no changes
- **Contributor:** no resize, delete or re-share of the plot
- **Owner:** all actions

`[TBC-11]` → T2-DAT-140

**HLR-EXP-060 [Future]** The owner display name shall be set in Settings, and recorded on plots created
afterwards. → T2-DAT-120

## 16. Navigation, lifecycle and display (NAV)

**HLR-NAV-010** The software shall provide these screens:
- plot list (start)
- create/edit plot
- layout
- camera
- encyclopedia with variety detail and variety editor
- settings
- about/audit log
- data-unreadable (HLR-PROT-040)

[Derived: screen structure.] → T2-FUN-150, T2-DAT-150, T2-CFG-010, T2-SEC-030

**HLR-NAV-020** Back (button or gesture) shall return to the previous screen. On the plot list it closes the
app. On the layout screen with unfinished work, it first asks "Discard unfinished …?", and only goes back if
confirmed. → T2-ENV-040

**HLR-NAV-030** Rotating the device, or changing display or font size, shall keep the current screen, plot,
tool, zoom level, the plot point at the centre of the view, and any unfinished work. It shall change no stored
data. → T2-ENV-010

**HLR-NAV-040** After the system stops the app in the background, reopening it shall restore:
- the same screen and plot
- the tool, zoom and view centre
- unfinished points
- text typed in an open dialog
- the undo history (HLR-HIST-050)

→ T2-ENV-020

**HLR-NAV-050** On the target phone, in portrait and landscape, no control or content shall be drawn under the
status bar, camera cut-out or navigation bar. `[TBC-21]` → T2-ENV-050, T2-ENV-010

**HLR-NAV-060** At the largest system font and display size, every control shall remain visible and usable.
Screens whose content doesn't fit shall scroll. → T2-ENV-050

**HLR-NAV-070** Every interactive control shall have a touch area of at least 48 × 48 dp and a spoken label.
Plants shall be announced by a screen reader as "variety name, planted date". → T2-ENV-060

**HLR-NAV-080** Each variety shall have a short letter code (1–3 characters), unique among the varieties on
the plot, drawn on its plant markers and shown in the legend. `[TBC-22]` → T2-ENV-060, T2-FUN-130

**HLR-NAV-090** Every distance the software shows or accepts shall be in the display unit:
- metres shown with 2 decimals
- inches shown with 1 decimal
- conversion uses 1 in = 0.0254 m exactly

Values typed by the user are converted to metres once, when stored. → T2-CFG-100

**HLR-NAV-100** The layout screen shall offer a one-tap switch between metres and inches, which changes the
display unit setting. → T2-CFG-100

## 17. Settings (SET)

**HLR-SET-010** The settings screen shall group the settings listed in HLR-SET-020 by topic. It shall hide settings whose
feature is unavailable on the device or not yet present `[TBC-30]`. → T2-CFG-010

**HLR-SET-020** The settings, their ranges and defaults shall be:

| Setting | Range | Default | Used by | → T2 |
|---|---|---|---|---|
| Display unit | metres / inches | metres | NAV-090 | CFG-100 |
| Catalog tier | Basic / Standard / Pro | Basic | ENC-020 | DAT-190 |
| Spacing margin | 0.30–2.00 × (step 0.05) | 1.00 × | RULE-020, AREA-080 | CFG-040 |
| Enforce companion rules | on / off (Pro only) | on | RULE-060 | CFG-050 |
| Ruler base tick interval | 0.25–5 m | 1 m | VIEW-040 | CFG-060 |
| Ruler text size | 8–28 sp | 14 sp | VIEW-040 | CFG-060 |
| Minimum zoom | 0.10–1.00 × | 0.50 × | VIEW-010 | CFG-070 |
| Maximum zoom | 1–8 × | 3 × | VIEW-010 | CFG-070 |
| Zoom step | 0.05–1.00 × | 0.25 × | VIEW-010 | CFG-070 |
| Undo depth | 5–100 steps | 25 | HIST-030 | CFG-080 |
| Minimum plot dimension | 0.01–5 m | 0.05 m | PLOT-030 | CFG-090 |
| Maximum plot dimension | 10–2000 m | 1000 m | PLOT-030 | CFG-090 |
| Location accuracy limit | 3–100 m | 15 m | PLOT-120 | ENV-025 |
| Low-light threshold | 1–50 lux | 10 lux | CAM-060 | ENV-030 |
| Storage floor | 1–25 % | 5 % | CAM-070 | ERR-020 |
| Low-battery threshold | 5–50 % | 15 % `[TBC-29]` | CAM-080 | SAF-020 |
| Season end date | any day-month | 31 Oct `[TBC-06]` | TIME-080 | FUN-080 |
| Owner display name [Future] | 0–40 chars | empty | EXP-060 | DAT-120 |

The minimum zoom shall always be below the maximum zoom, and the minimum plot dimension below the maximum.
A change that would break this is refused, with a message.
→ T2-CFG-040, T2-CFG-050, T2-CFG-060, T2-CFG-070, T2-CFG-080, T2-CFG-090, T2-CFG-100, T2-ENV-025, T2-ENV-030, T2-ERR-020, T2-SAF-020, T2-DAT-190, T2-FUN-080

**HLR-SET-030** A setting change shall be stored immediately and apply to every screen from then on, including
after restart and update. A stored value that is missing, unreadable or out of range shall be replaced by that
setting's default, without affecting other settings. → T2-CFG-020

**HLR-SET-040** "Reset all settings" shall ask for confirmation `[TBC-31]`, then restore every default. It
shall not change plots, plants, paths or varieties. → T2-CFG-030

**HLR-SET-050** On the Basic and Standard tiers, "Enforce companion rules" shall be shown as on and locked,
with the explanation that it can be changed on the Pro tier. → T2-CFG-050

## 18. Performance and platform independence (PERF / PLTN)

**HLR-PERF-010** On the target phone, the plot list shall be usable within 2 s of a cold start `[TBC-15]`,
except on the very first start, when catalog installation may take up to 5 s with progress shown [H-TBC-09].
→ T2-CON-080

**HLR-PERF-020** On the target phone, with 500 plants `[TBC-16]` on a plot and overlays off, the layout shall
redraw during zoom, pan and drag within the display's frame time (no dropped frames at 60 Hz). With overlays on,
it shall stay within 2 frame times [H-TBC-10]. → T2-CON-080

**HLR-PERF-030** A tier switch to Pro shall finish within 10 s on the target phone, with progress shown
`[TBC-17]`. → T2-CON-080

**HLR-PLTN-010** The planning rules and calculations shall be implemented in a component with no dependency on
the user interface or on Android, and shall give identical results when run on a desktop JVM:
- units
- boundary, spacing, path and companion checks
- grid generation
- dates, germination and Plan B
- planting windows
- irrigation route
- weed-risk area

→ T2-PLT-030

**HLR-PLTN-020** The stored data model shall be documented with each field's meaning, type, unit and range,
independently of Android types. → T2-PLT-010

## 19. Suspended — measurement (MEAS) [Suspended: decision D-03]

**HLR-MEAS-010 [Suspended]** Placeholder for the measuring method's software requirements:
- calibration flow (T2-PRE-010)
- measurement with uncertainty (T2-PRE-020)
- accept/edit before use (T2-FUN-025)
- warning and plant removal when the scale source changes (T2-VAL-012)

It is written once the method is chosen. T2-PRE-030 is expected to be deleted.
→ T2-FUN-025, T2-PRE-010, T2-PRE-020, T2-PRE-030, T2-VAL-012

## 20. HLR-level values to confirm `[H-TBC]`

| H-TBC | HLR | Question | Proposal |
|---|---|---|---|
| 01 | PLOT-120 | How long to wait for a location fix | 30 s |
| 02 | AREA-020/060, STOR-060 | Maximum points per path or polygon | 200 |
| 03 | AREA-040 | New path over existing plants: warn and allow, or refuse? | Warn, list them, let the user choose |
| 04 | HIST-050 | Keep undo history after leaving a plot? | No, clear it on leaving |
| 05 | TIME-050 | Catch-crop order | Longest days-to-harvest that still fits first |
| 06 | CLIM-020 | Maximum distance to the nearest ZIP for lat/lon | 50 km |
| 07 | STOR-060 | Maximum paths per plot | 100 |
| 08 | PROT-080 | Audit log file size before rotation | 1 MB |
| 09 | PERF-010 | First-start time with catalog installation | ≤ 5 s |
| 10 | PERF-020 | Frame budget with overlays on | 2 frames |

## 21. Traceability T2 → HLR

Generated by script from the `→` links above. §22 gives the coverage check.

<!-- TRACE-TABLE -->
| T2 requirement | Title | T2 status | Implemented by HLR |
|---|---|---|---|
| T2-FUN-010 | Plot photo | Active | HLR-LAY-030, HLR-CAM-010, HLR-CAM-020, HLR-CAM-050 |
| T2-FUN-020 | Plot dimensions | Active | HLR-PLOT-030, HLR-PLOT-060 |
| T2-FUN-025 | Measuring plot dimensions with the device | Suspended | HLR-MEAS-010 |
| T2-FUN-030 | Variety and planting date per plant | Active | HLR-PLC-020, HLR-PLC-030, HLR-PLC-050, HLR-PLC-090 |
| T2-FUN-040 | To-scale layout display | Active | HLR-LAY-010, HLR-LAY-020, HLR-LAY-030, HLR-LAY-040, HLR-PLC-020 |
| T2-FUN-050 | Spacing zone display | Active | HLR-LAY-020 |
| T2-FUN-060 | Irrigation route suggestion | Active | HLR-VIEW-090, HLR-VIEW-100 |
| T2-FUN-070 | Edit and delete plots and plants | Active | HLR-PLOT-070, HLR-PLOT-080, HLR-PLOT-090, HLR-PLOT-100, HLR-PLC-040, HLR-PLC-070, HLR-STOR-040 |
| T2-FUN-080 | Germination alert and Plan B | Active | HLR-LAY-050, HLR-PLC-040, HLR-RULE-080, HLR-TIME-030, HLR-TIME-040, HLR-TIME-050, HLR-TIME-060, HLR-TIME-070, HLR-TIME-080, HLR-SET-020 |
| T2-FUN-090 | Plot location | Active | HLR-PLOT-110, HLR-PLOT-120, HLR-PLOT-130 |
| T2-FUN-100 | No-plant paths | Active | HLR-AREA-010, HLR-AREA-020, HLR-AREA-030 |
| T2-FUN-110 | Auto-populate an area | Active | HLR-RULE-080, HLR-AREA-050, HLR-AREA-060, HLR-AREA-070, HLR-AREA-080, HLR-AREA-090 |
| T2-FUN-120 | Change a plant's variety | Active | HLR-PLC-060, HLR-RULE-080 |
| T2-FUN-130 | Variety colours and legend | Active | HLR-VIEW-060, HLR-VIEW-070, HLR-NAV-080 |
| T2-FUN-140 | Ruler | Active | HLR-VIEW-040, HLR-VIEW-050 |
| T2-FUN-150 | Plot list | Active | HLR-PLOT-010, HLR-PLOT-020, HLR-NAV-010 |
| T2-DAT-010 | Harvest date | Active | HLR-PLC-040, HLR-TIME-020 |
| T2-DAT-020 | Pests | Active | HLR-ENC-050 |
| T2-DAT-030 | Companions and antagonists | Active | HLR-ENC-050, HLR-ENC-060 |
| T2-DAT-040 | Care and watering guidance | Active | HLR-ENC-050 |
| T2-DAT-050 | Germination conditions | Active | HLR-ENC-050 |
| T2-DAT-060 | Custom varieties | Active | HLR-ENC-070, HLR-ENC-080, HLR-ENC-090, HLR-STOR-040 |
| T2-DAT-065 | Botanical family is mandatory | Active | HLR-TIME-050, HLR-ENC-070 |
| T2-DAT-070 | Concurrent fallback pathways | Merged | — (merged into T2-FUN-080) |
| T2-DAT-080 | Offline climate data | Active | HLR-CLIM-010, HLR-CLIM-020, HLR-CLIM-030 |
| T2-DAT-090 | Planting windows | Active | HLR-PLOT-130, HLR-TIME-090 |
| T2-DAT-100 | Climate-suited recommendations | Active | HLR-PLOT-130, HLR-TIME-100 |
| T2-DAT-110 | Export and import | Active | HLR-EXP-010 |
| T2-DAT-120 | Ownership information | Active | HLR-PLOT-060, HLR-EXP-010, HLR-EXP-060 |
| T2-DAT-130 | Import conflicts | Active | HLR-EXP-040 |
| T2-DAT-140 | Roles | Active | HLR-EXP-050 |
| T2-DAT-150 | Encyclopedia | Active | HLR-ENC-040, HLR-ENC-050, HLR-NAV-010 |
| T2-DAT-160 | Dates and time zones | Active | HLR-TIME-010, HLR-TIME-030, HLR-STOR-020 |
| T2-DAT-190 | Tiered catalog | Active | HLR-ENC-010, HLR-SET-020 |
| T2-DAT-200 | Tier switch keeps user data | Active | HLR-ENC-020, HLR-ENC-030, HLR-ENC-080 |
| T2-DAT-210 | Species-level companion rules | Active | HLR-RULE-040 |
| T2-DAT-220 | Catalog data quality | Active | HLR-ENC-060, HLR-ENC-070, HLR-ENC-100, HLR-ENC-110 |
| T2-CON-010 | Offline only | Active | HLR-PROT-050 |
| T2-CON-020 | No tracking | Active | HLR-PROT-050 |
| T2-CON-030 | Edits are saved immediately and completely | Active | HLR-AREA-090, HLR-HIST-040, HLR-STOR-010 |
| T2-CON-040 | Photo size limit | Active | HLR-CAM-030 |
| T2-CON-050 | Capability check | Active | HLR-CAPS-010 |
| T2-CON-060 | Missing hardware | Active | HLR-CAM-010, HLR-CAM-060, HLR-CAPS-020 |
| T2-CON-070 | Data kept across app updates | Active | HLR-STOR-030 |
| T2-CON-080 | Responsiveness | Active | HLR-PLC-110, HLR-ENC-020, HLR-STOR-060, HLR-PERF-010, HLR-PERF-020, HLR-PERF-030 |
| T2-VAL-010 | Dimensions required | Active | HLR-PLOT-030, HLR-PLOT-040 |
| T2-VAL-012 | Changing the scale source | Suspended | HLR-MEAS-010 |
| T2-VAL-015 | Unusual dimensions need confirmation | Active | HLR-PLOT-050 |
| T2-VAL-017 | Resizing must keep plants inside | Active | HLR-PLOT-080 |
| T2-VAL-020 | Plants stay inside the plot | Active | HLR-PLC-020, HLR-RULE-010, HLR-RULE-080 |
| T2-VAL-030 | Undo and redo | Active | HLR-AREA-090, HLR-HIST-010, HLR-HIST-020, HLR-HIST-030, HLR-HIST-050 |
| T2-VAL-040 | Spacing rule | Active | HLR-PLC-020, HLR-RULE-020, HLR-RULE-080, HLR-RULE-090 |
| T2-VAL-050 | Paths are kept clear | Active | HLR-PLC-020, HLR-RULE-030, HLR-RULE-080, HLR-AREA-040 |
| T2-VAL-070 | Moved plants follow the same rules | Active | HLR-PLC-080, HLR-RULE-080 |
| T2-VAL-080 | Choosing a variety | Active | HLR-PLC-100 |
| T2-ENV-010 | Orientation never corrupts the layout | Active | HLR-NAV-030, HLR-NAV-050 |
| T2-ENV-020 | Interruptions don't lose work | Active | HLR-HIST-050, HLR-NAV-040 |
| T2-ENV-025 | Location accuracy gate | Active | HLR-PLOT-120, HLR-SET-020 |
| T2-ENV-030 | Low-light warning | Active | HLR-CAM-060, HLR-SET-020 |
| T2-ENV-040 | Back button behaviour | Active | HLR-NAV-020 |
| T2-ENV-050 | Display adaptation | Active | HLR-NAV-050, HLR-NAV-060 |
| T2-ENV-060 | Accessibility | Active | HLR-LAY-020, HLR-NAV-070, HLR-NAV-080 |
| T2-INT-010 | Resizing redraws at the new scale | Active | HLR-PLOT-080 |
| T2-INT-020 | Changing a planting date updates dependent dates | Active | HLR-PLC-050, HLR-TIME-030 |
| T2-INT-030 | Companion conflicts | Active | HLR-LAY-060, HLR-PLC-020, HLR-RULE-040, HLR-RULE-050, HLR-RULE-070, HLR-RULE-080, HLR-RULE-090 |
| T2-INT-040 | Weed-risk overlay | Active | HLR-VIEW-080 |
| T2-INT-070 | Zoom and pan | Active | HLR-VIEW-010, HLR-VIEW-020 |
| T2-INT-080 | Zoom and pan are never accidental | Active | HLR-VIEW-030 |
| T2-INT-090 | Move plants | Active | HLR-PLC-080 |
| T2-INT-100 | Move is locked by default | Active | HLR-PLC-010 |
| T2-PRE-010 | Calibration before measuring | Suspended | HLR-MEAS-010 |
| T2-PRE-020 | Measurements show their uncertainty | Active | HLR-MEAS-010 |
| T2-PRE-030 | IMU as primary tracking | Suspended | HLR-MEAS-010 |
| T2-PRE-040 | Corner markers to correct the photo | Active | HLR-CAM-100 |
| T2-ERR-010 | Storage errors fail safely | Active | HLR-STOR-050, HLR-PROT-040 |
| T2-ERR-020 | Low storage blocks new photos | Active | HLR-CAM-070, HLR-SET-020 |
| T2-ERR-030 | Image processing failure | Active | HLR-CAM-040, HLR-CAM-090 |
| T2-SEC-010 | Encrypted data | Active | HLR-CAM-030, HLR-PROT-010, HLR-PROT-020 |
| T2-SEC-020 | Validate imports | Active | HLR-EXP-030 |
| T2-SEC-030 | Audit log | Active | HLR-ENC-020, HLR-PROT-060, HLR-PROT-070, HLR-PROT-080, HLR-EXP-030, HLR-NAV-010 |
| T2-SEC-040 | Signed exports | Active | HLR-EXP-020 |
| T2-SEC-050 | Backups and lost keys | Active | HLR-PROT-030, HLR-PROT-040 |
| T2-SAF-010 | Low battery never blocks the camera | Active | HLR-CAM-080 |
| T2-SAF-020 | Low battery warning | Active | HLR-CAM-080, HLR-SET-020 |
| T2-SAF-030 | Continue or cancel | Active | HLR-CAM-080 |
| T2-SAF-040 | Record overrides | Active | HLR-CAM-080, HLR-PROT-060 |
| T2-PLT-010 | Platform-neutral data model | Active | HLR-STOR-020, HLR-PLTN-020 |
| T2-PLT-020 | Documented exchange format | Active | HLR-EXP-010 |
| T2-PLT-030 | Rules independent of the screen | Active | HLR-PLTN-010 |
| T2-CFG-010 | One settings screen | Active | HLR-NAV-010, HLR-SET-010 |
| T2-CFG-020 | Settings persist | Active | HLR-SET-030 |
| T2-CFG-030 | Reset to defaults | Active | HLR-SET-040 |
| T2-CFG-040 | Adjustable spacing strictness | Active | HLR-RULE-020, HLR-AREA-080, HLR-SET-020 |
| T2-CFG-050 | Companion rule toggle | Active | HLR-RULE-060, HLR-SET-020, HLR-SET-050 |
| T2-CFG-060 | Ruler settings | Active | HLR-VIEW-040, HLR-SET-020 |
| T2-CFG-070 | Zoom settings | Active | HLR-VIEW-010, HLR-SET-020 |
| T2-CFG-080 | Undo depth | Active | HLR-HIST-030, HLR-SET-020 |
| T2-CFG-090 | Plot size limits | Active | HLR-PLOT-030, HLR-SET-020 |
| T2-CFG-100 | Display unit | Active | HLR-PLOT-010, HLR-PLOT-060, HLR-LAY-040, HLR-PLC-100, HLR-RULE-090, HLR-VIEW-040, HLR-VIEW-090, HLR-ENC-050, HLR-STOR-020, HLR-NAV-090, HLR-NAV-100, HLR-SET-020 |

## 22. Coverage check

- HLRs: **140** (10 contain a derived element: HLR-PLC-060, HLR-PLC-090, HLR-AREA-010, HLR-AREA-040, HLR-VIEW-050, HLR-ENC-090, HLR-STOR-030, HLR-STOR-060, HLR-PROT-080, HLR-NAV-010).
- System requirements: 99 in SGP-SYS-REQ-001 (94 active, 4 suspended, 1 merged).
- Active or suspended system requirements with no HLR: **0**.
- HLRs with no system parent: **0**.
- Trace links to unknown system IDs: **0**.
