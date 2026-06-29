# Development Log & Technical Debt Ledger
**Document Identifier:** SGP-DEVLOG-v20.19  
**System Baseline:** Smart Garden Planner (v20.19)  

---

## 1. Document Index & Structural Roadmap
* **1. Document Index & Structural Roadmap**
* **2. Current Implementation Status Summary**
* **3. Active Development Milestones & Worklogs**
* **4. Technical Debt Registry & Refactoring Schedule**
  * *4.1 Software-Backed Keystore Fallback Remediation [DEBT-SEC-001]*
  * *4.2 Thread Allocation Refactoring [DEBT-VM-002]*
  * *4.3 Schema Migration Strategy Realignment [DEBT-DB-003]*

---

## 2. Current Implementation Status Summary
The **v20.19** code configuration secures local relational schemas, hardware cryptography engines, and core presentation state models. Active development tasks focus on moving from mock sensors to live device components without degrading safety properties.

---

## 3. Active Development Milestones & Worklogs
* **Milestone A (Completed):** Stabilized Jetpack Compose visual canvas interaction models and verified the `Path.Op.DIFFERENCE` inverse visual weed mask operation.
* **Milestone B (Current):** Refactoring `StorageViewModel` coroutine execution paths to improve data thread separation during complex layout reads.

---

## 4. Technical Debt Registry & Refactoring Schedule

### 4.1 Software-Backed Keystore Fallback Remediation [DEBT-SEC-001]
* **Location:** `com.example.smartgardenplanner.SecurityKeyManager`
* **Current Workaround:** If a legacy device lacks a dedicated hardware-backed TEE or StrongBox container, the security pipeline falls back to a software key simulator.
* **Refactoring Plan:** Restructure the module initialization routine to strictly mandate a hardware environment. The boot sequence must fail and drop safely into a system-halt condition if hardware cryptographic blocks are missing.

### 4.2 Thread Allocation Refactoring [DEBT-VM-002]
* **Location:** `com.example.smartgardenplanner.ui.StorageViewModel`
* **Current Workaround:** Asynchronous database calls run within default IO context catch pools, introducing potential frame drops during complex historical database queries.
* **Refactoring Plan:** Map all persistence and file processing streams onto distinct, non-blocking worker threads.

### 4.3 Schema Migration Strategy Realignment [DEBT-DB-003]
* **Location:** `com.example.smartgardenplanner.data.AppDatabase`
* **Current Workaround:** Database schema updates rely on a destructive migration pattern that clears local data records when data definitions shift.
* **Refactoring Plan:** Code precise, incremental SQL migration scripts to transform tables without deleting user planning histories.
