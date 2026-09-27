# Smart Garden Planner — User Manual

**Covers:** the Android app and the portable computer planner (`web/dist/smart-garden-planner.html`), as of
2026-09-27 (database schema 10, plan file format 1).
**Status:** Living document, updated with every change. If something here doesn't match what you see, the manual is
out of date: please flag it. The `manual-tester` subagent checks the computer planner against this manual on request.

Where the two versions differ, this manual says **Phone:** and **Computer:**. Both use the same planning rules and
open the same plan files.

---

## 1. The idea in one minute

1. Make a **plot**: its size, which way it faces and its ZIP code.
2. Draw what's **fixed** there: fences, walls, buildings, trees, paths, sunny or shady areas, and your sprinklers,
   drip lines and hose tap. These stay with the plot year after year.
3. **Plant**, either one plant at a time or with **Plan an area for me**, which decides where everything goes.
4. At the end of the year, **start the next season**. The plants become history, and next year's plan rotates the
   crops so no family goes back where it just grew.

---

## 2. Getting started

### Phone
- **Dashboard** (home screen) lists your plots.
  - **Create New Plot** makes a new one.
  - The folder icon is **Open plan file**.
  - The disk icon is **Save all plots to a file**.
  - The search icon opens the **Encyclopedia**; the sliders icon opens **Settings**.
- **Creating a plot:**
  1. Enter a name, the length (left to right) and the width (top to bottom) in metres.
  2. Choose **Which way does the top edge of the plot face?** by tapping one of N, NE, E, SE, S, SW, W, NW. Stand
     at the bottom edge and look across the plot; the direction you face is the answer.
  3. Optionally enter the **ZIP code**. It fills in the USDA hardiness zone (2023 map) and the latitude, with no
     internet needed.
  4. Tap **Initialize Spatial Workspace**.
- You can change the direction and ZIP later from the layout menu (☰) → **Plot direction and ZIP…**.

### Computer
- Double-click `smart-garden-planner.html`. It runs in any modern browser (Chrome, Edge, Firefox, Safari), offline,
  with nothing to install.
- **New plot** asks for the same details: name, size, ZIP, which way the top edge faces, and soil.
- **Open…** opens a `.sgp.json` plan file. You can also drop one onto the page.
- **Save** writes the plan file:
  - In Chrome and Edge, you pick where it goes and later saves update that same file.
  - Other browsers download it instead.
- The planner also remembers your work in the browser between visits. The file is what you keep and share.
- **?** opens the help.

---

## 3. The layout

The plot is drawn to scale on a light background in both light and dark mode, with a metre ruler along the top and
left and a **compass** at the top right. The compass has four arrowheads (N, E, S, W) turned to your plot's
direction. North is red once the direction is set, and grey with "N?" until you set it.

### Placing, changing and moving plants
- **Choosing what to plant:**
  - **Phone:** tap **Choose a Variety to Place** and pick Category → Species → Cultivar.
  - **Computer:** use the **Plants** tab. Search accepts everyday words such as "sweet bell", "cherry tomato",
    "spring onion" or "hot".
- **Planting:**
  - **Phone:** double-tap the layout.
  - **Computer:** choose the **Plant** tool and click.
  - A placement that breaks a rule is refused, and the message says why: too close to another plant, a plant it
    dislikes nearby, outside the outline, on a no-plant path, or a perennial that won't survive the zone.
- **Changing a plant:**
  - **Phone:** tap a plant to see its details (kind, planted date, expected harvest), with **Change Variety** and
    **Delete**.
  - **Computer:** double-click a plant (or select it and click **Edit plant…**) to change its variety or planting
    date, or delete it.
- **Moving:**
  - **Phone:** turn on **Move Mode** (lock icon) and drag.
  - **Computer:** use **Select / move** and drag.
  - The new spot is checked with the same rules.
- **Undo / Redo** covers every change: plants, paths, obstacles, areas, outline, irrigation and seasons. On the
  computer, use **Ctrl+Z / Ctrl+Y**.
- **Crop-rotation note:** if you plant a crop where its family grew recently, it is still planted, but a note says
  what grew there, when, why it matters and what would do better (see §5).

### What each plant is
- Peppers say **sweet or spicy** (with a heat level from mild to extremely hot), their shape (bell, horn, chile) and
  their ripe colour.
- Tomatoes say **cherry, salad, slicing, beefsteak or paste**, with their colour.
- Onions say **bulb onion or spring onion**. Three spring onions are in every catalog size.
- **Plant names:** short names such as "Bell red", "Cherry orange" or "Spring" can be shown under each plant.
  - **Phone:** menu → **Show plant names**.
  - **Computer:** the **Names** tool.
- The dot in the middle of each plant is the **fruit colour**, so red and yellow bell peppers look different.

### Finding plants
- **Phone:** the menu's **Legend (this plot)** lists every variety planted with its count and kind. Tap one to
  circle its plants in orange. The button that appears ("Showing … — tap to clear") turns it off.
- **Computer:** the **On this plot** box at the bottom left does the same. Click a line; click it again or press
  **Esc** to clear. Lines on the **Food** tab (expected harvest) and the **Harmony** tab also find their plants.

### No-plant paths
- **Phone:** menu → **Draw / edit no-plant path**. Drag a rectangle (Straight), or tap points and then **Finish
  Path** (Curved). Tap a path to edit or delete it.
- **Computer:** the **No-plant path** tool (drag a rectangle).
- No plant's spacing circle may overlap a path.

### Site tools: outline, areas, obstacles, irrigation
- **Plot outline** (for plots that aren't rectangles):
  - **Phone:** menu → **Draw plot outline**. Tap the corners in order, then **Finish Outline**. To change it, open
    the tool again, tap a corner (it turns orange), then tap where it should go. Menu → **Delete outline (back to
    the full rectangle)** removes it.
  - **Computer:** the **Plot outline** tool. Drag the white corners, double-click an edge to add a corner, or click
    corners in order and press Enter to redraw. **Delete outline** removes it.
- **Sun / shade / flood / slope areas:**
  - **Phone:** menu → **Mark sun / shade / flood / slope area**.
  - **Computer:** the **Sun / shade / flood / slope area** tool (types on the Plot tab).
  - Flood areas take the months they flood; slopes take a grade and downhill direction.
- **Trees, fences, walls, buildings**, each with a rough height (fence 1.8 m, one-storey house 5 m):
  - **Phone:** menu → **Place tree / fence / wall / building**.
  - **Computer:** the **Tree** and **Fence / wall / building** tools.
  - These cast shade.
- **Irrigation** (§7):
  - **Phone:** menu → **Irrigation** → **Place sprinkler / drip line / hose tap**.
  - **Computer:** Plot tab → **Irrigation**.
- **Editing any obstacle, area or irrigation item:**
  - **Phone:** tap it with its tool active to **Edit**, **Delete** or **Move** it.
  - **Computer:** select it and drag it, or click **Edit selected…**.

### Other overlays (Phone menu → Overlays)
- **Show site areas and barriers**
- **Show sun and shade** (§6)
- **Show water map** (§7)
- **Show weed-risk mask**: highlights ground no plant's spacing covers, where weeds are likely.
- **Show irrigation route**: a suggested drip route through every plant.

---

## 4. Plan an area for me, and Fill the whole plot

For when you know *what* you want but not *where*.

1. **Choose the area:**
   - **Phone:** tap **✨ Plan an area for me** and drag over the area (or tap its corners in custom-shape mode).
   - **Computer:** use the **Plan an area for me** tool and drag.
   - To plan everything at once, use **Fill the whole plot…**. It's a button on both.
2. **List what you want and how many.** The list starts as follows:
   - with your **last list**, which is remembered;
   - otherwise with **what you usually plant** (the varieties you grow most, over all plots and seasons), which are
     also offered as one-tap buttons.
3. **How many fit?** keeps the proportions of your list and fills the area: for example, 2 corn for every 1 lettuce
   becomes 24 corn and 12 lettuce. Change any number afterwards.
4. **How should each crop be arranged?**
   - **Organised clumps (recommended):** each crop is a small block of rows and columns at its own spacing (20 corn
     = 4 rows of 5; 7 tomatoes = a row of 4 and a row of 3), with a **45 cm walkway** between crops so you can walk
     round and water with a hose. Next year the blocks can swap places.
   - **Long rows:** crops lined up by height. Tidy, but harder to rotate: a long row of tomatoes at the back has
     nowhere to go next year without shading the rest.
5. **Plan it** shows a proposal as dashed circles. Nothing is planted yet. The card explains every decision:
   - Tall crops are on the side away from the midday sun (north in the northern hemisphere).
   - Sun lovers get the sunniest spots.
   - Pollinator flowers are next to crops that need bees.
   - Sweet corn is planted as a block.
   - Plants with similar watering needs are together.
   - Crops are kept off spots their family used recently.
   - **Climbers** (pole beans, peas) are at the back, with a note to put up a trellis there.
   - **Sprawling vines** (watermelon, squash, pumpkin, cucumber, melon, sweet potato, gourds) are on the sunny side,
     with their runway toward the sun kept free (about 2 m for watermelon), shown by a **green arrow**. Guide the
     runners that way.
6. Choose:
   - **Keep this plan** (**Plant them** on the phone) plants everything as one undo step.
   - **Change selections** goes back to your list for the same area.
   - **Discard** drops only the proposal. Your plot and your list stay as they are.

---

## 5. Seasons, history and crop rotation

A plot keeps its fixed features every year and remembers every past season.

| I want to… | Phone | Computer |
|---|---|---|
| See which seasons I have | Plot insights → **Harmony** → Seasons & crop rotation | **Plot** tab → Seasons & crop rotation (each season lists its plants and where each family grew) |
| Look at a past season | Menu → **Season shown: …** (tap to cycle). The layout shows that season, read-only; tap the banner to return | Plot tab → **Season shown on the layout** → a past year (read-only). **Back to …** returns |
| See last year faintly while planning | Menu → **Past season on layout** | Plot tab → **Also show a past season faintly** |
| End this season and start empty | Menu → **Start a new season (empty)…** | Plot tab → **Start a new season (empty)…** |
| Re-plan next year with the same crops, rotated | Menu → **Plan next season (rotate)…** | Plot tab → **Plan next season (rotate)…** |
| See the next 3 to 10 years | Menu → **Rotation plan for several seasons…** | Plot tab → **Rotation plan for 5 seasons…** (choose 2–10) |
| Keep a template or try another plan | Menu → **Duplicate this plot…** | Plot tab → **Duplicate…** (top, next to Edit details) or **Duplicate plot…** under Templates |

**Which season am I in?** The season being planned is the year this season's plants were planted (this year if
there are none). It is always the year after the last season you closed. You plan that season; past seasons are for
looking at.

**Plan next season (rotate)** reuses this season's list (or your last closed season's), plans the whole plot, and
shows the proposal. Choosing **Start next season** (**Start next season with this plan** on the computer) does the
following in one undo step:
1. Moves this season's plants into history.
2. Plants the new layout.

**Rotation plan** plans several seasons in a row from the same list and shows one year at a time: ◀ / ▶, or
**Year ▶** on the computer. Nothing changes until you choose **Use <first year> now**. The plan is worked out again
each time from your plot, so it follows any changes you make.

**The rotation rules:**
- Vegetables belong to rotation families, each with a waiting period:
  - legumes: 2 years
  - cabbage family: 3
  - nightshades (tomato, pepper, eggplant, potato): 3
  - squash family: 2
  - corn and grains: 2
  - onion family: 3
  - carrot family: 3
  - beet and spinach family: 2
  - lettuce family: 1
- Flowers, herbs and perennials don't rotate.
- **Plan an area for me never puts a crop where its family grew last season**, as long as anything else fits. If
  nothing else fits, the card says so.
- Older seasons, still within the family's waiting period, are avoided more weakly the further back they are.
- Planting by hand is never blocked. You get a note instead.
- The advice (Harmony tab on the phone, Plot tab on the computer) tells you:
  - what grew where last season, in compass terms;
  - which family should go there next (legumes → cabbage family → fruiting crops → roots and onions → legumes);
  - which of this season's plants break the rotation;
  - when last season used one long row.
- History is saved in plan files and is never removed by later seasons. It is deleted only with its plot.

---

## 6. Sun and shade

Turn it on:
- **Phone:** menu → **Show sun and shade** (Pro tier).
- **Computer:** **Shade** in the top bar.

Choose:
- **The day:** Today, spring equinox, midsummer (longest day), autumn equinox or midwinter (shortest day). The sun is
  much lower in winter, so shade reaches further.
- **Whole day:** each spot is coloured by the hours of direct sun it gets from sunrise to sunset:
  - yellow = full sun (6 h or more)
  - blue = part shade (3 to 6 h)
  - dark indigo = shade (under 3 h)

  **Computer:** point at a spot to see *when* it gets sun, e.g. "Sun 7:15–11:30 and 14:00–18:45".
- **At a time of day:** a slider from sunrise to sunset shows where the shade is at that moment, and where the sun
  is. Slide it to watch the shade move across the plot.
- **Plants' shade** (on by default): planted crops cast shade at their **mature height** (corn about 2.2 m, tomatoes
  1.5 m, pole beans 2 m), so you can see how much tall plants shade their neighbours. Low crops (under 0.5 m) are
  ignored.
- **Phone:** tap the blue words in the legend to change the day, switch between whole day and time of day, or turn
  plants' shade on and off.

Times are **solar time**: noon is when the sun is due south (due north in the southern hemisphere), which can be up
to an hour or so from clock time. Clouds are not modelled.

---

## 7. Irrigation: sprinklers, drip lines and hoses

Draw what you have and see what gets watered.

- **Sprinkler:** tap or click where it stands, then set:
  - how far it throws water (metres);
  - its pattern: full, three-quarter, half or quarter circle;
  - for a part circle, which way the middle of the spray points.
- **Drip line / soaker hose:** tap points along it and finish, then set the wetted strip either side (drip about
  0.3 m, soaker about 0.2 m).
- **Hose tap:** tap where the tap is and give the hose length. Anywhere within that distance counts as reachable
  (in a straight line; walls or beds in the way are not modelled).

Turn on the **water map** (phone menu → **Show water map**, or **Water** in the computer's top bar):
- Wet areas are coloured by source: drip, sprinkler, or hose reach.
- **Plants no source reaches get a red dashed ring**: they need a watering can.

The Care tab (**How each plant gets water**) counts plants per source and lists those needing hand watering. It
also warns when a sprinkler wets the leaves of crops prone to blight or mildew (tomatoes, potatoes, squash,
cucumbers, melons, peppers), where a drip line is better.

---

## 8. Templates: duplicating a plot

**Duplicate** copies the plot, much like duplicating a browser tab. The copy keeps the size, direction, ZIP, soil,
outline, fences, buildings, trees, paths, areas and irrigation. You choose whether it also takes this season's
plants and the history, so crop rotation carries on in the copy. Change the copy freely; the original stays as it
was.

---

## 9. Plot insights (Phone) and the side tabs (Computer)

**Phone:** menu → **Plot insights**. **Computer:** the tabs on the right.

- **Site** (phone) / **Plot** (computer): zone, location, direction, soil (with improvement tips), sunlight;
  seasons, templates and irrigation (computer).
- **Harmony:** a score, clashes, good neighbours, ideas; and on the phone, **Seasons & crop rotation**.
- **Suggest** (phone) / **Plants** (computer): varieties that suit the plot or an area.
- **Care:** how each plant gets water, the watering interval, the feeding plan (organic or conventional), and pests
  to watch for.
- **Food:** expected harvest, what it feeds, recipes from your garden, and a homestead starter list for your
  household size.

---

## 10. Encyclopedia and catalog

Reach it from the Dashboard's search icon.

- **Browsing:** search, see details (family, spacing, germination and harvest, care, pests, companions and
  antagonists), edit, or **Add Variety**. A canvas colour is optional (16 presets, or Auto).
- **Catalog sizes** (Settings → Catalog):
  - **Basic 253**
  - **Standard 603**
  - **Pro 2,939** varieties
- **Switching size:** replaces the bundled entries, but never your own. New bundled varieties (such as the spring
  onions) are added automatically when the app starts.
- **Choosing a variety:** always Category → Species → Cultivar, with search. Peppers, tomatoes and onions show their
  kind (sweet or spicy, size, colour).
- The spacing, germination and harvest figures are planning estimates; check a seed packet when it matters.

---

## 11. Settings (Phone)

Changes save immediately; the reset icon restores the defaults.

- **Catalog:** Basic, Standard or Pro.
- **Units:** metres or inches. Also switchable from the "[in]/[m]" chip on the layout header.
- **Spacing & placement:**
  - the spacing margin (0.3× to 2.0×);
  - companion rules on or off (Pro);
  - guild planting (Pro). Also switchable from the "GUILDS ON/OFF" chip.
- **Canvas display:** ruler text size and tick spacing, zoom limits and step, undo depth.
- **Plot validation:** minimum and maximum plot size.
- **Household & care:**
  - household size;
  - organic or conventional advice;
  - daily care reminders (Pro);
  - how much rain counts as a watering.
- **Online features:** off by default. When on, the app may fetch weather, hardiness zone and nutrition data.
- **Sensors & hardware:** reserved for the measuring features, not yet connected.

**Computer:** similar settings are on the Plot tab: companion rules, guilds, spacing margin, organic care. The page's
own colours (**Auto / Light / Dark**) are in the top bar. The layout itself always stays light so shade is easy to
see.

---

## 12. Moving plans between phone and computer

- **Computer → phone:**
  1. **Save** on the computer.
  2. Copy the `.sgp.json` file to the phone (USB, email, Drive, OneDrive).
  3. On the phone: Dashboard → **Open plan file**.
- **Phone → computer:**
  1. On the phone, use menu → **Save this plot as a file…** (or **Save all plots to a file** on the Dashboard).
  2. On the computer: **Open…**, or drop the file onto the page.
- **What travels in the file:** plants, paths, obstacles, areas, irrigation, outline, direction, ZIP, soil and the
  **season history**.
- **Opening a file never overwrites anything.** The phone adds the file's plots as new plots. On the computer,
  opening a file replaces what's open, so save first.

---

## 13. Known gaps

- **Camera capture and phone-sensor measuring** of plot size are not connected yet (use the typed size).
- **Phone:** you can't drag outline corners (tap a corner, then its new place) or add corners. You can't change a
  plant's planting date. Pointing at a spot to see its sun hours is computer-only.
- **Rotation plans** are shown but not stored as future seasons. They are worked out again when you open them.
- **Irrigation:** hose reach is a straight line. Water pressure, flow and run times are not modelled.
- **Shade:** clear-sky, solar time, and approximate plant heights.
- **Variety details** (sweet or spicy, size, colour) cover peppers, tomatoes and onions only.
- **Ownership and roles** on plots are not used yet.

---

## Changelog

- **2026-09-27 (seasons and water):**
  - Look back at any past season.
  - **Plan next season (rotate)**, and **Rotation plan** for several seasons. Rotation now never reuses last
    season's spot for the same family when anything else fits.
  - Shade by time of day and by day of the year, with tall plants casting shade.
  - **Fill the whole plot** and **How many fit?**
  - **Duplicate plot** as a template.
  - **Irrigation:** sprinklers, drip lines and hose taps, with a water map and a hand-watering list.
- **2026-09-27 (find and edit):**
  - Organised clumps (rows × columns with walkways), room for vines to run toward the sun, climbers at the back.
  - Legend with **find**.
  - Edit or delete planted plants.
  - Move or add outline corners and delete the outline.
  - N/E/S/W compass.
- **2026-09-27 (seasons and varieties):**
  - Discarding a proposal keeps your plot and list; **Change selections**.
  - The last list and what you usually plant are remembered.
  - Sweet or spicy, size and colour for peppers, tomatoes and onions; spring onions added.
  - **Start a new season** and season history.
  - Crop-rotation notes and advice.
  - Clumps or rows. Database schema 10.
- **2026-09-27 (computer and shade):** the portable computer planner; shade shown on a light layout with coloured
  sun bands in light and dark mode.
- **2026-09-27 (guided planting):** Plan an area for me, plot direction and ZIP (offline zone), plan files, obstacles
  that can be moved and undone.
- **2026-09-27 (roadmap):** site tools (outline, sun, shade, flood and slope areas, trees and buildings), plot
  insights (site, harmony, suggestions, care, food), guilds, grey-out of incompatible varieties, care reminders,
  online features.
- **Earlier:**
  - tiered catalog (Basic, Standard and Pro), 3-step variety picker
  - zoom and pan, settings, units
  - recovery plans for failed germination, custom varieties
  - ruler, no-plant paths, auto-populate
  - moving plants, weed-mask and irrigation-route overlays
