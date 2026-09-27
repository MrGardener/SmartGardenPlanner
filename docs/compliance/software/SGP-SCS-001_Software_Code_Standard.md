# SGP-SCS-001 — Software Code Standard (Kotlin)

| Field | Value |
|---|---|
| Document ID | SGP-SCS-001 |
| Revision | A (Draft) |
| DO-178C reference | §11.8 |
| Enforcement | Android Lint + detekt + ktlint configurations under CM (DW-1502), plus code review (SVP §4.3). Rules marked **(R)** are review-only. |
| Control category | CC1 |

Base style: the official Kotlin coding conventions (`kotlin.code.style=official`). Rules below add to or
override the base style. A deviation from a rule needs a `@Suppress` with a comment giving the rule ID and
the justification, and it is checked in review.

## 1. Complexity

| ID | Rule |
|---|---|
| R-CPX-1 | Cyclomatic complexity ≤ 10 per function (detekt `CyclomaticComplexMethod`). |
| R-CPX-2 | Function length ≤ 60 lines; file length ≤ 400 lines (Composable files ≤ 300). |
| R-CPX-3 | **At most 3 conditions per decision** (`&&`/`||` operands). Split into named booleans or functions otherwise. This keeps MC/DC analysis tractable (SVP §8.3). |
| R-CPX-4 | Nesting depth ≤ 4. |
| R-CPX-5 | No recursion in `:core` unless its depth is bounded and documented. |

## 2. Safety of expressions

| ID | Rule |
|---|---|
| R-EXP-1 | No `!!`. Use safe calls with explicit handling. |
| R-EXP-2 | No side effects inside conditions (no assignments, no mutating calls). |
| R-EXP-3 | No `==` / `!=` between `Float`/`Double` values except against exact sentinels defined as constants. Use tolerance comparisons from `:core` (R-11 of the requirements standard). |
| R-EXP-4 | `when` on enums and sealed types must be exhaustive, without `else` (so a new case causes a compile error). |
| R-EXP-5 | No implicit numeric narrowing surprises: conversions (`toInt()`, `roundToInt()`) state the rounding intent. Integer division that truncates needs a comment. |
| R-EXP-6 | No magic numbers in logic. Use named constants with units, or Settings. |

## 3. Types and state

| ID | Rule |
|---|---|
| R-TYP-1 | Public APIs declare explicit types. |
| R-TYP-2 | Prefer `val` and immutable collections. Mutable state is private and exposed read-only. |
| R-TYP-3 | No `lateinit` in `:core`. Allowed in `:app` only for framework-injected fields. |
| R-TYP-4 | Distances in `:core` use meters. Pixel values appear only in `:app`, in the canvas transform. |
| R-TYP-5 | No platform types leaking from Java APIs without a null check at the boundary. |

## 4. Concurrency

| ID | Rule |
|---|---|
| R-CON-1 | No `GlobalScope`; no `CoroutineScope(...)` created in UI code; no `runBlocking` outside tests. |
| R-CON-2 | Coroutine exception handling is explicit. A `catch` must not swallow `CancellationException`. |
| R-CON-3 | No blocking I/O on the main thread (StrictMode enabled in debug builds). |

## 5. Error handling

| ID | Rule |
|---|---|
| R-ERR-1 | No empty `catch`, and no `catch (e: Throwable)` except at a documented top-level boundary. |
| R-ERR-2 | Do not catch `OutOfMemoryError` for flow control. Avoid it by design (bounded decoding). |
| R-ERR-3 | Every caught exception is mapped to a documented error state or logged with context, never ignored. |

## 6. Compose / Android

| ID | Rule |
|---|---|
| R-UI-1 | Composables are stateless where practical (state hoisting). No DB/repository access from composables. |
| R-UI-2 | User-visible strings come from `strings.xml`. |
| R-UI-3 | Colours and typography come from the theme, except canvas palettes defined in the theme module. |
| R-UI-4 | Touch targets ≥ 48 dp. Content descriptions on icons and interactive canvas elements (R). |
| R-UI-5 | No deprecated APIs (Lint `Deprecation` as an error in `:app` after Phase 1). |

## 7. Security & privacy

| ID | Rule |
|---|---|
| R-SEC-1 | No network APIs, no analytics/telemetry libraries (T2-CON-010/020). Lint check on the merged manifest: no `INTERNET` permission. |
| R-SEC-2 | No secrets, keys or passphrases in source or logs. No logging of user data at INFO level or above in release builds. |
| R-SEC-3 | Crypto only through the project `security` package (Keystore-backed). No custom cryptographic algorithms. |

## 8. Traceability and comments

| ID | Rule |
|---|---|
| R-TRC-1 | Every non-private function in `:core`/`:data` has KDoc with `@implements LLR-…`. |
| R-TRC-2 | Tests carry `@verifies <ID>`. |
| R-TRC-3 | Comments explain *why*. Change history belongs in git, not in comments (no `[NEW]/[FIXED]` tags). **(R)** |
| R-TRC-4 | No commented-out code. No dead code. |
