# Pixel 10 Pro phone checklist

| | |
|---|---|
| Purpose | Everything in Smart Garden Planner that needs a real phone, for a campaign on a Google Pixel 10 Pro (Android 16) |
| Build | The debug APK from the "Android build and unit tests" run of the commit under test (artifact `smart-garden-planner-debug-apk`) |
| Procedures | Each check names its step in SGP-TPR-001; the step there is the full definition |
| Test files | [`phone-test-files/`](phone-test-files/) |
| Checks | 44 |

Mark each check Pass, Fail or Skip. For a failure, note what you did, what you saw, and take a screenshot (Power + Volume down).

Before you start: the measuring checks need a tape measure, a sheet of Letter or A4 paper, and a flat floor, patio or lawn.

## S. Install

Do these first, in order.

| Check | How | You should see | Step |
|---|---|---|---|
| **S1** Download the app | On the phone, sign in to github.com (browser or GitHub app) and open the build page link. Scroll to Artifacts and tap smart-garden-planner-debug-apk. | A zip of about 25 MB lands in Downloads. | — |
| **S2** Remove the old copy | If Smart Garden Planner is already installed, long-press its icon → Uninstall. Each build is signed with a new test key, so installing over an older build fails with "App not installed". This deletes its data. | No Smart Garden Planner icon left. | — |
| **S3** Install | Open the zip in Files by Google and tap app-debug.apk. Allow "Install unknown apps" for Files when asked. If Play Protect warns about an unknown developer, tap More details → Install anyway. | Installed; the icon appears in the app drawer. | — |
| **S4** First start | Open the app; try to get past the disclaimer without accepting; accept; close the app from Recents and open it again. | Disclaimer can't be skipped; then an empty plot list; not shown again after restarting; Encyclopedia lists the Basic catalog (253 varieties). | TP-10.1 |

## N. New in this build

The features built this round. Most important.

| Check | How | You should see | Step |
|---|---|---|---|
| **N1** Camera permission refused | Create New Plot → Take a photo → Don't allow. Then Choose a photo. | "Camera permission refused. You can still choose a photo from the gallery." The photo picker still opens. | TP-24.1 |
| **N2** Measure a rectangle from a photo | Mark a rectangle about 3 × 2 m on a floor or patio and measure it with a tape. Lay a Letter or A4 sheet flat inside it. Allow the camera (Settings → Apps → Smart Garden Planner → Permissions), Take a photo from head height aiming down about 45°. Reference: the paper. Tap its 4 corners in order starting along a long side, then the rectangle's 4 corners. Use these sizes, then create the plot. | Step text guides each tap; result like "3.02 × 1.98 m (± 0.10 m), 5.98 m²"; your tape sizes are within the ± shown; the size fields are filled; the created plot has those sizes. Write both results in the note. | TP-24.2 |
| **N3** Photo turned the right way | Take one measuring photo holding the phone upright and one holding it sideways. | Both photos appear the right way up in the measuring screen, within about 2 seconds, with no crash (the 50 MP photos are reduced to 2048 px). | LLR-PMEAS-050 |
| **N4** Two points and an L-shape | Measure again tapping only 2 points along one side. Then Start over and tap the 6 corners of an L-shaped area. | "Distance: … m" and the button "Use as the length". The L-shape says "(not a rectangle: outline kept)" and the created plot shows that outline. | TP-24.3 |
| **N5** Zoom and correct taps | In the measuring screen: pinch in as far as it goes and back out, drag the photo, Fit photo, Undo last tap, Start over. | Zoom stops at 1× and 8×; Fit shows the whole photo; Undo removes the last tap; Start over clears all taps. | TP-24.3 |
| **N6** Poor photos warn | Take a measuring photo from low down (phone near the ground, looking across), and one from far away with the paper tiny in the picture. | Warnings such as "The reference is small in the photo…" or "The result may be off by more than 10%…", or "A corner is too close to the horizon…". | TP-24.4 |
| **N7** Your own reference size | Reference → Something else. Leave the sizes empty; type letters; type 1,5 and 0.30; paste 13 characters. Switch Settings → Units to inches and type 11 and 8.5 for a Letter sheet. Tap the reference corners in a crossing order. | "Type the reference's long and short sides." until both are numbers; the comma is accepted; text stops at 12 characters; 11 × 8.5 in gives the same result as Letter; crossing order is refused with the reason. | TP-24.5 |
| **N8** The photo isn't kept | After measuring with the camera, open Google Photos and the plot's details. | The measuring photo is not in your gallery; the plot keeps the sizes (and outline), not the photo. | TP-24.6 |
| **N9** Unusual sizes ask first | Create a plot of 250 × 4 m: tap Change it, then save again and tap Yes, create it. Create 200 × 4 m. In inches, create 10 × 100 in. | "Is that size right?" names the sizes; Change it returns to the form unchanged; Yes creates it; no question at 200 m; asked for 10 in. | TP-12.6 |
| **N10** How distances show and type | In meters and in inches, look at the rulers, plant details and dialogs. In a size field type 1,5 (allowed), then -3, 1e2, 1,000 and 1.2.3 (each refused, field turns red). | A space before the unit: "2.35 m" (2 decimals), "92.5 in" (1 decimal), always with a dot. | LLR-UNIT-030/040 |
| **N11** Security audit log | Switch the catalog Basic → Pro → Basic (Settings → Catalog). Open a plan file, then try damaged.sgp.json from the test files. Then Settings → About this planner → Security audit log → Verify. | Newest first: IMPORT_REJECTED (reason), IMPORT_ACCEPTED (plots, plants), TIER_SWITCH (from, to, kept) and the first TIER_SWITCH from NONE; "✓ The log is intact (n entries)." | TP-21.9 |
| **N12** Plan file with an unknown variety | Open unknown-variety.sgp.json from the test files with Open plan file. | The plot opens with one tomato, and a message says 1 plant was skipped because its variety isn't in this phone's catalog. | TP-14.7 |

## P. This phone and Android 16

Things only a real Pixel shows: the screen edges, gestures, the security chip, the camera.

| Check | How | You should see | Step |
|---|---|---|---|
| **P1** Screen edges | Visit every screen (plot list, creator, layout, encyclopedia, settings, insights, measuring screen, dialogs) in portrait and landscape. | Nothing hidden under the status bar, the camera hole or the gesture bar; bottom buttons can be tapped. | TP-13.6 |
| **P2** Back gesture | On each screen, swipe slowly from the left edge, look, then let go. On the plot list do the same. | A preview of the previous screen while swiping; letting go goes back; on the plot list the app closes. No crash for a plot deleted elsewhere. | TP-10.2 |
| **P3** Turning the phone | On the layout: turn the phone with points in progress, with a dialog open, twice quickly, and right after Undo. Also turn it in the measuring screen. | Layout keeps tool, zoom, center, points, dialog text and plants. The measuring screen may lose taps; no crash. | TP-13.6 |
| **P4** Dark theme | Phone Settings → Display → Dark theme on; visit every screen. | All text readable; the plot drawing stays light so shade is visible. | LLR-DISP-010 |
| **P5** Largest text | Phone Settings → Display → Display size and text: font size and display size at maximum; visit every screen, then repeat in Spanish. | Nothing cut off or overlapping; dialogs scroll. | TP-13.7, TP-20.2 |
| **P6** TalkBack | Turn on TalkBack and move over the layout toolbar, the creator and the measuring screen. | Every button is read out with a name. | TP-13.7 |
| **P7** Smooth with 500 plants | Make a 30 × 20 m plot and use Fill on a big area until it has about 500 plants. Pan, zoom and drag with overlays and labels on and off. Time a cold start (close from Recents, then open). | No visible stutter on the 120 Hz screen; the plot list is ready within about 2 seconds. | TP-23.1, TP-23.2 |
| **P8** Care reminders | Settings → Catalog → Pro; turn on daily care reminders; answer Don't allow, then turn it on again and Allow. | Android asks for notifications; refusing doesn't crash; allowed reminders arrive the next day. | Manual §12 |
| **P9** Photo picker for the satellite photo | On the layout: menu → Satellite photo… → add a full-resolution 50 MP photo from the camera roll. | The Android photo picker opens (no storage permission asked); the photo shows, reduced to 1600 px; no crash. | TP-19.4 |
| **P10** Split screen | Open the app in split screen next to Files, then in a pop-up window; create a plot and place a plant. | Usable at the smaller size; no crash. | TP-13.6 |
| **P11** Spanish | Settings → Language → Español; visit every screen including the measuring screen and the audit log; back to English. | Spanish everywhere; plot names stay as typed. | TP-20.1 |
| **P12** Encrypted storage and the security chip | Add plots and plants; restart the phone; open the app. | Everything is still there (the key lives in the Pixel's security chip); the audit log still verifies as intact. | TP-21.3 |

## R. Main features still work

A quick pass over what earlier rounds built.

| Check | How | You should see | Step |
|---|---|---|---|
| **R1** Plots | Create plots of 0.5 × 0.5 m, 8 × 5 m and 1000 × 1000 m (confirm the size question); rename one; delete them one by one. | Names, sizes and plant counts in the list; empty list text after the last. | TP-12.1 |
| **R2** Zoom and pan | Pinch past the zoom limits; pan far past each edge; reset view. | Zoom stops at the limits; the plot stays on screen; reset gives zoom 1, centered. | TP-13.3 |
| **R3** Place plants | Search the picker for TOMATO, tomato, %, an emoji; place plants at the center and corners; tap twice at the same spot. | Same results whatever the case; no crash; the second tap refused with the spacing message. | TP-14.1, TP-14.2 |
| **R4** Move and undo | Move a plant onto another and off the plot; undo, redo, change again. | Refused moves snap back with a message; undo and redo work; a new change clears redo. | TP-14.4, TP-14.5 |
| **R5** Paths, obstacles, fill | Draw a path (width 1,5 accepted, abc refused); add a tree and a fence (height 150 m refused); fill an area that crosses the path. | Nothing planted on the path; the tree stays inside the plot; one undo removes the fill. | TP-15.1, TP-15.4, TP-15.5 |
| **R6** Plan an area for me | Drag an area, plan 50 plants, plant them; plan again over them with Replace, then Discard. | All placed; one undo removes them; Discard leaves the plants as they were. | TP-16.1–16.4 |
| **R7** Seasons | Start a new season with years 1899, 1900, 3000, 3001; view a past season and try to place a plant. | 1899 and 3001 refused; past seasons are read-only. | TP-17.1, TP-17.2 |
| **R8** Insights and Plan B | Open plot insights with and without a ZIP; use Plan B with a variety too wide for the spot. | "Set a location" without a ZIP; Plan B refuses the wide variety and keeps the old plant. | TP-18.1, TP-18.4 |
| **R9** Encyclopedia | Search ñ and '; create a custom variety (try spacing 0 and a duplicate code); delete an unused one; try to delete one that's planted. | Bad entries refused; a planted variety can't be deleted and its plots are listed. | TP-18.8 |
| **R10** Plan files | Save all plots to a file; open it again (names get " (imported)"); send it to the computer planner and back. | Everything comes back: plants, paths, outline, direction, ZIP, history, pests, satellite photo. | TP-19.2 |
| **R11** Settings | Move every slider to both ends; switch units; Reset all settings, cancel, then confirm; restart. | Values kept after restart; cancel changes nothing; confirm restores defaults; plots untouched. | TP-10.3, TP-10.5 |
| **R12** Online features off | With Online features off, press every online button. Turn it on in airplane mode. | No connection tried; buttons say it's off; an offline message when on. | TP-21.8 |

## X. Optional: needs a computer with adb

Only if you connect the phone with USB debugging. Skip otherwise.

| Check | How | You should see | Step |
|---|---|---|---|
| **X1** Phone tests from Android Studio | Developer options → USB debugging on; connect; in the Android App folder run ./gradlew connectedDebugAndroidTest. | All device tests pass, including the audit log and database tests. | TP-08 |
| **X2** Database encrypted | adb shell run-as com.example.smartgardenplanner ls databases, then pull the database and try sqlite3 on it. | Version 12; the file can't be read without the key. | TP-21.1, TP-21.3 |
| **X3** Measuring photo deleted | After measuring with the camera: adb shell run-as com.example.smartgardenplanner ls cache/measure | The folder is empty. | TP-24.6 |
| **X4** No backup of app data | adb backup -f sgp.ab com.example.smartgardenplanner | No app data included. | TP-21.7 |
