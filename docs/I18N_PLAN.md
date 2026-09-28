# Interface languages — plan (FR-062, T2-FUN-480)

## What's in place (updated 2026-09-28)
- **US English source text.** Every interface text is written once, in US English spelling (neighbor, color, center,
  meters, organized, gray, counterclockwise). The plan file keeps `"units":"metres"` for compatibility, and published
  cultivar names (e.g. "Dwarf Grey Sugar") are unchanged.
- **One dictionary per language**, shared by both apps: `Android App/app/src/main/assets/i18n/<code>.txt`. The web build
  embeds each file in the page (`sgp-i18n-<code>`), so the computer planner stays a single offline file.
- **Engine:** `core/GroupTools.kt` → `I18n` (pure Kotlin, JVM and JS): `parse`, `use`, `tr`, `coverage`. Besides exact
  entries and `{0}` templates it translates text built at run time: variety names ("Maíz dulce - Honey Select"), comma
  lists, a leading bullet or symbol, a final full stop or colon, several sentences in one text, "A — B" and
  "Label: rest". Results are cached.
- **Computer:** header language menu; `Lang.apply` translates text, tooltips, placeholders and accessible names after
  every render. The choice is remembered in the browser.
- **Phone:** Settings → Language; every `Text(…)` goes through `tr()`, as do messages and section titles; start-up loads
  the saved language; switching rebuilds the screen.
- **Spanish (complete):** about 3,270 entries: every interface text, advice text and planner note, 323 species names
  (and their lower-case forms), 322 catalog care notes, the pest names in the catalog's pest notes, generic cultivar
  words ("Mid-Season", "Early Season", "Golden"…), plant labels ("Bell red" → "Morrón rojo") and short dates
  ("May 12" → "12 may"). Registered cultivar names ("Honey Select", "Golden California Wonder") stay as published.
- **Checks:** `SpanishDictionaryTest` (planner notes for a 13-crop list, names, lists, bullets, dates) and
  `web/tests/i18n_coverage.mjs`, a CI step that walks the main screens and fails if an English sentence is left.

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
| 1 | Review es.txt by a native speaker before release (safety FC-29: a wrong unit or warning could mislead) | es.txt |
| 2 | Dates and numbers in the user's locale (decimal comma, full month names) | DistanceFormatter, GrowingSeason.describe |
| 3 | Coverage report in the language menu ("about N % translated") using `I18n.coverage` | web Lang, phone Settings |
| 4 | Extend the coverage check to every dialog (seasons, irrigation, satellite photo) | tests/i18n_coverage.mjs |
| 5 | Optional: generate Android string resources from the dictionary if Play Store localization tooling is needed | build script |

## Adding text to the app
Any new interface text needs a Spanish entry in the same change; the CI coverage check fails if an untranslated sentence shows on one of the screens it walks. For a sentence
with numbers or names, add a template (`"{0} plants placed"`) rather than one entry per value.
