# Concept of Operations (ConOps) Document
**Document Identifier:** SGP-CONOPS-v20.19  
**System Baseline:** Smart Garden Planner (v20.19)  
**Regulatory Standards Baseline:** ARP4754A / DO-178C Level D Isolation Compliance  

---

## 1. Document Index & Structural Roadmap
* **1. Document Index & Structural Roadmap**
* **2. System Identity & Mission Scope**
* **3. Operational Regulatory Standards Baseline**
* **4. Architectural High-Level Block Flow**
* **5. Operational Capabilities & Implementation Matrices**
  * *5.1 Fully Implemented Operational Layers*
  * *5.2 Pending Lifecycle Implementation Gaps*
* **6. Strategic Expansion Tracking & Growth Vectors**

---

## 2. System Identity & Mission Scope
The Smart Garden Planner system transforms a standalone mobile computational platform into a localized visual engineering layout environment for optimized agricultural micro-allocations. Operating within environments with restricted or unavailable communications infrastructure, the system enforces a strict **offline-first footprint**.

### 2.1 Critical Operational Constraints
* **Telemetry Exclusion:** The system contains zero external logging hooks, analytics scrapers, or third-party performance monitors.
* **Deterministic Local Boundaries:** Every calculation, pixel-to-meter structural scaling operation, and geometric intersection calculation is computed entirely on the host mobile processing unit.

---

## 3. Operational Regulatory Standards Baseline
The structural formulation, verification depth, and verification cycle tracking of this application adhere to the principles outlined below:
* **ARP4754A Guidelines:** Used to establish system-level architectural requirements, environmental boundaries, and validation safety interlocks.
* **DO-178C Level D Specifications:** Dictates lifecycle traceability protocols, low-level software requirement (LLR) partitioning, and rigorous automated structural test coverage matrices.

---

## 4. Architectural High-Level Block Flow
System data operations flow through isolated logical boundaries to prevent race conditions or thread corruption during configuration changes:

```
[User Canvas Interaction / Touch Node Placement]
                       │
                       ▼
┌────────────────────────────────────────────────────────┐
│ UI Presentation Layer (Jetpack Compose UI)             │
│ - Enforces real-time input bounding boxes             │
│ - Computes Vector Differences via Path.Op.DIFFERENCE   │
└───────────────────────┬────────────────────────────────┘
                        │ Asynchronous State Channel (StateFlow)
                        ▼
┌────────────────────────────────────────────────────────┐
│ State Control Layer (StorageViewModel)                 │
│ - Encapsulates asynchronous coroutine context handlers  │
│ - Executes 3-Path "Plan B" Botanical Determinations   │
└───────────────────────┬────────────────────────────────┘
                        │ Isolated Worker Transaction
                        ▼
┌────────────────────────────────────────────────────────┐
│ Storage & Cryptographic Isolation Layer (AppDatabase)  │
│ - Executes atomic operations under WAL constraints     │
│ - Encrypts schema boundaries via Hardware Key Manager  │
└────────────────────────────────────────────────────────┘
```

---

## 5. Operational Capabilities & Implementation Matrices

### 5.1 Fully Implemented Operational Layers
1. **Inverse Visual Weed Control Mask Engine:** Employs explicit mathematical boolean difference vector operations on geometric vector shapes to isolate non-crop target treatment profiles:
   $$\text{MaskPath} = \text{BoundaryPath} \cdot \text{Op.DIFFERENCE}(\text{CropPlacementPaths})$$
2. **3-Path "Plan B" Germination Logic System:** Establishes standalone logical branches (Primary, Alternative, and Mitigation) to maintain structural plant planning when target local climate values drop below historical standard envelopes.
3. **Offline Geographic Coordinate Database:** Computes bounding box evaluation queries directly against pre-compiled static internal JSON records:
   $$\text{SELECT} * \text{FROM climatic\_geo\_zones WHERE} :lat \text{ BETWEEN lat\_min AND lat\_max}$$

### 5.2 Pending Lifecycle Implementation Gaps
1. **IMU Accelerometer Covariance Calculation Engine:** Designed to intercept raw device sensor signals to infer physical step dimensions for canvas scale operations. Currently uses mock simulation drivers; awaiting native low-pass filter integration.
2. **Asynchronous Multi-Threaded CameraX Compression Stream:** Intercepts live bitmap streams, forces downscaling to a maximum 1080px resolution boundary, and limits the compression profile to an exact 85% JPEG envelope before writing to local application storage.

---

## 6. Strategic Expansion Tracking & Growth Vectors
* **Cross-Runtime Serialization Ecosystem:** The business engine, localized data models, and vector coordinate matrix evaluators are written in pure Kotlin, leaving the core software stack ready for compilation across Kotlin Multiplatform (KMP) runtimes, including desktop and web targets.
* **Edge ML Automated Weed Processing:** The architectural interface boundaries leave dedicated entry hooks for a localized neural net inference model to analyze frames captured within the computed inverse visual weed control mask.
