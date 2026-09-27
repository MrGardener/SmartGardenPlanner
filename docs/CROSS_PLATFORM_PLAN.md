# Planning on a computer and a phone — approach and plan

| | |
|---|---|
| Document | SGP-XPL-001 |
| Status | Proposal for owner decision, 2026-09-27 |
| Requirement | T2-PLT-040 (new), T2-PLT-020, T2-PLT-030; HLR-PORT-030 [Future] |

## 1. What you asked for

- Plan on a computer (Windows, macOS, Linux, ChromeOS), check and change the plan on the phone, and back.
- Portable, with nothing to install.
- A file that moves between the two.

Your idea was a web page that produces a JSON file. That is the right foundation. The plan below keeps it and
adds one thing: the computer planner uses **the same planning code** as the phone, not a rewrite.

## 2. Recommendation

### 2.1 The file (done)

Both apps save and open a **Smart Garden plan file** (`.sgp.json`), a plain JSON file that is documented and
versioned (`PLAN_FILE_FORMAT.md`). The Android app already does this:

- Plot list → **Open plan file** loads a file as new plots.
- Plot list → **Save**, or layout menu → **Save this plot as a file**, writes one.

The file moves between devices however you like: USB cable, Google Drive, OneDrive, iCloud Drive, Dropbox, email,
or a USB stick. On Android, the system file picker opens files straight from any of these.

### 2.2 The computer planner: one web page, no install

A **web app that runs entirely in the browser**, delivered two ways:

| | How the user gets it | Works offline | Install? |
|---|---|---|---|
| **Portable** | A single `smart-garden-planner.html` file, e.g. on a USB stick or in a downloads folder. Double-click to open. | Yes, always | No |
| **Hosted** | The same page on a web address (e.g. GitHub Pages) | Yes, after the first visit | No (browsers offer an optional "Install app"; not needed) |

It works in current Chrome, Edge, Firefox and Safari, on Windows, macOS, Linux, ChromeOS and Android. There is no
account and no server. The plan lives in the files the user saves.

- **Opening and saving:** Chrome and Edge use the File System Access API, so "Save" rewrites the same file.
  Firefox and Safari fall back to "download a copy".
- **Crash safety:** an automatic draft copy is kept in the browser's own storage.

### 2.3 Same rules everywhere: Kotlin Multiplatform

The planning rules (spacing, companions, plan-for-me, sun and shade, harmony, recommendations, care plans,
nutrition, and the file reader/writer) live in `core/` as plain Kotlin. With **Kotlin Multiplatform**, that same
source compiles to:

- JVM/Android bytecode for the phone app (as today), and
- JavaScript for the web page.

So both apps give **identical results**, and each rule is written, reviewed and tested once. The same test
vectors run on both targets. This matters for the DO-178C/DAL A approach: one implementation to verify instead
of two that could drift apart (T2-PLT-030).

### 2.4 The web user interface

Two choices:

- **A. HTML/SVG interface (recommended for portability).** A light interface in HTML and SVG, written in Kotlin/JS
  or TypeScript, calling the shared Kotlin core. It can be bundled into one self-contained `.html` file that works
  when opened from disk.
- **B. Compose Multiplatform for Web.** Reuses the phone's Compose screens, compiled to WebAssembly. It shares
  more code, but browsers won't load WebAssembly from a local file. It would only work hosted, or with a small
  local web server, which breaks "portable, nothing to install".

**Recommendation: A**, with B kept in view as Compose for Web matures. Screens are the smaller, easier part to
duplicate. The rules are what must not be duplicated, and they are shared in both options.

### 2.5 Alternatives considered

| Option | Why not |
|---|---|
| Electron or Tauri desktop app | Must be installed; separate builds per operating system; larger security surface. |
| A server-based web app with accounts | Needs hosting, sign-in and privacy handling; conflicts with "offline first" (T2-CON-010). |
| Rewriting the rules in JavaScript | Two implementations to verify, and results would drift apart. |
| Flutter or React Native web | Would mean rewriting the Android app too. |

## 3. Work plan

| Step | Work | Result |
|---|---|---|
| W1 | Turn `core/` into a Kotlin Multiplatform module (`commonMain`): replace the few Java-only calls (dates, `String.format`, `Math`) with multiplatform equivalents; keep the Room entities as plain data classes with Android-only annotations in `androidMain`. | The same core builds for Android and JS; every existing host test also runs on JS. |
| W2 | Web shell (option A): plot list from opened files, layout with ruler and compass, place/move/delete plants, paths, obstacles and areas, plot direction, Plan an area for me, harmony and suggestions, open/save `.sgp.json`. | Usable computer planner. |
| W3 | Parity tests: shared test vectors (planner, file round-trip, sun) run on JVM and JS in CI; plus a file made on each side is opened on the other. | Evidence of identical behaviour. |
| W4 | Packaging: single-file HTML build and a hosted copy (GitHub Pages) from CI; browser test matrix (Chrome, Edge, Firefox, Safari × Windows, macOS, Linux, ChromeOS, Android). | Downloadable, portable planner. |
| W5 | Android: open `.sgp.json` directly from the Files app, email and Drive (intent filter); a "share plan" button. | One-tap transfer. |
| W6 | Documents: user guide for computer and phone, requirements for the web client (HLR/LLR), tool assessment for the Kotlin/JS compiler, browsers as a COTS platform (extends DEV-01/DEV-05). | Compliance package updated. |

## 4. Decisions for you

1. **Option A (HTML/SVG, single portable file) or B (Compose for Web, hosted only)?** Recommended: A.
2. **Hosting:** also publish a hosted copy on GitHub Pages (public URL), or only the portable file? Recommended:
   both.
3. **File protection:** add an optional passphrase to plan files (planned for version 2)? Recommended: yes,
   optional.
