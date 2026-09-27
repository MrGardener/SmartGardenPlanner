# SGP-SDS-001 — Software Design Standard

| Field | Value |
|---|---|
| Document ID | SGP-SDS-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| DO-178C reference | §11.7; DO-332 design considerations |
| Control category | CC1 |

## 1. Architecture rules

| Rule | Text |
|---|---|
| D-01 | Three modules with one-way dependencies: `:app → :data → :core`. `:core` has **no** Android, AndroidX or Room dependency, and no I/O. |
| D-02 | All domain rules live in `:core` as pure functions or immutable classes: placement validation, geometry, units, scheduling, Plan B, auto-populate, irrigation, weed-mask geometry (as a platform-neutral polygon/circle model), settings range validation. |
| D-03 | `:data` exposes repositories with suspend/Flow APIs. DAOs, Room entities and SQLCipher never leak into `:app` types. |
| D-04 | UI follows unidirectional data flow: Composable → event → ViewModel → repository/use case → new immutable `UiState` (StateFlow) → Composable. Composables contain no business rules and no persistence calls. |
| D-05 | Navigation uses Navigation Compose with typed routes. Arguments are IDs only, never whole objects. |
| D-06 | State that must survive process death is saved via `SavedStateHandle` / `rememberSaveable`, and is limited to IDs and small primitives. Everything else is reloaded from the DB. |
| D-07 | Concurrency: only structured concurrency (`viewModelScope`, `lifecycleScope`, repository suspend functions). No `GlobalScope`, no unmanaged `CoroutineScope(...)`, no custom thread pools unless justified in the SDD. |
| D-08 | Every multi-step database mutation is one `@Transaction`. |
| D-09 | Time is read only through an injected `Clock`. Randomness is read only through an injected source. |
| D-10 | Errors: repositories return typed results or throw documented exceptions that the ViewModel maps to error states with IDs from the error catalogue. No silent `catch (e: Exception) {}`. |
| D-11 | Every collection that grows with user input has a specified upper bound (nodes per plot, points per path/polygon, undo depth, catalog size), enforced in `:core`. |
| D-12 | Platform services (camera, sensors, location, battery, storage) sit behind interfaces owned by the project, so they can be faked in tests and their failure modes are explicit. |

## 2. Object-oriented rules (DO-332)

| Rule | Text |
|---|---|
| OO-01 | Inheritance depth ≤ 2 for project classes (excluding framework base classes such as `ViewModel`). Prefer composition, sealed interfaces and data classes. |
| OO-02 | Any subtype used through a supertype reference must satisfy LSP. Sealed hierarchies are verified by exhaustive `when` tests. |
| OO-03 | No reflection, no dynamic class loading, no runtime code generation in project code. |
| OO-04 | Exceptions: each public function in `:core`/`:data` documents what it throws. `:core` prefers `Result`-style returns for expected failures. |
| OO-05 | Memory: no bitmap is decoded without target dimensions. No unbounded caches. Objects are not allocated per frame inside draw lambdas without justification. |
| OO-06 | Generics and inline functions: allowed. Their bytecode expansion is covered by the compiler-added code analysis (SVP §8.4). |

## 3. Design description content (SDD)

The SDD must contain:
1. Architecture overview (modules, components, responsibilities, dependency rules).
2. Per component: purpose, LLRs, interfaces (public API with types and units), states and transitions,
   error handling.
3. Data model: DB schema (tables, keys, FKs with ON DELETE rules, indexes), migrations, file formats (PDI,
   export).
4. Threading/coroutine model: which dispatcher runs what, and which operations are main-safe.
5. **Data coupling table:** producer component, consumer component, data item, type/unit/range.
6. **Control coupling table:** caller, callee, trigger, conditions.
7. Deactivated code design (§7).
8. Error catalogue (IDs, conditions, user text reference, recovery).
9. COTS usage domain (DW-0405).
10. Resource bounds (D-11) and performance budgets.

## 4. Naming and units in the design

Variables and properties carry units in their names where ambiguous: `lengthM`, `radiusM`, `radiusPx`,
`epochMillisUtc`, `zoomScale`. The conversion between plot meters and screen pixels happens in exactly one
component (the canvas transform).

## 5. Canvas transform rule

One transform object holds the plot-to-screen mapping: uniform scale (px per meter), letterbox offset,
zoom, pan, and optional display rotation. Every drawing, hit test, ruler and gesture conversion uses it.
X and Y never use different scales (fixes REVAMP CAN-01).

## 6. Persistence rules

- Foreign keys: plot → nodes/paths `CASCADE`; seed → nodes `RESTRICT`. Any operation that deletes seeds
  must first handle referenced seeds (DW-0801).
- Schema is exported to `schemas/`. Every schema change ships with a migration and a migration test.
- Encryption key handling states the behaviour on key loss (DW-0605).

## 7. Deactivated code (tier gating)

- Gating decisions are made in one place (`FeatureFlags` in `:core`) as a pure function of the
  configuration. UI entry points consult it, and so do the enforcing ViewModels/use cases (defence in depth,
  as FR-012 already does).
- For each gated feature, the SDD lists the entry points, and the tests show they are unreachable when
  inactive.
- Coverage analysis reports deactivated code per tier configuration.
