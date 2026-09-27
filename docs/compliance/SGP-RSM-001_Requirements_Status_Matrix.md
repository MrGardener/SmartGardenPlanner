# SGP-RSM-001 — Requirements Implementation Status Matrix

| Field | Value |
|---|---|
| Document ID | SGP-RSM-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| Baseline assessed | Source at commit `074117f`; requirements from `smart_garden_planner_master_plan.md` (v20.18) and Knowledge Base Part 11 |
| Method | Requirement text extracted by script from the sources above; statuses assigned by code inspection (not by test) |

This matrix records, for every system (T2) and software high-level (HLR) requirement, whether the current
code implements it. **It is not verification evidence.** No status here was confirmed by a
requirements-based test. It is the starting point for re-baselining the requirements (SGP-DWR-001 WP-03) and
for planning implementation (WP-05…WP-12).

Status codes:
- **IMPL**: behaviour present and reachable in the app (defects may still be noted)
- **PART**: partly implemented, or implemented with a defect that breaks the requirement
- **CODE**: code exists but isn't reachable from the UI / never called
- **NONE**: not implemented
- **DEC**: blocked on an open decision (REVAMP_PLAN §5)
- **CONFLICT**: the requirement conflicts with the platform or architecture, so it must be rewritten
- **PROC**: a process obligation, not a product requirement (to be moved to the SQAP)

Summary: T2 (84): CODE 7, DEC 5, IMPL 24, NONE 21, PART 25, PROC 2. HLR (84): CODE 10, CONFLICT 1, DEC 9, IMPL 27, NONE 21, PART 14, PROC 2.

LLR status: LLRs are **not** assessed one by one, because the LLR set is to be rewritten (SGP-GAP-001 §4.3).
Until then, each LLR inherits the status of its parent HLR. The LLRs known to conflict with the platform are
listed in SGP-DWR-001 DW-0305.

## 1. System requirements (T2)

| ID | Requirement (abridged) | Status | Evidence / defect / DWR ref |
|---|---|---|---|
| T2-FUN-010 | The system shall capture a digital image of a planting zone (pot or field) using the device’s native hardware camera. | **CODE** | `CameraCaptureManager` exists; no screen, no runtime permission, `imagePath` never set or shown. DW-1101 |
| T2-FUN-020 | The system shall allow the user to input the physical dimensions of the captured planting zone to establish a spatial scale. | **IMPL** | Creator screen, unit-aware. Raw-SQL insert to be replaced (DW-0704). |
| T2-FUN-025 | The system shall provide a local measuring tool utilizing onboard device sensors to estimate physical distances or dimensions post-capture. | **DEC** | `SensorMeasurementEngine` exists, unused, physically unsound (double integration). Decision D-03. DW-1105 |
| T2-FUN-030 | The system shall allow the user to log the type of seed planted and the exact date it was planted. | **PART** | Seed type yes; planting date auto-set to now and not editable. DW-1110 |
| T2-FUN-040 | The system shall record and visually display where seeds are organized and planted within the captured zone. | **IMPL** | Canvas shows positions; not over a captured image (depends on T2-FUN-010). |
| T2-FUN-050 | The system shall calculate and display the correct physical spacing required between seeds during layout planning. | **PART** | Spacing circles + validation exist; plot drawn non-uniformly scaled (X≠Y), so displayed spacing is wrong in Y. DW-0901 |
| T2-FUN-060 | The system shall calculate and overlay an optimized layout grid for irrigation (drip lines, hoses, or sprinklers) across the field image. | **PART** | Greedy nearest-neighbour route from (0,0); 'optimized' undefined; no hose/sprinkler types; not over field image. |
| T2-FUN-070 | The system shall provide capabilities to Update and Delete/Archive previously recorded plots and seed nodes. | **PART** | Nodes: update/delete yes. Plots: no rename/resize/delete/archive in UI. DW-0805 |
| T2-FUN-080 | The system shall evaluate germination status based on elapsed time and present a comprehensive suite of concurrent agricultural contingency options… | **PART** | Overdue ring + dialog exist; catalog has no fast-track/catch-crop data, so only 'nursery' is ever offered. DW-0808 |
| T2-FUN-090 | The system shall allow the user to input a location parameter (ZIP code or geographic coordinates) associated with the planting zone. | **NONE** | No location/ZIP input anywhere. DW-1104 |
| T2-DAT-010 | The system shall calculate and display target harvest dates based on the planting date. | **IMPL** | Shown in plant detail dialog. |
| T2-DAT-020 | The system shall identify and display possible pests that may attack the specific plants selected. | **IMPL** | Pest notes in Encyclopedia. |
| T2-DAT-030 | The system shall identify and display the best companion plants for the selected seeds. | **PART** | Companions listed; 15 codes in Basic tier resolve only in higher tiers (shown as raw codes). DW-0809 |
| T2-DAT-040 | The system shall provide care and specific watering strategies tailored to the field/crop type. | **PART** | Care notes only; no watering strategy per field/crop type. |
| T2-DAT-050 | The system shall display optimal conditions required for seed germination. | **NONE** | Only germination *days*; no germination conditions (temperature, depth, light). |
| T2-DAT-060 | The system shall provide an interface for the user to create, read, update, and delete custom seed profiles within the local data repository. | **PART** | Create/read/update yes; no delete; duplicate code silently ignored. DW-0803, DW-0804 |
| T2-DAT-065 | Custom seed profile creation interfaces shall enforce the assignment of a broad, pre-defined botanical family reference to anchor default fallback… | **PART** | Family is free text defaulting to 'Unclassified'; no fixed family reference table. |
| T2-DAT-070 | The system shall compute and present concurrent fallback pathways. | **PART** | See T2-FUN-080. |
| T2-DAT-080 | The system shall maintain an offline climatic database mapping location parameters to USDA Plant Hardiness Zones and historical frost-date models. | **PART** | `climate_zones` table with 5 hardcoded ZIPs; never queried; no frost-date model beyond 2 day-of-year values. DW-0810 |
| T2-DAT-090 | The system shall cross-reference the local climatic constants against the botanical dictionary to calculate optimal seasonal planting windows. | **NONE** | No planting-window calculation. |
| T2-DAT-100 | The system shall extract historical growing season windows from local datasets and recommend crop varieties dynamically optimized for the localized… | **NONE** | No recommendations; `hardinessZoneMin/Max` stored but unused (FR-014). |
| T2-CON-010 | The system shall execute all data storage, calculations, and image processing locally on the device with zero network dependencies. | **IMPL** | No INTERNET permission; to be re-verified on the merged manifest after dependency upgrades. |
| T2-CON-020 | The system shall strictly omit any history tracking, telemetry, or analytics SDKs (including Google Analytics). | **IMPL** | No analytics SDKs; re-verify merged manifest/dependencies after upgrades. |
| T2-CON-030 | The system shall persist all application states and configurations into non-volatile local storage on any state change transaction. | **PART** | Canvas edits written immediately; in-progress points, dialogs and screen state are lost on config change/process death. DW-0603 |
| T2-CON-040 | The system shall downscale and compress captured image data prior to non-volatile storage serialization to enforce a hard maximum storage cap. | **CODE** | Downscale/compress code exists, unused; EXIF rotation ignored; OOM fallback stores full-size file. DW-1102 |
| T2-VAL-010 | The system shall prevent the layout configuration from saving if the spatial scale fields (length or width) are unpopulated or set to zero. | **IMPL** | Button disabled until both dimensions valid. |
| T2-VAL-012 | The system shall clear all active canvas seed node placements and require a layout reset if the user changes the configuration scale engine source… | **NONE** | Scale source cannot be changed after creation, so the behaviour is untestable; revisit with D-03. |
| T2-VAL-015 | The system shall validate user-entered dimensions against a predefined operational threshold and trigger a confirmation prompt if values are unreal… | **PART** | Hard bounds reject values; no confirmation prompt for unusual-but-allowed values; thresholds undefined (RQ). |
| T2-VAL-017 | The system shall reject updates to a plot's physical scale dimensions if the modification recomputes existing node pin coordinates to lie outside t… | **NONE** | No plot resize function. DW-0805 |
| T2-VAL-020 | The system shall validate user-entered seed node coordinates to ensure they fall strictly within the boundaries of the defined layout image. | **PART** | Tap placement is inside the canvas, but drag-move can move a plant outside the plot (no clamp/check). DW-0902 |
| T2-VAL-030 | The system shall provide a single-step "Undo" and "Redo" buffer for active seed node placement manipulations on the canvas screen. | **PART** | Multi-step undo/redo exists; conflicts with 'single-step' wording (RQ-01); snapshot duplication defect. DW-0904 |
| T2-ENV-010 | The system shall lock the application orientation to a specific user-chosen mode (e.g., Portrait) during active canvas plotting to prevent coordina… | **DEC** | Not implemented; decision D-04 (support both orientations instead). DW-0604 |
| T2-ENV-020 | The system shall serialize temporary session state variables during Android onPause/onStop lifecycle interruptions to prevent current transactional… | **NONE** | No lifecycle state serialisation. DW-0603 |
| T2-ENV-025 | The localization data processor shall reject automated device geographic coordinate readings failing to satisfy a maximum horizontal uncertainty li… | **NONE** | No location code. DW-1104 |
| T2-ENV-030 | The system shall continuously evaluate ambient device light sensors post-camera activation and present a low-lux warning UI if illumination levels… | **CODE** | `AmbientLightMonitor` exists, unused; threshold hardcoded (10 lux) instead of Settings. DW-1103 |
| T2-INT-010 | If a user alters the global spatial dimensions of a plot, the system shall automatically recalculate and update all existing seed nodes' physical s… | **NONE** | No plot dimension editing; overlays are recomputed at draw time, so this is satisfied by design once editing exists. |
| T2-INT-020 | If a user changes the global "Planting Date," the system shall recalculate and update all derived dependent attributes (germination thresholds, tar… | **NONE** | Planting date not editable; derived dates computed on the fly. |
| T2-INT-030 | The system shall continuously monitor spatial coordinates of seed nodes and trigger a visual conflict warning if a newly designated node violates t… | **PART** | Implemented as *rejection* + message, not a warning (RQ-07). |
| T2-INT-040 | The system shall generate an inverse graphical overlay ("Weed Control Mask") defining all coordinate matrices outside the active seed and spacing a… | **IMPL** | Weed-risk overlay (Path DIFFERENCE). Recomputed every frame (perf, DW-0907). |
| T2-PRE-010 | The system shall require a sensor calibration routine (e.g., planar reference step) before generating measurement data from the onboard IMU/camera… | **DEC** | Calibration code unused; depends on D-03. |
| T2-PRE-020 | The system shall display estimated dimensions with an explicit tolerance margin indication (e.g., +/- X cm) based on sensor accuracy readings. | **DEC** | Tolerance computed in unused IMU engine; depends on D-03. |
| T2-PRE-030 | The system shall maintain raw IMU sensor alignment calculations as the primary coordinate tracking matrix. | **DEC** | Proposed for deletion/replacement under D-03. |
| T2-PRE-040 | The system shall provide an optional interface allowing the user to map visual fiducial indicators (colorful flags) at boundary thresholds (corners… | **NONE** | No fiducial/corner-flag mapping or perspective correction. |
| T2-ERR-010 | Upon detection of an unrecoverable database read/write exception, the system shall terminate the active action safely and display a descriptive loc… | **NONE** | DB exceptions are uncaught in several paths and crash the app. DW-0705 |
| T2-ERR-020 | Upon reaching 95% of allocated local application storage capacity, the system shall generate a system warning and prohibit new image captures until… | **CODE** | Storage check exists (≤5% free, not '95% of allocated'); unused (RQ-02). DW-1103 |
| T2-ERR-030 | If the local image transformation process exceeds the available JVM memory heap allocation during spatial scaling, the system shall safely terminat… | **CODE** | OOM catch exists in unused camera code; fallback behaviour contradicts T2-CON-040. |
| T2-SEC-010 | Encrypt persisted application data at rest. | **PART** | SQLCipher + Keystore. Backup/restore key mismatch crashes the app; deprecated library. DW-0506, DW-0605 |
| T2-SEC-020 | Validate imported data before processing. | **NONE** | No import function. |
| T2-SEC-030 | Maintain tamper-evident audit records. | **PART** | Hash-chained log exists; only DB-init events logged; verification never run or shown. DW-1204 |
| T2-SEC-040 | Support digitally signed export packages. | **CODE** | `DatabaseSecurityPackager` exists, unused; device-bound HMAC can't be verified on another device. DW-1203 |
| T2-SAF-010 | Low battery shall not hard-block camera operation. | **NONE** | No battery monitoring. DW-1103 |
| T2-SAF-020 | Display warning when battery falls below configured threshold. | **NONE** | No battery monitoring. DW-1103 |
| T2-SAF-030 | Permit user-approved override continuation. | **NONE** | No battery monitoring. DW-1103 |
| T2-SAF-040 | Record override events locally. | **NONE** | No battery monitoring. DW-1103 |
| T2-CON-050 | Evaluate hardware capabilities during onboarding. | **CODE** | `checkCapabilities()` exists, unused. DW-1106 |
| T2-CON-060 | Gracefully degrade when optional sensors are unavailable. | **NONE** | No degraded-mode handling. DW-1106 |
| T2-DAT-110 | Support dataset export/import. | **NONE** | No export/import/merge. DW-1203 |
| T2-DAT-120 | Support ownership metadata. | **PART** | `ownerRole` column only; no behaviour. |
| T2-DAT-130 | Support conflict-aware merges. | **NONE** | No export/import/merge. DW-1203 |
| T2-DAT-140 | Support Owner, Contributor, Viewer roles. | **PART** | `ownerRole` column only; no behaviour. |
| T2-PLT-010 | Canonical data model shall be platform-neutral. | **PART** | Entities carry Room annotations (Android-bound); not platform-neutral. DW-0708 |
| T2-PLT-020 | Desktop/Web clients shall use identical schemas. | **NONE** | No desktop/web client; requirement should be restated as a schema-portability requirement. |
| T2-PLT-030 | Business logic shall remain UI-independent. | **PART** | Engines are pure Kotlin; much business logic sits in composables. DW-0702 |
| T2-GOV-010 | Execute formal 11-perspective audits. | **PROC** | Process obligation; move to SQAP and delete from product requirements (GAP §4.4). |
| T2-GOV-020 | Produce review artifacts at handoff. | **PROC** | Process obligation; move to SQAP and delete from product requirements (GAP §4.4). |
| T2-CFG-010 | The system shall provide a single screen where every user-configurable behavioral/numeric setting can be viewed and changed, organized into section… | **IMPL** | Settings screen. Zoom min/max sliders can block each other (DW-1005). |
| T2-CFG-020 | Setting changes shall persist across app restarts and take effect without requiring reinstallation or data loss. | **IMPL** | Settings screen. Zoom min/max sliders can block each other (DW-1005). |
| T2-CFG-030 | The system shall provide a single control to reset all settings to their original defaults. | **IMPL** | Settings screen. Zoom min/max sliders can block each other (DW-1005). |
| T2-CFG-040 | The plant-to-plant spacing enforcement rule shall be adjustable by the user (a multiplier on the standard non-overlap distance), rather than fixed. | **IMPL** | Settings screen. Zoom min/max sliders can block each other (DW-1005). |
| T2-CFG-050 | Companion/antagonist enforcement shall be independently toggleable without disabling spacing enforcement. | **IMPL** | Tier-gated to Pro (FR-012). Interaction with T2-INT-030 to be resolved (RQ-07). |
| T2-CFG-060 | Ruler tick spacing and text size shall be user-configurable. | **IMPL** | Settings screen. Zoom min/max sliders can block each other (DW-1005). |
| T2-CFG-070 | Canvas zoom bounds (minimum, maximum) and zoom step increment shall be user-configurable. | **IMPL** | Settings screen. Zoom min/max sliders can block each other (DW-1005). |
| T2-CFG-080 | Undo/redo history depth shall be user-configurable. | **IMPL** | Settings screen. Zoom min/max sliders can block each other (DW-1005). |
| T2-CFG-090 | Plot dimension validation bounds (minimum/maximum length and width accepted on the Creator screen) shall be user-configurable. | **IMPL** | Settings screen. Zoom min/max sliders can block each other (DW-1005). |
| T2-CFG-100 | The system shall support a user-selectable distance display unit (meters or inches), converting only at input/display boundaries — all internal sto… | **PART** | Several texts still hardcode meters (snackbars, dialogs, dashboard, path width). DW-0908 |
| T2-INT-070 | The system shall allow the user to zoom and pan the canvas view without affecting the underlying plot data or coordinate system. | **IMPL** | Zoom/Pan mode. Redesign to pinch (DW-0906) must keep these requirements satisfied or revise them. |
| T2-INT-080 | Zoom and pan shall be reachable via an explicit, deliberate toggle rather than always-active gesture recognition, to prevent accidental triggering… | **IMPL** | Zoom/Pan mode. Redesign to pinch (DW-0906) must keep these requirements satisfied or revise them. |
| T2-INT-090 | The system shall allow the user to drag an already-placed plant to a new position, subject to the same validation as initial placement. | **IMPL** | Move Mode. |
| T2-INT-100 | Drag-to-reposition shall be reachable via an explicit lock/unlock toggle, off by default, to prevent accidental repositioning during normal use. | **IMPL** | Move Mode. |
| T2-VAL-070 | A dragged plant's destination shall be rejected (and the plant returned to its original position) under the same conditions initial placement would… | **PART** | Same validation as placement, but neither checks plot bounds for drag (DW-0902). |
| T2-DAT-190 | The system shall provide a bundled reference catalog of seed varieties spanning vegetables, fruit, herbs, flowers, and ornamentals, at three select… | **IMPL** | Tiered catalog, species-level companions, 3-step picker. |
| T2-DAT-200 | Switching the active catalog tier shall replace only bundled (non-user-created) catalog entries; any variety the user has personally added or edite… | **PART** | Custom varieties are protected, but the switch crashes when catalog seeds are planted. DW-0801 |
| T2-DAT-210 | Companion/antagonist relationships shall be evaluated at the species level, so that any named cultivar of a given species correctly participates in… | **IMPL** | Tiered catalog, species-level companions, 3-step picker. |
| T2-VAL-080 | Variety selection (initial placement, changing an existing plant's variety, and auto-populate) shall present the catalog as a hierarchical Category… | **IMPL** | Tiered catalog, species-level companions, 3-step picker. |

## 2. Software high-level requirements (HLR)

> **Historical:** these v20.18 HLRs are retired and replaced by SGP-SW-HLR-001 (written from scratch, traced to T2). This section is kept only as a record of the code's state against the old HLRs.

| ID | Requirement (abridged) | Status | Evidence / defect / DWR ref |
|---|---|---|---|
| HLR-SEN-010 | Interface with Android CameraX API core pipeline to establish a local preview lifecycle. | **CODE** | CameraX binding and capture callback exist; no UI. DW-1101 |
| HLR-SEN-020 | Capture images asynchronously via ImageCapture.OnImageSavedCallback structure. | **CODE** | CameraX binding and capture callback exist; no UI. DW-1101 |
| HLR-SEN-030 | Poll data from Sensor.TYPE_LIGHT at a minimum sampling frequency of 5 Hz during active preview. | **CODE** | Light listener at SENSOR_DELAY_NORMAL; no moving average; no UI. |
| HLR-SEN-040 | Trigger low-illumination warning state if light sensor drops below 10 lux. | **CODE** | Light listener at SENSOR_DELAY_NORMAL; no moving average; no UI. |
| HLR-SEN-050 | Initialize low-latency event queues utilizing Sensor.TYPE_ACCELEROMETER and Sensor.TYPE_GYROSCOPE. | **DEC** | IMU engine: unused and unsound (drift; bias via EMA not a 3 s mean). D-03. |
| HLR-SEN-060 | Execute a 3-second static noise-bias baseline calibration routine to clear inertial drift. | **DEC** | IMU engine: unused and unsound (drift; bias via EMA not a 3 s mean). D-03. |
| HLR-SEN-070 | Compute translation distance via a double-integration algorithm on acceleration vectors. | **DEC** | IMU engine: unused and unsound (drift; bias via EMA not a 3 s mean). D-03. |
| HLR-SEN-080 | Calculate dynamic measurement tolerance boundaries using standard covariance propagation. | **DEC** | IMU engine: unused and unsound (drift; bias via EMA not a 3 s mean). D-03. |
| HLR-SEN-090 | Compress captured raw bitmaps utilizing Bitmap.compress with a JPEG quality target of 85. | **CODE** | Implemented in unused camera code with defects (integer scaling, EXIF, OOM fallback). DW-1102 |
| HLR-SEN-100 | Enforce a hard physical downscale constraint bounding captured image height to exactly 1080 pixels. | **CODE** | Implemented in unused camera code with defects (integer scaling, EXIF, OOM fallback). DW-1102 |
| HLR-SEN-110 | Isolate bitmap operations within a Java OutOfMemoryError try-catch frame to force safe fallback states. | **CODE** | Implemented in unused camera code with defects (integer scaling, EXIF, OOM fallback). DW-1102 |
| HLR-SEN-120 | Instantiate a four-point perspective warp matrix (android.graphics.Matrix) for the active layout layer. | **NONE** | No perspective warp / polygon marker grid. |
| HLR-SEN-130 | Compute an orthogonal Cartesian coordinate system grid from user-defined visual polygon markers. | **NONE** | No perspective warp / polygon marker grid. |
| HLR-SEN-140 | Map sensor translation scalar measurements to populate canvas scaling pixel-to-dimension variables. | **DEC** | IMU engine: unused and unsound (drift; bias via EMA not a 3 s mean). D-03. |
| HLR-SEN-150 | Invalidate active measurement streams if rotational pitch/roll vectors deviate by more than +/- 5 degrees. | **DEC** | Tilt check integrates raw gyro rates incorrectly; D-03. |
| HLR-SEN-160 | Check disk block states via Android StatFs API; block camera execution if free space is <= 5%. | **CODE** | StatFs check exists; unused; threshold hardcoded. |
| HLR-SEN-170 | Monitor battery state during camera use. | **NONE** | No battery code. |
| HLR-SEN-180 | Display low-battery risk workflow. | **NONE** | No battery code. |
| HLR-SEN-190 | Permit continuation after explicit user confirmation. | **NONE** | No battery code. |
| HLR-SEN-200 | Record override actions. | **NONE** | No battery code. |
| HLR-DAT-010 | Implement a local relational database infrastructure utilizing Android Room library over SQLite. | **IMPL** | Room over SQLCipher; plots/seeds/planted_nodes tables with FKs. |
| HLR-DAT-020 | Enforce an atomic transaction pattern on all data access object (DAO) mutation streams. | **PART** | Room uses WAL by default; multi-step writes (undo restore, tier switch) are not transactional. DW-0802 |
| HLR-DAT-030 | Establish separate, foreign-key relational tables for Plots, Seeds, and PlantedNodes. | **IMPL** | Room over SQLCipher; plots/seeds/planted_nodes tables with FKs. |
| HLR-DAT-040 | Project circular exclusion vector radii over canvas screen derived from crop database constants. | **IMPL** | Implemented (see T2 notes for scale defect on radii, DW-0901). |
| HLR-DAT-050 | Evaluate coordinate intersection arrays across active datasets and flag companion mismatch violations. | **IMPL** | Implemented (see T2 notes for scale defect on radii, DW-0901). |
| HLR-DAT-060 | Generate an inverse visual mask overlay across canvas bounds identifying non-planted sector fields. | **IMPL** | Implemented (see T2 notes for scale defect on radii, DW-0901). |
| HLR-DAT-070 | Project target harvest dates by applying automated temporal duration integers to planting timestamps. | **IMPL** | Implemented (see T2 notes for scale defect on radii, DW-0901). |
| HLR-DAT-080 | Monitor calendar epochs against node profiles; toggle alerts if germination intervals expire. | **PART** | Overdue evaluated at draw/tap time only; no alerts/notifications. |
| HLR-DAT-090 | Process a 3-path concurrent contingency routing logic block immediately upon a Plan A failure state. | **PART** | See T2-FUN-080. |
| HLR-DAT-100 | Render an optimized physical drip irrigation routing path grid across interactive coordinate arrays. | **IMPL** | Greedy nearest-neighbour polyline. |
| HLR-DAT-110 | Configure foreign-key constraint triggers with strict ON DELETE CASCADE configuration parameters. | **IMPL** | Implemented (see T2 notes for scale defect on radii, DW-0901). |
| HLR-DAT-120 | Reject custom profile database insertions failing string-type validations or integer field integrity. | **PART** | Only radius>0, code and name non-blank validated; invalid day values silently default. DW-0811 |
| HLR-DAT-130 | The database shall maintain a static lookup entity mapping unique ZIP code strings to USDA Plant Hardiness. | **PART** | Table exists with 5 rows; unused. |
| HLR-DAT-140 | The database shall maintain a static geospatial entity storing localized bounding box matrices. | **NONE** | No geo table, frost filtering or location handling. |
| HLR-DAT-150 | The timeline calculator shall intercept climate zone data and discard crops extending beyond historical first-frost. | **NONE** | No geo table, frost filtering or location handling. |
| HLR-DAT-160 | Construct a unified query structure extracting target crop records whenever a localized node interaction event is intercepted. | **PART** | Data assembled from in-memory lists, not a single query; acceptable if the requirement is rewritten as behaviour. |
| HLR-DAT-170 | Node creation shall automatically extract host system clock timestamps to populate baseline date_planted. | **IMPL** | Implemented (see T2 notes for scale defect on radii, DW-0901). |
| HLR-DAT-180 | Execute conversion algorithm mapping pixel limits to physical measurement parameters computed by sensors. | **DEC** | Depends on D-03. |
| HLR-DAT-190 | Enforce database constraint requiring a valid foreign key reference to a fixed baseline botanical family. | **NONE** | No family reference table / FK. |
| HLR-DAT-200 | Intercept hardware location updates and discard coordinate inputs if accuracy radius exceeds 15.0 meters. | **NONE** | No geo table, frost filtering or location handling. |
| HLR-DAT-210 | Associate ownership metadata with datasets. | **PART** | Column only. |
| HLR-DAT-220 | Execute merge conflict detection/resolution. | **NONE** | No merge. |
| HLR-DAT-230 | Serialize portable exchange packages. | **CODE** | Packager only. |
| HLR-UI-010 | Implement single-Activity, multi-Fragment navigation using Android Jetpack Navigation Component. | **CONFLICT** | Compose + hand-rolled enum navigation; requirement names Fragments/Navigation Component. Rewrite for Navigation Compose. DW-0703 |
| HLR-UI-020 | Instantiate top-level UI destinations: Dashboard, Creator, Canvas, Encyclopedia. | **IMPL** | All four destinations exist, plus Settings (extra destination needs a requirement). |
| HLR-UI-030 | CanvasFragment shall force strict orientation lock to block view reconstruction errors. | **DEC** | Not implemented; D-04. |
| HLR-UI-040 | Utilize Jetpack ViewModel architecture to separate transactional layout variables from lifecycle destrains. | **NONE** | No screen uses a ViewModel. DW-0702 |
| HLR-UI-050 | Intercept Android lifecycle state mutations and automatically serialize uncommitted coordinate objects. | **NONE** | No state serialisation / restoration. DW-0603 |
| HLR-UI-060 | Reload cached state vectors on process restoration and execute state reconciliation sequence. | **NONE** | No state serialisation / restoration. DW-0603 |
| HLR-UI-070 | Maintain operational double-stack pointer registry (UndoStack, RedoStack) capturing chronological coordinates. | **PART** | Two bounded stacks with snapshots; duplication defect. DW-0904 |
| HLR-UI-080 | Instantiate active interception callback on native system exit events; surface exit-confirmation modal. | **NONE** | No back interception; Back exits the app. DW-0602 |
| HLR-UI-090 | Display non-interactive, modal calibration state overlay upon Canvas initialization. | **DEC** | No calibration overlay; D-03. |
| HLR-UI-100 | Location configuration controller shall capture and validate input sequences against lookup tables. | **NONE** | No location input. |
| HLR-UI-110 | Intercept scale engine source switch actions, clearing active Nodes and throwing modal reset. | **NONE** | No scale-source switch / plot bounds editing. |
| HLR-UI-120 | Intercept modifications to physical bounds, validate existing nodes, and block saving if out-of-bounds. | **NONE** | No scale-source switch / plot bounds editing. |
| HLR-UI-130 | Execute onboarding capability assessment. | **NONE** | No onboarding capability assessment. |
| HLR-UI-140 | Present degraded-mode guidance. | **NONE** | No onboarding capability assessment. |
| HLR-SEC-010 | Encrypt persistence using platform-backed key storage. | **PART** | See T2-SEC-010. |
| HLR-SEC-020 | Validate imported package integrity and schema compliance. | **NONE** | No import. |
| HLR-SEC-030 | Sign exported packages. | **CODE** | Unused packager. |
| HLR-SEC-040 | Maintain tamper-evident audit logs. | **PART** | See T2-SEC-030. |
| HLR-GOV-010 | Generate 11-perspective review reports. | **PROC** | Process obligation; move to SQAP. |
| HLR-GOV-020 | Execute review workflow during handoff. | **PROC** | Process obligation; move to SQAP. |
| HLR-CFG-010 | Persist each setting as an individually-keyed row in the existing generic key-value configuration table, rather than introducing a new schema/table… | **IMPL** | Implemented. |
| HLR-CFG-020 | Load the full settings object once per screen that depends on it, at screen entry, rather than maintaining a single persistent in-memory instance a… | **IMPL** | Implemented. |
| HLR-CFG-030 | Group settings into UI sections matching T2-CFG-010's categories: Units, Catalog, Spacing & Placement, Canvas Display, Plot Validation, Sensors & H… | **IMPL** | Implemented. |
| HLR-CFG-040 | Apply the configured spacing-margin multiplier and companion-rule toggle to every placement-validation call site uniformly (individual placement, d… | **IMPL** | Implemented. |
| HLR-CFG-050 | Convert between the internal canonical distance unit (meters) and the configured display unit at exactly two boundaries: formatting a stored value… | **PART** | Violated where texts hardcode 'm' (DW-0908). |
| HLR-ZOOM-010 | Maintain a persistent zoom scale factor and constrain it within the configured min/max bounds (Settings → Canvas Display) on every adjustment. | **IMPL** | Implemented; to be revised with the canvas redesign (DW-0906). |
| HLR-ZOOM-020 | Size the canvas element explicitly as a function of a stable, once-measured reference viewport size and the current zoom factor, rather than relyin… | **IMPL** | Implemented; to be revised with the canvas redesign (DW-0906). |
| HLR-ZOOM-030 | Attach pan (scroll) behavior only while Zoom/Pan mode is active, and only to a layout node distinct from the one used for the stable reference-size… | **IMPL** | Implemented; to be revised with the canvas redesign (DW-0906). |
| HLR-ZOOM-040 | Present zoom controls (+/-, and a conditional recenter control) in a fixed screen position that does not shift based on which of those controls hap… | **IMPL** | Implemented; to be revised with the canvas redesign (DW-0906). |
| HLR-ZOOM-050 | Adjust the ruler's effective tick interval as an inverse function of the current zoom factor, so tick density visually increases when zoomed in. | **PART** | Tick interval scales, but ruler geometry does not follow zoom/pan (DW-0903). |
| HLR-MOVE-010 | While Move Mode is active, a drag gesture beginning within an existing plant's hit-radius shall reposition that plant's live rendered position for… | **IMPL** | Implemented. |
| HLR-MOVE-020 | On drag release, validate the candidate destination coordinates using the same spacing/companion/no-plant-path checks as new placement, excluding t… | **PART** | No plot-bounds check (DW-0902). |
| HLR-MOVE-030 | On successful validation, persist the new coordinates and push a new undo/redo snapshot; on failed validation, discard the drag with no persisted c… | **IMPL** | Implemented. |
| HLR-CAT-010 | Store catalog data as bundled flat-file assets (not embedded source-code literals), parsed at load time into typed records. | **IMPL** | Implemented. |
| HLR-CAT-020 | On first launch, load the Basic tier automatically if no seed records exist yet in the local database. | **IMPL** | Implemented. |
| HLR-CAT-030 | On a tier switch, delete only catalog-sourced (non-custom) seed records before inserting the newly selected tier's full record set. | **PART** | Delete-before-insert violates the FK when catalog seeds are planted (DW-0801). |
| HLR-CAT-040 | Mark every seed record created or edited through the Encyclopedia's Add/Edit flow as user-owned (excluded from tier-switch deletion), regardless of… | **IMPL** | Implemented. |
| HLR-CAT-050 | Derive a "species" grouping key from each cultivar's catalog code by its shared prefix, and a display species name from the shared portion of its c… | **IMPL** | Implemented. |
| HLR-SEL-010 | Present catalog Category as the first selection step, Species (grouped per HLR-CAT-050, with cultivar count shown per species) as the second, and i… | **IMPL** | Implemented. |
| HLR-SEL-020 | Provide search filtering at the Species and Cultivar steps, scoped to entries currently visible at that step (not the whole catalog). | **IMPL** | Implemented. |
| HLR-SEL-030 | Any UI surface that previously listed the full seed dictionary in a non-virtualized way (a plain iterated list/menu, as opposed to a lazily-rendere… | **IMPL** | Implemented. |

## 3. Implemented behaviour with no requirement (untraced functionality)

DO-178C requires every piece of code to trace to a requirement; code without one is either missing a
requirement or is extraneous code to be removed. Found by inspection:

| Behaviour | Location | Action |
|---|---|---|
| No-plant paths (rectangle + polyline, edit/delete, width) | `MainActivity.kt` canvas, `PathZoneEntity` | Write T2/HLR (roadmap-era feature, never specified). DW-0302 |
| Auto-populate (line/hex, rectangle/polygon) | `AutoPopulateEngine`, canvas | Write T2/HLR (FR-001 covers only the polygon part). DW-0302 |
| Variety colour coding + manual override + legend | `VegetableColorPalette`, Encyclopedia | Write requirements. DW-0302 |
| Ruler | `drawRuler` | Covered by T2-CFG-060 only for configuration; the ruler itself needs a requirement. DW-0302 |
| Change variety of a planted node (validated) | Canvas | Write requirement. DW-0302 |
| Germination "Keep as-is" (resolve flag) | Canvas | Write requirement. DW-0302 |
| Debug "Hardware-Encrypted Storage Vault" card | `HardwareSecureVaultCard`, `SecureConfigVault` | No requirement: remove from the product (extraneous code). DW-1001 |
| `StorageViewModel` | `ui/StorageViewModel.kt` | Only used by the vault card; remove or give it a requirement. DW-0702 |
| `SeedDataset.starterSeeds`, `WorkspaceUiState`, `security/SecurityKeyManager.kt` | various | Dead code: remove. DW-0706, DW-1201 |
| Settings screen as a 5th destination | `SettingsScreen.kt` | HLR-UI-020 lists four destinations; add Settings. DW-0302 |
