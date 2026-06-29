# Verification Plan & Complete Test Specification
**Document Identifier:** SGP-VERPLAN-v20.19  
**System Baseline:** Smart Garden Planner (v20.19)  
**Regulatory Standards Baseline:** DO-178C Level D 100% Comprehensive Coverage Matrix  

---

## 1. Document Index & Structural Roadmap
* **1. Document Index & Structural Roadmap**
* **2. Evaluation Methodology & Verification Scope**
* **3. Complete Low-Level Requirement (LLR) Test Case Specifications**
  * *3.1 Subsystem 1: Low-Level Camera & Sensor Capture [LLR-SEN-010]*
  * *3.2 Subsystem 2: Relational Schema & Atomicity Controls [LLR-DAT-020]*
  * *3.3 Subsystem 3: Presentation UI & Bounding Canvas Interlocks [LLR-UI-120]*
  * *3.4 Subsystem 4: Keystore Cryptography & Governance Boundaries [LLR-SEC-010]*

---

## 2. Evaluation Methodology & Verification Scope
To satisfy DO-178C certification requirements, this verification suite maps every low-level requirement (LLR) to a specific test procedure. Tests run locally using deterministic test runners, with zero reliance on cloud connections.

---

## 3. Complete Low-Level Requirement (LLR) Test Case Specifications

### 3.1 Subsystem 1: Low-Level Camera & Sensor Capture [LLR-SEN-010]

#### [TC-SEN-010-A] CameraX Baseline Binding Test
* **Traceability Link:** Enforces `[LLR-SEN-010-A]` (CameraX Lifecycle Association).
* **Execution Procedure:**
  1. Initialize the application container within a test lifecycle holder.
  2. Transition the container state to `ON_START`.
  3. Query the provider context to verify active CameraX use-case allocations.
* **Objective Pass Metrics:** The CameraX lifecycle wrapper must bind to the layout context cleanly without throwing an unhandled exception.

#### [TC-SEN-010-B] Layout Element Rotation Reset Test
* **Traceability Link:** Enforces `[LLR-SEN-010-B]` (Camera Preview View State Preservations).
* **Execution Procedure:**
  1. Boot the active camera interface workspace frame.
  2. Programmatically execute a 90-degree configuration orientation change.
  3. Read the visual surface texture assignment profile.
* **Objective Pass Metrics:** The underlying surface matrix configuration must persist its preview binding without dropping active stream parameters.

#### [TC-SEN-010-C] Camera Configuration Fallback Interlock Test
* **Traceability Link:** Enforces `[LLR-SEN-010-C]` (Camera Error Exception Handlers).
* **Execution Procedure:**
  1. Mock a camera device hardware failure.
  2. Invoke the initialization routine for the device video capture module.
  3. Monitor the application error collection channel.
* **Objective Pass Metrics:** The application must intercept the failure safely, log the tracking status parameter, and launch the non-camera rendering mode.

#### [TC-SEN-010-D] Image Resolution Constraint Audit
* **Traceability Link:** Enforces `[LLR-SEN-010-D]` (Bitmap Coordinate Dimensions Downscale).
* **Execution Procedure:**
  1. Pass an oversized test bitmap artifact ($2048 \times 2048$ pixels) into the capture processor buffer.
  2. Fire the asynchronous resolution normalization routine.
  3. Measure the height parameter bounds of the output resource file.
* **Objective Pass Metrics:** The final processing matrix height dimension must match exactly 1080px along its primary axis.

---

### 3.2 Subsystem 2: Relational Schema & Atomicity Controls [LLR-DAT-020]

#### [TC-DAT-020-A] Atomic Persistence Rollback Evaluation
* **Traceability Link:** Enforces `[LLR-DAT-020-A]` (Atomic Transaction Grouping).
* **Execution Procedure:**
  1. Open a new database write transaction block over the `AppDatabase` interface.
  2. Insert three valid crop layout entities into the configuration schema.
  3. Inject an unhandled I/O exception runtime signal prior to completing the block transaction.
  4. Query the target tables to inspect state persistence records.
* **Objective Pass Metrics:** The database must clear the partial modifications, returning to the pre-transaction baseline state.

#### [TC-DAT-020-B] Foreign Key Cascade Consistency Audit
* **Traceability Link:** Enforces `[LLR-DAT-020-B]` (Relational Database Integrity Restraints).
* **Execution Procedure:**
  1. Write a parent garden plot profile record linked to three dependent plant nodes.
  2. Execute a delete operation targeting the top-level parent plot record.
  3. Run a query check for orphan node records across the dependent tables.
* **Objective Pass Metrics:** Parent deletions must cleanly clean up child rows via cascading rules; orphan items cause a verification failure.

#### [TC-DAT-020-C] Write-Ahead Logging Concurrent Access Test
* **Traceability Link:** Enforces `[LLR-DAT-020-C]` (WAL Concurrent Channel Configurations).
* **Execution Procedure:**
  1. Open a heavy write block transaction channel on an active database worker thread.
  2. Simultaneously spin up an independent query request thread targeting the same database tables.
  3. Track execution timeline profiles across both operational paths.
* **Objective Pass Metrics:** Read pipelines must execute immediately without waiting for concurrent background write locks to clear.

---

### 3.3 Subsystem 3: Presentation UI & Bounding Canvas Interlocks [LLR-UI-120]

#### [TC-UI-120-A] Dynamic Input Change Monitor Test
* **Traceability Link:** Enforces `[LLR-UI-120-A]` (Text-Watcher Input Interceptors).
* **Execution Procedure:**
  1. Focus the canvas plot dimension configuration fields.
  2. Programmatically enter a modification string value into the input field.
  3. Check the internal live processing data flags.
* **Objective Pass Metrics:** The data modification listener must capture the change events and update the view model state.

#### [TC-UI-120-B] Temporary Projection Coordinate Calculation Test
* **Traceability Link:** Enforces `[LLR-UI-120-B]` (Proportional Spatial Canvas Calculations).
* **Execution Procedure:**
  1. Pass an input modification string value into the plot field bounds.
  2. Read the state parameter calculations assigned to the workspace transformation matrix.
* **Objective Pass Metrics:** The internal coordinate mapping systems must build an accurate temporary transformation matrix before updating the real storage records.

#### [TC-UI-120-C] Canvas Outer Boundary Intersection Interlock Test
* **Traceability Link:** Enforces `[LLR-UI-120-C]` (Coordinate Limit Intersection Check).
* **Execution Procedure:**
  1. Set the active canvas plot bounding parameters to a $10\text{m} \times 10\text{m}$ area grid.
  2. Programmatically place a layout node pin into the coordinate matrix at position ($12\text{m}, 5\text{m}$).
  3. Invoke the boundary verification routine.
* **Objective Pass Metrics:** The interface container confirmation flag `isEnabled` must switch to `false`.

#### [TC-UI-120-D] Out-of-Bounds Visual Indicator Rendering Test
* **Traceability Link:** Enforces `[LLR-UI-120-D]` (Canvas Limit Out-of-Bounds Layout Overlays).
* **Execution Procedure:**
  1. Inject an out-of-bounds pin node coordinate choice into the grid plane.
  2. Verify the visibility state flags of the UI canvas layouts.
* **Objective Pass Metrics:** The visual system must render an error state overlay highlighting the conflicting pin placement index locations.

#### [TC-UI-120-E] Persistent Warning State Presentation Test
* **Traceability Link:** Enforces `[LLR-UI-120-E]` (Persistent Alert Layout Retention rules).
* **Execution Procedure:**
  1. Trigger an out-of-bounds error state overlay.
  2. Minimize and restore the application container framework.
  3. Re-examine the active UI alert components.
* **Objective Pass Metrics:** The warning notification view layout must persist across application lifecycle changes until the invalid node pins are removed.

---

### 3.4 Subsystem 4: Keystore Cryptography & Governance Boundaries [LLR-SEC-010]

#### [TC-SEC-010-A] Hardware-Backed TEE Cryptographic Isolation Test
* **Traceability Link:** Enforces `[LLR-SEC-010-A]` (AES-GCM Encryption Protection pipelines).
* **Execution Procedure:**
  1. Process an unencrypted text payload string through the `SecurityKeyManager` interface.
  2. Inspect the cipher payload parameters.
  3. Check the host Keystore metadata to verify its integration with the hardware layer.
* **Objective Pass Metrics:** The resulting cipher block must use an authenticated key stored within the device's secure hardware enclave (TEE/StrongBox).
