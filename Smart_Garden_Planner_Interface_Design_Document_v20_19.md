# Interface Design Document (IDD)

> **Status note (2026-09-27):** this document describes the interfaces as of v20.19 (July 2026) and is kept as a
> historical record. The current interfaces are specified in the requirements set: plan file format
> `docs/PLAN_FILE_FORMAT.md` (T2-PLT-020, HLR-PORT-*, LLR-PFILE-*, LLR-SEAS-030, LLR-WATER-010), the database schema
> (schema 10: LLR-SEAS-010 and the entity LLRs in SGP-SW-LLR-001) and the user interface (HLR/LLR sections). Where this
> document and those disagree, the requirements set applies.

**Document Identifier:** SGP-IDD-v20.19  
**System Baseline:** Smart Garden Planner (v20.19)  
**Regulatory Standards Baseline:** DO-178C Data Interface Protection  

---

## 1. Document Index & Structural Roadmap
* **1. Document Index & Structural Roadmap**
* **2. Interface Boundary Scope & Isolation Strategy**
* **3. Hardware Cryptographic Key Interface Protocol [INT-010-SEC]**
* **4. UI Input Validation Canvas Interface Protocol [INT-020-UI]**
* **5. Local Filesystem JSON Import Schema Protocol [INT-030-IO]**
* **6. Cross-Document Traceability Matrix Reference**

---

## 2. Interface Boundary Scope & Isolation Strategy
The *Interface Design Document* establishes data transformation boundaries, structural alignment parameters, and input/output validation checks across all communication vectors within the local system runtime.

```
┌────────────────────────────┐                  Raw Record Stream                 ┌───────────────────────────┐
│ Room Database Data Objects ├───────────────────────────────────────────────────►│ SecurityKeyManager Engine │
│ (Plaintext Memory Buffers) │◄───────────────────────────────────────────────────┤ (AES-GCM / 12-Byte IV)    │
└────────────────────────────┘              Encrypted Binary BLOB Block           └─────────────┬─────────────┘
                                                                                                │
                                                                                                ▼
                                                                                  ┌───────────────────────────┐
                                                                                  │ Hardware Keystore Slot    │
                                                                                  │ (Secure TEE / StrongBox)  │
                                                                                  └───────────────────────────┘
```

---

## 3. Hardware Cryptographic Key Interface Protocol [INT-010-SEC]
* **Input Origin Entity:** Unencrypted database string layers written within Room memory buffers.
* **Output Sink Entity:** Encrypted SQLite transaction BLOB files written onto disk.
* **Data Properties & Cipher Constraints:** Data passes through an `AES/GCM/NoPadding` transform vector. The interface layer explicitly mandates an Initialization Vector (IV) length parameter of exactly 12 bytes. If an unaligned IV or non-matching length profile enters this boundary, the transaction is dropped to block memory corruption.

---

## 4. UI Input Validation Canvas Interface Protocol [INT-020-UI]
* **Input Origin Entity:** Continuous string entries from layout dimension parameter fields.
* **Output Sink Entity:** Visual state flow updates driving the Jetpack Compose canvas.
* **Verification Constraints:** Real-time text watchers evaluate the input entries. If a newly introduced pin coordinate violates layout dimensions, the canvas updates the confirmation flag to `isEnabled = false` and generates a persistent out-of-bounds overlay.

---

## 5. Local Filesystem JSON Import Schema Protocol [INT-030-IO]
* **Input Origin Entity:** Static filesystem JSON configuration files.
* **Output Sink Entity:** In-memory configuration data arrays.
* **Validation Rules:** Validates structural signatures against a strict schema. The parsing process drops modified, unaligned, or malformed entries instantly, protecting database storage pools from corruption.

---

## 6. Cross-Document Traceability Matrix Reference
* **INT-010-SEC:** Governs data flows evaluated by **[TC-SEC-010]** inside the Verification Plan.
* **INT-020-UI:** Governs input pipelines validated by **[TC-UI-120]** inside the Verification Plan.
* **INT-030-IO:** Governs structural files checked by **[TC-SEC-020]** inside the Verification Plan.





