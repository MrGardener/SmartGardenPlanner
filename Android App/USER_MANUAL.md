# Smart Garden Planner — User Manual

**Version:** tracks the app's own version (currently v20.20+, updated through the ongoing dev session)
**Status:** Living document — updated whenever functionality is added, changed, or removed. If a
feature described here doesn't match what you see in the app, the manual is out of date; flag it.

---

## 1. Getting Started

### Dashboard
The home screen. Shows every plot you've created as a list. From here:
- **Create New Plot** (bottom-right button) → goes to the Creator screen.
- **Search icon** (top bar) → Botanical Encyclopedia.
- **Tune/sliders icon** (top bar) → Settings.
- **Settings/gear icon** (top bar) → the hardware-encrypted key-value vault card (unrelated to
  app Settings — this is a low-level debug tool for the underlying secure storage).
- Tap any plot in the list to open it in the Canvas.

### Creator (making a new plot)
1. Enter a plot name.
2. Enter length and width in meters. Bounds are configurable in Settings → Plot Validation
   (default: 0.05m–1000m).
3. Choose a scale source: **Manual Dimensions Entry** (type the numbers, as above) or **Device
   IMU Sensor Measuring** (walk/measure with the phone's motion sensors — this option exists in
   the picker but the underlying calibration/measurement flow is not yet reachable from the UI;
   use Manual for now).
4. Tap **Initialize Workspace** once both dimensions are valid.

---

## 2. The Canvas

The Canvas is where you lay out a plot: place plants, mark paths, and auto-fill areas.

### Modes
The canvas has three interaction modes, switched via the **menu icon** (☰) in the top bar:
- **Place plants** (default)
- **Draw / edit no-plant path**
- **Select area to auto-populate**

Two more toggles, independent of the three modes above, live as icons directly in the top bar:
- **Move Mode** (lock icon) — off by default. Turn on to drag-reposition already-placed plants.
- **Zoom/Pan Mode** (magnifying-glass icon) — off by default. Turn on to zoom and pan the canvas.

**Move Mode and Zoom/Pan Mode are mutually exclusive** with each other and with the three canvas
modes' own gestures — turning one on turns the others off. This is deliberate: it keeps touch
gestures from competing with each other.

### Placing plants
1. Make sure you're in **Place plants** mode and Move/Zoom-Pan are both off.
2. Pick a variety from the list at the bottom of the screen. The currently-selected variety is
   shown above the canvas ("Now placing: ...") so it's never ambiguous.
3. **Double-tap** anywhere on the canvas to place it there.
4. If placement is rejected, the message tells you exactly which existing plant conflicts and by
   how much distance. See **Settings → Spacing & Placement** if you want to loosen or tighten
   that rule (below).
5. **Single-tap** an existing plant to open its detail dialog: shows planted date and expected
   harvest date, and offers **Change Variety** or **Delete**.
6. If a plant's germination window has passed with no resolution, it shows a red ring on the
   canvas. Tapping it opens a **Recovery Plan** dialog instead of the normal detail dialog, with
   up to three fallback options (fast-track substitute, nursery-transplant restart, alternate
   catch-crop), sourced from that variety's Encyclopedia entry.

### Moving a plant
1. Turn on **Move Mode** (lock icon, top bar).
2. Drag any existing plant to a new spot. It follows your finger live.
3. On release, the new position is validated the same way a new placement would be — if it
   conflicts, it snaps back and tells you why.
4. Turn Move Mode back off when done, to avoid accidental repositioning later.

### Drawing a no-plant path
1. Switch to **Draw / edit no-plant path** mode via the menu.
2. Choose a path style, also in the menu: **Straight** (drag a rectangle) or **Curved** (tap a
   sequence of points, then **Finish Path** and set a width).
3. Tap an existing path (of either style) to edit its width or delete it.
4. Plants cannot be placed (individually or via auto-populate) anywhere their spacing circle
   would overlap a path zone — not just where their center point lands.

### Auto-populating an area
1. Switch to **Select area to auto-populate** mode.
2. Drag a rectangle over the area you want filled.
3. In the dialog: pick a variety (defaults to whatever you had selected in Place-plants mode) and
   a packing pattern — **Lines** (simple grid) or **Hexagon** (denser packing). A live count
   estimate updates as you change these.
4. Tap **Populate**. Points that would conflict with existing plants or paths are silently
   skipped, and you're told how many were placed vs. skipped.
5. The canvas automatically switches back to Place-plants mode afterward, so you can immediately
   tap/edit what was just placed.

### Zoom & Pan
1. Turn on **Zoom/Pan Mode** (magnifying-glass icon, top bar).
2. A floating **+ / −** control appears in the bottom-right corner of the canvas (fixed position,
   doesn't move around). A **recenter** button appears above it whenever you're not at 1.0x zoom.
3. Drag anywhere on the canvas to pan while zoomed in.
4. Turn Zoom/Pan Mode back off to return to normal plant/path/area interactions.

### Overlays
In the menu (☰), under "Overlays":
- **Show weed-risk mask** — highlights every part of the plot NOT covered by a plant's spacing
  radius (a sprout appearing there is very likely a weed).
- **Show irrigation route** — draws a suggested drip-line route connecting every planted node,
  nearest-neighbor from an assumed water source at the plot's origin corner.

Both are off by default and don't affect placement/validation — display only.

### Ruler
Meter-calibrated tick marks along the top and left edges of the canvas. Text size and tick
spacing are both configurable — see **Settings → Canvas Display**.

---

## 3. Botanical Encyclopedia

Reachable from the Dashboard's search icon. Browse, search, and manage the variety dictionary
that the Canvas draws from.

- **Search bar** filters by common name or botanical family.
- Tap a variety card to see full details: family, spacing, germination/harvest windows, care
  notes, pests, and companion/antagonist plants.
- **Edit** button (in the detail dialog) lets you change anything about that variety, including
  its spacing/exclusion radius and its canvas color.
- **Add Variety** (bottom-right button) creates a brand-new custom entry — code, name, family,
  spacing, germination/harvest days, companions/antagonists, care notes, and a color.
- **Canvas color**: pick from 16 preset swatches, or leave on **"A" (Auto)** to get a color
  automatically derived from the variety's code. Use a manual color if two varieties happen to
  get similar automatic colors.
- Anything you add or edit here is permanently protected from the tiered catalog system below —
  switching catalog tiers never touches your own varieties.

### Tiered seed catalog (Basic / Standard / Pro)
The Encyclopedia is backed by a real, bundled reference catalog spanning vegetables, fruit,
herbs, flowers, and ornamentals — annuals and perennials — across USDA hardiness zones 3–11.
Most entries are named cultivars of a smaller set of species (e.g. "Tomato - Brandywine,"
"Tomato - Cherokee Purple," "Tomato - San Marzano" are three separate catalog entries), which is
how real commercial seed catalogs reach hundreds of "varieties" from a much smaller number of
actual species — this app's catalog is generated the same way, using common, real, widely-sold
cultivar names as a reference point (Burpee's catalog was used as a rough guide for realistic,
recognizable naming), not scraped or independently verified against any single source. Treat the
spacing/germination/harvest figures as reasonable planning estimates, not authoritative agronomic
data — worth spot-checking a seed packet for anything you're relying on precisely.

Three tiers, chosen in **Settings → Catalog**:
- **Basic — 250 varieties.** The default on first install. Covers the most commonly grown
  vegetables, herbs, and flowers.
- **Standard — 600 varieties.** Basic's set plus a broader spread of less-common vegetables,
  fruit, and ornamentals.
- **Pro — 2,939 varieties.** The full catalog: 322 species spanning vegetables, fruit, herbs,
  flowers, and ornamentals, with deep cultivar lists for high-diversity crops (tomatoes, peppers,
  lettuce, apples, roses, dahlias, tulips, etc.), plus microgreens/sprouting seed, wildflower/
  native species, succulents/houseplants, ornamental grasses, and flowering shrubs/trees.

Switching tiers **replaces** the bundled (non-custom) portion of your seed dictionary with the
new tier's set — it does not add on top of what's there. Anything you've personally created or
edited through **Add Variety** / **Edit** is never touched by a tier switch, regardless of tier.
Switching can take a few seconds for the Pro tier given the volume of data involved.

### Choosing a variety (Category → Species → Cultivar)
With a catalog this large, picking a variety is a 3-step flow everywhere you're asked to choose
one (placing a plant, changing a plant's variety, auto-populating an area):
1. **Category** — Vegetable, Fruit, Herb, Flower, or Ornamental.
2. **Species** — e.g. Tomato, Pepper, Asiatic Lily, Lilac. Shows how many cultivars each has.
3. **Cultivar** — the specific named variety, e.g. San Marzano, Big Boy, Madame Lemoine.
Each step has a search box (from step 2 onward) to jump straight to what you're looking for
instead of scrolling.

---

## 4. Settings

Reachable from the Dashboard's tune/sliders icon. Every numeric or behavioral value that used to
be a fixed constant in the app now lives here, organized into sections. Changes save immediately
— there's no separate "Save" button. A reset icon (top bar) resets everything to defaults.

### Catalog
See §3 above for full detail. Pick Basic (253), Standard (603), or Pro (2,939) varieties. Shows
the current count of bundled varieties loaded. Your own custom varieties are never affected.

### Units
Meters or Inches — a quick-toggle appears right on the Canvas header (tap "[in]"/"[m]" next to
the plot size), or in Settings → Units for the same control. Everything is still stored
internally in meters; only what you see and type is converted.

### Spacing & Placement
- **Spacing margin** (0.3x–2.0x, default 1.0x): scales the plant-to-plant spacing rule. The
  default (1.0x) is the standard non-overlap rule — two plants' spacing circles are allowed to
  just touch, not overlap. Lower this if you want to pack plants tighter than that and are
  comfortable with the tradeoff; raise it to be more conservative than the default.
- **Enforce companion/antagonist rules**: turn off to ignore "these two dislike each other"
  warnings (spacing is still enforced either way).

### Canvas Display
- **Ruler text size**, **ruler tick spacing** — self-explanatory.
- **Minimum/maximum zoom**, **zoom step** — controls the bounds and increment of the +/- buttons.
- **Undo history depth** — how many steps back Undo/Redo can go.

### Plot Validation
- **Minimum/maximum plot dimension** — the length/width bounds enforced on the Creator screen.

### Sensors & Hardware
Tilt-abort threshold, low-light lux threshold, GPS accuracy gate, storage floor percentage.
**Not yet connected to any active screen** — the camera/sensor capture UI hasn't been built yet
(it's next on the list). These are here now so the settings exist by the time that screen lands,
rather than needing another retrofit later.

---

## 5. Known Gaps (as of this version)

- Camera capture has no screen yet — the underlying capture/downscale/permission logic exists in
  code but nothing in the UI calls it.
- IMU sensor-based plot measurement is selectable in the Creator screen's dropdown but not
  actually wired to a calibration/measurement flow.
- GPS-based climate lookup doesn't exist yet — only a ZIP-code lookup table does, and nothing in
  the UI currently reads from it either.
- Ownership/role fields exist on each plot but nothing reads or enforces them yet.

---

## Changelog

- **This version**: Massively expanded the tiered catalog — Basic 100→250, Standard 250→600, Pro
  604→2,936 varieties, now covering 322 species (up from 160) including microgreens/sprouting
  seed, wildflower/native species, succulents/houseplants, ornamental grasses, and flowering
  shrubs/trees, alongside deeper cultivar lists for high-diversity crops. Replaced every flat
  variety-selection list (place plant, change variety, auto-populate) with a 3-step Category →
  Species → Cultivar picker with search at each step — a flat list of up to 2,936 items was no
  longer usable. The options-menu color legend is now bounded to varieties actually placed on the
  current plot instead of iterating the entire catalog.
- Replaced the original 5-seed starter dictionary with a full tiered catalog —
  Basic (100), Standard (250), and Pro (604) varieties — spanning vegetables, fruit, herbs,
  flowers, and ornamentals across USDA zones 3–11, switchable any time from Settings → Catalog
  without affecting your own custom varieties. Companion/antagonist matching was updated to work
  at the species level (any cultivar of a species now correctly counts for its companion/
  antagonist relationships, not just one specific exact match) — necessary for this to actually
  function against a catalog with hundreds of cultivar-level entries.
- Fixed a real bug where planted nodes appeared to disappear when zooming in —
  caused by the scroll container and the canvas-size measurement sharing the same layout node,
  which corrupted the size calculation. Zoom controls and the zoom hint no longer move when
  panning. The ruler's tick spacing now gets finer as you zoom in. Added a distance unit setting
  (Meters/Inches) — a quick-toggle chip on the Canvas header, plus a full Settings section;
  storage is still always meters internally, only display and input are converted. Creator screen,
  Canvas header, and Encyclopedia now all respect the chosen unit.
- Added Settings screen (5th screen) and made every previously-hardcoded value configurable
  through it. Added zoom/pan (buttons + drag, gated behind a dedicated mode toggle). Moved zoom
  controls to a fixed floating cluster in the canvas's bottom-right corner. Made the
  spacing-margin rule and companion/antagonist enforcement configurable — directly in response to
  the strict default rule being too rigid for some real placements.
- Prior versions: recovery-plan UI, custom variety add/edit, ruler, no-plant paths (straight and
  curved), auto-populate (line/hex), plant color-coding, drag-to-reposition, weed-mask and
  irrigation-route overlays. See the project's own dev-log knowledge base for the detailed
  history of fixes and additions before this manual existed.
