# Smart Garden Planner — v20.20 Fix & Feature Pass

This archive is a corrected/extended copy of the project tree under `Android App/` from your
repo, built from the real source files you uploaded. It is **not a clean-room rewrite** — every
file that wasn't touched is copied over unchanged, and every file that was changed has inline
`[FIXED]` / `[NEW]` / `[UPDATED]` comments at the point of change explaining what and why.

Full rationale for every change is in `SGP_Knowledge_Base_09_Documentation_Corrections_v20.20.md`
(Part 9 of the knowledge base) — read that alongside this if you want the "why," not just the "what."

## Bugs fixed
1. **HMAC forgery vulnerability** — both `SecurityKeyManager` implementations fell back to a
   hardcoded key when `SecretKey.encoded` returned null (the expected outcome for real
   hardware-backed keys). Fixed by using a dedicated Keystore-native HMAC key instead of
   extracting raw key bytes at all.
2. **Two non-test "test" files** — `AppDatabaseTest.kt` and `StorageViewModelTest.kt` were
   verbatim copies of the production classes they were supposed to test. Replaced with real tests.
3. **Duplicate/dead security implementation** — the standalone, CBC-mode, misplaced-package
   `SecurityKeyManager.kt` singleton has been deleted. `RealSecurityKeyManager` (the one
   `MainActivity` actually uses) is the only implementation now.
4. **Destructive migration** — `.fallbackToDestructiveMigration()` replaced with a real
   `MIGRATION_2_3` that preserves existing data.
5. **Silently-failing seed pre-population** — was raw SQL against a guessed, nonexistent table
   name. Now inserts through a real `SeedDao` against a real `seeds` table.

## What's newly implemented
- `SeedEntity` / `SeedDao` / `SeedDataset` — the botanical dictionary (was entirely absent).
- `ClimateZoneEntity` / `ClimateZoneDao` — ZIP-based hardiness/frost lookup (ZIP path only; GPS
  bounding-box path is still open).
- `GerminationContingencyEngine` — the "Plan B" 3-path fallback logic.
- `CompanionPlantingValidator` — spacing + antagonist collision detection.
- `IrrigationRouteCalculator` — nearest-neighbor drip route + length estimate.
- `WeedMaskGeometryEngine` (replaces the old flat-subtraction `WeedingMaskCalculator`) — real
  `Path.Op.DIFFERENCE` vector geometry.
- `CameraCaptureManager` / `AmbientLightMonitor` — the entire CameraX subsystem, previously 100%
  absent. CameraX dependency and the `CAMERA` permission were added to support this.
- `EncyclopediaScreen` — the 4th documented screen, previously not even in the navigation enum.
  This one is fully wired end-to-end (reachable from the Dashboard, queries real data).
- `Migrations.kt` — real Room migration infrastructure.
- 3 new pure-Kotlin unit test files for the new engines (14 test methods total), plus 2 corrected
  instrumented tests and 1 corrected unit test.

## What's still open (see Part 9 §7 for the full list)
The germination, companion-planting, irrigation, and weed-mask engines are real and unit-tested,
but **none of them are called from the Canvas UI yet** — there's no rendering/warning surface for
any of them. The camera manager is real but has no preview screen calling it. These are the
natural next PRs; the hard architectural/data-model work (this pass) is what was blocking them.

## Before this will build
- This was hand-edited by reading and reasoning about the real source, not compiled — you should
  run a real Gradle sync/build before trusting it. The migration SQL, Room annotations, and CameraX
  API usage were written carefully but were not verified against an actual Android toolchain in
  this session (no network/Android SDK available here).
- `gradle/libs.versions.toml` is still orphaned/unused by the module build (a pre-existing issue,
  not something this pass fixed) — see Part 8/Part 9 for details if you want to clean that up too.
