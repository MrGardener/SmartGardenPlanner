# SGP-SYS-REQ-001 — System Requirements (T2), Re-baselined

| Field | Value |
|---|---|
| Document ID | SGP-SYS-REQ-001 |
| Revision | A — Proposed (awaiting Project Owner review) |
| Level | T2 — system requirements (ARP4754A), per SGP-SRS-STD-001 |
| Supersedes | T2 section of `smart_garden_planner_master_plan.md` (v20.18) and the T2 entries in Knowledge Base Part 11 |
| Control category | CC1 |

## How to read this document

- **Your original requirements are all here, under their original IDs.** Each one shows:
  - **Statement:** the re-baselined wording
  - **Was:** your v20.18 wording, when it changed
  - **Why changed:** what was ambiguous, contradictory or tied to one implementation
  - **Now:** what today's code does (from SGP-RSM-001)
- **Intent was kept.** Where a change was needed, the wording was changed to state *what the app must do
  for the user*, not *how the code does it*. Implementation details (Android APIs, JPEG quality numbers,
  thread models) move down to the HLR/LLR levels.
- **Nothing was dropped silently.**
  - Requirements that wait on a decision are marked **Suspended**, with the decision ID.
  - The two process requirements (GOV) are **Deleted**, and moved to the QA plan.
  - One requirement is **Merged** into another. It keeps its ID and says where it went.
- **New requirements** cover functions the app already has but that were never written down (no-plant
  paths, auto-populate, colours, ruler…), and gaps found in the review (Back button, app updates, backups,
  display). They take new IDs in the same areas.
- **Values marked `[TBC-nn]`** are **proposals for you to confirm**. They are collected in §15, so you
  can settle them all in one pass.
- Metadata line per requirement: `Parent` (source) · `Safety` (FHA link, SGP-SAP-001) · `Verify` (method) ·
  `Status`.
- All requirements have status **Proposed** unless stated otherwise.

---

## 1. Functional overview (what the app does, in plain words)

| Area | What the user can do | Main requirements |
|---|---|---|
| Plots | Create a plot from its real dimensions, optionally photograph it, list, open, edit and delete plots | FUN-010/020/070/090/150, VAL-010/015/017 |
| Layout | Place plants to scale, see their spacing zones, move them, change their variety, undo/redo | FUN-030/040/050/120, INT-090/100, VAL-020/030/040/070 |
| Rules | Spacing and companion/antagonist checks with clear messages; adjustable strictness | VAL-040, INT-030, CFG-040/050, DAT-210 |
| Areas | Mark no-plant paths; auto-fill an area with a chosen variety | FUN-100/110, VAL-050 |
| Views | Zoom/pan, ruler, colour per variety + legend, weed-risk overlay, irrigation route | FUN-060/130/140, INT-040/070/080 |
| Timing | Harvest dates, germination alerts, "Plan B" recovery options | DAT-010, FUN-080, INT-020, DAT-160 |
| Knowledge | Plant catalog in three tiers, encyclopedia, custom varieties, companions, pests, care | DAT-020…065, DAT-150, DAT-190…220, VAL-080 |
| Place & climate | Location per plot, hardiness zone, frost dates, planting windows, recommendations | FUN-090, DAT-080…100, ENV-025 |
| Camera & sensors | Plot photo, light/storage/battery safeguards, capability check | FUN-010, CON-040, ENV-030, ERR-020/030, SAF-*, CON-050/060 |
| Trust & safety | Offline only, no tracking, encrypted data, audit log, safe failures, data kept across updates | CON-010/020/030/070, SEC-*, ERR-010, ENV-040 |
| Settings | Every tunable value in one screen, units, reset | CFG-* |
| Future | Measurement tool, export/import/sharing, roles, other platforms | FUN-025, PRE-*, DAT-110…140, PLT-* |

---

## 2. Plots and layout (FUN)

### T2-FUN-010 — Plot photo
**Statement:** The system shall let the user take a photo of a planting zone (pot, bed or field) with the
device's rear camera and attach it to a plot. The photo shall be shown as the background of the plot layout,
fitted to the plot's boundaries. The user shall be able to replace or remove the photo.
The user shall also be able to pick an existing photo from the device's gallery instead of taking one
`[TBC-01]`.
Parent: ConOps §5.1 · Safety: FC-12 · Verify: Test (target) · Status: Proposed
**Was:** "…capture a digital image of a planting zone (pot or field) using the device's native hardware camera."
**Why changed:** Added what happens with the photo (background, replace/remove), which the original left open.
**Now:** Camera code exists but no screen uses it (CODE).

### T2-FUN-020 — Plot dimensions
**Statement:** The system shall let the user enter a plot's length and width, in the selected display unit
(T2-CFG-100), within the allowed range (T2-CFG-090). These dimensions set the real-world scale of the
plot layout.
Parent: ConOps §5.3 · Safety: FC-01 · Verify: Test · Status: Proposed
**Was:** "…input the physical dimensions of the captured planting zone to establish a spatial scale."
**Why changed:** Linked to the unit and range requirements. The photo is optional, so the plot is not
necessarily "captured".
**Now:** Implemented (IMPL).

### T2-FUN-025 — Measuring plot dimensions with the device
**Statement:** The system shall provide a way to obtain plot dimensions by measuring with the device, as an
alternative to typing them. The measured values shall be shown with their uncertainty (T2-PRE-020), and the
user shall be able to accept or edit them before use.
Parent: ConOps §5.2 · Safety: FC-14 · Verify: Test (target) · Status: **Suspended — decision D-03 (method)**
**Was:** "…local measuring tool utilizing onboard device sensors to estimate physical distances or dimensions post-capture."
**Why changed:** Stated as a capability, independent of the method. Measuring by double-integrating the
accelerometer (the method in the HLRs) does not give usable metre-scale accuracy on a phone. Candidate
methods: ARCore, a reference object in the photo, or GPS for large fields.
**Now:** An unused, physically unsound IMU engine exists (DEC).

### T2-FUN-030 — Variety and planting date per plant
**Statement:** For every plant placed, the system shall record its variety and its planting date. The
planting date shall default to today's date and shall be editable by the user, from any past date up to
`[TBC-02: 365]` days in the future (for planned plantings).
Parent: ConOps §5.3 · Safety: FC-04, FC-05 · Verify: Test · Status: Proposed
**Was:** "…log the type of seed planted and the exact date it was planted."
**Why changed:** "Exact date" made editable. Today the date is always "now" and can't be changed.
**Now:** Variety yes; date automatic only (PART).

### T2-FUN-040 — To-scale layout display
**Statement:** The system shall display every plant of a plot at its recorded position, within the plot
boundary, over the plot photo when one exists, using **the same scale on both axes**, so that distances on
screen are proportional to real distances in every direction.
Parent: ConOps §5.3 · Safety: FC-01, FC-07 · Verify: Test · Status: Proposed
**Was:** "…record and visually display where seeds are organized and planted within the captured zone."
**Why changed:** Added the equal-scale condition. Today the plot is stretched to the screen shape, which
distorts spacing on a tall phone screen.
**Now:** Displayed, but X and Y scales differ (PART).

### T2-FUN-050 — Spacing zone display
**Statement:** The system shall show each plant's required spacing zone as a circle of the variety's spacing
radius, drawn to the plot scale (T2-FUN-040), while plants are being placed, moved or viewed.
Parent: ConOps §5.3 · Safety: FC-01 · Verify: Test · Status: Proposed
**Was:** "…calculate and display the correct physical spacing required between seeds during layout planning."
**Why changed:** "Correct" is not testable. It is now defined by the catalog radius (T2-DAT-220) plus the
enforcement rule in T2-VAL-040.
**Now:** Circles drawn, radius scaled on X only (PART).

### T2-FUN-060 — Irrigation route suggestion
**Statement:** The system shall, on request, overlay a suggested drip-line route on the plot:
- the route connects every plant exactly once
- it starts at a water-source point, by default the plot's top-left corner, movable by the user
  `[TBC-03]`
- each next plant is the nearest one not yet connected

The route's total length shall be shown in the display unit. The overlay shall be labelled as a suggestion.
Hoses and sprinklers are future scope `[TBC-04]`.
Parent: ConOps §5.4 · Safety: FC-08 · Verify: Test · Status: Proposed
**Was:** "…calculate and overlay an optimized layout grid for irrigation (drip lines, hoses, or sprinklers) across the field image."
**Why changed:** "Optimized" replaced by the actual rule (nearest-neighbour), which is what exists. A truly
shortest route would be a different requirement. Hoses and sprinklers split out.
**Now:** Nearest-neighbour route from the corner; no length shown; source not movable (PART).

### T2-FUN-070 — Edit and delete plots and plants
**Statement:** The system shall let the user:
- rename a plot
- change a plot's dimensions (subject to T2-VAL-017)
- delete a plot, together with its plants, paths and photo, after a confirmation
- move, change the variety of, or delete individual plants

Archiving a plot (hiding it without deleting it) `[TBC-05]`.
Parent: ConOps §5.3 · Safety: FC-10 · Verify: Test · Status: Proposed
**Was:** "…Update and Delete/Archive previously recorded plots and seed nodes."
**Why changed:** Listed the specific actions. Archive marked for your confirmation.
**Now:** Plants yes; plots can't be edited or deleted (PART).

### T2-FUN-080 — Germination alert and Plan B
**Statement:** The system shall mark a plant as **germination overdue** when more whole days than the
variety's germination period have passed since its planting date, and the user has not yet recorded an
outcome. For an overdue plant, the user shall be able to either record that it germinated (the alert clears)
or declare a germination failure. On a declared failure, the system shall present all of the following
recovery options that apply, side by side:
- (a) **Fast-track:** a variety of the same species (or, failing that, the same botanical family) whose
  days-to-harvest is shorter than the failed variety's and fits the remaining season
- (b) **Nursery transplant:** restart the same variety from a nursery transplant
- (c) **Catch crop:** a different variety whose days-to-harvest fits the remaining season

Choosing an option replaces the plant's variety and resets its planting date to today. "Remaining season"
= days until the plot's first expected fall frost (T2-DAT-080), or, if no location is set, until
`[TBC-06: a season-end date set in Settings]`.
Parent: ConOps §5.4 · Safety: FC-05, FC-06 · Verify: Test · Status: Proposed
**Was:** "…evaluate germination status based on elapsed time and present a comprehensive suite of concurrent agricultural contingency options ("Plan B") upon a user-declared germination failure."
**Why changed:** "Comprehensive suite" replaced by the three defined options (your transcript §C) and the
rules for choosing them. Today only (b) ever appears, because the catalog has no fast-track or catch-crop
data. The rules above let the app compute (a) and (c) itself.
**Now:** Alert + dialog; only the nursery option in practice (PART).

### T2-FUN-090 — Plot location
**Statement:** The system shall let the user set a location for each plot, either as a 5-digit US ZIP code
or as latitude/longitude, entered by hand or taken from the device's location service (subject to
T2-ENV-025). The location is optional. Features that need it (T2-DAT-080…100) shall say so when it is
missing.
Parent: ConOps §5.3 · Safety: FC-13 · Verify: Test · Status: Proposed
**Was:** "…input a location parameter (ZIP code or geographic coordinates) associated with the planting zone."
**Why changed:** Added the device-location option, and the behaviour when the location is absent.
**Now:** IMPL: ZIP → latitude/longitude offline (bundled table) and ZIP → hardiness zone offline from the 2023
USDA Plant Hardiness Zone Map ZIP tables (PRISM/OSU; 40,502 ZIPs incl. AK, HI, PR); manual zone list; online
lookup only for ZIPs not in the table; device location on the Site tab.

### T2-FUN-100 — No-plant paths (NEW)
**Statement:** The system shall let the user mark areas of a plot where nothing may be planted (paths,
walkways), either as a rectangle or as a line through two or more points with a user-set width. The user
shall be able to change a path's width and delete a path.
Parent: existing functionality (User Manual §2) · Safety: — · Verify: Test · Status: Proposed
**Now:** Implemented; had no requirement (IMPL).

### T2-FUN-110 — Auto-populate an area (NEW)
**Statement:** The system shall let the user select an area of a plot and fill it with plants of one chosen
variety, in either a row grid or a hexagonal pattern at the variety's spacing:
- The area can be a rectangle. On the Standard and Pro tiers it can also be a polygon of 3 or more points.
- Before filling, the system shall show how many plants will be placed.
- Positions that would break any placement rule (T2-VAL-020/040/050, T2-INT-030) shall be skipped, and the
  system shall report how many were placed and how many skipped.
- The fill shall be undoable as one step.

Parent: existing functionality; FEATURE_ROADMAP FR-001 · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** Implemented; had no requirement (IMPL).

### T2-FUN-120 — Change a plant's variety (NEW)
**Statement:** The system shall let the user change the variety of a placed plant. The change shall be
accepted only if the plant, with its new variety, satisfies the same rules as a new placement. Otherwise the
system shall say which neighbour conflicts and keep the old variety.
Parent: existing functionality · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** Implemented (IMPL).

### T2-FUN-130 — Variety colours and legend (NEW)
**Statement:** The system shall draw each variety in its own colour. The user shall be able to choose a
variety's colour from at least 16 preset colours, or leave it automatic. The system shall provide a legend
listing the varieties placed on the current plot with their colours.
Parent: existing functionality · Safety: — · Verify: Test · Status: Proposed
**Now:** Implemented (IMPL).

### T2-FUN-140 — Ruler (NEW)
**Statement:** The system shall show a ruler along the top and left edges of the plot layout, in the display
unit. Its marks shall line up with the plot at every zoom and pan position.
Parent: existing functionality · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** Ruler exists but doesn't follow zoom/pan (PART).

### T2-FUN-150 — Plot list (NEW)
**Statement:** The system shall list all of the user's plots, with name and dimensions in the display unit,
and open a plot's layout when it is selected.
Parent: existing functionality · Safety: — · Verify: Test · Status: Proposed
**Now:** Implemented (units always shown in metres) (PART).

### T2-FUN-160 — Plan an area for me (NEW 2026-09-27)
**Statement:** The system shall let a user with no gardening knowledge select a plot, select an area of it
(rectangle or polygon), and list which varieties to plant and how many of each. The system shall then decide
where each plant goes, and shall:
- put taller plants on the side of the area away from the midday sun (north in the northern hemisphere,
  south in the southern), using the plot's compass direction (T2-FUN-170), and shorter plants on the sunny side;
- give plants that need full sun the sunniest positions, using marked sun/shade areas and the estimated shade of
  obstacles (T2-FUN-180);
- support pollination: plant wind-pollinated crops such as sweet corn in a compact block, spread any pollinator
  flowers or herbs in the list among the crops that need insects, and recommend pollinator plants when such crops
  are listed without any;
- group plants with similar watering needs, and place companions next to each other where possible;
- apply every placement rule (T2-VAL-020/040/050, T2-INT-030) to every plant.

Before anything is stored, the system shall show the proposed positions on the layout, how many of each variety
fit, what didn't fit, and a plain-language explanation. Accepting stores the plants as one undoable step;
cancelling stores nothing. If the plot's compass direction isn't set, the system shall say so and offer to set it.
Parent: owner request 2026-09-27 (FEATURE_ROADMAP FR-027) · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** IMPL (AutoPlanner; layout "Plan an area for me").

### T2-FUN-170 — Plot compass direction (NEW 2026-09-27)
**Statement:** The system shall let the user set which compass direction the top edge of the plot, as drawn on
the screen, faces (any bearing, with the eight main directions offered as shortcuts), when creating the plot and
at any time afterwards. The layout shall show a compass indicating north. Until the direction is set, the layout
shall show that it is missing, and features that depend on it (sun, shade, T2-FUN-160) shall say they are
assuming the top edge faces north.
Parent: owner request 2026-09-27 (FR-028) · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-180 — Obstacles that cast shade (NEW 2026-09-27)
**Statement:** The system shall let the user place trees (trunk position, crown radius, height), and fences,
walls and buildings (a line or outline, height) on a plot, and change, move and delete them. The system shall
estimate, from the plot's location and compass direction, which parts of the plot these obstacles shade and for
how long on a given day, and show this as an overlay.
Parent: FEATURE_ROADMAP FR-006; owner request 2026-09-27 (relocation) · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-190 — Undo covers every layout change (NEW 2026-09-27)
**Statement:** Undo and redo on the layout shall cover every change made there: plants, no-plant paths,
obstacles, sun/shade/flood/slope areas, and the plot outline.
Parent: owner problem report 2026-09-27 (undo did not remove a placed building, tree or fence) · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-200 — Say what each variety is (NEW 2026-09-27)
**Statement:** Wherever a variety is chosen or shown (lists, the plan-for-me list and proposal, and the layout),
the system shall say in everyday words what it is where the species name alone doesn't: for peppers, sweet or spicy
(with a heat level), bell or other shape, and ripe colour; for tomatoes, cherry, salad, slicing, beefsteak or paste
size and colour; for onions, bulb or spring (green) onion. The layout shall be able to show a short name under each
plant, and plants whose fruit colour is known shall be told apart by it (e.g. red and yellow bell peppers). The
catalog shall include spring onions.
Parent: owner request 2026-09-27 (FR-031) · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-210 — Crop rotation and clumps (NEW 2026-09-27)
**Statement:** The system shall group vegetables into crop-rotation families and, using the plot's past seasons
(T2-FUN-220), shall (a) warn, without blocking, when a plant is placed where its family grew within that family's
waiting period, (b) make "Plan an area for me" avoid such spots, and (c) advise, per plot, what grew where last
season and which family should go there next. "Plan an area for me" shall arrange each crop as a compact clump by
default, explaining that clumps can swap places next year, and shall let the user choose rows instead.
Parent: owner request 2026-09-27 (FR-032) · Safety: FC-17 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-220 — Seasons and plot history (NEW 2026-09-27)
**Statement:** The user shall be able to start a new season on a plot. Doing so shall keep the plot and everything
fixed on it (fences, walls, buildings, trees, paths, sun/shade/flood/slope areas, outline) and move the season's
plants into the plot's history, labelled with the season year. History shall never be discarded by later seasons:
it shall stay viewable (per season, and drawn faintly on the layout on request), be used for crop rotation
(T2-FUN-210), travel in plan files (T2-PLT-020), and starting a season shall be undoable.
Parent: owner request 2026-09-27 (FR-033) · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-240 — Organised clumps and room for vines (NEW 2026-09-27)
**Statement:** "Plan an area for me" shall, by default, lay out each crop as an organised block of rows and columns
at the crop's spacing (e.g. 20 sweet corn as 4 rows of 5; 7 tomatoes as a row of 4 and a row of 3), leave a walkway
between blocks so every plant can be reached and watered with a hose, keep climbing crops at the side away from the
midday sun (for a trellis), and put sprawling vines (e.g. watermelon, squash, pumpkin, cucumber) at the sunny side
with free, sunny ground reserved toward the sun for their runners, shown on the proposal, so they don't grow into
other crops looking for light.
Parent: owner request 2026-09-27 (FR-035) · Safety: FC-18 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-250 — Find, edit and outline (NEW 2026-09-27)
**Statement:** The user shall be able to (a) see a legend of what is planted on the plot, with counts, and have the
plants of a chosen variety pointed out on the layout, also from the harvest and harmony lists; (b) change a planted
plant's variety (and, on the computer, its planting date) or delete it; (c) move the corners of a plot outline, add
corners, and delete the outline. The layout's compass shall show four arrowheads labelled N, E, S and W turned to the
plot's direction, with north stressed.
Parent: owner problem report 2026-09-27 (FR-036) · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-260 — Plan season after season (NEW 2026-09-27)
**Statement:** The user shall be able to choose which season of a plot is shown (past seasons read-only), to re-plan
the whole plot for the next season from the current (or last) season's plant list with crop rotation, and to see a
rotation plan for several seasons in a row (1 to 30, chosen by the user; revised 2026-09-27 from 2 to 10 at the owner's
request) before committing to the first, and to replace a variety with another from any year of that plan onward. When the system places plants
(T2-FUN-160), no plant shall go where its rotation family grew in the previous season unless there is no other room,
in which case the user shall be told.
Parent: owner request 2026-09-27 (FR-037) · Safety: FC-17 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-270 — Sun and shade through the day and year (NEW 2026-09-27)
**Statement:** The shade display (T2-FUN-180) shall offer the hours of direct sun over a whole day and the shade at a
chosen time of day, for today, the equinoxes, midsummer and midwinter, shall let the user find when a given spot gets
sun, and shall be able to include the shade cast by planted crops at their mature height.
Parent: owner request 2026-09-27 (FR-038) · Safety: FC-19 · Verify: Test · Status: Proposed
**Now:** IMPL (phone: no per-spot sun times).

### T2-FUN-280 — Irrigation coverage (NEW 2026-09-27)
**Statement:** The user shall be able to place sprinklers (throw radius, full or part circle and its direction), drip
lines or soaker hoses (wetted width) and hose taps (hose length) on a plot, move, edit and delete them, and see which
areas and which plants each reaches, which plants need hand watering, and where overhead watering wets crops prone to
leaf disease. Irrigation shall travel in plan files.
Parent: owner request 2026-09-27 (FR-039) · Safety: FC-20 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-290 — Fill the plot (NEW 2026-09-27)
**Statement:** The user shall be able to plan the whole plot at once from a list of crops and have the system estimate,
keeping the list's proportions, how many of each fit.
Parent: owner request 2026-09-27 (FR-040) · Safety: FC-18 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-300 — Plot templates (NEW 2026-09-27)
**Statement:** The user shall be able to duplicate a plot, keeping its site (size, direction, location, soil, outline,
obstacles, areas, paths, irrigation) and optionally its current plants and its history, so the copy can be planned
independently of the original.
Parent: owner request 2026-09-27 (FR-041) · Safety: FC-21 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-310 — Pests and wildlife in the yard (NEW 2026-09-27)
**Statement:** When a plot is created, the system shall ask which pests and animals the user sees regularly in the yard
(at least deer, rabbits, raccoons, squirrels, groundhogs, gophers, voles, chipmunks, skunks/opossums, armadillos, wild
hogs, pets, birds, slugs and common insect pests), keep the answer with the plot, let the user change it, and in the Care
section give for each one the signs of damage, ways to prevent it (for example fence height and type), and the plot's
plants it is known to go for.
Parent: owner request 2026-09-27 (FR-042) · Safety: FC-23 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-320 — Most important plants and checks before planning (NEW 2026-09-27)
**Statement:** In "Plan an area for me" and "Fill the whole plot" the user shall be able to mark the plants that matter
most; the system shall show checks before anything is placed (space needed, sun available for each sun need, plants that
grow poorly together, hardiness, crop rotation, watering mix, the yard's pests, and which plants are marked), and shall
place the marked plants first, in the sunniest spots that suit them.
Parent: owner request 2026-09-27 (FR-043) · Safety: FC-18, FC-25 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-330 — Disclaimer (NEW 2026-09-27)
**Statement:** Before first use, and afterwards from help (computer) or Settings (phone) and in the user manual, the
system shall state that it is a planning aid, that it does not guarantee any result, and that its advice is guidance to
help the user decide rather than mandatory instructions; the user shall acknowledge it once.
Parent: owner request 2026-09-27 (FR-044) · Safety: FC-23, FC-25 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-340 — Watering recommendations (NEW 2026-09-27)
**Statement:** The Care section shall give watering recommendations: general practice, how to choose between sprinklers,
drip and hose, how much water each planted crop needs, which plants no sprinkler, drip line or hose reaches, and where
drip is better than overhead watering; the tools to draw irrigation and the water map shall be reachable from there.
Parent: owner request 2026-09-27 (FR-045) · Safety: FC-20 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-350 — Satellite photo under the plot (NEW 2026-09-27)
**Statement:** The user shall be able to open a map service (Google Maps) in satellite view for the plot's address, add
a screenshot or aerial photo of the yard under the plot, set its scale from two points a known distance apart, move and
turn it, change how see-through it is, hide or remove it, and trace obstacles on top of it. The photo shall be kept with
the plot, travel in plan files and duplicates, and the system shall not fetch map imagery by itself.
Parent: owner request 2026-09-27 (FR-046, FR-049: turn up to 180° clockwise or counter-clockwise with a slider and a
number box that stay in step) · Safety: FC-24 · Verify: Test · Status: Proposed
**Now:** IMPL (phone: the photo is clipped to the plot rectangle; photo changes aren't undoable on the phone).

### T2-FUN-360 — Replace all plants of a variety (NEW 2026-09-27)
**Statement:** From the list of what's on the plot, the user shall be able to change every plant of one variety to
another variety in one step, keeping their places and planting dates, be told how many now crowd a neighbour, and undo
it in one step.
Parent: owner request 2026-09-27 (FR-051) · Safety: FC-26 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-370 — Plot list out of the way (NEW 2026-09-27)
**Statement:** On the computer, the "On this plot" box shall be movable anywhere over the layout by dragging, remember
where it was left, and return to its corner on request, so it never has to cover the part of the plot being reviewed.
Parent: owner request 2026-09-27 (FR-052) · Safety: — · Verify: Test · Status: Proposed
**Now:** IMPL (the phone shows the list in the layout menu, which doesn't cover the plot).

### T2-FUN-380 — Choose how clumps are arranged (NEW 2026-09-27)
**Statement:** Before planting, the user shall be able to choose for each crop how its clump is arranged (for example 50
as 5 rows of 10, 10 rows of 5, 7 rows of 7 + 1, 6 rows of 8 + 2, or one row), be offered nearby counts that make a neat
rectangle, and change the count. The planner shall keep each crop in one block where it can; when it has to split a crop
into several groups it shall say so, with the group sizes and why, before anything is planted.
Parent: owner request 2026-09-27 (FR-047) · Safety: FC-18 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-390 — Help on every control (NEW 2026-09-27)
**Statement:** On the computer every button shall show how to use it when the pointer rests on it; tools that place
things shall turn off when clicked again; drawing a line (drip line, fence, area, outline) shall offer Finish and Cancel,
and finishing a drip line shall stop adding points.
Parent: owner problem report 2026-09-27 (FR-053) · Safety: — · Verify: Test · Status: Proposed
**Now:** IMPL (touch screens have no hover; the phone relies on labels and hints).

### T2-FUN-400 — Plot address and Google Maps (NEW 2026-09-27)
**Statement:** The user shall be able to keep the plot's street address with the plot and open it in Google Maps with one
action (for example to take a satellite screenshot); the address shall travel in plan files and duplicates and shall be
sent nowhere unless the user opens Google Maps.
Parent: owner request 2026-09-27 (FR-050) · Safety: FC-24 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-FUN-230 — Keep the gardener's choices (NEW 2026-09-27)
**Statement:** Discarding a "Plan an area for me" proposal shall not change the plot or lose the list of plants;
the user shall be able to go back from the proposal to the list, with the same area and choices, change it and plan
again. The system shall remember the last list for next time and shall offer the varieties the user plants most
often (from all plots and past seasons) as one-tap choices.
Parent: owner request 2026-09-27 (FR-034) · Safety: FC-01 · Verify: Test · Status: Proposed
**Now:** IMPL.

---

## 3. Plant knowledge and data (DAT)

### T2-DAT-010 — Harvest date
**Statement:** The system shall show, for each plant, the expected harvest date = planting date +
the variety's days-to-harvest.
Parent: ConOps §5.3 · Safety: FC-04 · Verify: Test · Status: Proposed
**Was:** "…calculate and display target harvest dates based on the planting date."
**Why changed:** The formula is now stated.
**Now:** IMPL.

### T2-DAT-020 — Pests
**Statement:** The system shall show, for each variety, the pests known to affect it, as recorded in the
catalog.
Parent: ConOps §5.3 · Verify: Test · Status: Proposed
**Now:** IMPL. *(Wording essentially unchanged.)*

### T2-DAT-030 — Companions and antagonists
**Statement:** The system shall show, for each variety, its companion plants (grow well nearby) and its
antagonist plants (should not be planted nearby), by name.
Parent: ConOps §5.3 · Safety: FC-03 · Verify: Test · Status: Proposed
**Was:** "…identify and display the best companion plants for the selected seeds."
**Why changed:** "Best" is not testable. Antagonists added, because the app uses them.
**Now:** Listed; some names show as codes in the Basic tier (PART).

### T2-DAT-040 — Care and watering guidance
**Statement:** The system shall show, for each variety, care guidance and a watering strategy (for example
frequency and method) as recorded in the catalog.
Parent: ConOps §5.3 · Verify: Test · Status: Proposed
**Was:** "…care and specific watering strategies tailored to the field/crop type."
**Why changed:** Tied to catalog data. "Field type" tailoring needs soil data (FR-013) `[TBC-07]`.
**Now:** Care notes only (PART).

### T2-DAT-050 — Germination conditions
**Statement:** The system shall show, for each variety, its germination conditions: soil temperature range,
sowing depth and light requirement, as recorded in the catalog.
Parent: ConOps §5.3 · Verify: Test · Status: Proposed
**Was:** "…display optimal conditions required for seed germination."
**Why changed:** "Optimal conditions" defined as three data fields. Needs new catalog data `[TBC-08: source]`.
**Now:** NONE.

### T2-DAT-060 — Custom varieties
**Statement:** The system shall let the user create, view, edit and delete their own varieties. Deleting a
variety that is planted on any plot shall be refused, with a message saying where it is used.
Parent: ConOps §5.3 · Verify: Test · Status: Proposed
**Was:** "…create, read, update, and delete custom seed profiles within the local data repository."
**Why changed:** Added the planted-variety rule (the database refuses it anyway, so the app must explain).
**Now:** No delete (PART).

### T2-DAT-065 — Botanical family is mandatory
**Statement:** When a custom variety is created or edited, the system shall require the user to choose its
botanical family from a fixed list (including "Other").
Parent: ConOps §5.4 · Safety: FC-06 · Verify: Test · Status: Proposed
**Was:** "…enforce the assignment of a broad, pre-defined botanical family reference to anchor default fallback contingency behaviors."
**Why changed:** Wording simplified; the intent (the family drives the Plan B fallback in T2-FUN-080a) is kept.
**Now:** Free text, defaults to "Unclassified" (PART).

### T2-DAT-070 — Concurrent fallback pathways
**Status: Merged into T2-FUN-080** (the three options are presented side by side there).
**Was:** "…compute and present concurrent fallback pathways."

### T2-DAT-080 — Offline climate data
**Statement:** The system shall include offline data that gives, for a plot location, the USDA hardiness zone
and the average last spring frost and first fall frost dates. Coverage: all US 5-digit ZIP codes, plus
latitude/longitude within the continental US `[TBC-09: coverage outside the US]`. The source and version
of the data shall be recorded.
Parent: ConOps §5.3 · Safety: FC-13 · Verify: Test + Analysis (data audit) · Status: Proposed
**Was:** "…maintain an offline climatic database mapping location parameters to USDA Plant Hardiness Zones and historical frost-date models."
**Why changed:** Coverage and provenance stated.
**Now:** 5 sample ZIPs, unused (PART).

### T2-DAT-090 — Planting windows
**Statement:** For a plot with a location, the system shall show for each variety the date window in which
it can be sown so that it reaches harvest before the first fall frost, starting no earlier than the last
spring frost `[TBC-10: frost-tolerant varieties may start earlier]`.
Parent: ConOps §5.3 · Safety: FC-13 · Verify: Test · Status: Proposed
**Was:** "…cross-reference the local climatic constants against the botanical dictionary to calculate optimal seasonal planting windows."
**Why changed:** "Optimal window" defined.
**Now:** NONE.

### T2-DAT-100 — Climate-suited recommendations
**Statement:** For a plot with a location, the system shall list the varieties whose hardiness zone range
includes the plot's zone and that can still reach harvest in the remaining season, and shall warn when the
user places a perennial whose zone range does not include the plot's zone.
Parent: ConOps §5.3; FEATURE_ROADMAP FR-014 · Safety: FC-13 · Verify: Test · Status: Proposed
**Was:** "…extract historical growing season windows from local datasets and recommend crop varieties dynamically optimized for the localized climate profile."
**Why changed:** "Dynamically optimized" replaced by testable filters.
**Now:** NONE.

### T2-DAT-110 — Export and import
**Statement:** The system shall let the user export one plot or all plots, with their plants, paths, custom
varieties used and photos, to a single file chosen by the user, and import such a file on the same or
another device.
Parent: ConOps §4 (sharing) · Safety: FC-15 · Verify: Test · Status: Proposed
**Was:** "Support dataset export/import."
**Why changed:** Scope stated.
**Now:** PART: plots, plants, paths, obstacles/areas, outline, direction, location, soil and custom varieties are
exported to a Smart Garden plan file (.sgp.json, docs/PLAN_FILE_FORMAT.md) and imported as new plots. Photos and
signing (T2-SEC-040) are not included yet.

### T2-DAT-120 — Ownership information
**Statement:** The system shall record, for each plot, its owner's display name and the date/time it was
created and last modified, and include them in exports.
Parent: ConOps §4 · Verify: Test · Status: Proposed (future)
**Was:** "Support ownership metadata."
**Now:** Timestamps stored; no owner name; nothing displayed (PART).

### T2-DAT-130 — Import conflicts
**Statement:** When an imported plot already exists on the device (same plot identity), the system shall show
the differences and let the user keep the local version, take the imported version, or keep both as
separate plots. Nothing shall change until the user chooses.
Parent: ConOps §4 · Safety: FC-10 · Verify: Test · Status: Proposed (future)
**Was:** "Support conflict-aware merges."
**Now:** NONE.

### T2-DAT-140 — Roles
**Statement:** For plots shared by export/import, the system shall support three roles:
- **Owner:** full control
- **Contributor:** may add, move and delete plants and paths, but not delete or resize the plot
- **Viewer:** read-only

The role shall travel with the export file.
Parent: ConOps §4 · Verify: Test · Status: Proposed (future) `[TBC-11: still wanted?]`
**Was:** "Support Owner, Contributor, Viewer roles."
**Now:** A role column exists; no behaviour (PART).

### T2-DAT-150 — Encyclopedia (NEW)
**Statement:** The system shall let the user browse and search the whole catalog (including custom
varieties) by name, botanical family and category, and view all data held for a variety.
Parent: existing functionality · Verify: Test · Status: Proposed
**Now:** IMPL (search by name/family only).

### T2-DAT-160 — Dates and time zones (NEW)
**Statement:** All date calculations (harvest dates, germination alerts, planting windows) shall use calendar
days in the device's current time zone. Changing the device clock or time zone shall not change any stored
planting date.
Parent: review finding (GAP §4.5) · Safety: FC-04, FC-05 · Verify: Test · Status: Proposed
**Now:** Uses raw milliseconds; untested for time zone changes (PART).

### T2-DAT-190 — Tiered catalog
**Statement:** The system shall include a catalog of plant varieties covering vegetables, fruit, herbs,
flowers and ornamentals, selectable in three sizes:
- Basic: 253 varieties
- Standard: 603 varieties, including all of Basic
- Pro: 2,939 varieties, including all of Standard

Parent: KB Part 11 · Verify: Test · Status: Proposed
**Was:** "…bundled reference catalog of seed varieties… at three selectable size tiers."
**Why changed:** Sizes and nesting stated.
**Now:** IMPL.

### T2-DAT-200 — Tier switch keeps user data
**Statement:** Switching the catalog tier shall replace only the bundled varieties. Varieties the user created
or edited shall be unchanged. Bundled varieties that are planted on any plot shall be kept (and stay usable)
even if the new tier does not include them. The switch shall never fail because a variety is in use.
Parent: KB Part 11 · Safety: FC-10 · Verify: Test · Status: Proposed
**Was:** "Switching… shall replace only bundled (non-user-created) catalog entries; any variety the user has personally added or edited shall be unaffected…"
**Why changed:** Added the planted-variety rule. Today the switch crashes the app as soon as any bundled
variety is planted.
**Now:** PART (crash).

### T2-DAT-210 — Species-level companion rules
**Statement:** Companion and antagonist relationships shall apply at the species level: every cultivar of a
species takes part in the relationships listed for that species.
Parent: KB Part 11 · Safety: FC-03 · Verify: Test · Status: Proposed
**Now:** IMPL. *(Wording unchanged in substance.)*

### T2-DAT-220 — Catalog data quality (NEW)
**Statement:**
- Every catalog entry shall have: a unique code, a name, a family, a category, a spacing radius
  `[TBC-12: 0.02–10 m]`, germination days `[1–365]`, days to harvest `[1–3650]` (perennials and fruit trees take years) and a zone range within 1–13.
- Every companion or antagonist reference shall resolve to a species present in the same tier or in a
  species list shipped with the app.
- The source of the spacing, germination and harvest values shall be documented.
- The app shall present these values as planning estimates.

Parent: review finding (GAP §4.5); PSSA · Safety: FC-09 · Verify: Analysis (automated checks) + Review (sample audit) · Status: Proposed
**Now:** Values unverified; 15 Basic references unresolved (PART).

---

## 4. Constraints (CON)

### T2-CON-010 — Offline first
**Statement:** The system shall perform all storage, calculation and image processing on the device and
shall provide every function without a network connection. Network connections shall be made only while
the user has switched on "Online features" (off by default), only over HTTPS to the approved data services
listed in the design, and only to fetch optional data (weather, sunshine history, nutrition updates); every
such feature shall fall back to offline behaviour when the switch is off or the connection fails.
Parent: ConOps §1.2 · Verify: Analysis (merged manifest, network code, allow-list) + Test (airplane mode; switch off) · Status: Proposed (revised 2026-09-27)
**Was (Rev A):** "…shall make no network connections, and shall not request the Internet permission." Original: "…locally on the device with zero network dependencies."
**Why changed:** The owner asked for all roadmap features to be implemented, including FR-026 (optional
network layer). The revised text keeps the original intent, zero network *dependencies*, while allowing
the user-controlled switch (roadmap option A). Needs the safety/security assessment noted in DW-1626.
**Now:** IMPL (NetworkGateway + OnlineData allow-list; switch in Settings → Online features).

### T2-CON-020 — No tracking
**Statement:** The system shall contain no analytics, telemetry, crash-reporting or advertising components,
and shall not record usage history other than the audit log (T2-SEC-030).
Parent: ConOps §1.2 · Verify: Analysis · Status: Proposed
**Now:** IMPL.

### T2-CON-030 — Edits are saved immediately and completely
**Statement:** Every edit the user completes (placing, moving, deleting, changing plants or paths, plot and
setting changes) shall be saved to the device's permanent storage as part of that action. If the app is
stopped at any moment, the stored data shall contain either the whole edit or none of it.
Parent: ConOps §5.5 · Safety: FC-10 · Verify: Test (forced stop) · Status: Proposed
**Was:** "…persist all application states and configurations into non-volatile local storage on any state change transaction."
**Why changed:** Added the all-or-nothing rule. Work in progress that isn't finished yet is covered by
T2-ENV-020.
**Now:** Saved immediately; some multi-step edits aren't all-or-nothing (PART).

### T2-CON-040 — Photo size limit
**Statement:** A stored plot photo shall be no larger than 1080 pixels on its longest side, shall be
correctly oriented, and shall be compressed so that it takes no more than `[TBC-13: 1 MB]`.
Parent: ConOps §5.1 · Verify: Test · Status: Proposed
**Was:** "…downscale and compress captured image data prior to non-volatile storage serialization to enforce a hard maximum storage cap."
**Why changed:** Your 1080 px value moved up from the HLRs; the cap made explicit.
**Now:** Code exists, unused (CODE).

### T2-CON-050 — Capability check
**Statement:** At every start, the system shall check which optional hardware is present and usable (camera,
light sensor, location, and any sensor used by the measuring method), and remember the result.
Parent: ConOps §6.3 · Verify: Test · Status: Proposed
**Was:** "Evaluate hardware capabilities during onboarding."
**Now:** CODE.

### T2-CON-060 — Missing hardware
**Statement:** When a piece of optional hardware is missing, unusable or not permitted by the user, the features
that need it shall be disabled with an explanation, and every other feature shall keep working.
Parent: ConOps §6.3 · Verify: Test · Status: Proposed
**Was:** "Gracefully degrade when optional sensors are unavailable."
**Now:** NONE.

### T2-CON-070 — Data kept across app updates (NEW)
**Statement:** Installing a newer version of the app shall keep all of the user's data (plots, plants, paths,
photos, custom varieties, settings) from any earlier version released from `[TBC-14: v20.20]` onward.
Parent: review finding · Safety: FC-10 · Verify: Test (migration tests) · Status: Proposed
**Now:** Migrations exist; untested (PART).

### T2-CON-080 — Responsiveness (NEW)
**Statement:** On the target phone:
- the plot list shall be usable within `[TBC-15: 2 s]` of a cold start (after the first start)
- the layout shall respond to touch without visible lag with up to `[TBC-16: 500]` plants on one plot
- a catalog tier switch shall complete within `[TBC-17: 10 s]`, with progress shown

The maximum number of plants per plot shall be `[TBC-16]`.
Parent: review finding · Verify: Test (target measurement) · Status: Proposed
**Now:** Not measured; startup work runs on the main thread (NONE).

---

## 5. Validation rules (VAL)

### T2-VAL-010 — Dimensions required
**Statement:** The system shall not create or save a plot while its length or width is empty, zero, not a
number, or outside the allowed range (T2-CFG-090).
Parent: ConOps §5.3 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-VAL-012 — Changing the scale source
**Statement:** If the source of a plot's dimensions is changed after plants have been placed (typed ↔
measured), the system shall warn the user that all plants will be removed, and shall remove them only if
the user confirms.
Parent: ConOps §5.5 · Verify: Test · Status: **Suspended — D-03** (only typed dimensions exist today)
**Was:** "…clear all active canvas seed node placements and require a layout reset if the user changes the configuration scale engine source type post-initialization."
**Why changed:** Added a confirmation instead of silent clearing.

### T2-VAL-015 — Unusual dimensions need confirmation
**Statement:** When the user enters a plot length or width that is allowed but below `[TBC-18: 0.30 m]` or
above `[TBC-18: 200 m]`, the system shall ask for confirmation before creating or updating the plot.
Parent: ConOps §5.5 · Verify: Test · Status: Proposed
**Was:** "…trigger a confirmation prompt if values are unrealistically small or large."
**Why changed:** "Unrealistically" given values.
**Now:** Hard limits only; no confirmation (PART).

### T2-VAL-017 — Resizing must keep plants inside
**Statement:** The system shall refuse a change to a plot's dimensions that would leave any plant or path
outside the new boundary, and shall show which plants or paths are affected.
Parent: ConOps §5.5 · Verify: Test · Status: Proposed
**Was:** "…reject updates to a plot's physical scale dimensions if the modification recomputes existing node pin coordinates to lie outside the newly bound layout boundary."
**Why changed:** Wording simplified; paths added. Plant positions are stored in metres and don't change on
resize.
**Now:** No resize function (NONE).

### T2-VAL-020 — Plants stay inside the plot
**Statement:** The system shall accept a plant position (from placing, moving, auto-populating or a Plan B
choice) only if the plant's centre lies inside the plot boundary `[TBC-19: centre inside, or the whole
spacing circle inside]`.
Parent: ConOps §5.5 · Verify: Test · Status: Proposed
**Was:** "…validate user-entered seed node coordinates to ensure they fall strictly within the boundaries of the defined layout image."
**Why changed:** Now covers every way a plant can get a position. Today a dragged plant can be moved off
the plot.
**Now:** PART.

### T2-VAL-030 — Undo and redo
**Statement:** The system shall let the user undo and redo their layout edits (plants and paths), step by
step, up to the configured history depth (T2-CFG-080). Undo shall restore the layout exactly as it was
before the edit; redo shall re-apply it exactly.
Parent: ConOps §5.5 · Verify: Test · Status: Proposed
**Was:** "…a single-step "Undo" and "Redo" buffer for active seed node placement manipulations…"
**Why changed:** Your later requirement T2-CFG-080 made the depth configurable, which contradicts "single-step".
Multi-step is kept, since it is what you built and asked for. Paths added.
**Now:** Multi-step, but undo→redo can duplicate plants (PART).

### T2-VAL-040 — Spacing rule (NEW — was implicit)
**Statement:** The system shall refuse a plant position where the distance between its centre and any other
plant's centre is less than (sum of both spacing radii) × the spacing margin (T2-CFG-040), with a
tolerance of `[TBC-20: 0.1 mm]` so that circles that exactly touch are allowed. The refusal message shall
name the conflicting plant, the actual distance and the required distance, in the display unit.
Parent: ConOps §5.3; KB Part 11 · Safety: FC-01, FC-02 · Verify: Test · Status: Proposed
**Now:** Implemented, but touching circles are wrongly refused due to rounding (SVP §7.4), and the message
always uses metres (PART).

### T2-VAL-050 — Paths are kept clear (NEW — was implicit)
**Statement:** The system shall refuse a plant position where the plant's spacing circle overlaps a no-plant
path (T2-FUN-100).
Parent: existing functionality · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-VAL-070 — Moved plants follow the same rules
**Statement:** A plant moved to a new position shall be subject to every rule for a new placement
(T2-VAL-020/040/050, T2-INT-030), ignoring itself. If the move is refused, the plant shall return to its
original position and the system shall say why.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** Same rules except the boundary check (PART).

### T2-VAL-080 — Choosing a variety
**Statement:** Wherever the user chooses a variety (placing, changing a variety, auto-populating), the system
shall offer a three-step choice (Category → Species → Cultivar), with search available at the species and
cultivar steps.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Was:** "…once the catalog is large enough that a flat list is not practically navigable."
**Why changed:** The condition was removed, because every tier is large enough.
**Now:** IMPL.

---

## 6. Operating environment (ENV)

### T2-ENV-010 — Orientation never corrupts the layout
**Statement:** Rotating the device, or changing display size or font size, shall not change any stored plant or
path position, and shall not lose work in progress. The layout shall stay usable in portrait and in
landscape `[TBC-21: or lock the layout screen to one orientation — decision D-04]`.
Parent: ConOps §5.5 · Verify: Test (target) · Status: Proposed
**Was:** "…lock the application orientation to a specific user-chosen mode (e.g., Portrait) during active canvas plotting to prevent coordinate recalculation errors."
**Why changed:** Your goal (no coordinate errors) is kept as the requirement. The lock was only one way to
achieve it, and Android 16 ignores orientation locks on large screens.
**Now:** No lock; rotation resets the screen (PART / DEC).

### T2-ENV-020 — Interruptions don't lose work
**Statement:** If the app is interrupted (phone call, switching apps, the system closing the app in the
background), then on return the system shall show the same plot and screen, with the same mode, zoom and
position, and with any unfinished work (path or area points being drawn, text being entered in a dialog)
restored.
Parent: ConOps §5.5 · Verify: Test (target, "Don't keep activities") · Status: Proposed
**Was:** "…serialize temporary session state variables during Android onPause/onStop lifecycle interruptions to prevent current transactional data loss."
**Why changed:** Stated as user-visible behaviour, without Android method names.
**Now:** Everything unsaved is lost; the app returns to the plot list (NONE).

### T2-ENV-025 — Location accuracy gate
**Statement:** The system shall not use a device location whose reported horizontal accuracy is worse than
the configured limit (default 15 m, range 3–100 m). In that case it shall tell the user and offer manual entry.
Parent: ConOps §6.2 · Safety: FC-13 · Verify: Test · Status: Proposed
**Was:** "…reject automated device geographic coordinate readings failing to satisfy a maximum horizontal uncertainty limit of +/- 15 meters."
**Why changed:** Linked to the Settings value (already configurable in the app); fallback stated.
**Now:** NONE.

### T2-ENV-030 — Low-light warning
**Statement:** While the camera is in use, the system shall show a warning when ambient light is below the
configured threshold (default 10 lux, range 1–50 lux), updated at least once per second, and remove it when
the light is sufficient again. Capture shall remain possible.
Parent: ConOps §5.1 · Verify: Test (target) · Status: Proposed
**Was:** "…continuously evaluate ambient device light sensors post-camera activation and present a low-lux warning UI if illumination levels threaten image-processing fidelity."
**Why changed:** "Continuously" and "threaten fidelity" replaced by values.
**Now:** CODE.

### T2-ENV-040 — Back button behaviour (NEW)
**Statement:** The Back action (button or gesture) shall return to the previous screen. On the layout
screen with unfinished work (T2-ENV-020), it shall first ask whether to discard that work. On the plot list,
it shall leave the app.
Parent: review finding (HLR-UI-080 intent) · Safety: FC-10 · Verify: Test · Status: Proposed
**Now:** Back exits the app from every screen (NONE).

### T2-ENV-050 — Display adaptation (NEW)
**Statement:** On the target phone, no control or content shall be hidden behind the status bar, camera
cut-out or navigation bar, in portrait or landscape. All functions shall remain usable at the largest system
font size and display size.
Parent: review finding (Android 16) · Verify: Test (target) · Status: Proposed
**Now:** Not handled (NONE).

### T2-ENV-060 — Accessibility (NEW)
**Statement:**
- Interactive controls shall be at least 48 dp × 48 dp.
- Icons and controls shall have spoken labels for screen readers.
- Varieties shall be distinguishable by something other than colour alone `[TBC-22: e.g. a letter or
  pattern on each plant]`.

Parent: review finding · Verify: Test + Inspection · Status: Proposed
**Now:** Partial (NONE formally).

---

## 7. Interactions and automatic updates (INT)

### T2-INT-010 — Resizing redraws at the new scale
**Statement:** After a plot's dimensions are changed (T2-FUN-070, T2-VAL-017), the layout, spacing zones,
ruler and overlays shall be redrawn at the new scale. Plant and path positions (in metres) shall be unchanged.
Parent: ConOps §5.5 · Verify: Test · Status: Proposed
**Was:** "…automatically recalculate and update all existing seed nodes' physical spacing overlays based on the updated scale."
**Why changed:** Clarified that positions don't move.
**Now:** No resize (NONE).

### T2-INT-020 — Changing a planting date updates dependent dates
**Statement:** When a plant's planting date is changed, its harvest date, germination-overdue state and any
planting-window checks shall be recalculated immediately.
Parent: ConOps §5.5 · Verify: Test · Status: Proposed
**Was:** "If a user changes the global "Planting Date," …recalculate and update all derived dependent attributes (germination thresholds, target harvest dates)."
**Why changed:** Planting dates are per plant, not global.
**Now:** Date not editable (NONE).

### T2-INT-030 — Companion conflicts
**Statement:** When companion rules are enforced (T2-CFG-050), the system shall refuse a plant position
within `[TBC-23: 2 ×]` the required spacing distance of a plant of an antagonist species, and name the
conflicting plant. When the rules are not enforced, the system shall allow the position but mark both plants
with a visible conflict indicator `[TBC-24]`.
Parent: ConOps §5.3 · Safety: FC-03 · Verify: Test · Status: Proposed
**Was:** "…continuously monitor spatial coordinates of seed nodes and trigger a visual conflict warning if a newly designated node violates the botanical exclusion zone (companion planting restriction) of an adjacent node."
**Why changed:** Resolves the contradiction between "warning" (original) and "refusal" (built), and the
enforcement toggle. Proposal: refuse when enforced; warn when not.
**Now:** Refuses when enforced; no indicator when not (PART).

### T2-INT-040 — Weed-risk overlay
**Statement:** The system shall, on request, shade every part of the plot that is outside all plants' spacing
zones and outside paths, as "weed-risk" guidance, labelled as display-only guidance.
Parent: ConOps §5.4 · Safety: FC-07 · Verify: Test · Status: Proposed
**Was:** "…generate an inverse graphical overlay ("Weed Control Mask") defining all coordinate matrices outside the active seed and spacing areas as unplanted/clearance target zones."
**Why changed:** Plainer wording. Excluding paths is a proposal `[TBC-25]` (today paths are shaded too).
**Now:** IMPL.

### T2-INT-070 — Zoom and pan
**Statement:** The system shall let the user zoom the layout between the configured minimum and maximum
(T2-CFG-070) and pan across it, without changing any stored data.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL (buttons + drag).

### T2-INT-080 — Zoom and pan are never accidental
**Statement:** Zooming and panning shall not be triggered by the one-finger gestures used to place, draw or
move `[TBC-26: either the current separate Zoom/Pan mode, or two-finger pinch and pan that are always
available]`.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Was:** "Zoom and pan shall be reachable via an explicit, deliberate toggle rather than always-active gesture recognition…"
**Why changed:** Your goal (no accidental zoom) is kept. The toggle was one way to meet it. Two-finger
gestures are the usual alternative on phones.
**Now:** IMPL (toggle).

### T2-INT-090 — Move plants
**Statement:** The system shall let the user drag a placed plant to a new position, subject to T2-VAL-070.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-INT-100 — Move is locked by default
**Statement:** Dragging plants shall only be possible after the user has explicitly turned on Move mode. Move
mode shall be off whenever a plot is opened.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL.

---

## 8. Measurement precision (PRE)

### T2-PRE-010 — Calibration before measuring
**Statement:** If the measuring method (T2-FUN-025) needs calibration, the system shall guide the user through
it before any measurement is shown.
Parent: ConOps §5.2 · Verify: Test · Status: **Suspended — D-03**
**Was:** "…require a sensor calibration routine (e.g., planar reference step) before generating measurement data from the onboard IMU/camera framework."

### T2-PRE-020 — Measurements show their uncertainty
**Statement:** Any dimension obtained by measurement shall be displayed together with its estimated
uncertainty (for example "3.20 m ± 0.05 m").
Parent: ConOps §5.2 · Safety: FC-14 · Verify: Test · Status: Proposed (applies once T2-FUN-025 exists)
**Was:** "…display estimated dimensions with an explicit tolerance margin indication (e.g., +/- X cm) based on sensor accuracy readings."
**Now:** Only in unused IMU code.

### T2-PRE-030 — IMU as primary tracking
**Statement:** —
Status: **Suspended — D-03; recommended for deletion.** It prescribes one measuring method (raw inertial
tracking), and that method is not accurate enough on a phone.
**Was:** "…maintain raw IMU sensor alignment calculations as the primary coordinate tracking matrix."

### T2-PRE-040 — Corner markers to correct the photo
**Statement:** The system shall let the user mark the four corners of the plot on its photo, and shall
correct the photo's perspective so that it matches the plot's rectangle.
Parent: ConOps §5.1 · Safety: FC-12 · Verify: Test · Status: Proposed (future) `[TBC-27: still wanted?]`
**Was:** "…optional interface allowing the user to map visual fiducial indicators (colorful flags) at boundary thresholds (corners) to bound and correct coordinate positioning drift on the visual canvas matrix."
**Why changed:** Stated as the user-visible result: a photo aligned with the plot.
**Now:** NONE.

---

## 9. Error handling (ERR)

### T2-ERR-010 — Storage errors fail safely
**Statement:** If reading or writing the app's data fails, the system shall cancel the action in progress,
leave the stored data as it was before the action, show a message explaining what failed and what the user
can do, and keep running.
Parent: ConOps §5.5 · Safety: FC-10, FC-16 · Verify: Test (fault injection) · Status: Proposed
**Was:** "Upon detection of an unrecoverable database read/write exception, the system shall terminate the active action safely and display a descriptive local error message."
**Why changed:** Added "data unchanged" and "keep running".
**Now:** Some failures crash the app (NONE).

### T2-ERR-020 — Low storage blocks new photos
**Statement:** When free device storage is below the configured floor (default 5 %, range 1–25 %), the system
shall show a warning and shall not allow new photos, until space is freed. All other functions shall keep
working.
Parent: ConOps §6 · Verify: Test · Status: Proposed
**Was:** "Upon reaching 95% of allocated local application storage capacity, the system shall generate a system warning and prohibit new image captures until space is cleared."
**Why changed:** Resolves the conflict between "95 % of allocated app storage" (T2) and "≤5 % free disk"
(HLR/Settings). Android doesn't allocate a fixed storage quota to apps, so free device storage is the
measurable quantity.
**Now:** CODE.

### T2-ERR-030 — Image processing failure
**Statement:** If a photo can't be processed (for example because it is too large for available memory), the
system shall keep the plot's previous photo (or none), show a message, and keep running. If the photo or
overlays can't be drawn, the layout shall be shown without them.
Parent: ConOps §5.1 · Verify: Test · Status: Proposed
**Was:** "If the local image transformation process exceeds the available JVM memory heap allocation during spatial scaling, the system shall safely terminate the transform layer and fall back to displaying the raw, un-overlayed plot image rather than triggering an OutOfMemory runtime crash."
**Why changed:** Stated without JVM terms. Storing the raw oversized image (the current code's fallback)
would break T2-CON-040, so the new photo is not stored at all.
**Now:** CODE.

---

## 10. Security (SEC)

### T2-SEC-010 — Encrypted data
**Statement:** The system shall store all user data encrypted:
- plots, plants, paths, custom varieties, settings and the audit log
- photos `[TBC-28]`

The key shall be held in the phone's hardware-backed key store, and shall never be written to storage in
readable form.
Parent: ConOps §5.6 · Safety: FC-11 · Verify: Analysis + Test · Status: Proposed
**Was:** "Encrypt persisted application data at rest."
**Why changed:** Scope and key protection stated. Note: photos are stored unencrypted in today's camera code.
**Now:** Database encrypted (PART).

### T2-SEC-020 — Validate imports
**Statement:** The system shall reject an import file as a whole if its format version is unknown, its
signature doesn't verify (T2-SEC-040), or any value is outside its allowed range. A rejected import shall
change nothing and shall tell the user why.
Parent: ConOps §5.6 · Safety: FC-15 · Verify: Test · Status: Proposed (future, with T2-DAT-110)
**Was:** "Validate imported data before processing."
**Now:** NONE.

### T2-SEC-030 — Audit log
**Statement:** The system shall keep a local log of these events, each with its date/time:
- data reset
- encryption-key loss
- battery overrides
- imports accepted and rejected
- catalog tier switches

The log shall be built so that changing or removing any earlier entry is detected when the log is checked.
The user shall be able to view the log and its check result.
Parent: ConOps §5.6 · Verify: Test · Status: Proposed
**Was:** "Maintain tamper-evident audit records."
**Why changed:** Events and the check made explicit.
**Now:** Tamper-evident log exists; only startup events recorded; never checked or shown (PART).

### T2-SEC-040 — Signed exports
**Statement:** Export files shall carry a digital signature that lets the receiving device confirm the file
was not modified after export and which device or owner produced it.
Parent: ConOps §5.6 · Safety: FC-15 · Verify: Test · Status: Proposed (future)
**Was:** "Support digitally signed export packages."
**Why changed:** Verification on *another* device is required, which today's device-only key can't do.
**Now:** CODE.

### T2-SEC-050 — Backups and lost keys (NEW)
**Statement:** The encrypted data shall be excluded from the phone's automatic backup. If at start-up the
encryption key is missing or doesn't match the data (for example after restoring the phone), the system
shall explain the situation and offer to start with empty data (keeping the old file until the user
confirms). It shall never crash or silently delete data.
Parent: review finding (REVAMP PLT-06) · Safety: FC-10 · Verify: Test (target backup/restore) · Status: Proposed
**Now:** Restoring a backup makes the app crash at every start (NONE).

---

## 11. Battery safeguards (SAF)

### T2-SAF-010 — Low battery never blocks the camera
**Statement:** A low battery level shall never, on its own, prevent the user from taking a plot photo.
Parent: ConOps §6.1 · Verify: Test · Status: Proposed
**Was:** "Low battery shall not hard-block camera operation."
**Now:** NONE.

### T2-SAF-020 — Low battery warning
**Statement:** When the camera is opened with the battery below the configured threshold `[TBC-29: default
15 %, range 5–50 %]`, the system shall show a warning explaining the risk (for example the phone shutting
down during capture).
Parent: ConOps §6.1 · Verify: Test · Status: Proposed
**Was:** "Display warning when battery falls below configured threshold."
**Now:** NONE.

### T2-SAF-030 — Continue or cancel
**Statement:** The warning in T2-SAF-020 shall offer "Continue" and "Cancel". Continue opens the camera;
Cancel returns to the previous screen.
Parent: ConOps §6.1 · Verify: Test · Status: Proposed
**Was:** "Permit user-approved override continuation."
**Now:** NONE.

### T2-SAF-040 — Record overrides
**Statement:** Each "Continue" choice in T2-SAF-030 shall be recorded in the audit log (T2-SEC-030) with the
date/time, battery level and plot.
Parent: ConOps §6.1 · Verify: Test · Status: Proposed
**Was:** "Record override events locally."
**Now:** NONE.

---

## 12. Platform independence (PLT)

### T2-PLT-010 — Platform-neutral data model
**Statement:** The data model (plots, plants, paths, varieties, settings) shall be defined and documented
independently of Android, in units and types any platform can represent.
Parent: ConOps §7 · Verify: Review + Analysis · Status: Proposed
**Was:** "Canonical data model shall be platform-neutral."
**Now:** Model tied to Android database annotations (PART).

### T2-PLT-020 — Documented exchange format
**Statement:** The export file format (T2-DAT-110) shall be fully documented and versioned, so that another
client (desktop or web) could read and write it.
Parent: ConOps §7 · Verify: Review · Status: Proposed
**Was:** "Desktop/Web clients shall use identical schemas."
**Why changed:** No other client exists, so the verifiable part is the documented format.
**Now:** IMPL: format version 1 documented in docs/PLAN_FILE_FORMAT.md.

### T2-PLT-030 — Rules independent of the screen
**Statement:** All planning rules and calculations (spacing, companions, dates, Plan B, auto-populate,
irrigation, weed-risk area, unit conversion) shall produce the same results regardless of the user interface
that calls them, and shall be testable without any user interface.
Parent: ConOps §7 · Verify: Analysis + Test · Status: Proposed
**Was:** "Business logic shall remain UI-independent."
**Now:** Engines are separate; much logic still sits in screen code (PART).

### T2-PLT-040 — Portable planner for computers (NEW 2026-09-27)
**Statement:** A planning client shall run on Windows, macOS, Linux and ChromeOS computers (and Android
devices) inside a standard web browser, without installing anything and without an account or server. It shall
open and save the same plan files as the Android app (T2-PLT-020), so a plan made on a computer can be
reviewed and changed on the phone and back, and it shall use the same planning rules (T2-PLT-030).
Parent: owner request 2026-09-27 · Safety: FC-15 · Verify: Test (each platform's browser) · Status: Proposed
**Now:** PART (2026-09-27): single-file planner `web/dist/smart-garden-planner.html` (option A), built from the
same `core/` source; tested in Chromium on Linux. Other browsers/platforms not yet tested (docs/CROSS_PLATFORM_PLAN.md W4).

---

## 13. Settings (CFG)

Values and ranges for each setting are specified at HLR level (HLR-CFG). The defaults and ranges are the
ones implemented today unless stated otherwise.

### T2-CFG-010 — One settings screen
**Statement:** The system shall provide one screen where every user-adjustable setting can be viewed and
changed, grouped by topic. Settings for features that aren't available shall not be shown `[TBC-30]`.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL (shows unconnected sensor settings).

### T2-CFG-020 — Settings persist
**Statement:** Setting changes shall be saved immediately, shall survive restarts and app updates, and shall
take effect without reinstalling or losing data.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-CFG-030 — Reset to defaults
**Statement:** The system shall provide one action, with confirmation `[TBC-31]`, that resets all settings to
their defaults, without changing plots, plants or varieties.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL (no confirmation).

### T2-CFG-040 — Adjustable spacing strictness
**Statement:** The user shall be able to set the spacing margin used by T2-VAL-040, from 0.30× to 2.00×
(default 1.00×).
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-CFG-050 — Companion rule toggle
**Statement:** On the Pro tier, the user shall be able to turn companion/antagonist enforcement off without
affecting spacing enforcement (see T2-INT-030 for behaviour when off). On Basic and Standard, enforcement shall
always be on, and the setting shall be shown as locked with an explanation.
Parent: KB Part 11; FEATURE_ROADMAP FR-012 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-CFG-060 — Ruler settings
**Statement:** The user shall be able to set the ruler's tick spacing and text size.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-CFG-070 — Zoom settings
**Statement:** The user shall be able to set the minimum zoom, maximum zoom and zoom step. The minimum shall
always stay below the maximum.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-CFG-080 — Undo depth
**Statement:** The user shall be able to set the undo/redo history depth (5–100 steps, default 25).
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-CFG-090 — Plot size limits
**Statement:** The user shall be able to set the minimum and maximum allowed plot length/width, within an
absolute range of 0.01 m to 2000 m (defaults 0.05 m and 1000 m).
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Now:** IMPL.

### T2-CFG-100 — Display unit
**Statement:** The user shall be able to choose metres or inches as the display unit, from Settings and from the
layout screen. Every distance shown or entered anywhere in the app shall use the chosen unit. Stored values
and calculations shall always be in metres.
Parent: KB Part 11 · Verify: Test · Status: Proposed
**Was:** "…converting only at input/display boundaries…"
**Why changed:** "Every distance, anywhere" made explicit, because several messages still show metres.
**Now:** PART.

---

## 14. Deleted (process requirements moved to the QA plan)

| ID | Was | Moved to |
|---|---|---|
| T2-GOV-010 | Execute formal 11-perspective audits. | SGP-SQAP-001 §6 |
| T2-GOV-020 | Produce review artifacts at handoff. | SGP-SQAP-001 §6 |

---

## 15. Values and choices for you to confirm `[TBC]`

Settle these whenever you're ready. Each proposal is what the requirement says until you change it.

| TBC | Requirement | Question | Proposal |
|---|---|---|---|
| 01 | FUN-010 | Allow picking a photo from the gallery as well as the camera? | Yes |
| 02 | FUN-030 | How far in the future can a planting date be? | 365 days |
| 03 | FUN-060 | Can the user move the water-source point? | Yes; default top-left corner |
| 04 | FUN-060 | Hoses and sprinklers: separate future feature? | Yes, future |
| 05 | FUN-070 | Archive (hide) plots as well as delete? | Yes |
| 06 | FUN-080 | Season end when no location is set | A "season end" date in Settings (default 31 Oct) |
| 07 | DAT-040 | Tailor watering to soil/field type? | Only after soil input (FR-013) |
| 08 | DAT-050 | Source for germination conditions data | To be chosen (seed-supplier data) |
| 09 | DAT-080 | Climate coverage outside the US? | US only for now |
| 10 | DAT-090 | Frost-tolerant crops may be sown before the last frost? | Yes, by a per-variety offset |
| 11 | DAT-140 | Keep roles (Owner/Contributor/Viewer)? | Keep, future |
| 12 | DAT-220 | Allowed ranges for catalog values | Radius 0.02–10 m, germination 1–365 d, harvest 1–3650 d, zones 1–13 (bundled Pro data reaches 180 d germination and 2,560 d to harvest) |
| 13 | CON-040 | Maximum stored photo size | 1 MB |
| 14 | CON-070 | Oldest version whose data must survive an update | v20.20 |
| 15 | CON-080 | Cold start to usable plot list | ≤ 2 s |
| 16 | CON-080 | Maximum plants per plot / smooth-interaction target | 500 |
| 17 | CON-080 | Maximum tier switch time | ≤ 10 s with progress |
| 18 | VAL-015 | "Unusual" plot size thresholds | < 0.30 m or > 200 m |
| 19 | VAL-020 | Must the plant's centre, or its whole spacing circle, be inside the plot? | Centre |
| 20 | VAL-040 | Tolerance for touching circles | 0.1 mm |
| 21 | ENV-010 | Support both orientations, or lock the layout screen? (D-04) | Both |
| 22 | ENV-060 | How to tell varieties apart without colour | Letter code on each plant |
| 23 | INT-030 | "Nearby" distance for antagonists | 2 × required spacing (today's rule) |
| 24 | INT-030 | When rules are off: show a conflict indicator? | Yes |
| 25 | INT-040 | Exclude paths from the weed-risk shading? | Yes |
| 26 | INT-080 | Separate Zoom/Pan mode, or two-finger pinch/pan? | Two-finger pinch/pan (keep +/− buttons) |
| 27 | PRE-040 | Keep corner-marker photo correction? | Keep, future |
| 28 | SEC-010 | Encrypt photos too? | Yes |
| 29 | SAF-020 | Low-battery threshold | 15 % (range 5–50 %) |
| 30 | CFG-010 | Hide settings of unavailable features? | Yes |
| 31 | CFG-030 | Confirmation before reset? | Yes |

## 16. Counts

| Status | Count |
|---|---|
| Proposed (original IDs) | 77 (PRE-020 applies only once FUN-025 exists) |
| Proposed (new) | 22 (17 in Rev A; FUN-160, FUN-170, FUN-180, FUN-190, PLT-040 added 2026-09-27) |
| Suspended (D-03) | 4 (FUN-025, VAL-012, PRE-010, PRE-030) |
| Merged | 1 (DAT-070) |
| Deleted | 2 (GOV-010, GOV-020) |
| **Total original IDs accounted for** | **84** (77 + 4 + 1 + 2) |

Next step: re-baseline the HLRs so that each traces to these T2 requirements (SGP-DWR-001 DW-0301 continues).
