# Smart Garden Planner — Consolidated Knowledge Base
## Part 11: New Requirements for Settings Infrastructure, Canvas Interaction, and Tiered Catalog (T2 / HLR / LLR)

This documents everything implemented **after** the Part 10 batch (recovery plan, straight/curved
paths, auto-populate, Encyclopedia CRUD, ruler, color coding) through the tiered-catalog and
3-step-picker delivery. Same honesty framing as Part 10: these requirements were written after
the code, to document what was built, not before it as disciplined DO-178C process would require.
No independent review board pass or automated verification exists for any ID below beyond what's
explicitly noted. Two new subsystems are added to the four from Part 1 §2.1 and the one from
Part 10.

---

## Subsystem 6: Settings & Configuration Infrastructure

Covers: the Settings screen, persisted app-wide configuration, and every previously-hardcoded
value that now reads from it.

### T2 — Product-Level Requirements

- **T2-CFG-010:** The system shall provide a single screen where every user-configurable
  behavioral/numeric setting can be viewed and changed, organized into sections by topic.
- **T2-CFG-020:** Setting changes shall persist across app restarts and take effect without
  requiring reinstallation or data loss.
- **T2-CFG-030:** The system shall provide a single control to reset all settings to their
  original defaults.
- **T2-CFG-040:** The plant-to-plant spacing enforcement rule shall be adjustable by the user
  (a multiplier on the standard non-overlap distance), rather than fixed.
- **T2-CFG-050:** Companion/antagonist enforcement shall be independently toggleable without
  disabling spacing enforcement.
- **T2-CFG-060:** Ruler tick spacing and text size shall be user-configurable.
- **T2-CFG-070:** Canvas zoom bounds (minimum, maximum) and zoom step increment shall be
  user-configurable.
- **T2-CFG-080:** Undo/redo history depth shall be user-configurable.
- **T2-CFG-090:** Plot dimension validation bounds (minimum/maximum length and width accepted on
  the Creator screen) shall be user-configurable.
- **T2-CFG-100:** The system shall support a user-selectable distance display unit (meters or
  inches), converting only at input/display boundaries — all internal storage and calculation
  shall remain in a single canonical unit regardless of the display setting.

### T3 — HLRs

- **HLR-CFG-010:** Persist each setting as an individually-keyed row in the existing generic
  key-value configuration table, rather than introducing a new schema/table — each key falls back
  independently to its documented default if missing or unparseable.
- **HLR-CFG-020:** Load the full settings object once per screen that depends on it, at screen
  entry, rather than maintaining a single persistent in-memory instance across the whole app.
- **HLR-CFG-030:** Group settings into UI sections matching T2-CFG-010's categories: Units,
  Catalog, Spacing & Placement, Canvas Display, Plot Validation, Sensors & Hardware.
- **HLR-CFG-040:** Apply the configured spacing-margin multiplier and companion-rule toggle to
  every placement-validation call site uniformly (individual placement, drag-move, change-variety,
  auto-populate) — no call site may bypass the configured values.
- **HLR-CFG-050:** Convert between the internal canonical distance unit (meters) and the
  configured display unit at exactly two boundaries: formatting a stored value for display, and
  parsing a user-entered value back to the canonical unit before it is stored or used in any
  calculation.

### T4 — LLRs

- **LLR-CFG-010-A:** `AppSettings` is an immutable data class holding every configurable value
  with defaults matching what was previously hardcoded, so introducing the settings system alone
  changes no behavior until a value is actually edited.
- **LLR-CFG-020-A:** `SettingsRepository.load()` reads each key independently via
  `configValue?.toFloatOrNull() ?: default` (or the `Int`/`Boolean`/enum equivalent) — a single
  corrupted or missing key cannot fail the load of any other setting.
- **LLR-CFG-020-B:** `SettingsRepository.save()` writes every field as its own key on every save
  call (full-object save, not partial/dirty-field-only), keeping the persisted representation
  simple and consistent.
- **LLR-CFG-040-A:** `CompanionPlantingValidator.validatePlacement()` takes
  `marginMultiplier: Float = 1.0f` and `enforceCompanionRules: Boolean = true` as parameters
  (not internal constants); every call site in `CanvasWorkspaceScreen` passes
  `settings.spacingMarginMultiplier` / `settings.enforceCompanionAntagonistRules` explicitly.
- **LLR-CFG-050-A:** `DistanceFormatter.format(meters, unit)` converts meters → the display unit
  and formats with the unit's suffix; `DistanceFormatter.parseToMeters(text, unit)` performs the
  inverse. No other code path performs unit math — every UI surface showing or accepting a
  distance goes through one of these two functions.
- **LLR-CFG-050-B:** A quick-access unit toggle chip lives directly on the Canvas header
  (`[in]`/`[m]`, tap to switch), in addition to the full control in Settings → Units, per the
  explicit request that switching be reachable without navigating away from the canvas.

---

## Subsystem 7: Canvas Interaction — Zoom/Pan and Drag-to-Reposition

Covers: pinch-free zoom/pan (button + drag, mode-gated), and drag-to-reposition of placed plants
(lock-gated). Both share a design principle worth stating once: **each is implemented as a
mode/toggle mutually exclusive with every other canvas gesture**, specifically to avoid two
independent gesture detectors competing for the same touch input — a real class of bug hit twice
before landing on this pattern (documented in the session history: buttons shifting position,
then planted nodes appearing to disappear when zooming).

### T2 — Product-Level Requirements

- **T2-INT-070:** The system shall allow the user to zoom and pan the canvas view without
  affecting the underlying plot data or coordinate system.
- **T2-INT-080:** Zoom and pan shall be reachable via an explicit, deliberate toggle rather than
  always-active gesture recognition, to prevent accidental triggering during normal plant/path
  placement.
- **T2-INT-090:** The system shall allow the user to drag an already-placed plant to a new
  position, subject to the same validation as initial placement.
- **T2-INT-100:** Drag-to-reposition shall be reachable via an explicit lock/unlock toggle, off
  by default, to prevent accidental repositioning during normal use.
- **T2-VAL-070:** A dragged plant's destination shall be rejected (and the plant returned to its
  original position) under the same conditions initial placement would be rejected.

### T3 — HLRs

- **HLR-ZOOM-010:** Maintain a persistent zoom scale factor and constrain it within the
  configured min/max bounds (Settings → Canvas Display) on every adjustment.
- **HLR-ZOOM-020:** Size the canvas element explicitly as a function of a stable, once-measured
  reference viewport size and the current zoom factor, rather than relying on the same layout
  node's constraints once a scrollable (pan) modifier is also attached to it.
- **HLR-ZOOM-030:** Attach pan (scroll) behavior only while Zoom/Pan mode is active, and only to
  a layout node distinct from the one used for the stable reference-size measurement in
  HLR-ZOOM-020.
- **HLR-ZOOM-040:** Present zoom controls (+/-, and a conditional recenter control) in a fixed
  screen position that does not shift based on which of those controls happen to be visible at a
  given moment.
- **HLR-ZOOM-050:** Adjust the ruler's effective tick interval as an inverse function of the
  current zoom factor, so tick density visually increases when zoomed in.
- **HLR-MOVE-010:** While Move Mode is active, a drag gesture beginning within an existing
  plant's hit-radius shall reposition that plant's live rendered position for the duration of the
  drag, without committing any change until release.
- **HLR-MOVE-020:** On drag release, validate the candidate destination coordinates using the
  same spacing/companion/no-plant-path checks as new placement, excluding the dragged plant
  itself from the neighbor set.
- **HLR-MOVE-030:** On successful validation, persist the new coordinates and push a new
  undo/redo snapshot; on failed validation, discard the drag with no persisted change and no
  undo/redo entry.

### T4 — LLRs

- **LLR-ZOOM-010-A:** `zoomScale` bounded via `.coerceIn(settings.zoomMin, settings.zoomMax)` on
  every +/- button press, adjusted by `settings.zoomStep`.
- **LLR-ZOOM-020-A:** `BoxWithConstraints` with no scroll modifiers directly attached captures
  `maxWidth`/`maxHeight` once as `baseWidth`/`baseHeight`; the `Canvas` is explicitly sized via
  `Modifier.size(baseWidth * zoomScale, baseHeight * zoomScale)`.
- **LLR-ZOOM-030-A:** A separate inner `Box` wraps only the `Canvas`, with
  `Modifier.horizontalScroll(...).verticalScroll(...)` applied conditionally via
  `Modifier.then(if (zoomPanModeEnabled) ... else Modifier)` — the outer measuring `BoxWithConstraints`
  never carries these modifiers, so its size measurement cannot be corrupted by them.
- **LLR-ZOOM-040-A:** Zoom controls render in a `Column` anchored `Alignment.BottomEnd` as a
  direct child of the outer (non-scrollable) `BoxWithConstraints`, not the inner scrollable
  content box, so they stay fixed in the viewport during pan.
- **LLR-ZOOM-050-A:** `drawRuler(..., tickIntervalM = settings.rulerTickIntervalM / zoomScale, ...)`.
- **LLR-MOVE-010-A:** `detectDragGestures` on a `pointerInput` keyed on `moveModeEnabled`, gated
  `if (canvasMode == PLACE_NODE && moveModeEnabled)`; `onDragStart` hit-tests the nearest node
  within a fixed 0.4m tolerance to begin the drag. The tap-detector `pointerInput` block is
  simultaneously gated `!moveModeEnabled`, making the two mutually exclusive on the same `Canvas`.
- **LLR-MOVE-020-A:** `onDragEnd` converts the live screen offset to plot-relative meters, checks
  `isInsidePath(...)`, then calls `CompanionPlantingValidator.validatePlacement()` against
  `nodesState.filter { it.id != draggedId }`.
- **LLR-MOVE-030-A:** Valid moves call `PlantedNodeDao.update()` and
  `undoStack.push(CanvasSnapshot(...))`; invalid moves only clear `draggingNodeId`/
  `dragPreviewOffset` local state, with no database write and no snapshot push.

---

## Subsystem 8: Tiered Seed Catalog and 3-Step Selection

Covers: the Basic/Standard/Pro bundled catalog, its asset-based loading mechanism, and the
Category → Species → Cultivar picker that replaced every flat variety-selection list once the
catalog grew large enough that a flat list stopped being usable.

### T2 — Product-Level Requirements

- **T2-DAT-190:** The system shall provide a bundled reference catalog of seed varieties spanning
  vegetables, fruit, herbs, flowers, and ornamentals, at three selectable size tiers.
- **T2-DAT-200:** Switching the active catalog tier shall replace only bundled (non-user-created)
  catalog entries; any variety the user has personally added or edited shall be unaffected by a
  tier switch, regardless of tier.
- **T2-DAT-210:** Companion/antagonist relationships shall be evaluated at the species level, so
  that any named cultivar of a given species correctly participates in that species'
  companion/antagonist relationships, not only cultivars explicitly and individually listed.
- **T2-VAL-080:** Variety selection (initial placement, changing an existing plant's variety, and
  auto-populate) shall present the catalog as a hierarchical Category → Species → Cultivar choice
  with search available from the Species step onward, rather than a single flat list, once the
  catalog is large enough that a flat list is not practically navigable.

### T3 — HLRs

- **HLR-CAT-010:** Store catalog data as bundled flat-file assets (not embedded source-code
  literals), parsed at load time into typed records.
- **HLR-CAT-020:** On first launch, load the Basic tier automatically if no seed records exist
  yet in the local database.
- **HLR-CAT-030:** On a tier switch, delete only catalog-sourced (non-custom) seed records before
  inserting the newly selected tier's full record set.
- **HLR-CAT-040:** Mark every seed record created or edited through the Encyclopedia's Add/Edit
  flow as user-owned (excluded from tier-switch deletion), regardless of whether it started as a
  bundled entry.
- **HLR-CAT-050:** Derive a "species" grouping key from each cultivar's catalog code by its
  shared prefix, and a display species name from the shared portion of its common name, without
  requiring a separate explicit species table.
- **HLR-SEL-010:** Present catalog Category as the first selection step, Species (grouped per
  HLR-CAT-050, with cultivar count shown per species) as the second, and individual Cultivars as
  the third.
- **HLR-SEL-020:** Provide search filtering at the Species and Cultivar steps, scoped to entries
  currently visible at that step (not the whole catalog).
- **HLR-SEL-030:** Any UI surface that previously listed the full seed dictionary in a
  non-virtualized way (a plain iterated list/menu, as opposed to a lazily-rendered scrolling list)
  shall either adopt the 3-step picker or be bounded to a naturally small subset (e.g. varieties
  actually in use on the current plot), since an unbounded non-virtualized list becomes
  impractical once the catalog reaches thousands of entries.

### T4 — LLRs

- **LLR-CAT-010-A:** Catalog lines use a 14-field pipe-delimited format
  (`botanicalCode|commonName|botanicalFamily|plantType|lifecycle|zoneMin|zoneMax|exclusionRadiusM|
  germinationDays|daysToHarvest|companionCodes|antagonistCodes|pestNotes|careNotes`), parsed via
  `SeedCatalogLoader.parseLine()`, which returns `null` (skipping the line) rather than throwing
  on a malformed row.
- **LLR-CAT-020-A:** `MainActivity.onCreate()` checks `SeedDao.count() == 0` and, if true, loads
  `CatalogTier.BASIC` via `SeedCatalogLoader` and inserts the result.
- **LLR-CAT-030-A:** `SeedDao.deleteCatalogSeeds()` is `DELETE FROM seeds WHERE isCustom = 0`;
  `SettingsScreen`'s tier-switch handler calls this before `insertAll()`-ing the newly loaded
  tier's records.
- **LLR-CAT-040-A:** `SeedEntity.isCustom` defaults to `false` for catalog-sourced records
  (`SeedCatalogLoader`) and is explicitly set `true` in the `SeedEditDialog` save handler for
  every record it produces, whether newly created or an edit of a pre-existing entry.
- **LLR-CAT-050-A:** `speciesPrefix(botanicalCode) = botanicalCode.substringBefore("-")`;
  `speciesNameOf(seed) = seed.commonName.substringBefore(" - ")`;
  `cultivarNameOf(seed) = seed.commonName.substringAfter(" - ", seed.commonName)`.
- **LLR-SEL-010-A:** `VarietyPickerDialog` maintains `step: Int` (0/1/2) and derives `categories`,
  `speciesInCategory`, and `cultivarsInSpecies` as `remember`-memoized computations keyed on the
  dictionary and current selection, avoiding recomputation on unrelated recompositions.
- **LLR-SEL-030-A:** Three call sites — initial placement (`showVarietyPicker`), change-variety
  (`changeVarietyNode`), and auto-populate (`showAutoPopVarietyPicker`) — each instantiate
  `VarietyPickerDialog` rather than an inline flat list; the options-menu color legend instead
  iterates `nodesState.map { it.seedCode }.distinct()`, bounding it to what's actually on the
  current plot rather than the full catalog.

---

## Traceability — T2 → T3 (New, this file)

| T2 Requirement | HLR Allocation |
|---|---|
| T2-CFG-010/020/030 | HLR-CFG-010, HLR-CFG-020, HLR-CFG-030 |
| T2-CFG-040/050 | HLR-CFG-040 |
| T2-CFG-060/070/080/090 | HLR-CFG-030 |
| T2-CFG-100 | HLR-CFG-050 |
| T2-INT-070/080 | HLR-ZOOM-010 through HLR-ZOOM-050 |
| T2-INT-090/100, T2-VAL-070 | HLR-MOVE-010, HLR-MOVE-020, HLR-MOVE-030 |
| T2-DAT-190 | HLR-CAT-010, HLR-CAT-020 |
| T2-DAT-200 | HLR-CAT-030, HLR-CAT-040 |
| T2-DAT-210 | HLR-CAT-050 (enables species-level grouping used by the validator fix — see Part 9 §3 for the validator change itself, which predates this file) |
| T2-VAL-080 | HLR-SEL-010, HLR-SEL-020, HLR-SEL-030 |

## Traceability — T3 → T4 (New, this file)

| HLR | LLR Decomposition |
|---|---|
| HLR-CFG-010/020 | LLR-CFG-010-A, LLR-CFG-020-A, LLR-CFG-020-B |
| HLR-CFG-040 | LLR-CFG-040-A |
| HLR-CFG-050 | LLR-CFG-050-A, LLR-CFG-050-B |
| HLR-ZOOM-010 | LLR-ZOOM-010-A |
| HLR-ZOOM-020 | LLR-ZOOM-020-A |
| HLR-ZOOM-030 | LLR-ZOOM-030-A |
| HLR-ZOOM-040 | LLR-ZOOM-040-A |
| HLR-ZOOM-050 | LLR-ZOOM-050-A |
| HLR-MOVE-010 | LLR-MOVE-010-A |
| HLR-MOVE-020 | LLR-MOVE-020-A |
| HLR-MOVE-030 | LLR-MOVE-030-A |
| HLR-CAT-010/020 | LLR-CAT-010-A, LLR-CAT-020-A |
| HLR-CAT-030 | LLR-CAT-030-A |
| HLR-CAT-040 | LLR-CAT-040-A |
| HLR-CAT-050 | LLR-CAT-050-A |
| HLR-SEL-010/020 | LLR-SEL-010-A |
| HLR-SEL-030 | LLR-SEL-030-A |

---

## Known Gaps Against These New Requirements

- **No automated test coverage** exists for any ID in this file. Everything here was verified
  only by manual code reasoning during implementation and by your own device testing across many
  rounds — real signal, but not the same as a repeatable test suite.
- **HLR-ZOOM-020/030** (the container-splitting fix) is the single highest-value candidate for a
  real UI/instrumented test if this project ever builds one out, given it's already caused two
  real user-visible bugs (buttons shifting position, plants disappearing) before landing on the
  current design.
- **T2-DAT-210 / HLR-CAT-050** — species-level companion matching is a real behavior change from
  the exact-code matching Part 2/Part 9 originally documented. If those earlier parts are ever
  consulted for the original companion-planting design intent, note that this file supersedes
  them on that specific point.
- This file does **not** cover any of the 25 new feature requests captured in
  `FEATURE_ROADMAP.md` — none of those are implemented yet, so none have requirements here. HLRs/
  LLRs for those begin only once each is actually built, per the roadmap document's own stated
  process.
