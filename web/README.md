# Smart Garden Planner: portable planner for computers

`dist/smart-garden-planner.html` is the whole planner in **one file**. There's nothing to install, and you don't
need an account, a server or an internet connection.

## Use it

1. Download `dist/smart-garden-planner.html`. On GitHub, open the file and click **Download raw file**.
2. Double-click it. It opens in your browser: Chrome, Edge, Firefox or Safari on Windows, macOS, Linux or
   ChromeOS. It also works from a USB stick.
3. Create a plot (size, ZIP code and which way the top edge faces), or **Open…** a `.sgp.json` plan file.
4. **Save** writes a `.sgp.json` file:
   - In Chrome and Edge you choose where it goes, and later saves update that same file.
   - Other browsers download it.

Your work is also kept in the browser between visits. The file is what you keep, back up and share.

### Moving plans between computer and phone

| From | To | Steps |
|---|---|---|
| Computer | Phone | 1. **Save** here.<br>2. Copy the file to the phone by USB, email, Drive or OneDrive.<br>3. On the phone: Plots → **Import plan file**. |
| Phone | Computer | 1. On the phone: **Export plan file**.<br>2. Copy the file to the computer.<br>3. **Open…** it here, or drop it on the page. |

Importing never overwrites anything. The phone adds the file's plots as new plots. Here, opening a file replaces
what's open, so save first.

## What's inside

- **Same rules as the phone.** The planning rules come from the Android app's `core/` folder, compiled to
  JavaScript: spacing, companions, guilds, hardiness, sun and shade, Plan an area for me, harmony, suggestions, care
  and food. There is no second copy of the rules that could drift.
- **Same data.** The page embeds the Pro seed catalog (2,939 varieties) and the offline ZIP tables:
  - ZIP → USDA 2023 hardiness zone, from the PRISM Group.
  - ZIP → latitude.

| Path | What it is |
|---|---|
| `src/app/` | Browser UI (Kotlin/JS): `Main.kt` (shell, open/save), `Canvas.kt` (SVG layout and tools), `Panels.kt` (side tabs), `Dialogs.kt`, `Model.kt` (plots, undo, draft), `Data.kt` (embedded catalog and ZIP tables), `Dom.kt` |
| `src/platform/` | Browser versions of `PlatformClock`, and no-op stand-ins for the Room annotations used by the core entities |
| `tools/bundle.py` | Inlines the styles, compiled script and data into the single HTML file |
| `tests/smoke.mjs` | Drives the real page in headless Chromium: create, plant, obstacle move/undo, shade (whole day and time of day), plan an area (change selections, discard, remembered list, organised clumps), variety search, find/legend, plant editing, outline editing, seasons (look back, plan next season, rotation plan), fill the plot, irrigation water map, duplicate, save, reload, open |

## Build

You need Java 17+, Python 3 and curl. On first run, `build.sh` downloads the Kotlin 2.2.10 compiler jars from Maven
Central into `~/.cache/sgp-kotlin` (set `KOTLIN_JARS` to use another folder).

```sh
sh web/build.sh                  # writes web/dist/smart-garden-planner.html (reproducible)
cd web && npm install --no-save playwright@1 && npx playwright install chromium
node tests/smoke.mjs             # 84 checks
```

On every pull request that touches `web/`, `core/` or the assets, CI (`.github/workflows/web-planner.yml`) does
three things:

1. Rebuilds the file.
2. Fails if the committed copy is out of date.
3. Runs the smoke test.

Requirements: T2-PLT-040, HLR-PORT-030/040, LLR-WEB-010…040. Plan: `docs/CROSS_PLATFORM_PLAN.md`.
