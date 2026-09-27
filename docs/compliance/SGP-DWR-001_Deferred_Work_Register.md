# SGP-DWR-001 — Deferred Work Register

| Field | Value |
|---|---|
| Document ID | SGP-DWR-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| Purpose | Single list of **everything that still has to be handled or implemented**: product work, compliance work, documentation, debt and open decisions. |
| Sources | REVAMP_PLAN_Pixel10Pro.md (issue IDs such as BLD-01, CAN-04), SGP-GAP-001, SGP-RSM-001, FEATURE_ROADMAP.md (FR-xxx), Dev Log debt items (DEBT-xxx), new findings from this review (marked **NEW**). |

## 1. How this register works

- **ID format:** `DW-WWNN`, where `WW` is the work package (§2) and `NN` the item. IDs are never reused.
- **Status values:** Open → In progress → Implemented (awaiting verification) → Verified → Closed. An item is
  **Closed** only when the "Done when" criterion is met **and** the evidence (review record, test result,
  trace entry) exists in the repository. "Implemented" is not enough (same rule as FEATURE_ROADMAP.md).
- **Severity:** B blocker, H high, M medium, L low (same meaning as in the REVAMP plan).
- **Phase:** the REVAMP_PLAN phase (0–10) in which the item is planned.
- **Life cycle data (LCD):** which DO-178C/ARP4754A data items must be created or updated when the item is
  done. Abbreviations: SRS (software requirements), SDD (design description), SRC (source code), EOC
  (executable object code), SVCP (verification cases & procedures), SVR (verification results), TRC (trace
  data), PR (problem report), SCI/SECI (configuration indexes), SAS (accomplishment summary),
  FHA/PSSA/SSA (safety assessment), PDI (parameter data item).
- **Change control:** once SGP-SCMP-001 is approved, every item becomes (or links to) a Problem Report / change
  request in the issue tracker. This file then keeps the index and the status.
- All items start with status **Open**, except those listed in §5 (status updates).

## 2. Work packages

| WP | Title | Summary |
|---|---|---|
| WP-01 | Compliance planning | Approve and maintain the plans and standards in `docs/compliance/`. |
| WP-02 | Safety assessment | FHA → PSSA → SSA, derived safety requirements, DAL rationale. |
| WP-03 | Requirements re-baseline | Controlled T2/HLR/LLR set that satisfies the requirements standard, with trace. |
| WP-04 | Design description | Architecture, LLR-level design, interfaces, data and control coupling. |
| WP-05 | Build & toolchain | Modern, reproducible, 16 KB-compatible build for API 36+. |
| WP-06 | Platform behaviour | Edge-to-edge, back navigation, state, startup, backup, permissions. |
| WP-07 | Architecture refactor | Modules, ViewModels, navigation, repositories, removal of raw SQL/dead code. |
| WP-08 | Data layer | Database correctness, catalog/climate data, migrations. |
| WP-09 | Canvas | Scale correctness, gestures, redesign, performance. |
| WP-10 | Other screens / UX | Dashboard, Creator, Encyclopedia, Settings, theme, accessibility. |
| WP-11 | Hardware features | Camera, light/storage/battery, location, measurement, capability checks. |
| WP-12 | Security | Key handling, export/import, audit log. |
| WP-13 | Verification | Tests, coverage, MC/DC, coupling analysis, device campaigns, trace tooling. |
| WP-14 | Tool qualification | Tool assessment and qualification per DO-330. |
| WP-15 | CM & SQA infrastructure | Baselines, PR process, CI, records, independence. |
| WP-16 | Roadmap features | FR-001 … FR-026 (future scope, each needs requirements first). |
| WP-17 | Documentation | Manual, ConOps, superseded documents, changelog. |
| WP-18 | Technical debt ledger | Items carried from the Dev Log. |
| WP-19 | Open decisions | Decisions that block items above. |

## 3. Register

### WP-01 — Compliance planning

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0101 | Review and approve SGP-CB-001 (compliance basis & deviations) | NEW | H | 0 | Project Owner approval recorded; revision baselined | CB |
| DW-0102 | Review and approve the PSAC (SGP-PSAC-001) | NEW | H | 0 | Approved; Annex A compliance table complete | PSAC |
| DW-0103 | Review and approve SDP, SVP, SCMP, SQAP | NEW | H | 0 | Each approved with a review record | SDP, SVP, SCMP, SQAP |
| DW-0104 | Review and approve the three standards (requirements, design, code) | NEW | H | 0 | Approved; detekt/ktlint configuration committed to match the code standard (DW-1502) | Standards |
| DW-0105 | Review and approve the system plans (SDCP, SAP, SVVP) | NEW | H | 0 | Approved | ARP4754A plans |
| DW-0106 | Decide on ARP4754A vs ARP4754B as the basis | NEW | L | 0 | Decision recorded in SGP-CB-001 | CB |
| DW-0107 | Keep plans current: re-review every plan at each phase transition | NEW | M | all | Plan review record per phase transition | Plans |
| DW-0108 | Write the Software Accomplishment Summary (SAS) at the end of the revamp | NEW | H | 10 | SAS lists deviations, open PRs, and the compliance status of each Annex A objective | SAS |

### WP-02 — Safety assessment (ARP4761A)

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0201 | Complete the system FHA from the preliminary FHA in SGP-SAP-001 §6 | NEW | H | 0–2 | All functions from the ConOps analysed; classifications reviewed | FHA |
| DW-0202 | PSSA: allocate safety requirements to design (data integrity, uncertainty display, fail-safe defaults) | NEW | H | 3 | Derived safety requirements in the SRS, flagged "SAF" | PSSA, SRS |
| DW-0203 | Common cause analysis (shared platform, shared catalog data, shared clock) | NEW | M | 3 | CCA report | CCA |
| DW-0204 | Feed every derived software requirement back to the safety assessment | NEW | M | 3–7 | Each derived requirement has a safety assessment disposition | PSSA |
| DW-0205 | SSA at the end: confirm the implemented system meets the FHA/PSSA | NEW | H | 10 | SSA report | SSA |
| DW-0206 | Security risk assessment (data at rest, backup, export) — optional DO-326A-style analysis | NEW | M | 8 | Threat list with mitigations traced to requirements | Security assessment |

### WP-03 — Requirements re-baseline

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0301 | Create the controlled requirements document(s) (system + software) in the format of SGP-SRS-STD-001, keeping existing IDs | GAP §4 | B | 0–3 | Every requirement has: statement, rationale, verification method, parent trace, derived flag, status | SRS, TRC |
| DW-0302 | Write requirements for untraced functionality: no-plant paths, auto-populate, colours/legend, ruler, change variety, "keep as-is", Settings destination | RSM §3 | H | 3 | Each behaviour traces to an HLR | SRS, TRC |
| DW-0303 | Resolve inconsistencies RQ-01 … RQ-10 | GAP §4.1 | H | 3 | Each resolved by a requirement change with rationale | SRS |
| DW-0304 | Rewrite non-verifiable wording with measurable criteria (GAP §4.2 list) | GAP §4.2 | H | 3 | Requirements review checklist passes for each | SRS |
| DW-0305 | Rewrite LLRs that prescribe the wrong APIs or embed design: LLR-UI-010-A/B/C, LLR-UI-020-A/B/C, LLR-UI-030-B/C, LLR-UI-040-C, LLR-UI-050-A/B/C, LLR-UI-060-A/B/C, LLR-UI-120-A/D, LLR-SEN-010-A, LLR-SEN-040-D/E, LLR-SEN-050-C, LLR-SEN-110-C, LLR-SEN-160-D, LLR-DAT-020-B, LLR-DAT-190-D | GAP §4.3 | H | 3 | Replaced by verifiable behavioural LLRs; APIs moved to the SDD | SRS, SDD |
| DW-0306 | Move process requirements (T2-GOV-*, HLR-GOV-*, LLR-GOV-*) to the SQAP and mark them Deleted | GAP §4.4 | L | 0 | Done | SRS, SQAP |
| DW-0307 | Add missing requirements: back navigation, backup/restore, migrations, performance/timing, plot bounds for moved plants, accessibility, text scaling, insets, time zones/DST, catalog data integrity | GAP §4.5 | H | 3 | Requirements written and reviewed | SRS |
| DW-0308 | Identify derived requirements and flag them | GAP §4.5 | H | 3 | Flag present; each sent to the safety assessment (DW-0204) | SRS, PSSA |
| DW-0309 | Write Parameter Data Item requirements for the catalog and climate files (format, ranges, referential integrity, provenance) | NEW | H | 4 | PDI specification exists | PDI |
| DW-0310 | Write user-modifiable-parameter requirements: range, default and behaviour at the boundary for every Setting | NEW | M | 3 | Requirements per setting | SRS |
| DW-0311 | Write deactivated-code requirements for tier gating (what is active per tier, and how deactivation is guaranteed) | NEW | M | 3 | Requirements + design (SDD §7) | SRS, SDD |
| DW-0312 | Requirements validation (ARP4754A): check T2 correctness/completeness against the ConOps | NEW | H | 3 | Validation matrix complete (SVVP §4) | Validation records |
| DW-0313 | Requirements for every decision outcome in WP-19 (e.g. measurement method, orientation) | REVAMP §5 | M | 3–7 | Requirements updated after each decision | SRS |

### WP-04 — Design description

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0401 | Write the Software Design Description (architecture, modules `:core`/`:data`/`:app`, LLR-level design) | NEW | H | 3 | SDD reviewed against the design standard | SDD |
| DW-0402 | Interface description replacing the IDD/ICD: UI↔ViewModel state/events, repository APIs, DB schema, file formats, platform APIs used | GAP §2.4 | H | 3 | Interfaces documented and traced | SDD |
| DW-0403 | Data coupling and control coupling description (inputs for the SVP §8.5 analysis) | NEW | H | 3 | Coupling tables per module boundary | SDD |
| DW-0404 | DO-332 analyses: class hierarchy/substitutability, dynamic dispatch, memory management, exception management | CB DEV-03 | H | 3–5 | Analysis records | SDD, SVR |
| DW-0405 | COTS usage-domain analysis: every AndroidX/Kotlin/SQLCipher function relied on | CB DEV-01 | H | 3 | Usage list with a verification approach per item | SDD, SVP |
| DW-0406 | Error-handling design (error codes, messages, recovery) replacing ERR-VAL-001…ERR-MEM-005 | GAP §2.4 | M | 3 | Error catalogue in the SDD; strings in `strings.xml` | SDD |

### WP-05 — Build & toolchain

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0501 | Upgrade AGP/Gradle/Kotlin to current versions; compileSdk/targetSdk 36+ | BLD-01 | B | 1 | Debug + release build succeed in current Android Studio | SECI |
| DW-0502 | Use `libs.versions.toml` for all plugins and dependencies | BLD-02 | B | 1 | No hardcoded versions in build scripts | SECI |
| DW-0503 | Compose compiler Gradle plugin; remove `composeOptions` | BLD-03 | B | 1 | Builds | SECI |
| DW-0504 | kapt → KSP; upgrade Room | BLD-04 | B | 1 | Builds; Room schema generated | SECI |
| DW-0505 | Upgrade all AndroidX/CameraX dependencies | BLD-06 | H | 1 | Versions recorded in the SECI | SECI |
| DW-0506 | Replace `android-database-sqlcipher` with `sqlcipher-android`; keep existing DBs readable | BLD-05 | B | 1 | Existing DB opens after upgrade (tested); APK Analyzer shows 16 KB alignment OK | SRC, SVR |
| DW-0507 | Add missing libraries (viewmodel-compose, navigation-compose, serialization, UI test) | BLD-07 | M | 3 | Builds | SECI |
| DW-0508 | Release build: R8 keep rules for the new SQLCipher package; test the minified APK | BLD-08 | H | 1 | Release APK passes the smoke test on the target | EOC, SVR |
| DW-0509 | Reproducible build: Gradle dependency verification (`verification-metadata.xml`), wrapper checksum, locked versions | NEW | H | 1 | Clean checkout builds a bit-identical DEX from the same inputs (or the differences are explained) | SECI, SCMP |
| DW-0510 | Stop tracking `.idea/` (except shared code style); rename the project | BLD-09 | L | 0 | Done | SCI |
| DW-0511 | versionCode policy: incremented per build given to testing | BLD-10 | L | 1 | Policy in the SCMP; applied | SCMP |
| DW-0512 | Rename the application ID away from `com.example` | REVAMP D-09 | M | 3 | New ID; migration note | SRC |

### WP-06 — Platform behaviour (Android 16 / Pixel 10 Pro)

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0601 | Edge-to-edge + insets on every screen; Material 3 window theme | PLT-01 | B | 2 | No content under system bars/cutout on the target (screenshots in SVR) | SRC, SVR |
| DW-0602 | Back navigation (predictive back) + unsaved-work confirmation on the canvas | PLT-02, HLR-UI-080 | B | 2–3 | Back never exits unexpectedly; test cases pass | SRC, SVCP |
| DW-0603 | State survives configuration change and process death (screen, plot, mode, zoom, undo, in-progress points) | PLT-03, T2-ENV-020 | H | 3 | "Don't keep activities" test passes | SRC, SVCP |
| DW-0604 | Orientation: implement decision D-04 | PLT-04 | H | 5 | Requirement + test for both orientations (or the lock) | SRS, SRC |
| DW-0605 | Backup/restore: exclude DB/key/config/audit files; handle key-loss exceptions with a controlled reset + message | PLT-06, SEC-02 | B | 2 | `bmgr` backup/restore test on the target passes without a crash | SRC, SVCP |
| DW-0606 | Move key generation and DB opening off the main thread; splash screen | PLT-05 | H | 2 | No main-thread DB/keystore work (StrictMode clean); cold start time measured | SRC, SVR |
| DW-0607 | Runtime permission flows (camera, location) with rationale and a denied-permission path | PLT-07 | M | 7 | Tests for grant/deny/"don't ask again" | SRC, SVCP |
| DW-0608 | Text sizing through the typography scale and density conversion; test at font scale 1.0/1.3/2.0 | PLT-09 | M | 5–6 | Screenshots at each scale in SVR | SRC, SVR |
| DW-0609 | Declare optional sensor features (`required=false`) | PLT-10 | L | 7 | Manifest updated | SRC |

### WP-07 — Architecture refactor

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0701 | Split `MainActivity.kt` into feature packages; split `AgriculturalIsolationEngine.kt` | ARC-01, ARC-10 | H | 3 | No file > ~400 lines without justification | SRC |
| DW-0702 | ViewModel per screen with `StateFlow` UI state; move business logic out of composables; remove or re-specify `StorageViewModel` | ARC-02, T2-PLT-030 | H | 3 | Composables contain no DB calls or rule logic | SRC, SDD |
| DW-0703 | Navigation Compose with typed routes | ARC-03, HLR-UI-010 | H | 3 | All destinations reachable; back stack tests | SRC |
| DW-0704 | Remove raw SQL (table/column guessing) in Dashboard and Creator; use DAOs | ARC-04 | H | 2–3 | No `sqlite_master` queries in the app | SRC |
| DW-0705 | Structured error handling: no unmanaged `CoroutineScope`; exceptions become UI error states (T2-ERR-010) | ARC-05 | B | 2 | Fault-injection test: DB write failure shows a message and doesn't crash | SRC, SVCP |
| DW-0706 | Remove dead code: `SgpExecutors`, `WorkspaceUiState`, `SeedDataset.starterSeeds` | ARC-06, RSM §3 | L | 3 | Removed; reverse trace clean | SRC |
| DW-0707 | `Application` class + dependency container | ARC-07 | M | 3 | Done | SRC |
| DW-0708 | `:core` pure Kotlin module (no Android imports); domain model independent of Room annotations | CB §4, T2-PLT-010 | H | 3 | `:core` compiles as a JVM module | SRC, SDD |
| DW-0709 | Flow-based DAOs and a reactive settings repository | ARC-08, ARC-09 | M | 3 | Screens update on data change without reload hacks | SRC |
| DW-0710 | Strings to `strings.xml` | ARC-12 | M | 6 | No user-facing literals in Kotlin (lint check) | SRC |
| DW-0711 | Remove `[NEW]/[FIXED]` history comments; keep rationale comments | ARC-11 | L | 10 | Done | SRC |
| DW-0712 | Inject a `Clock` into all time-dependent logic (germination, harvest, planting date) instead of `System.currentTimeMillis()` | NEW | H | 3 | Deterministic tests with a fixed clock; time zone/DST cases tested | SRC, SVCP |

### WP-08 — Data layer

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0801 | Tier switch must not violate the FK: upsert, delete only unreferenced catalog seeds, in one transaction, and report what was kept | DAT-01, T2-DAT-200 | B | 2 | Test: plant a catalog seed, switch tier both ways, no crash, node still valid | SRC, SVCP |
| DW-0802 | Transactional multi-step writes (undo/redo restore, tier switch, auto-populate) | DAT-02, HLR-DAT-020 | H | 2 | `@Transaction` methods; fault-injection test | SRC, SVCP |
| DW-0803 | Encyclopedia delete for custom varieties, blocked with a message when planted | DAT-04, T2-DAT-060 | H | 4 | Tests for both cases | SRC, SVCP |
| DW-0804 | Duplicate variety code: validation error instead of silent ignore | DAT-03 | H | 4 | Test | SRC, SVCP |
| DW-0805 | Plot rename / resize (with out-of-bounds check, T2-VAL-017) / delete with confirmation | DAT-05, T2-FUN-070 | H | 4 | Tests incl. resize rejection | SRC, SVCP |
| DW-0806 | Export the Room schema; migration tests from every shipped version; document the minimum supported schema | DAT-06 | M | 4 | `MigrationTestHelper` tests pass | SRC, SVCP |
| DW-0807 | Default active variety: last used (persisted) or none; remove the non-existent `SOL-LYC` default | DAT-07 | M | 4 | Test | SRC |
| DW-0808 | Plan B options computed at runtime (fast-track, catch-crop by remaining season) | DAT-08, T2-FUN-080 | M | 4 | Algorithm specified in the LLRs; unit tests | SRS, SRC, SVCP |
| DW-0809 | Species-name table so companion names resolve in every tier | DAT-09 | M | 4 | No raw codes shown in any tier (test) | PDI, SRC |
| DW-0810 | Real bundled ZIP → zone/frost dataset with documented provenance | DAT-10, T2-DAT-080 | M | 7 | PDI verified against its source (sample audit) | PDI |
| DW-0811 | Custom seed input validation: numeric ranges for every field, no silent defaults | HLR-DAT-120, **NEW** | M | 4 | Boundary tests | SRC, SVCP |
| DW-0812 | Key material from `SecureRandom.nextBytes` | DAT-11 | L | 2 | Done | SRC |
| DW-0813 | Catalog data verification: provenance for spacing/germination/harvest values; automated integrity checks (field count, ranges, unique codes, companion references) at build time | **NEW** | H | 4 | Build fails on invalid catalog data; provenance recorded | PDI, SVR |
| DW-0814 | Floating-point precision analysis for the unit conversion and coordinate maths (Float storage, meters ↔ inches round trips) | **NEW** | M | 4 | Analysis record; tolerances in the LLRs | SVR |
| DW-0815 | Wire up the ownership/role metadata, or delete the columns (decide within T2-DAT-120/140 scope) | DAT-12 | L | 8 | Decision + implementation | SRS, SRC |

### WP-09 — Canvas

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-0901 | Uniform scale (single px-per-meter), letterboxed plot, radii correct in both axes | CAN-01, T2-FUN-050 | H | 5 | Test: circle drawn with equal pixel radius in X and Y for any plot aspect ratio | SRC, SVCP |
| DW-0902 | **NEW** Drag-move and every placement path must keep the plant inside the plot (clamp or reject) | **NEW**, T2-VAL-020/070 | H | 2 | Test: drag beyond the edge is rejected | SRC, SVCP |
| DW-0903 | Ruler follows zoom/pan with "nice" tick steps | CAN-03, HLR-ZOOM-050 | H | 5 | Test at several zoom levels | SRC, SVCP |
| DW-0904 | Undo/redo snapshot duplication fix; undo/redo as a ViewModel operation | CAN-04, T2-VAL-030 | H | 2 | Test: place → undo → redo gives exactly one plant | SRC, SVCP |
| DW-0905 | Phone layout: top bar + bottom toolbar + variety bottom sheet; landscape side rail | CAN-05 | H | 5 | Usability check on the target; UI tests | SRC, SVR |
| DW-0906 | Pinch-zoom + two-finger pan; keep +/- buttons; revise T2-INT-080 if the separate mode goes away | CAN-06 | M | 5 | Gesture tests; requirement updated | SRS, SRC |
| DW-0907 | Performance: seed map lookup, cached weed-mask path, reused Paint/TextMeasurer; 500+ plants at display refresh rate | CAN-07 | M | 5 | Macrobenchmark / frame-timing result on the target recorded | SRC, SVR |
| DW-0908 | All distances go through `DistanceFormatter` (snackbars, dialogs, dashboard, path width input) | CAN-08, T2-CFG-100 | M | 5 | No hardcoded unit suffixes (grep check + test in inches mode) | SRC, SVCP |
| DW-0909 | Hit tolerance in dp converted to meters | CAN-02 | H | 5 | Test on small (1 m) and large (100 m) plots | SRC, SVCP |
| DW-0910 | Visible mode selector, first-run hints; decision D-06 (tap vs double-tap) | CAN-09 | M | 5 | Implemented per decision | SRC |
| DW-0911 | Gesture exclusion near screen edges in draw modes | CAN-10 | M | 5 | Test with gesture navigation | SRC |
| DW-0912 | Themed canvas palette + high-contrast outdoor option | CAN-11 | M | 6 | Screenshots light/dark/high-contrast | SRC |
| DW-0913 | Grid spacing tied to the ruler step | CAN-12 | L | 5 | Done | SRC |
| DW-0914 | Replace deprecated Compose APIs (`Divider`, `Icons.Default.ArrowBack`, …) | CAN-13 | L | 1–6 | No deprecation warnings | SRC |
| DW-0915 | Canvas bounds on node count, path points and polygon points (memory bounds, DO-332) | CB DEV-03, **NEW** | M | 5 | Limits specified and tested | SRS, SRC |

### WP-10 — Other screens / UX

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-1001 | Remove the debug storage-vault card from the product UI (extraneous code) | UI-01 | H | 2 | Not reachable in release builds | SRC |
| DW-1002 | Dashboard redesign (summary cards, units, overdue badge, overflow actions) | UI-02 | M | 6 | Done | SRC |
| DW-1003 | Creator: plain labels, hide the IMU option until D-03, optional ZIP, keyboard-safe layout | UI-03 | M | 6 | Done | SRC |
| DW-1004 | Encyclopedia: filters, full-screen editor, companion multi-select, unit-aware input | UI-04 | M | 6 | Done | SRC |
| DW-1005 | Settings: hide unconnected sections, range sliders, tier-switch progress, About/data section | UI-05 | M | 6 | Done | SRC |
| DW-1006 | Accessibility: semantics, 48 dp targets, colour-independent plant markers, TalkBack pass | UI-06 | M | 6 | Accessibility Scanner clean on the target | SRC, SVR |
| DW-1007 | Adaptive + themed app icon | UI-07 | L | 6 | Done | SRC |
| DW-1008 | One Material 3 theme (light/dark), decision D-05 | PLT-08 | M | 6 | Done | SRC |

### WP-11 — Hardware features

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-1101 | Camera screen + permission + photo as canvas background (+ Photo Picker import) | HW-01, T2-FUN-010 | H | 7 | Requirements-based tests on the target | SRS, SRC, SVCP |
| DW-1102 | Image processing: exact downscale, EXIF rotation, compression; failure behaviour consistent with T2-CON-040/T2-ERR-030 | HW-02 | M | 7 | Tests with oversized/rotated images | SRC, SVCP |
| DW-1103 | Light, storage and battery guards on the camera screen using the Settings thresholds; override logging (T2-SAF-*) | HW-03 | M | 7 | Tests incl. override record in the audit log | SRC, SVCP |
| DW-1104 | ZIP entry + lookup; optional GPS with the accuracy gate (T2-FUN-090, T2-ENV-025) | HW-05 | M | 7 | Tests with valid/invalid ZIP; mocked location on the emulator + real fix on the target | SRC, SVCP |
| DW-1105 | Plot measurement feature per decision D-03; remove the unsound IMU engine if dropped | HW-04 | H | 7 | Decision implemented; requirements updated | SRS, SRC |
| DW-1106 | Capability assessment and degraded mode (T2-CON-050/060) | RSM | M | 7 | Test on a device profile without the sensor | SRC, SVCP |
| DW-1107 | Planting windows and zone-aware warnings (T2-DAT-090/100, FR-014) | RSM | M | 7 | Tests with fixed climate data | SRC, SVCP |
| DW-1108 | Perspective correction / fiducial corner mapping (T2-PRE-040, HLR-SEN-120/130), or delete the requirement | RSM | L | 7 | Decision + implementation | SRS |
| DW-1109 | Germination condition data (T2-DAT-050) and watering strategies (T2-DAT-040) in the catalog | RSM | L | 7 | PDI extended + displayed | PDI, SRC |
| DW-1110 | Editable planting date (T2-FUN-030) with recalculation (T2-INT-020) | RSM | M | 4 | Tests | SRC, SVCP |

### WP-12 — Security

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-1201 | Delete `security/SecurityKeyManager.kt` (CBC, hardcoded fallback) | SEC-01 | H | 0 | File removed | SRC |
| DW-1202 | Key-loss handling (see DW-0605) and a user-visible reset procedure | SEC-02 | B | 2 | Tested | SRC, SVCP |
| DW-1203 | Export/import design: schema version, validation, signature scheme verifiable on another device (asymmetric), SAF file access, merge rules | SEC-03, T2-DAT-110…140 | M | 8 | Requirements + implementation + tests incl. tampered files | SRS, SRC, SVCP |
| DW-1204 | Audit log: cached last hash, rotation, log the events T2-SAF-040/T2-SEC-030 require, and a verification function | SEC-04 | M | 8 | Tests incl. tamper detection | SRC, SVCP |
| DW-1205 | Close DEBT-SEC-001 as "accepted: TEE/software fallback" | SEC-05 | L | 0 | Recorded | Debt |

### WP-13 — Verification

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-1301 | Delete the test file that is a copy of `StorageViewModel` | TST-01 | B | 0 | Unit tests compile | SRC |
| DW-1302 | Update instrumented tests for the new SQLCipher; add migration tests | TST-02 | H | 1–4 | Green on the target | SVCP, SVR |
| DW-1303 | Requirements-based test cases + procedures for every HLR and LLR (normal range + robustness), per SVP §7 | GAP §2.1 | B | 3–9 | 100% HLR/LLR coverage by tests (or justified analysis/review) | SVCP, TRC |
| DW-1304 | Structural coverage: statement + decision (JaCoCo) and MC/DC worksheets for multi-condition decisions; resolve every coverage gap | CB DEV-07 | B | 9 | Coverage report + gap resolutions | SVR |
| DW-1305 | Data coupling and control coupling analysis | SVP §8.5 | H | 9 | Analysis record | SVR |
| DW-1306 | Trace tooling: requirement IDs in test names/annotations; generated bidirectional trace (T2↔HLR↔LLR↔code↔test) | GAP §4.6 | H | 3 | Trace report generated in CI; no orphans | TRC |
| DW-1307 | Source-to-bytecode traceability analysis for a sample of `:core` (compiler-added code) | CB DEV-02 | H | 9 | Analysis record | SVR |
| DW-1308 | Reviews of requirements, design, code and test cases with checklists (SVP §4); independence per DEV-04 | SVP §4 | B | 3–9 | Review records for every item | Review records |
| DW-1309 | Target test campaigns on the physical Pixel 10 Pro (SECI recorded per campaign) | SVP §6 | H | 9 | SVR per campaign | SVR, SECI |
| DW-1310 | Emulator/target equivalence analysis (which tests can take credit on the emulator) | SVP §6 | M | 9 | Analysis record | SVP |
| DW-1311 | Robustness tests: invalid inputs, DB failures, low storage, permission denial, process death, clock change | SVP §7.3 | H | 9 | Tests pass | SVCP, SVR |
| DW-1312 | Canvas ViewModel unit tests and Compose UI tests for the §1.1 checklist in the REVAMP plan | TST-03 | H | 3–9 | Green | SVCP, SVR |
| DW-1313 | Unit tests for the geometry helpers after moving them to `:core` | TST-04 | M | 3 | Green with full coverage | SVCP, SVR |
| DW-1314 | Regression test policy + CI (unit tests on every change; device suite per baseline) | NEW | H | 1 | CI configured | SCMP, SVP |
| DW-1315 | Verification of verification: review of test procedures and results (Table A-7 obj. 1–2) | NEW | H | 9 | Review records | Review records |

### WP-14 — Tool qualification

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-1401 | Complete the tool assessment table (SGP-TQP-001 §3) for the final toolchain | NEW | H | 1 | Every tool has a criteria/TQL decision | TQP |
| DW-1402 | TQL-5 qualification for JaCoCo, if coverage credit is taken from it | NEW | M | 9 | Tool Operational Requirements + qualification tests | TQ data |
| DW-1403 | TQL-5 qualification for the trace-report generator, if trace credit is taken from it | NEW | M | 9 | As above | TQ data |
| DW-1404 | TQL-5 for detekt/ktlint rule checks, if code-standard compliance credit is taken | NEW | L | 9 | As above | TQ data |
| DW-1405 | Record the compiler/R8/ART mitigation evidence (DEV-02) | CB DEV-02 | H | 9 | See DW-1307 | SVR |

### WP-15 — CM & SQA infrastructure

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-1501 | Issue tracker set up as the Problem Report system (templates, labels, states per SCMP §7) | NEW | H | 0 | Templates committed | SCMP |
| DW-1502 | Branch protection + PR template with review checklist; CI gates (build, unit tests, lint, detekt) | NEW | H | 1 | Configured | SCMP |
| DW-1503 | Baseline naming and tagging per SCMP §5; first formal baseline after Phase 1 | NEW | H | 1 | Tag exists; SCI generated | SCI |
| DW-1504 | SECI and SCI templates, filled per baseline | NEW | H | 1 | Filled | SECI, SCI |
| DW-1505 | Move withdrawn documents to `docs/archive/` at the first baseline | GAP §5 | L | 1 | Done | SCM record |
| DW-1506 | SQA audit schedule and records (SQAP §5) | NEW | H | 0– | Records per phase | SQA records |
| DW-1507 | Obtain an independent reviewer (DEV-04) | CB DEV-04 | H | 0– | Named person, with a recorded agreement | SQAP |

### WP-16 — Roadmap features (future scope)

Each item needs T2/HLR requirements and a safety assessment disposition before implementation (SDP §5).
Details are in `Android App/FEATURE_ROADMAP.md`.

| ID | Roadmap item | Status there | Phase |
|---|---|---|---|
| DW-1601 | FR-001 Polygon area-select | Implemented, awaiting confirmation → needs requirements/tests (DW-0302) | 3 |
| DW-1602 | FR-002 Polygon plot shapes (major data-model change) | Not started | after 10 |
| DW-1603 | FR-003 Slope configuration | Not started | after 10 |
| DW-1604 | FR-004 Seasonal flooding zones | Not started | after 10 |
| DW-1605 | FR-005 Sun/shade zones | Not started | after 10 |
| DW-1606 | FR-006 Sunlight barriers + shading estimate | Not started | after 10 |
| DW-1607 | FR-007 Historical sunlight (needs FR-026) | Not started | after 10 |
| DW-1608 | FR-008 Companion names | Implemented, awaiting confirmation (see DW-0809) | 4 |
| DW-1609 | FR-009 Interplanting guilds | Not started | after 10 |
| DW-1610 | FR-010 Grey out incompatible varieties | Not started | after 10 |
| DW-1611 | FR-011 Garden harmony report | Not started | after 10 |
| DW-1612 | FR-012 Companion toggle Pro-only | Implemented, awaiting confirmation; resolve RQ-07 | 3 |
| DW-1613 | FR-013 Soil test input | Not started | after 10 |
| DW-1614 | FR-014 Zone-aware recommendations | Not started (see DW-1107) | 7 |
| DW-1615 | FR-015 Recommend & auto-populate | Not started | after 10 |
| DW-1616 | FR-016 Homestead starter list | Not started | after 10 |
| DW-1617 | FR-017 Fertilizing plan | Not started | after 10 |
| DW-1618 | FR-018 Pest-management plan | Not started | after 10 |
| DW-1619 | FR-019 Rain-aware reminders (needs FR-026; notifications permission) | Not started | after 10 |
| DW-1620 | FR-020 Nutrition guide (USDA data; refresh needs FR-026) | Not started | after 10 |
| DW-1621 | FR-021 Recipes | Not started | after 10 |
| DW-1622 | FR-022 Yield per plant | Not started | after 10 |
| DW-1623 | FR-023 Vendor links (placeholder) | Not started | after 10 |
| DW-1624 | FR-024 Vendor targeting | Not started | after 10 |
| DW-1625 | FR-025 Tier feature flags | Implemented, awaiting confirmation; deactivated-code design (DW-0311) | 3 |
| DW-1626 | FR-026 Toggleable network layer — **conflicts with T2-CON-010 (zero network dependencies)**; needs a system requirement change + safety/security assessment before any work | Blocked on decision | after 10 |

### WP-17 — Documentation

| ID | Item | Source | Sev | Phase | Done when | LCD |
|---|---|---|---|---|---|---|
| DW-1701 | Revise the ConOps per SGP-GAP-001 §3.1 | GAP | M | 0–3 | Reviewed | ConOps |
| DW-1702 | Rewrite the test environment log as SECI + emulator/target analysis for the Pixel 10 Pro | GAP | M | 1 | Done | SECI |
| DW-1703 | Fix README_v20.20 claim about the deleted security file; fix the journal's SQLCipher statement | GAP | L | 0 | Done | — |
| DW-1704 | User manual update for the new UI (screenshots from the target) | REVAMP Ph.10 | M | 10 | Done | Manual |
| DW-1705 | CHANGELOG from this point on | NEW | L | 1 | File exists and is maintained | SCM record |
| DW-1706 | Update FEATURE_ROADMAP statuses and link each FR to its requirements | NEW | L | 3 | Done | — |
| DW-1707 | `docs/DEVICE_SETUP.md`: Pixel 10 Pro AVD (4 KB + 16 KB images) and physical device setup | REVAMP Ph.0 | M | 0 | Done | SECI |

### WP-18 — Technical debt ledger (carried over)

| ID | Debt item | Disposition |
|---|---|---|
| DW-1801 | DEBT-SEC-001 software keystore fallback | Accept the fallback (DW-1205) |
| DW-1802 | DEBT-VM-002 thread allocation | Superseded by DW-0702/DW-0706 (Room executors + ViewModels) |
| DW-1803 | DEBT-DB-003 destructive migrations | Largely closed (real migrations exist); remaining: DW-0806 |
| DW-1804 | DEBT-SEC-005 HMAC hardcoded key fallback | Fixed in `RealSecurityKeyManager`; close after DW-1201 and a verification test |

### WP-19 — Open decisions

| ID | Decision | Blocks | Recommendation (REVAMP §5) |
|---|---|---|---|
| DW-1901 | D-01 Documentation approach | — | **Decided:** keep DO-178C/ARP4754A DAL A, with the rewritten plans (this document set) |
| DW-1902 | D-02 minSdk | DW-0501 | Keep 29 |
| DW-1903 | D-03 Measurement method | DW-1105, T2-FUN-025, T2-PRE-*, HLR-SEN-050…150 | Manual now; ARCore or reference-object later |
| DW-1904 | D-04 Orientation | DW-0604 | Support both |
| DW-1905 | D-05 Visual identity | DW-1008 | Brand palette + high contrast |
| DW-1906 | D-06 Placement gesture | DW-0910 | Single tap |
| DW-1907 | D-07 Backup policy | DW-0605 | Exclude encrypted data; explicit export later |
| DW-1908 | D-08 Tier gating model | DW-0311 | Keep for development |
| DW-1909 | D-09 Application ID | DW-0512 | Rename in Phase 3 |
| DW-1910 | D-10 Network toggle (FR-026) vs T2-CON-010 | DW-1626 | Decide after Phase 7 |
| DW-1911 | ARP4754A vs ARP4754B basis | DW-0106 | Keep 4754A; review 4754B changes |
| DW-1912 | Independent reviewer arrangement | DW-1507 | Needed before any Level A credit is claimed |

## 4. Counts (Revision A)

| WP | Items |
|---|---|
| WP-01 … WP-15, WP-17 … WP-19 | 169 |
| WP-16 roadmap | 26 |
| **Total** | **195** |

## 5. Status updates

| Date | Item(s) | New status | Note |
|---|---|---|---|
| 2026-09-27 | DW-0101 … DW-0105 | In progress — provisionally adopted | Project Owner directed work to proceed under all Revision A plans and standards; the formal review is deferred. |
| 2026-09-27 | DW-1901 (D-01) | Closed | Keep DO-178C/ARP4754A DAL A. |
| 2026-09-27 | DW-0301 | In progress | T2 level done: `docs/requirements/SGP-SYS-REQ-001_System_Requirements_T2.md` (Proposed). HLR and LLR levels remain. |
| 2026-09-27 | DW-0302, DW-0303, DW-0304, DW-0306, DW-0307 | In progress | Done at T2 level (new T2 for untraced functions, conflicts RQ-01…RQ-10 resolved or proposed, measurable wording, GOV deleted, missing requirements added). HLR/LLR level remains. 31 values await confirmation (`[TBC-01…31]`, §15 of SGP-SYS-REQ-001). |
| 2026-09-27 | DW-0301 | In progress | HLR level done: `docs/requirements/SGP-SW-HLR-001_Software_High_Level_Requirements.md` (140 HLRs, new area codes). Per Project Owner direction, the HLRs are rewritten from scratch; the v20.18 HLR/LLR IDs are retired, and traceability is kept **only** T2 → new HLR (full coverage, generated table in §21–22). 10 HLR-level values await confirmation (H-TBC-01…10). LLRs remain. |
| 2026-09-27 | DW-0305 | Superseded | The old LLRs will not be patched. The LLRs are rewritten from scratch under the new HLRs. |
| 2026-09-27 | DW-1306 | Changed | Trace scope per Project Owner: T2 ↔ HLR is mandatory; HLR ↔ LLR ↔ code/test is to be established for the new LLRs only (no trace to retired IDs). |
| 2026-09-27 | DW-0301 | Implemented (awaiting review) | LLR level done: `docs/requirements/SGP-SW-LLR-001_Software_Low_Level_Requirements.md` (224 LLRs by design component; generated HLR → LLR trace, all active HLRs covered). The requirements baseline T2 → HLR → LLR is complete, except Future/Suspended features. Catalog value ranges corrected in T2/HLR to match real bundled data (germination ≤ 365 d, harvest ≤ 3650 d). |
| 2026-09-27 | DW-0401 | In progress | LLR §1 fixes the component structure (`:core`/`:data`/`:app`) that the design description will elaborate. |
