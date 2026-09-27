# Smart Garden Planner — Revamp Plan for Pixel 10 Pro (Emulator + Physical Device)

**Scope:** planning only. No code has been changed.
**Compliance planning:** see `docs/compliance/` (DO-178C / ARP4754A DAL A plans, deferred work register).
**Inputs reviewed:** every document in the repo root (ConOps, IDD/ICD, Integration Plan, Verification Plan,
Review Board record, Dev Log, Progress Journal, Test-Environment log, Master Plan, Transcript,
traceability/risk/audit files, handoff script, restart prompt), plus `Android App/` (USER_MANUAL,
FEATURE_ROADMAP, README_v20.20_CHANGES, Knowledge Base Part 11) and all Kotlin, Gradle, manifest,
resource and asset files.
**Limitation:** the project could not be compiled here (no Android SDK, and Google's Maven repository is
blocked in this environment). Every finding below comes from reading the code. Phase 1 has to be done in
Android Studio, and it will probably turn up more compile errors than are listed here.

---

## 1. What the app is trying to be (short version)

An **offline-first Android garden planner**:

1. Create a plot (name + real-world length/width; later: photo of the plot, location/ZIP).
2. Lay it out on a to-scale canvas: place plants by variety, get spacing and companion/antagonist
   conflict checks, draw no-plant paths (straight/curved), auto-populate areas (grid/hex, rectangle
   or polygon), move/delete/change plants, undo/redo, zoom/pan, ruler.
3. Overlays: inverse "weed-risk mask" (everything outside plant spacing circles) and a drip-irrigation route.
4. Crop intelligence: harvest date, germination-overdue alert → "Plan B" recovery options, pests, care,
   companions, from a bundled tiered catalog (Basic 250 / Standard 600 / Pro 2,936 varieties).
5. Encyclopedia with custom varieties + color override; Settings for every tunable value; tier gating.
6. Planned but not yet built: camera capture of the plot, sensor-based measurement (IMU), light/storage/
   battery checks, ZIP/GPS climate lookup, encrypted export/import with signatures, ownership roles,
   and the 26 roadmap items (FR-001 … FR-026).
7. Constraints: no network, no analytics, encrypted local data.

### 1.1 Functionality that must survive the revamp (checklist)

| # | Feature | Where it lives today |
|---|---|---|
| K-01 | Plot list, create plot with unit-aware dimension validation | `MainActivity.kt` Dashboard/Creator |
| K-02 | Place plant (double-tap), spacing + antagonist validation with explanatory message | `CanvasWorkspaceScreen`, `CompanionPlantingValidator` |
| K-03 | Plant detail: planted date, harvest date, change variety (validated), delete | Canvas dialogs |
| K-04 | Germination-overdue red ring + Recovery Plan (fast-track / nursery / catch-crop / keep) | `GerminationContingencyEngine` |
| K-05 | Move Mode (drag with validation, snap back) | Canvas |
| K-06 | No-plant paths: rectangle drag, curved polyline with width, tap to edit/delete | Canvas, `PathZoneEntity` |
| K-07 | Auto-populate: rectangle or polygon area, line/hex packing, conflict skip + count | `AutoPopulateEngine` |
| K-08 | Unified undo/redo for plants and paths, configurable depth | `BoundedHistoryStack`, `CanvasSnapshot` |
| K-09 | Zoom/Pan mode, zoom limits/step, recenter | Canvas |
| K-10 | Ruler with tick interval / font size, meters/inches everywhere | `drawRuler`, `DistanceFormatter` |
| K-11 | Weed-risk mask and irrigation route overlays | `WeedMaskGeometryEngine`, `IrrigationRouteCalculator` |
| K-12 | Color per variety, manual override swatches, legend of placed varieties | `VegetableColorPalette` |
| K-13 | 3-step Category → Species → Cultivar picker with search | `VarietyPickerDialog` |
| K-14 | Encyclopedia: search, detail, add/edit custom variety, companion names resolved | `EncyclopediaScreen`, `CompanionNameResolver` |
| K-15 | Tiered catalog switch that never touches custom varieties | `SettingsScreen`, `SeedCatalogLoader` |
| K-16 | Settings (all sections) + reset; tier gating (FR-012 companion toggle, FR-001 polygon) | `AppSettings`, `FeatureFlags` |
| K-17 | Encrypted database (SQLCipher + Keystore) and non-destructive migrations | `AppDatabase`, `Migrations.kt` |
| K-18 | Hash-chained audit log | `SecurityAuditLogger` |

Nothing on this list gets removed. Items can move, be rewritten or be redesigned, but each one needs an
acceptance test on the Pixel 10 Pro before the revamp is done (Phase 9).

---

## 2. Headline findings (read these first)

These are the problems most likely to stop the app from building or running properly on a Pixel 10 Pro:

1. **The build toolchain is about two years old.** The build uses AGP 8.3, Kotlin 1.9.22, kapt,
   compileSdk/targetSdk 34 and Gradle 8.7. But your Android Studio has already generated a
   `gradle/libs.versions.toml` with AGP 9.2.1, Kotlin 2.2.10 and Compose BOM 2026.02, and the build
   ignores that file entirely. The Pixel 10 Pro runs Android 16 (API 36) or newer, and Google Play now
   requires targetSdk 35/36.
2. **The SQLCipher library is deprecated and not 16 KB page-size compatible.** The build uses
   `net.zetetic:android-database-sqlcipher:4.5.4`. Its native `.so` files are not 16 KB-aligned, which
   Android 15+ 16 KB devices and emulator images require, and which Play enforces for targetSdk ≥ 35. The
   replacement is `net.zetetic:sqlcipher-android` (package `net.zetetic.database.sqlcipher`).
3. **Restoring from a Google backup crashes the app on every launch.** This is exactly what happens when
   you set up a new Pixel 10 Pro from your old phone. `allowBackup="true"` with empty backup rules backs
   up the encrypted DB and `sgp_db_key.enc`, but not the Keystore key. On the new phone, decrypting the
   passphrase throws `AEADBadTagException`, and the "self-heal" only catches `SQLiteException`.
4. **The system Back gesture exits the whole app from any screen.** There is no `BackHandler`, and
   navigation is a plain `mutableStateOf` enum. Android 16's predictive back makes this more visible.
   Rotation, dark-mode or font-scale changes, and process death all reset the app to the Dashboard, and
   the canvas loses undo history, mode and zoom, because nothing uses `rememberSaveable` or a ViewModel.
5. **The canvas does not keep the plot to scale.** The plot is stretched to fill the viewport, so X and Y
   use different pixels-per-meter. Circles are drawn with the X scale only, so on a tall portrait phone
   screen, spacing looks wrong in Y. The hit radius is a fixed 0.4 m (tiny on big plots, huge on small
   ones), and the ruler doesn't follow zoom/pan.
6. **Two confirmed crash paths and one data-corruption bug:**
   - Switching catalog tier after planting anything throws `SQLiteConstraintException`, because
     `planted_nodes.seedCode` has `ON DELETE RESTRICT` and the tier switch deletes catalog seeds first.
     The exception is uncaught and crashes the app.
   - Creating a plot runs in an unmanaged `CoroutineScope` with no error handling, so any database error
     crashes the app.
   - Undo/redo snapshots duplicate items. After `reloadNodes()` the code pushes
     `nodesState + candidateNode`, and `nodesState` already contains the new plant. Undo followed by redo
     re-inserts duplicate plants and paths. The same happens after auto-populate and path creation.
7. **Startup does heavy work on the main thread:** StrongBox key generation, the SQLCipher key
   derivation, and opening the database. That means jank and possible ANRs on first launch.
8. **Much of what the documents describe as done does not exist in the code.** Examples: Jetpack
   Navigation with Fragments, orientation lock, calibration overlay, back-press interception, JSON
   state caching in encrypted SharedPreferences, the camera screen, IMU measurement, the light-sensor
   warning, GPS/ZIP lookup, signed export/import, and roles. The Review Board "APPROVED" records and the
   2.6 MB Verification Plan (1,620 test cases, all generated from the same template text with no real
   inputs or expected values) do not reflect the actual state of the software (see §4, Phase 0).
9. **The IMU "double-integration" measurement will not work on a real phone.** Accelerometer drift
   makes metre-scale distances meaningless within seconds, and the tilt calculation in
   `SensorMeasurementEngine` is mathematically wrong. This needs a product decision (see §5, D-03).
10. **`MainActivity.kt` is one 2,257-line file** that mixes navigation, four screens, dialogs, geometry
    helpers, raw SQL, and an encryption helper. Most fixes above are much cheaper after splitting it.

---

## 3. Full issue inventory

Severity: **B** = blocker (won't build or crashes), **H** = high (wrong behaviour or data loss on the
Pixel 10 Pro), **M** = medium (UX/perf/maintainability), **L** = low (cleanup).

### 3.1 Build & toolchain

| ID | Sev | Problem | Fix |
|---|---|---|---|
| BLD-01 | B | AGP 8.3.0 / Kotlin 1.9.22 / Gradle 8.7 / compileSdk 34. Too old for API 36, and newer Studio versions may refuse or warn. | Move to the versions in `libs.versions.toml` (AGP 9.2.x, Kotlin 2.2.x, matching Gradle wrapper via the AGP Upgrade Assistant). compileSdk/targetSdk = 36, or the newest API in your SDK Manager. |
| BLD-02 | B | `libs.versions.toml` exists but is unused; the root `build.gradle.kts` hardcodes plugin versions. | Use the version catalog for all plugins and dependencies. |
| BLD-03 | B | Compose compiler set via `composeOptions.kotlinCompilerExtensionVersion` (Kotlin 1.9 style). | Apply `org.jetbrains.kotlin.plugin.compose`; delete `composeOptions`. |
| BLD-04 | B | kapt is used for Room. AGP 9 has built-in Kotlin, and kapt is deprecated. | Switch Room to KSP. Upgrade Room 2.6.1 → current 2.7/2.8.x. Drop the `org.jetbrains.kotlin.android` plugin if AGP 9 built-in Kotlin is used. |
| BLD-05 | B | `android-database-sqlcipher:4.5.4` (deprecated, not 16 KB aligned, old `net.sqlcipher` API). | `net.zetetic:sqlcipher-android` (current) + `androidx.sqlite`. Replace `SQLiteDatabase.loadLibs` / `SupportFactory` with `System.loadLibrary("sqlcipher")` / `SupportOpenHelperFactory`. Check alignment with APK Analyzer. |
| BLD-06 | H | Old dependency set (core-ktx 1.12, activity-compose 1.8, lifecycle 2.7, Compose BOM 2024.02, CameraX 1.3.1). | Upgrade all of them via the catalog. CameraX 1.4+/1.5 for Android 16 fixes. |
| BLD-07 | M | Missing dependencies the revamp needs: `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `navigation-compose`, `kotlinx-serialization` (typed nav routes + export), Compose UI test. | Add them. |
| BLD-08 | M | Release build: minify on, but keep rules only cover SQLCipher/Room. The `keepRules/rules.keep` folder convention is AGP-9-only. | Fix the keep rules for the new SQLCipher package. Test a minified release build on the device. |
| BLD-09 | L | `.idea/` is committed, with Windows paths and a Pixel 7 AVD selected; `rootProject.name = "Android App"`; a folder name with a space. | Stop tracking `.idea/` except shared code style. Rename the project to `SmartGardenPlanner`. Optionally rename the folder. |
| BLD-10 | L | `versionCode = 1` forever, `versionName = "20.20"`. | Increment `versionCode` on every install you want to test upgrades/migrations with. |

### 3.2 Android 16 / Pixel 10 Pro platform behaviour

| ID | Sev | Problem | Fix |
|---|---|---|---|
| PLT-01 | B | Edge-to-edge is enforced at targetSdk 35+, and the opt-out is removed at 36. The theme sets the deprecated `statusBarColor` and there is no insets handling. Content will sit under the status bar, the camera punch-hole and the gesture bar. | Call `enableEdgeToEdge()` in `MainActivity`. Keep `Scaffold` everywhere and pass `innerPadding`. Use `WindowInsets.safeDrawing` for the canvas and floating controls, and `imePadding()` on forms. Theme parent → `Theme.Material3.DayNight.NoActionBar` (add `com.google.android.material`) or a plain splash-screen theme. |
| PLT-02 | B | No back handling: Back or the predictive-back gesture closes the app from every screen and silently discards in-progress canvas work. | Navigation Compose back stack (Phase 3). `BackHandler` on the canvas for "unsaved/in-progress points: discard?" (this was already required by HLR-UI-080). Declare `android:enableOnBackInvokedCallback="true"`. |
| PLT-03 | H | All UI state is plain `remember`, so configuration changes and process death lose screen, plot, mode, zoom, undo stack and dialogs. | ViewModels + `SavedStateHandle` + `rememberSaveable` (Phase 3). |
| PLT-04 | H | Orientation lock (T2-ENV-010) was never implemented. Stretched coordinates break in landscape. | Recommended: support both orientations by making the canvas aspect-correct (CAN-01), so no lock is needed. Note: Android 16 ignores orientation locks on large screens (≥600 dp, e.g. Pixel Fold/tablets), so a lock is not a reliable safety measure anyway. |
| PLT-05 | H | Startup does keystore/StrongBox key generation and opens the SQLCipher DB on the main thread. | Lazy database initialisation on `Dispatchers.IO` behind a splash screen (`androidx.core:core-splashscreen`), with a loading state. |
| PLT-06 | H | The backup/restore crash (headline #3). | `data_extraction_rules.xml` + `backup_rules.xml`: exclude the DB, `sgp_db_key.enc`, `config_*.enc` and the audit log. Or set `allowBackup=false`, which fits the offline/privacy ConOps. Also catch `AEADBadTagException`/`KeyPermanentlyInvalidatedException`/`UnrecoverableKeyException` and null keys in `getDatabasePassphrase`, and go through the same controlled reset path with a user-visible message (not a silent wipe). |
| PLT-07 | M | Runtime permissions: CAMERA is declared but never requested. Location is needed for the GPS/ZIP feature. | Permission flows with rationale, only when the camera/location features are built (Phase 7). |
| PLT-08 | M | Hardcoded dark palette; the M3 `SmartGardenPlannerTheme` (dynamic color) exists but is unused. Many `Color.White`/`Color.Gray` literals ignore the theme. | Adopt one theme (Phase 6). Decide on dynamic color vs a brand palette (D-05). |
| PLT-09 | M | Text sizes hardcoded down to 10–11 sp and the ruler uses raw px (`fontSizeSp * 2f`). Unreadable at Pixel density and at larger font scales. | Use `MaterialTheme.typography` and convert with `LocalDensity` for canvas text. Test at font scale 1.0 / 1.3 / 2.0. |
| PLT-10 | L | `uses-feature camera required=false` is fine. Add `android.hardware.sensor.*` as `required=false` when sensors are used. | — |

### 3.3 Architecture & code structure

| ID | Sev | Problem | Fix |
|---|---|---|---|
| ARC-01 | H | `MainActivity.kt` (2,257 lines) holds 4 screens, dialogs, helpers and the vault. | Split into `ui/dashboard`, `ui/creator`, `ui/canvas` (screen, toolbar, dialogs, drawing, gestures), `ui/picker`, `ui/encyclopedia`, `ui/settings`, `ui/theme`, `ui/navigation`. |
| ARC-02 | H | No ViewModels in use. `StorageViewModel` is constructed manually (so it doesn't survive configuration changes) and is only used by the debug vault. Business logic sits in composables. | One ViewModel per screen, exposing `StateFlow<UiState>`. Canvas logic (validation, undo/redo, auto-populate) goes into `CanvasViewModel` + pure-Kotlin use cases, which also satisfies T2-PLT-030. |
| ARC-03 | H | Navigation is a hand-rolled enum. The docs specify Jetpack Navigation. | Navigation Compose with typed routes (`Dashboard`, `Creator`, `Canvas(plotId)`, `Encyclopedia`, `Settings`, later `Camera(plotId)`). Update HLR-UI-010 to say Compose, not Fragments. |
| ARC-04 | H | Dashboard and Creator use raw SQL that guesses table/column names (`plots` / `PlotEntity` / `plot`…). | Delete it. Use `PlotDao` (`Flow<List<PlotEntity>>`, `insert`). |
| ARC-05 | H | `CoroutineScope(SgpExecutors.dbDispatcher).launch` in UI code: leaks, no cancellation, uncaught exceptions crash the app. | `viewModelScope` + repositories with `try/catch` → UI error state (T2-ERR-010). |
| ARC-06 | M | A custom single-thread `dbDispatcher` wraps every Room call. Room already runs suspend DAOs off the main thread. | Remove `SgpExecutors`. Use Room's own executors / `Dispatchers.IO` for file work. |
| ARC-07 | M | No dependency injection. `AppDatabase`, the key manager and repositories are passed through composable parameters. | A small manual `AppContainer` in an `Application` subclass (or Hilt, if you prefer). |
| ARC-08 | M | DAOs are one-shot `suspend` reads, so screens don't update when data changes. | Expose `Flow` queries for plots, nodes, paths and seeds. |
| ARC-09 | M | Settings are loaded separately on every screen from key-value rows in the encrypted DB. | `SettingsRepository` exposing `Flow<AppSettings>` (keep the DB table, or move to DataStore; they're not sensitive). |
| ARC-10 | M | `AgriculturalIsolationEngine.kt` bundles unrelated classes: key manager, packager, audit log, history stack, IMU engine, weed mask, executors, unused `WorkspaceUiState`. | Split into `security/`, `sensors/`, `geometry/`, `history/`. Delete dead types. |
| ARC-11 | L | Hundreds of `[NEW]/[FIXED]/[UPDATED]` history comments inside the code. | Move the history to git/CHANGELOG. Keep comments that explain *why*. |
| ARC-12 | L | Every user-facing string is hardcoded in Kotlin. | Move them to `strings.xml`: needed for accessibility and future localisation. |

### 3.4 Data layer & database

| ID | Sev | Problem | Fix |
|---|---|---|---|
| DAT-01 | B | Tier switch crash: `DELETE FROM seeds WHERE isCustom = 0` violates `ON DELETE RESTRICT` whenever a planted node uses a catalog seed. | Upsert the new tier instead of delete-then-insert. Only delete catalog seeds that are **not referenced** (`… AND botanicalCode NOT IN (SELECT seedCode FROM planted_nodes)`). Wrap it in a `@Transaction`. Show which varieties were kept because they're planted. |
| DAT-02 | H | Undo/redo restore runs `deleteAllForPlot` + `insertAll` as separate, non-transactional calls. | A single `@Transaction` DAO method `replacePlotContents(plotId, nodes, paths)`. |
| DAT-03 | H | Creating a new variety with an existing code uses `OnConflictStrategy.IGNORE`, so the save silently does nothing. | Validate code uniqueness in the form and show an error. |
| DAT-04 | H | Encyclopedia has no delete (T2-DAT-060 requires full CRUD). Deleting a planted variety must be blocked by RESTRICT with a clear message. | Add delete for custom varieties with a "used by N plants" guard. |
| DAT-05 | H | Plots can't be renamed, resized or deleted from the UI (T2-FUN-070). The DAO supports it. | Dashboard long-press or overflow menu: rename / edit dimensions (with the T2-VAL-017 out-of-bounds check) / duplicate / delete with confirmation. |
| DAT-06 | M | `exportSchema = false`, and no `MIGRATION_1_2` (a DB at v1 would crash). | `exportSchema = true` with a schema directory + `MigrationTestHelper` tests for 2→…→current. Decide whether v1 installs exist (probably not, so document "min supported schema = 2"). |
| DAT-07 | M | The default active variety `"SOL-LYC"` doesn't exist in any catalog tier. `SeedDataset.starterSeeds` is dead data. | Default to the last-used variety (persisted) or none. Delete `starterSeeds`. |
| DAT-08 | M | The catalog has no Plan B data (`fastTrackAlternateCode` / `catchCropAlternateCode` are never set), so the Recovery Plan only ever offers "nursery transplant". | Compute Plan B at runtime: fast-track = same species/family with the shortest `daysToHarvest` below the failed one; catch-crop = any variety whose `daysToHarvest` fits the remaining season (needs frost dates from the ZIP lookup; until then, use a Settings "season end" date). |
| DAT-09 | M | 15 companion/antagonist codes in the Basic catalog refer to species that only exist in higher tiers, so they show as raw codes. | Add a species-name table (or a 2-field `species.txt` asset) so names resolve regardless of tier. |
| DAT-10 | M | Climate lookup: only 5 hardcoded ZIPs, and nothing reads them. | Ship a real bundled ZIP→zone/frost dataset as an asset (tens of thousands of rows; load it lazily into its own table or query the asset directly). Phase 7. |
| DAT-11 | L | `SecureRandom().generateSeed(32)` used for key material. | `SecureRandom().nextBytes(ByteArray(32))`. |
| DAT-12 | L | `PlotEntity.ownerRole`, `imagePath` and `locationZip` exist but are unused. | Keep them (the plan uses them) and wire them up in Phase 7 / roadmap. |

### 3.5 Canvas (the core screen) — correctness, phone UX, performance

| ID | Sev | Problem | Fix |
|---|---|---|---|
| CAN-01 | H | Plot is stretched to the viewport (`scaleX ≠ scaleY`); radii use `scaleX`. | A single `pxPerMeter = min(viewW/lengthM, viewH/widthM)`, with the plot centred (letterboxed). Optionally auto-rotate a long plot so its long side follows the long screen axis (store only a display rotation, never change data). |
| CAN-02 | H | Hit-testing uses a fixed 0.4 m tolerance. | Use a tolerance in dp (≈24 dp, the 48 dp touch-target guidance) converted to meters with the current scale and zoom. |
| CAN-03 | H | The ruler doesn't match the zoomed/panned canvas (it maps the full length to the unzoomed width and just divides the tick interval). | Draw the ruler from the same transform as the canvas (offset + scale). Choose "nice" tick steps (1/2/5 × 10ⁿ) based on pixels per tick. |
| CAN-04 | H | Undo snapshot duplication (headline #6). | Take snapshots from the DB result or from state *before* reload — never `reloaded + new`. Better: make undo/redo a pure ViewModel operation on in-memory state, with the DB written through a transaction. |
| CAN-05 | H | Top app bar overflow on a ~410–430 dp-wide portrait phone: title + 3 icons + "Undo" + "Redo" buttons. The title "Workspace UI Editor" hides the plot name. | Redesign: top bar = back + plot name + overflow. **Bottom toolbar** with mode segmented buttons (Plant / Path / Area / Move), undo/redo icons, and layers. The variety picker becomes a bottom sheet. |
| CAN-06 | M | Zoom is buttons-only, behind a separate mode. Phone users expect pinch. | Pinch-zoom + two-finger pan are always available (`detectTransformGestures` only acts with 2+ pointers), so they don't conflict with one-finger place/draw/move. Keep +/- buttons for accessibility. Remove the separate Zoom/Pan mode, or keep it only as "one-finger pan". |
| CAN-07 | M | Performance: `seedFor()` does a linear search of up to 2,936 seeds per node per frame; the weed mask rebuilds Path ops for every node every frame; `Paint` is allocated per ruler label per frame. | `Map<String, SeedEntity>`. Cache the weed-mask path with `remember(nodes, scale)` / `drawWithCache`. Reuse `Paint`/`TextMeasurer`. Aim for smooth 120 Hz on the Pixel's LTPO display with 500+ plants. |
| CAN-08 | M | Snackbar texts and dialogs hardcode meters (`"%.2fm"`, "Width (m)", "Spacing: …m"). | Route everything through `DistanceFormatter`, including path-width input. |
| CAN-09 | M | Mode discoverability: modes are hidden in a ☰ dropdown, and the double-tap-to-place rule is only shown in tiny hint text. | Visible mode selector + a first-run coach mark. Consider single-tap = place and tap-on-plant = select (double-tap is awkward outdoors with gloves). |
| CAN-10 | M | The back gesture edge zone overlaps the canvas edge when dragging rectangles/paths from the far left. | `Modifier.systemGestureExclusion()` on the canvas area in draw modes (within Android's limits), or keep a margin. |
| CAN-11 | M | Canvas colors are hardcoded dark (`0xFF070B14` background, grid `0xFF1E293B`). | Canvas palette from the theme; readable in light mode and outdoors in sunlight (high-contrast option). |
| CAN-12 | L | Grid lines are always every 1 m, regardless of zoom or plot size. | Tie the grid to the same "nice" tick step as the ruler. |
| CAN-13 | L | `Divider` and `Icons.Default.ArrowBack` are deprecated. | `HorizontalDivider`, `Icons.AutoMirrored.Filled.ArrowBack`. |

### 3.6 Other screens (UX redesign)

| ID | Sev | Problem | Fix |
|---|---|---|---|
| UI-01 | H | The debug "Hardware-Encrypted Storage Vault" card is exposed on the Dashboard (gear icon), next to a separate Settings icon, which is confusing. | Remove it from the release UI. Move it to a debug-only developer screen (or delete `SecureConfigVault` if nothing needs it). |
| UI-02 | M | Dashboard: plots show "m" always, no summary, no empty-state call to action, no edit/delete. | Card per plot: name, size in the chosen unit, plant count, next harvest, overdue germination badge, thumbnail (once photos exist). Overflow menu for DAT-05. |
| UI-03 | M | Creator: jargon labels ("Agricultural Plot Designation Name", "Scale Engine Computing Source"); the IMU option is selectable but does nothing; no ZIP field; no photo step. | Plain labels. Hide or disable IMU until D-03 is decided. Optional ZIP field. Optional "Add photo" step (Phase 7). `imePadding` + scroll so the keyboard doesn't cover the button. |
| UI-04 | M | Encyclopedia: 2,936 cards in one list without filters; the edit form uses free-text code lists for companions; the add/edit form is a dialog capped at 480 dp. | Filters (category, lifecycle, zone), species grouping, full-screen edit page, companion multi-select from species names, delete (DAT-04), unit-aware radius input. |
| UI-05 | M | Settings: the "Sensors & Hardware" section explains itself as "not connected yet"; zoom min/max sliders can deadlock each other; tier switching has no progress feedback for Pro. | Hide the unconnected settings until their features exist. Use range sliders. Show a progress indicator/snackbar on tier switch. Add an "About / data" section (version, export/backup, reset DB). |
| UI-06 | M | Accessibility: canvas content has no semantics, many touch targets are below 48 dp (ruler toggle `[in]`), and plants are distinguished by color alone. | Content descriptions, 48 dp targets, a pattern/letter marker option for color-blind users, TalkBack pass. |
| UI-07 | L | App icon is the default template launcher icon. | New adaptive + themed (monochrome) icon for Android 13+ themed icons. |

### 3.7 Hardware features (camera, sensors, location)

| ID | Sev | Problem | Fix |
|---|---|---|---|
| HW-01 | H | `CameraCaptureManager` exists but no screen uses it, there is no permission request, and `imagePath` is never shown under the canvas (T2-FUN-010/040). | `CameraScreen` (CameraX `PreviewView` via `AndroidView`, or `camera-compose` `CameraXViewfinder`), runtime permission, capture → downscale → set `plot.imagePath`. Draw the image as the canvas background, aspect-fitted to the plot. Also offer "pick from gallery" via the Photo Picker (no storage permission needed). |
| HW-02 | M | Downscaling uses integer `majorAxis / 1080` (`inSampleSize` rounding), ignores EXIF rotation (Pixel photos come out sideways), and the OOM fallback stores the full-size raw file (the opposite of the storage cap). | Use `ImageDecoder` with a target size (or the CameraX `ResolutionSelector` to capture near 1080p in the first place). Honour rotation. On failure, keep the capture unsaved and tell the user. |
| HW-03 | M | Light sensor, storage floor, battery check: implemented partly or not at all, and none are wired up. Thresholds are hardcoded (10 lux, 5%) instead of using Settings. | Wire them into the camera screen with the Settings values. Battery: `BatteryManager` sticky intent + warning dialog with "continue" (T2-SAF-010..040), logged to the audit log. |
| HW-04 | H | IMU measurement (T2-FUN-025, HLR-SEN-050..150) as designed is not physically workable on a phone (drift), and the tilt maths is wrong. | See decision D-03. Until decided: remove the Creator option and keep `SensorMeasurementEngine` out of the UI. |
| HW-05 | M | Location / ZIP climate lookup doesn't exist in the UI; no location permission. | ZIP text field (works offline, no permission) first. GPS as an optional button using `LocationManager` (no Play Services needed) with the Settings accuracy gate (T2-ENV-025). |
| HW-06 | L | Emulator vs device differences: the emulator has virtual camera scenes, fake light-sensor values, no StrongBox. | Documented in the test matrix (Phase 9). The existing StrongBox → TEE fallback is correct; keep it. |

### 3.8 Security

| ID | Sev | Problem | Fix |
|---|---|---|---|
| SEC-01 | H | `app/src/main/java/.../SecurityKeyManager.kt` (package `security`, CBC, hardcoded fallback key) is still in the main source set, even though README_v20.20 says it was deleted. | Delete it. |
| SEC-02 | H | Backup/restore key mismatch (PLT-06). | See PLT-06. |
| SEC-03 | M | `DatabaseSecurityPackager` (signed export) exists but there is no export/import feature. An HMAC signature from a device-bound Keystore key can only be verified on the same device, so it can't support "share with family" (T2-DAT-110..140). | Design export/import properly in the roadmap: JSON schema + version + per-export signature. If cross-device verification is wanted, use an asymmetric signing key with the public key embedded. Use the Storage Access Framework for the file. |
| SEC-04 | M | The audit log re-reads the whole file for every append (`lastHash`) and grows without limit. | Cache the last hash and rotate the file. |
| SEC-05 | L | DEBT-SEC-001 ("halt if no hardware keystore") would brick the emulator. | Keep the TEE fallback. Formally close the debt item as "accepted: software/TEE fallback". |

### 3.9 Tests

| ID | Sev | Problem | Fix |
|---|---|---|---|
| TST-01 | B | `app/src/test/java/com/example/smartgardenplanner/StorageViewModelTest.kt` is a **verbatim copy of the production `StorageViewModel` class** (same package `ui`, same class name), so unit test compilation will clash. | Delete it. The real test is `test/.../ui/StorageViewModelTest.kt`. |
| TST-02 | H | Instrumented DB test depends on the old SQLCipher API; there are no migration tests. | Update to the new SQLCipher; add `MigrationTestHelper` tests (DAT-06). |
| TST-03 | H | No UI tests, no ViewModel tests for the canvas (undo/redo, validation, auto-populate integration). | After the ViewModel split: JVM tests for `CanvasViewModel`; Compose UI tests for the critical flows in §1.1. |
| TST-04 | M | Geometry helpers (`pointInPolygon`, `distanceToPolyline`, `circleIntersectsRect`, `parsePoints`) are private in `MainActivity` and untested. | Move them to `core/geometry` and unit-test them. |

---

## 4. Phased plan

Each phase ends with a build that runs on **both** the Pixel 10 Pro emulator and the physical Pixel 10 Pro.
Phases 1–3 must be done in order. Phases 4–7 can overlap once Phase 3 is done.

### Phase 0 — Repository & documentation reset (short, first)
- D-01 is decided: keep DO-178C / ARP4754A DAL A. The rewritten plans and standards are in `docs/compliance/`.
  Phase 0 now means reviewing and approving them (SGP-DWR-001 WP-01), setting up the Problem Report system
  (WP-15), and starting the requirements re-baseline (WP-03) using SGP-RSM-001 as the status baseline.
  Withdrawn documents already carry supersession notices and move to `docs/archive/` at the first baseline.
- Delete dead files: `security/SecurityKeyManager.kt` (SEC-01), `test/.../StorageViewModelTest.kt` copy
  (TST-01), `SeedDataset.starterSeeds`, unused `WorkspaceUiState`, `.idea/` tracking.
- Add a short `docs/DEVICE_SETUP.md` (how to create the Pixel 10 Pro AVD and deploy to the phone, see
  Phase 9).

### Phase 1 — Make it build on current Android Studio (target API 36)
BLD-01…BLD-08, TST-01. Order:
1. Update the Gradle wrapper + AGP with the Upgrade Assistant; move everything into `libs.versions.toml`.
2. Kotlin 2.x + Compose compiler plugin; remove `composeOptions`.
3. kapt → KSP; upgrade Room.
4. SQLCipher migration (BLD-05). **Keep the same passphrase file format**, so any existing install still
   opens its DB (the new library reads SQLCipher 4 databases).
5. compileSdk/targetSdk 36; fix deprecations flagged as errors.
6. Get `./gradlew assembleDebug testDebugUnitTest` green and the app launching on the emulator,
   even if it looks broken.

**Exit criteria:** the app installs and launches on the Pixel 10 Pro emulator (API 36, including a 16 KB
page-size system image) and on the physical phone; the APK Analyzer shows no 16 KB alignment warnings.

### Phase 2 — Stop crashes & data loss on the device
PLT-01 (edge-to-edge baseline), PLT-02 (minimal `BackHandler`s as a stopgap), PLT-05, PLT-06/SEC-02,
DAT-01, DAT-02, DAT-03, CAN-04, ARC-05 (at least wrap the Creator/Settings coroutines).
**Exit criteria:** you can plant, switch tier, undo/redo, rotate, press Back, force-stop and relaunch,
restore from backup (use `adb shell bmgr` to test), and reinstall over an older build, with no crash and
no duplicated or lost data.

### Phase 3 — Architecture restructure (no visible behaviour change)
ARC-01…ARC-10, ARC-12, PLT-03, BLD-07.
- `Application` + `AppContainer`; repositories (`PlotRepository`, `GardenRepository` for nodes/paths,
  `SeedRepository`, `SettingsRepository` as `Flow`).
- Navigation Compose with typed routes; ViewModel per screen; `SavedStateHandle` for plotId/mode/zoom.
- Move the canvas logic into `CanvasViewModel` + pure-Kotlin use cases (placement validation, auto-populate
  filtering, snapshot history), each with unit tests (TST-03/04).
- Remove raw SQL and `SgpExecutors`.
**Exit criteria:** every item in §1.1 still works (manual checklist), unit test coverage on the core
logic, and the app state survives rotation and process death (Developer options → "Don't keep activities").

### Phase 4 — Data correctness & completeness
DAT-04…DAT-09, DAT-11, TST-02. Plan B computed at runtime; species name resolution across tiers;
Encyclopedia delete; plot rename/resize/delete; schema export + migration tests.

### Phase 5 — Canvas redesign for the phone
CAN-01…CAN-03, CAN-05…CAN-12, PLT-09. Proposed layout (portrait, ~410–430 dp wide):

```
┌──────────────────────────────────────┐
│ ←  Backyard Bed A      12.0 × 3.0 m ⋮│  top bar (plot name, size, overflow: layers, legend, units)
├──────────────────────────────────────┤
│ ruler (synced with zoom/pan)         │
│ ┌──────────────────────────────────┐ │
│ │                                  │ │  aspect-correct plot, letterboxed,
│ │   plot canvas (pinch = zoom,     │ │  optional photo background,
│ │   2-finger = pan, 1-finger =     │ │  +/− and recenter floating (bottom-end)
│ │   current tool)                  │ │
│ └──────────────────────────────────┘ │
│  Now placing: Tomato – San Marzano ▾ │  tap → variety bottom sheet (3-step picker)
├──────────────────────────────────────┤
│ [Plant][Path][Area][Move]   ↶  ↷  ◧  │  bottom toolbar: tools, undo, redo, layers
└──────────────────────────────────────┘
```
Landscape: the toolbar moves to a side rail, and the canvas uses the full height.
**Exit criteria:** placement on a 1 m × 1 m and on a 100 m × 20 m plot is equally usable. Spacing circles
look correct in both axes. Pinch/pan feel smooth. No UI hides under the status/navigation bars.

### Phase 6 — Visual & UX redesign of the other screens + theme
PLT-08, UI-01…UI-07, CAN-13. One Material 3 theme (light + dark), typography scale, strings in
`strings.xml`, new icon, accessibility pass.

### Phase 7 — Hardware features on the real phone
HW-01…HW-05, PLT-07, DAT-10, DAT-12. Suggested order: photo from camera/gallery as the canvas background →
ZIP field + bundled climate data → planting windows / zone warnings (FR-014) → light/storage/battery
guards on the camera screen → GPS (optional) → measurement feature according to D-03.

### Phase 8 — Security & data portability
SEC-03, SEC-04, SEC-05, plus the export/import design (T2-DAT-110..140, roadmap FR-026 if network is ever
allowed).

### Phase 9 — Verification on emulator + device
- **Emulator setup:** Android Studio → Device Manager → Create Virtual Device → **Pixel 10 Pro** (if your
  Studio version doesn't list it, update Studio or create a hardware profile: 1280 × 2856 px, 6.3",
  ~495 ppi). System images: newest **Google APIs/Play** image (API 36+), plus a **16 KB page-size**
  variant for the alignment check. Enable a virtual camera scene for the camera tests.
- **Physical Pixel 10 Pro:** Developer options → USB or Wireless debugging. Also test: "Don't keep
  activities", font size largest, display size largest, dark/light theme, battery saver, and a backup →
  restore cycle.
- **Test matrix:** every §1.1 item × {emulator, phone} × {portrait, landscape} × {light, dark}.
  Automate the core flows with Compose UI tests; the rest is a manual checklist recorded in the
  Test-Environment log (replacing the Pixel 8 Pro references).
- Replace the template Verification Plan with real test cases that list actual inputs, steps and
  expected results, and map them to the requirement IDs.

### Phase 10 — Documentation refresh
USER_MANUAL (new layout, gestures, screenshots from the Pixel 10 Pro), FEATURE_ROADMAP statuses,
re-baselined requirements, dev log, a CHANGELOG. Delete the `[NEW]/[FIXED]` history comments from the code.

---

## 5. Decisions needed from you

| ID | Question | Recommendation |
|---|---|---|
| D-01 | Keep the DAL A / DO-178C framing and generated compliance documents, or re-baseline to "requirements + real tests"? | **DECIDED:** keep the DO-178C / ARP4754A DAL A framing. The unusable plans are rewritten in `docs/compliance/` (index: `docs/compliance/README.md`), and all outstanding work is tracked in `docs/compliance/SGP-DWR-001_Deferred_Work_Register.md`. |
| D-02 | Minimum Android version: keep minSdk 29 (Android 10) or raise it? | Keep 29 unless it gets in the way. Target/compile at 36+. |
| D-03 | Plot measurement with sensors: (a) drop it and keep manual entry; (b) replace with **ARCore** (works offline once installed; the Pixel 10 Pro supports it; the emulator only partially); (c) camera + reference-object scaling (e.g. place a known-size card in the photo); (d) GPS walk-the-perimeter (±3–5 m, only for large fields). | (a) now, then (b) or (c) as a roadmap item. Double-integration IMU should be dropped. |
| D-04 | Orientation: lock the canvas to portrait, or support both? | Support both once the canvas is aspect-correct (CAN-01). |
| D-05 | Visual identity: Material You dynamic color (matches the phone's wallpaper) or a fixed garden-green brand palette? | Fixed brand palette with light + dark, plus a high-contrast outdoor mode. |
| D-06 | Placement gesture: keep double-tap-to-place or switch to single tap? | Single tap places; tapping an existing plant selects it (with a small tolerance). |
| D-07 | Backups: exclude the encrypted data from Android backup (data stays on the device), or `allowBackup=false`? | Exclude the DB/key files now. Offer an explicit user-driven export in Phase 8. |
| D-08 | Pro/Standard gating tied to the *catalog size* setting (anyone can switch to Pro for free today). Intentional for now? | Fine during development; revisit before any store release. |
| D-09 | Package/application ID `com.example.smartgardenplanner`: Google Play rejects `com.example`. Rename now (cheap) or later (painful)? | Rename during Phase 3 (e.g. `com.<yourname>.smartgarden`). |
| D-10 | FR-026 network toggle (options A–D in FEATURE_ROADMAP) is still undecided and blocks FR-007/019/020 refresh. | Not needed for the revamp; decide after Phase 7. |

---

## 6. Suggested first working session

1. Answer D-01, D-03, D-07 and D-09 (these affect Phase 0–3 structure).
2. Phase 0 cleanup + Phase 1 toolchain upgrade in Android Studio (you'll need Studio's SDK/Maven access;
   it isn't available in this environment).
3. Launch on the Pixel 10 Pro emulator, capture any compile/runtime errors, and feed them back. They'll
   be fixed in the same pass as Phase 2.
