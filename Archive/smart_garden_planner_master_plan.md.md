## 💾 MASTER PLAN (DO-178C/ARP4754A Lifecycle) — Version 6.3

```text
================================================================================
MASTER MASTER PLAN: SMART GARDEN PLANNER
================================================================================

1. PROJECT OVERVIEW & GOVERNANCE RULES
- Core Concept: Offline Android app for garden plot imagery mapping, sensor-based 
  distance estimation, automated seed spacing/germination tracking, and irrigation layout design.
- Lifecycle Rigor: ARP4754A and DO-178C requirements-driven development.
- MANDATORY OPERATIONAL DIRECTIVE [PERMANENT]: The AI collaborator must output the 
  complete, updated Master Plan at the beginning of every single response in this project.
- VERSIONING DIRECTIVE [PERMANENT]: The AI collaborator must increment and display 
  the active version number in the header block of the Master Plan with every single iteration.

2. REQUIREMENTS HIERARCHY STRUCTURING
- T1 Level: Customer-Specific Adaptation Requirements (Reserved for future clients).
- T2 Level: Product-Level Base Requirements (Frozen and Consolidated Below).
- T3 Level: Software High-Level Requirements (HLRs) (Frozen and Consolidated Below).

3. ARCHITECTURAL BASELINE & FUTURE DEVELOPMENT IDEAS
- [EVAL-01]: Core 4-Screen Layout (Dashboard, Creator, Canvas, Encyclopedia).
- [EVAL-02]: Touch-coordinate plotting over static local image matrix.
- [EVAL-03]: Android Sensor Framework (IMU/Camera) for local distance estimation.
- [EVAL-04]: Comprehensive Contingency Menu Presentation Architecture (All Options Available).
- [EVAL-05]: Binary Plot Vector Mapping (Plant Zone vs. Weed/Exclusion Zone Matrix).
- [EVAL-06]: Sensor-Primary Hybrid Anchor Matrix (Optional Visual Fiducial Corner Flares).

4. CURRENT LIFE CYCLE STEP & NEXT TASKS
- Status: Architecture Complete & Consolidated. Project specifications sealed into Version 6.3.
- Next Up: Execution of Low-Level Requirements (LLRs) or Test Implementation Planning.

================================================================================
T2 BASELINE REQUIREMENTS DOCUMENT (PRODUCT LEVEL - FULLY SEALED)
================================================================================
[T2-FUN-010] The system shall capture a digital image of a planting zone (pot or field) 
             using the device’s native hardware camera.
[T2-FUN-020] The system shall allow the user to input the physical dimensions of the 
             captured planting zone to establish a spatial scale.
[T2-FUN-025] The system shall provide a local measuring tool utilizing onboard device 
             sensors to estimate physical distances or dimensions post-capture.
[T2-FUN-030] The system shall allow the user to log the type of seed planted and the 
             exact date it was planted.
[T2-FUN-040] The system shall record and visually display where seeds are organized 
             and planted within the captured zone.
[T2-FUN-050] The system shall calculate and display the correct physical spacing required 
             between seeds during layout planning.
[T2-FUN-060] The system shall calculate and overlay an optimized layout grid for irrigation 
             (drip lines, hoses, or sprinklers) across the field image.
[T2-FUN-070] The system shall provide capabilities to Update and Delete/Archive 
             previously recorded plots and seed nodes.
[T2-FUN-080] The system shall evaluate germination status based on elapsed time and 
             present a comprehensive suite of concurrent agricultural contingency options 
             ("Plan B") upon a user-declared germination failure.
[T2-FUN-090] The system shall allow the user to input a location parameter (ZIP code or 
             geographic coordinates) associated with the planting zone.

[T2-DAT-010] The system shall calculate and display target harvest dates based on the planting date.
[T2-DAT-020] The system shall identify and display possible pests that may attack the specific plants selected.
[T2-DAT-030] The system shall identify and display the best companion plants for the selected seeds.
[T2-DAT-040] The system shall provide care and specific watering strategies tailored to the field/crop type.
[T2-DAT-050] The system shall display optimal conditions required for seed germination, to include:
             - A: Total expected germination time.
             - B: Lighting exposure rules (Dark vs. Light).
             - C: Soil coverage status (Covered vs. Uncovered).
             - D: Germination watering intensity (Light vs. Heavy watering).
             - E: Target planting depth.
[T2-DAT-060] The system shall provide an interface for the user to create, read, update, 
             and delete custom seed profiles within the local data repository.
[T2-DAT-070] The system shall compute and present concurrent fallback pathways (including 
             shorter-season varieties, transplant methodologies, and alternative catch-crops) 
             by recalculating the remaining seasonal timeline against the local botanical matrix.
[T2-DAT-080] The system shall maintain an offline climatic database mapping location 
             parameters to USDA Plant Hardiness Zones and historical frost-date models.
[T2-DAT-090] The system shall cross-reference the local climatic constants against the 
             botanical dictionary to calculate optimal seasonal planting windows.
[T2-DAT-100] The system shall extract historical growing season windows from local datasets 
             and recommend crop varieties dynamically optimized for the localized climate profile.

[T2-CON-010] The system shall execute all data storage, calculations, and image processing 
             locally on the device with zero network dependencies.
[T2-CON-020] The system shall strictly omit any history tracking, telemetry, or analytics 
             SDKs (including Google Analytics).
[T2-CON-030] The system shall persist all application states and configurations into 
             non-volatile local storage on any state change transaction.
[T2-CON-040] The system shall downscale and compress captured image data prior to 
             non-volatile storage serialization to enforce a hard maximum storage cap.

[T2-VAL-010] The system shall prevent the layout configuration from saving if the 
             spatial scale fields (length or width) are unpopulated or set to zero.
[T2-VAL-015] The system shall validate user-entered dimensions against a predefined 
             operational threshold and trigger a confirmation prompt if values are 
             unrealistically small or large.
[T2-VAL-020] The system shall validate user-entered seed node coordinates to ensure they 
             fall strictly within the boundaries of the defined layout image.
[T2-VAL-030] The system shall provide a single-step "Undo" and "Redo" buffer for active 
             seed node placement manipulations on the canvas screen.

[T2-ENV-010] The system shall lock the application orientation to a specific user-chosen 
             mode (e.g., Portrait) during active canvas plotting to prevent coordinate recalculation errors.
[T2-ENV-020] The system shall serialize temporary session state variables during Android 
             onPause/onStop lifecycle interruptions to prevent current transactional data loss.
[T2-ENV-030] The system shall continuously evaluate ambient device light sensors post-camera 
             activation and present a low-lux warning UI if illumination levels threaten 
             image-processing fidelity.

[T2-INT-010] If a user alters the global spatial dimensions of a plot, the system shall 
             automatically recalculate and update all existing seed nodes' physical 
             spacing overlays based on the updated scale.
[T2-INT-020] If a user changes the global "Planting Date," the system shall recalculate 
             and update all derived dependent attributes (germination thresholds, target harvest dates).
[T2-INT-030] The system shall continuously monitor spatial coordinates of seed nodes and 
             trigger a visual conflict warning if a newly designated node violates the botanical 
             exclusion zone (companion planting restriction) of an adjacent node.
[T2-INT-040] The system shall generate an inverse graphical overlay ("Weed Control Mask") 
             defining all coordinate matrices outside the active seed and spacing areas as 
             unplanted/clearance target zones.

[T2-PRE-010] The system shall require a sensor calibration routine (e.g., planar reference step) 
             before generating measurement data from the onboard IMU/camera framework.
[T2-PRE-020] The system shall display estimated dimensions with an explicit tolerance 
             margin indication (e.g., +/- X cm) based on sensor accuracy readings.
[T2-PRE-030] The system shall maintain raw IMU sensor alignment calculations as the 
             primary coordinate tracking matrix.
[T2-PRE-040] The system shall provide an optional interface allowing the user to map visual 
             fiducial indicators (colorful flags) at boundary thresholds (corners) to bound 
             and correct coordinate positioning drift on the visual canvas matrix.

[T2-ERR-010] Upon detection of an unrecoverable database read/write exception, the system 
             shall terminate the active action safely and display a descriptive local error message.
[T2-ERR-020] Upon reaching 95% of allocated local application storage capacity, the system 
             shall generate a system warning and prohibit new image captures until space is cleared.
[T2-ERR-030] If the local image transformation process exceeds the available JVM memory heap 
             allocation during spatial scaling, the system shall safely terminate the transform layer 
             and fall back to displaying the raw, un-overlayed plot image rather than triggering 
             an OutOfMemory runtime crash.

================================================================================
T3 BASELINE SOFTWARE REQUIREMENTS DOCUMENT (HIGH-LEVEL REQUIREMENTS - FULLY SEALED)
================================================================================
--- SUBSYSTEM 1: SENSORS & IMAGE PROCESSING ---
[HLR-SEN-010] Interface with Android CameraX API core pipeline to establish a local preview lifecycle.
[HLR-SEN-020] Capture images asynchronously via ImageCapture.OnImageSavedCallback structure.
[HLR-SEN-030] Poll data from Sensor.TYPE_LIGHT at a minimum sampling frequency of 5 Hz during active preview.
[HLR-SEN-040] Trigger low-illumination warning state if light sensor drops below 10 lux.
[HLR-SEN-050] Initialize low-latency event queues utilizing Sensor.TYPE_ACCELEROMETER and Sensor.TYPE_GYROSCOPE.
[HLR-SEN-060] Execute a 3-second static noise-bias baseline calibration routine to clear inertial drift.
[HLR-SEN-070] Compute translation distance via a double-integration algorithm on acceleration vectors.
[HLR-SEN-080] Calculate dynamic measurement tolerance boundaries using standard covariance propagation.
[HLR-SEN-090] Compress captured raw bitmaps utilizing Bitmap.compress with a JPEG quality target of 85.
[HLR-SEN-100] Enforce a hard physical downscale constraint bounding captured image height to exactly 1080 pixels.
[HLR-SEN-110] Isolate bitmap operations within a Java OutOfMemoryError try-catch frame to force safe fallback states.
[HLR-SEN-120] Instantiate a four-point perspective warp matrix (android.graphics.Matrix) for the active layout layer.
[HLR-SEN-130] Compute an orthogonal Cartesian coordinate system grid from user-defined visual polygon markers.
[HLR-SEN-140] Map sensor translation scalar measurements to populate canvas scaling pixel-to-dimension variables.
[HLR-SEN-150] Invalidate active measurement streams if rotational pitch/roll vectors deviate by more than +/- 5 degrees.
[HLR-SEN-160] Check disk block states via Android StatFs API; block camera execution if free space is <= 5%.

--- SUBSYSTEM 2: LOCAL DATABASE & CROP INTELLIGENCE ---
[HLR-DAT-010] Implement a local relational database infrastructure utilizing Android Room library over SQLite.
[HLR-DAT-020] Enforce an atomic transaction pattern on all data access object (DAO) mutation streams.
[HLR-DAT-030] Establish separate, foreign-key relational tables for Plots, Seeds, and PlantedNodes.
[HLR-DAT-040] Project circular exclusion vector radii over canvas screen derived from crop database constants.
[HLR-DAT-050] Evaluate coordinate intersection arrays across active datasets and flag companion mismatch violations.
[HLR-DAT-060] Generate an inverse visual mask overlay across canvas bounds identifying non-planted sector fields.
[HLR-DAT-070] Project target harvest dates by applying automated temporal duration integers to planting timestamps.
[HLR-DAT-080] Monitor calendar epochs against node profiles; toggle alerts if germination intervals expire.
[HLR-DAT-090] Process a 3-path concurrent contingency routing logic block immediately upon a Plan A failure state.
[HLR-DAT-100] Render an optimized physical drip irrigation routing path grid across interactive coordinate arrays.
[HLR-DAT-110] Configure foreign-key constraint triggers with strict ON DELETE CASCADE configuration parameters.
[HLR-DAT-120] Reject custom profile database insertions failing string-type validations or integer field integrity.
[HLR-DAT-130] The database shall maintain a static lookup entity (`ClimaticZipZones`) mapping unique ZIP code 
              strings to USDA Plant Hardiness Zone integers and median first/last frost timestamps.
[HLR-DAT-140] The database shall maintain a static geospatial entity (`ClimaticGeoZones`) storing localized 
              bounding box matrices (Latitude/Longitude thresholds with standard geographic tolerances) 
              mapping coordinate points to corresponding Hardiness Zone metrics.
[HLR-DAT-150] The timeline calculator shall intercept climate zone data from active tables and discard 
              any crop variety or contingency option whose calculated maturation date extends beyond 
              the local historical first-frost date threshold.
[HLR-DAT-160] Construct a unified query structure that extracts the target crop record’s companion fields, 
              watering strategies, pest warning matrices, and germination profiles whenever a localized 
              node interaction event is intercepted on the visual layout canvas.
[HLR-DAT-170] The node creation entry transaction shall automatically extract the host system's hardware clock 
              timestamp to populate the base date_planted table row, unless an explicit override array is received.
[HLR-DAT-180] Execute a conversion algorithm mapping pixel resolution dimension limits (W_px, H_px) directly to 
              the physical measurement parameters computed by the sensor matrix, establishing a dynamic layout 
              ratio constraint for all seed spacing radius calculations.

--- SUBSYSTEM 3: USER INTERFACE, STATE CONTROL & INTER-PROCESS TRANSITIONS ---
[HLR-UI-010] The software shall implement a single-Activity, multi-Fragment navigation architecture using the Android Jetpack Navigation Component.
[HLR-UI-020] The application layer shall instantiate four top-level UI destinations: DashboardFragment, CreatorFragment, CanvasFragment, and EncyclopediaFragment.
[HLR-UI-030] The CanvasFragment shall force a strict orientation lock via Activity.setRequestedOrientation based on a user-defined plot configuration state to block view reconstruction errors during coordinate plotting.
[HLR-UI-040] The user interface framework shall utilize Android Jetpack ViewModel architecture to separate transactional layout variables from layout lifecycle destrains.
[HLR-UI-050] The system shall intercept Android lifecycle state mutations (onSaveInstanceState) and automatically serialize uncommitted coordinate objects into an encrypted local Cache repository.
[HLR-UI-060] The software shall reload cached state vectors on process restoration (onCreate) and execute a smooth state reconciliation sequence if uncommitted transaction metrics are discovered.
[HLR-UI-070] The Canvas engine shall maintain an operational double-stack pointer registry (UndoStack, RedoStack) within the isolated layout context, capturing chronological coordinate array snapshots up to a fixed deep boundary.
[HLR-UI-080] The navigation manager shall instantiate an active interception callback on native system exit events; if the working memory contains uncommitted changes, it shall pause routing loops and surface an exit-confirmation modal.
[HLR-UI-090] The user interface framework shall display a non-interactive, modal calibration state overlay upon Canvas initialization, intercepting all touch inputs and blocking mapping operations until Subsystem 1 signals a successful calibration event match.
[HLR-UI-100] The location configuration view controller shall capture input sequences and validate them against active database lookup tables; if an entry returns a null entity reference, the layout engine shall block save transitions and surface an unrecognized-parameter error notification.
================================================================================