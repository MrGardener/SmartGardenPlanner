# SGP-TPR-001 — Software Test Procedures

| Field | Value |
|---|---|
| Document ID | SGP-TPR-001 |
| Revision | A — Proposed (2026-09-28) |
| Level | Test procedures for the cases of SGP-TCS-001 (DO-178C §6.4.2, §11.13) |
| Parent documents | SGP-TCS-001 (test cases), SGP-SVP-001 (verification plan) |
| Control category | CC1 |

A procedure says how to set up, run and judge a group of test cases. Automated procedures (TP-01 … TP-08) run
in CI on every pull request and every merge to main, so every regression case in SGP-TCS-001 §4 is re-run after
each merge. Manual procedures (TP-10 … TP-23) are run on an Android phone in each verification campaign and
before a release; each step is referenced from SGP-TCS-001 as `MANUAL TP-nn.m`.

**Recording results.** Automated procedures: the CI run (link, commit, pass/fail per test) is the record.
Manual procedures: record, per step, PASS / FAIL / NOT RUN, the phone model and Android version, the app
commit and the tester, in the campaign log (SGP-SVR, to be created, DW-1403). A FAIL becomes a new regression
entry (SGP-TCS-001 §4) and, where the failure can be reproduced off-device, a new automated test.

---

## 1. Automated procedures

### TP-01 — Host unit tests (JVM)

| | |
|---|---|
| Covers | Every `AUTO` case in SGP-TCS-001 |
| Environment | JDK 17, Kotlin 2.2.10, JUnit 4.13.2 |
| Run (CI) | `android-ci.yml`, step "Build debug APK, run unit tests and lint" (`./gradlew testDebugUnitTest`) |
| Run (without Android SDK) | Compile `core/*.kt` (except `AgriculturalIsolationEngine.kt`, `CameraCaptureManager.kt`) with the Room annotation stubs, compile `app/src/test/.../core/*.kt`, run `org.junit.runner.JUnitCore` on every class ending in `Test` |
| Pass criteria | Every test passes; random tests use fixed seeds, so a failure repeats exactly |

**TP-01.1** Run the suite. **TP-01.2** On a failure, re-run the single class and read the assertion message
(it names the input). **TP-01.3** Record the commit and result.

### TP-02 — Web build and up-to-date check

| | |
|---|---|
| Covers | `CI web-planner.yml «Build the single-file planner»`, `«Committed HTML matches the source»` |
| Run | `cd web && ./build.sh`, then `git diff --exit-code web/dist/smart-garden-planner.html` |
| Pass criteria | Build succeeds; the committed page equals a fresh build byte for byte |

### TP-03 — Browser smoke test

| | |
|---|---|
| Covers | Every `WEB smoke.mjs` case |
| Run | `node web/tests/smoke.mjs` (Playwright, Chromium; `SGP_SHOTS=<dir>` saves screenshots) |
| Pass criteria | All checks pass; no script errors |

### TP-04 — Browser adversarial test

| | |
|---|---|
| Covers | Every `WEB adversarial.mjs` case: refused dialog values, hostile plan files, undo storm, seeded random ("monkey") run |
| Run | `node web/tests/adversarial.mjs`; `SGP_SEED=<n>` repeats a given random run |
| Pass criteria | All checks pass; after 400 random actions the draft is readable and every plant is inside its plot |

**TP-04.1** Run with the default seed (CI). **TP-04.2** Before a release, run seeds 1–5. **TP-04.3** A failing
seed is recorded with its step number and becomes a regression entry.

### TP-05 — Spanish coverage

| | |
|---|---|
| Covers | `CI web-planner.yml «Spanish coverage»` |
| Run | `node web/tests/i18n_coverage.mjs` (`ALL=1` lists every remaining English text) |
| Pass criteria | Exit 0: no English sentence left on the main screens in Spanish |

### TP-06 — Requirements and test-case trace

| | |
|---|---|
| Covers | Trace T2 → HLR → LLR → TC → procedure |
| Run | `python3 tools/req_trace.py --check` and `python3 tools/tc_trace.py --check` (CI `requirements-trace.yml`) |
| Pass criteria | No trace gap; every LLR has ≥ 3 cases with a limit and an invalid/random case; every AUTO, DEVICE, WEB and CI reference exists; every MANUAL/REVIEW step exists in this document |

### TP-07 — Android build, unit tests and lint

| | |
|---|---|
| Covers | TP-01 on the Android toolchain, plus lint and the `ui` unit tests (`StorageViewModelTest`) |
| Run | `android-ci.yml` |
| Pass criteria | Build, tests and lint pass |

### TP-08 — Instrumented tests (device or emulator)

| | |
|---|---|
| Covers | Every `DEVICE` case (`AppDatabaseTest`, `SecurityKeyManagerTest`) |
| Run | `./gradlew connectedDebugAndroidTest` on an API 26+ emulator or phone |
| Pass criteria | All pass. Not in CI yet (no emulator runner); run in each campaign |

---

## 2. Manual procedures (Android phone)

Set-up for all manual procedures, unless a step says otherwise: install the debug build of the commit under
test on a phone with Android 8 or later; clear the app's data first; language English; units meters.

### TP-10 — First start and Settings

| Step | Action | Expected |
|---|---|---|
| **TP-10.1** | Start the app after a fresh install; accept the disclaimer; restart the app. | Disclaimer shown and can't be dismissed without accepting; then the empty plot list; the Basic catalog (253 varieties) is present in the Encyclopedia; not shown again after the restart. |
| **TP-10.2** | Visit every screen from the plot list (creator, layout, encyclopedia, settings, insights) and press Back from each; open a plot, delete it from another screen, return. | Each screen opens and Back returns; Back on the plot list closes the app; no crash for a missing plot. |
| **TP-10.3** | In Settings, move every slider to both ends and to a middle value; switch units to inches and back; restart the app. | Values shown with their unit, kept after restart; rulers and dialogs follow the unit. |
| **TP-10.4** | On Basic and Standard tiers, look at "Enforce companion rules"; switch to Pro, turn it off, place antagonists close together; switch back to Basic. | Locked on below Pro with the note; off on Pro allows the placement; back on after downgrading. |
| **TP-10.5** | Change several settings, then "Reset all settings" and cancel; repeat and confirm. | Cancel changes nothing; confirm restores every default; plots untouched. |
| **TP-10.6** | Add a custom variety and edit a bundled one; plant a Pro-only variety on Pro; switch Pro → Basic → Pro. Rotate the phone during a switch. | Progress then result; custom and edited varieties kept; the planted variety still shown; switching completes once. |
| **TP-10.7** | Open Settings → About this planner. | Version, data sources and the disclaimer. |

### TP-11 — Code and design review checklist

| Step | Check | Pass when |
|---|---|---|
| **TP-11.1** | Limits in `core` (plant, item, point, name, plot size limits). | Each limit is a named constant used by every caller. |
| **TP-11.2** | Add `import android.util.Log` to a `core` file and run TP-02. | The web build fails (so `core` stays platform-neutral); revert. |
| **TP-11.3** | KDoc of public model classes in `core`. | Each field has meaning, type, unit and range (or "unitless"). |
| **TP-11.4** | Key and passphrase handling in `data`/`core`. | Key arrays zeroed after use; no key material logged. |
| **TP-11.5** | Merged manifest. | `allowBackup="false"`, backup and extraction rules referenced, `usesCleartextTraffic="false"`. |
| **TP-11.6** | Search the source for `URL(`, `openConnection`, `Socket`. | Only in `NetworkGateway`. |
| **TP-11.7** | Canvas draw code. | No objects allocated per plant per frame. |
| **TP-11.8** | Interface strings. | US spelling (color, center, meters, neighbor…); the plan file keeps `"units":"metres"`. |

### TP-12 — Plots: create, edit, delete

| Step | Action | Expected |
|---|---|---|
| **TP-12.1** | With no plots, look at the list; create three plots of 0.5 × 0.5 m, 8 × 5 m and 1000 × 1000 m; delete them one by one. | Empty-state text and button; rows show names, sizes and plant counts; the list updates at once; empty state after the last. |
| **TP-12.2** | Create a plot; press Back; create a second plot with the same name. | The layout opens after creating; Back goes to the list; both plots kept. |
| **TP-12.3** | Rename a plot; shrink it with no plants outside; shrink it so a plant is exactly on the new edge; shrink it so plants fall outside (web: Plot details). | Saved; saved; edge plant allowed; refused with the list of plants outside. |
| **TP-12.4** | Note a plot's modified time; open it without changes; rename it; add a plant. | Unchanged when only opened; updated by rename and by the plant. |
| **TP-12.5** | Delete a plot with 500 plants: cancel, then confirm. | Confirmation states the counts; cancel keeps it; confirm deletes it and its plants. |

### TP-13 — Layout view

| Step | Action | Expected |
|---|---|---|
| **TP-13.1** | Open plots of 1 m, 10 m and 1000 m, one with a 200-character name; switch tools; leave with overlays on and reopen. | Whole plot visible at zoom 1; name ellipsized; one tool active at a time; overlays off on reopening. |
| **TP-13.2** | Open a 10 × 1 m plot and a square plot; place a plant in each. | Spacing circles are round (known defect DW-0901 on Android: stretched). |
| **TP-13.3** | Pinch in and out past the zoom limits; pan far past each edge; two- and three-finger gestures; press reset view. | Zoom stops at the limits; the plot stays on screen; no plant moved by multi-finger gestures; reset gives zoom 1, centered. |
| **TP-13.4** | Plant 40 varieties; open the legend; drag it past the edge; turn labels off; use varieties with the same short name; import a plan with a plant of an unknown variety; find a variety, then delete all its plants. | Legend lists each with count; clamped on screen; names hidden when off; both drawn; the unknown one listed by its code; find cleared with no ring left. |
| **TP-13.5** | Rulers and grid with tick 0.25 m on a 1000 m plot, 5 m on a 2 m plot, and in inches. | Readable numbers, unit shown once; grid inside the plot only. |
| **TP-13.6** | Rotate the phone on the layout (with 200 points in progress, with a dialog open, twice quickly, right after Undo). | Tool, zoom, center, points, dialog text and plants kept; no controls under system bars or cutouts. |
| **TP-13.7** | Run Accessibility Scanner; TalkBack over the toolbar; font scale 2.0. | 48 dp targets, every control named, nothing clipped. |
| **TP-13.8** | Set the plot direction to 0°, 90°, 359°; clear it. | Compass N follows; gray "N?" and a header warning when not set. |

### TP-14 — Plants: place, details, move, undo

| Step | Action | Expected |
|---|---|---|
| **TP-14.1** | Tap with no variety chosen; choose from the picker (search "TOMATO", "tomato", "%", emoji, 200 characters, empty); close the picker without choosing; tap outside the plot; tap the center and each corner. | Picker opens; case-insensitive results, no crash; nothing placed when closed; outside ignored; plants at the center and corners (or refused by the edge rule with a message). |
| **TP-14.2** | Tap twice quickly at the same spot; place outside the outline; place on a no-plant path. | Second refused with the spacing message; "Outside the plot"; path message. |
| **TP-14.3** | Tap a plant, just outside it, and between two touching plants; change its variety to one too wide for the spot; delete the last plant; open a plant whose variety was deleted. | Details of the nearest plant; placement when outside the tolerance; wide variety refused, old kept; empty plot; details without a crash. |
| **TP-14.4** | In Move, drag a plant onto another, off the plot, and put a second finger down mid-drag; switch tools mid-drag. | Refused or canceled, plant back at its place, message shown. |
| **TP-14.5** | Make a change, undo, redo, change again; undo everything; delete a plant and undo; leave and reopen the plot. | Buttons enabled per the stacks; redo cleared by a new change; the deleted plant returns with its id; history empty after reopening. |
| **TP-14.6** | Plant a crop with a planting date before its germination window (set the phone date forward); mark "It germinated"; change the time zone. | Overdue ring after the window, not on the last day; ring gone when germinated; dates recomputed. |

### TP-15 — Paths, obstacles, outline, area fill

| Step | Action | Expected |
|---|---|---|
| **TP-15.1** | Draw a rectangle path; a 4 cm one; one starting outside the plot; a line path with two taps 3 cm apart and a tap outside; widths 0, 0.5 m, "1,5", "abc", larger than the plot. | Added; not added; clipped; close point dropped, outside tap ignored; 0/"abc"/too wide refused, 0.5 and 1.5 accepted. |
| **TP-15.2** | Drag an area from each corner direction and a zero-size click. | Same rectangle each time; nothing for a click. |
| **TP-15.3** | Tap a path, overlapping paths, and a path while points are in progress; place a plant whose circle just touches a path; a one-point line path. | Edit dialog for the topmost; a point added while drawing; touching allowed; distance to the point used. |
| **TP-15.4** | Add a tree, fence and wall; enter barrier heights 0, 100, 100.5 and 150 m; move a tree past the plot edge; undo. | Heights over 100 m refused; tree clamped inside; undo restores it. |
| **TP-15.5** | Fill an area: note the preview count, change the variety and pattern quickly, Fill; fill an area crossing a no-plant path and existing plants; fill more than the plant limit; undo. | Preview recomputed; the same number placed; nothing on the path or on plants; limit snackbar and nothing over the limit; one undo removes the fill. |

### TP-16 — Planning an area

| Step | Action | Expected |
|---|---|---|
| **TP-16.1** | "Plan an area for me" on a dragged area, a 3 cm area and an area dragged off the plot. | Plan dialog; tiny area ignored; area clipped. |
| **TP-16.2** | Move the proposal card, fold it, drag it beyond the screen. | Moves, folds to 48 dp, stays on screen. |
| **TP-16.3** | Plan 50 plants and "Plant them"; kill the app during saving (developer options). | All or none saved; one undo removes them. |
| **TP-16.4** | Plan over existing plants with "Replace" and keep it; plan again with "Replace" and Discard. | Old plants removed and new added in one step; one undo restores the old ones; Discard leaves the plants unchanged. |
| **TP-16.5** | Select a group, "Move the whole group", turn Move Mode off, drag a plant. | Group selection cleared; only that plant moves. |

### TP-17 — Seasons

| Step | Action | Expected |
|---|---|---|
| **TP-17.1** | "Start a new season" with years 1899, 1900, 3000, 3001. | 1899 and 3001 refused; others accepted; one undo reopens the season. |
| **TP-17.2** | Show a past season with no plants; try to place a plant; press the return button. | Empty read-only layout; placement refused; back to the planning season. |

### TP-18 — Site advice (sun, water, pests, Plan B, catalog)

| Step | Action | Expected |
|---|---|---|
| **TP-18.1** | Open insights for a plot with no location. | Location-dependent items say "Set a location". |
| **TP-18.4** | Plan B with a variety too wide for the spot. | Refused with the spacing message; old plant kept. |
| **TP-18.8** | Encyclopedia: filter by category; search "ñ", "'", "%"; open tomato; create a custom variety (valid; spacing 0.02 and 10 m; duplicate code; blank name; spacing 0); edit a bundled variety; delete an unused and a planted custom variety. | Filters work; no crash; every field or "Not on file", companion names (raw codes where the tier lacks them); invalid entries refused; edited bundled variety kept as custom; planted variety can't be deleted and the plots are listed; bundled varieties have no Delete. |

### TP-19 — Files, copies and photos

| Step | Action | Expected |
|---|---|---|
| **TP-19.2** | Import a plan file whose plot name exists; import a file that fails half-way (edit it to break a plant's JSON after the first plot). | " (imported)" added; nothing imported from the broken file. |
| **TP-19.3** | Import a plan with a custom variety code equal to a bundled one. | The existing variety kept. |
| **TP-19.4** | Pick a very large satellite image (over 20 MP). | Scaled to ≤ 1600 px; stored; no out-of-memory. |

### TP-20 — Language

| Step | Action | Expected |
|---|---|---|
| **TP-20.1** | Settings → Language → Español; visit every screen; back to English. | Spanish everywhere a translation exists; plot names as typed; English restored. |
| **TP-20.2** | Spanish at font scale 1.3 on a small phone. | Nothing cut off. |

### TP-21 — Storage, security and network

| Step | Action | Expected |
|---|---|---|
| **TP-21.1** | Fresh install; inspect the database version and key files (debug build, `run-as`). | Version 12; key created; database encrypted. |
| **TP-21.2** | Install an older build (schema 8, then 10), add data, upgrade to the build under test. | Every migration runs; all plots, plants, features and history kept. |
| **TP-21.3** | Pull the database file; try to open it with sqlite3; check StrongBox fallback on a phone without it. | Not readable; app works with the TEE key. |
| **TP-21.4** | Delete the app's keystore entry (or restore the data to another phone); start; choose "Close app", then start again and "Start with empty data" (confirm twice); open the audit log; edit one audit line and verify. | Data-unreadable screen, nothing written before a choice; old files set aside; KEY_LOSS and DATA_RESET recorded; chain reported broken after the edit. |
| **TP-21.5** | Fill the phone's storage, then save a change; make the plot list query fail (debug flag). | Message shown; app keeps running. |
| **TP-21.6** | Insert a plant for an unknown plot id (debug console); delete the last plot. | Rejected by the constraint; no orphan rows. |
| **TP-21.7** | `adb backup`; cloud backup; device-to-device transfer. | No app data included. |
| **TP-21.8** | With Online features off, press every online button; turn it on with no network. | No connection attempted, buttons explain they're off; error message offline. |

### TP-22 — Camera, sensors, location (deferred)

| Step | Action | Expected |
|---|---|---|
| **TP-22.1** | Blocked: camera, sensor and location screens are not built (DW-1101, DW-1104, DW-1105). The steps are written when the features are scheduled. | — |

### TP-23 — Performance on the target phone

| Step | Action | Expected |
|---|---|---|
| **TP-23.1** | Cold start to an interactive plot list (5 runs); first start on the Pro tier; open a plot with 500 plants; rotate during a save. | Within HLR-PERF-010; progress shown; no freeze; save completes once. |
| **TP-23.2** | 500 plants: pan, zoom, drag, with overlays and labels on and off. | No visible stutter (HLR-PERF-020). |
| **TP-23.3** | Tier switch Basic → Pro and Pro → Basic with plots open. | Within HLR-PERF-030; no plants lost. |
