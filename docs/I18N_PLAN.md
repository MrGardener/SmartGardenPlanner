# Interface languages — plan (FR-062, T2-FUN-480)

## What's in place (2026-09-28)
- **US English source text.** Every interface text is written once, in US English spelling (neighbor, color, center,
  meters, organized, gray, counterclockwise). The plan file keeps `"units":"metres"` for compatibility, and published
  cultivar names (e.g. "Dwarf Grey Sugar") are unchanged.
- **One dictionary per language**, shared by both apps: `Android App/app/src/main/assets/i18n/<code>.txt`. The web build
  embeds each file in the page (`sgp-i18n-<code>`), so the computer planner stays a single offline file.
- **Engine:** `core/GroupTools.kt` → `I18n` (pure Kotlin, JVM and JS): `parse`, `use`, `tr`, `coverage`.
- **Computer:** header language menu; `Lang.apply` translates text, tooltips, placeholders and accessible names after
  every render. The choice is remembered in the browser.
- **Phone:** Settings → Language; `tr()` wraps texts; start-up loads the saved language; switching rebuilds the screen.
- **Languages:** English (built in), Español (started: header, tools, tabs, headings, planning dialog, proposal card,
  groups, settings sections — about 200 entries).

## Dictionary format
```
# comment
Plan an area for me	Planifica una zona por mí
{0} plants are already in this area	Ya hay {0} plantas en esta zona
```
- One entry per line: English, a TAB, the translation. Lines starting with `#` are comments.
- `{0}`, `{1}`… stand for numbers or names; they're copied unchanged (a name that itself has an entry is translated).
- Any text without an entry stays in English, so a partial dictionary is safe.

## Adding a language
1. Copy `es.txt` to `<code>.txt` (ISO 639-1 code) and translate the right-hand side.
2. Add `Language("<code>", "<name in that language>")` to `I18n.LANGUAGES`.
3. Web: add the file to the embedded dictionaries in `web/tools/bundle.py` (same pattern as `sgp-i18n-es`).
4. Run `OptionsGroupsI18nTest` and the browser smoke test.

## Still to do
| Step | What | Where |
|---|---|---|
| 1 | Translate the remaining static texts: Care, Food, Harmony, Site tabs; encyclopedia labels; phone screens other than the canvas and settings | es.txt; wrap more phone texts in `tr()` |
| 2 | Generated sentences (planner notes, checks, advice): turn each into a template with `{0}` parts in core, so one entry covers every number/name | core (AutoPlanner, PlanChecks, GardenAdvisor, Pests, WateringAdvice) |
| 3 | Plant names: species common names per language (cultivar names stay as published) | catalog column or a names dictionary |
| 4 | Dates and numbers in the user's locale (decimal comma, month names) | DistanceFormatter, GrowingSeason.describe |
| 5 | Review by a native speaker before release (safety FC-29: a wrong unit or warning could mislead) | per language |
| 6 | Coverage report in the language menu ("about N % translated") using `I18n.coverage` | web Lang, phone Settings |
| 7 | Optional: move the phone to Android string resources if Play Store localization tooling is needed; the dictionary files can be generated into `values-<code>/strings.xml` | build script |
