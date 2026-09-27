# Smart Garden Planner — Feature Roadmap & Tracking Document

**This file is the single source of truth for everything requested, in progress, or completed.
It will be included in every future delivery and updated every time work happens on any item
below.** Nothing gets implemented that isn't listed here first, and nothing gets marked complete
without your explicit confirmation — see the workflow rules immediately below.

---

## How this document works (please read before reviewing the list)

**Status values, in order:**
1. **Not Started** — captured, not yet begun.
2. **In Progress** — actively being built.
3. **Implemented — Awaiting Your Confirmation** — code delivered, but not yet marked done. This
   is the default landing state after any implementation work. It stays here indefinitely until
   you say it's good.
4. **Confirmed Complete** — you've explicitly said this is good as-is. Only status that means
   "finished." I will not move anything to this status on my own judgment.
5. **Needs Rework** — you gave feedback that it's not right yet; goes back to In Progress.

**The rule you asked for, stated plainly:** if I deliver something and you don't say anything
about it, it stays at "Implemented — Awaiting Your Confirmation," not "Confirmed Complete." I'll
keep iterating on anything in that state until you explicitly sign off, even across many
sessions. This document is how neither of us loses track of what's actually settled versus what
just hasn't been reviewed yet.

**Tier column:** Basic / Standard / Pro / All. These are my **proposed** defaults based on how
complex or advanced each feature is — nothing about tier assignment is final until you confirm
or change it. Where a feature should be a **toggle within Pro** (on/off, not tier-gated
existence), I've noted that specifically per your instruction about companion-planting
enforcement.

**What this document does NOT contain yet, per your explicit instruction:** HLRs, LLRs, or any
DO-178C-style requirement decomposition. That work starts only after you've reviewed and signed
off on this list. Nothing below has been implemented as part of this delivery — this is planning
only.

---

## Part A — Already Implemented (for your review/correction)

Two items on your list describe things I believe already exist in the app. Flagging these first
so you can tell me if my understanding is wrong, rather than silently assuming they're covered.

| ID | Feature | My understanding | Status |
|---|---|---|---|
| **EXIST-01** | Backup/recovery plan ("what to plant if the first seeds didn't work") | This exists: `GerminationContingencyEngine` + the on-canvas Recovery Plan dialog (tap a plant whose germination window has passed with no result → offers fast-track substitute / nursery-transplant restart / alternate catch-crop). If this isn't what you meant, or it's not working the way you expect, let me know specifically what's missing. | Implemented — Awaiting Your Confirmation |
| **EXIST-02** | Companion-planting enforcement should be configurable/deactivatable | This exists as a Settings toggle, and is now restricted to Pro tier (see FR-012, done this round). | **Implemented — Awaiting Your Confirmation** (superseded by FR-012 above) |

---

## Part B — New Feature Requests (from this message)

### B1. Canvas & Plot Geometry

| ID | Feature | Description | Proposed Tier | Depends On | Status |
|---|---|---|---|---|---|
| FR-001 | Polygon area-select for auto-populate | Replace rectangle-only area selection with the same point-based approach used for curved paths: tap points, "Finish Area" button available once 3+ points exist, then fills the drawn shape (not its bounding box) using point-in-polygon filtering on top of the existing rectangle-fill engine. | Standard | Existing curved-path point-drawing code (reusable) | **Implemented — Awaiting Your Confirmation** |
| FR-002 | Polygon (non-rectangular) plot shapes | Let the user draw the actual plot boundary from real measurements instead of only length×width rectangles — irregular/L-shaped/multi-sided plots. This is a significant data-model change (PlotEntity currently assumes a rectangle everywhere: ruler, scaleX/scaleY, node-placement bounds, path/area tools). | Pro | None, but touches most of the Canvas | **Implemented — Awaiting Your Confirmation** |
| FR-003 | Slope configuration | Let the user mark slope direction/grade on areas of the plot, for drainage/planting guidance. | Pro | FR-002 (more useful with irregular plots, but not strictly blocked by it) | **Implemented — Awaiting Your Confirmation** |
| FR-004 | Seasonal flooding zones | Mark areas that flood seasonally; factor into planting recommendations/warnings. | Pro | None | **Implemented — Awaiting Your Confirmation** |
| FR-005 | Sunny/shaded area delineation | Let the user mark sun-exposure zones directly on the canvas. | Standard | None | **Implemented — Awaiting Your Confirmation** |
| FR-006 | Sunlight barriers (trees, fences, walls) | Place barrier objects with an estimated height; estimate shading effect on nearby plants over the course of a day. | Pro | FR-005 (barriers inform shade zones) | **Implemented — Awaiting Your Confirmation** |
| FR-007 | Historical sunlight hours per plot | Estimate daily/seasonal sun hours for the plot location from historical data. **Answered:** use an external source, structure now for a togglable future connection. | Pro | FR-026 (toggleable network layer) | **Implemented — Awaiting Your Confirmation** |

### B2. Plant Selection, Companion Planning & Compatibility

| ID | Feature | Description | Proposed Tier | Depends On | Status |
|---|---|---|---|---|---|
| FR-008 | Companion plants shown by name, not code | The companion/antagonist fields currently display raw codes (e.g. "BAS,MAR,CAR"). Resolve these to full common names everywhere they're shown. | All | None — contained display fix | **Implemented — Awaiting Your Confirmation** |
| FR-009 | Named interplanting guilds (Three Sisters and others) | **Answered:** generalize beyond Three Sisters to other well-established real companion-guild groupings; must be explicitly deactivatable with a highly visible indicator when off. | Pro | Validator architecture change — needs care not to break normal overlap protection | **Implemented — Awaiting Your Confirmation** |
| FR-010 | Grey out incompatible varieties in the picker | In the Category → Species → Cultivar picker, visually disable/grey out cultivars that would conflict with something already planted on the current plot. | Standard | 3-step picker (exists) | **Implemented — Awaiting Your Confirmation** |
| FR-011 | Garden harmony report | A report screen: what's planted, what's incompatible with what, and recommendations to fix it. | Standard | FR-008 | **Implemented — Awaiting Your Confirmation** |
| FR-012 | Companion-rule toggle restricted to Pro | Below Pro, the toggle now shows locked-on with an explanation instead of being editable. | Pro (toggle) | FR-025 (tier-gating system) | **Implemented — Awaiting Your Confirmation** |

### B3. Soil, Zone & Site-Aware Recommendations

| ID | Feature | Description | Proposed Tier | Depends On | Status |
|---|---|---|---|---|---|
| FR-013 | Soil test / soil composition input | Let the user record or estimate soil composition (e.g. sand/silt/clay/organic matter, pH), with guidance on how to improve it over time. | Pro | None | **Implemented — Awaiting Your Confirmation** |
| FR-014 | Soil- and zone-aware plant recommendations | Recommend varieties based on the plot's hardiness zone and (if entered) soil profile; block or warn against perennials that won't survive the zone. This is the first feature that makes the catalog's `hardinessZoneMin/Max` fields actually functional — they're stored today but nothing reads them yet. | Standard | Catalog zone data (exists), FR-013 for the soil half | **Implemented — Awaiting Your Confirmation** |
| FR-015 | "Recommend & auto-populate" button | One-tap suggestion: given the plot's soil/zone/existing paths, propose what to plant in a selected area and auto-populate it. | Pro | FR-013, FR-014, existing auto-populate engine | **Implemented — Awaiting Your Confirmation** |
| FR-016 | "Homestead" starter list | A recommended minimum set of crops for the plot's hardiness zone, aimed at basic balanced nutrition for a household. | Standard | FR-014, FR-020 (nutrition data) | **Implemented — Awaiting Your Confirmation** |

### B4. Care Planning (Fertilizing, Pest Management, Reminders)

| ID | Feature | Description | Proposed Tier | Depends On | Status |
|---|---|---|---|---|---|
| FR-017 | Fertilizing plan | Generate a fertilizing schedule based on what's actually planted. | Standard | None | **Implemented — Awaiting Your Confirmation** |
| FR-018 | Pesticide/pest-management plan | Generate a pest-management plan based on what's planted, with an organic vs. conventional preference toggle. | Standard | None | **Implemented — Awaiting Your Confirmation** |
| FR-019 | Fertilize/water reminders, rain-aware | **Answered:** live weather data deferred; build on the toggleable network layer (FR-026) once that exists. | Pro | FR-026 | **Implemented — Awaiting Your Confirmation** |

### B5. Reference & Educational Content

| ID | Feature | Description | Proposed Tier | Depends On | Status |
|---|---|---|---|---|---|
| FR-020 | Nutritional value guide | **Answered:** USDA FoodData Central confirmed as the source. Must support on-demand refresh, not just a one-time bake-in — needs FR-026's network layer to actually refresh, but can ship with a static bundled snapshot before that exists. | All | FR-026 for the refresh capability specifically | **Implemented — Awaiting Your Confirmation** |
| FR-021 | Recipe suggestions | Recipes usable with what the user is growing. | Standard | FR-020 useful as a companion, not a hard dependency | **Implemented — Awaiting Your Confirmation** |
| FR-022 | Approximate yield per plant | Expected production weight per plant, to help size a garden for a household's needs. | Standard | None | **Implemented — Awaiting Your Confirmation** |

### B6. Vendor / Commerce Integration

| ID | Feature | Description | Proposed Tier | Depends On | Status |
|---|---|---|---|---|---|
| FR-023 | Vendor purchase links | **Answered:** placeholder/inert structure only for now — data model and UI slots built so a real vendor integration can be dropped in later without restructuring, but no live vendor site linked yet. | All (links), Pro (customization) | None architecturally | **Implemented — Awaiting Your Confirmation** |
| FR-024 | Vendor targeting/preference | Let the user pick one preferred vendor so all purchase links point there consistently. | Pro | FR-023 | **Implemented — Awaiting Your Confirmation** |

### B7. Platform Infrastructure

| ID | Feature | Description | Proposed Tier | Depends On | Status |
|---|---|---|---|---|---|
| FR-025 | Tier feature-flag system | The mechanism this whole document leans on: a central registry (`Feature` enum + `AppTier`) of which features are active for Basic/Standard/Pro, checked at the relevant screens/actions — reuses the existing catalog-tier setting as the source of truth for "what tier is this user on," no separate subscription concept added. | Infrastructure (not user-facing) | None | **Implemented — Awaiting Your Confirmation** |
| FR-026 | Toggleable network connection layer | **New, from your answer on FR-019/FR-007.** A user-controlled, non-permanent network capability that future live-data features (weather, sunlight history, nutrition refresh) route through. See "Toggleable Network Connection — Options" above — **not started pending your choice of Option A/B/C/D (or another design).** | Infrastructure (not user-facing) | Your decision on which option to use | **Implemented — Awaiting Your Confirmation** (option A) |

### B8. Guided planting and portability (owner request 2026-09-27)

| ID | Feature | Description | Proposed Tier | Depends On | Status |
|---|---|---|---|---|---|
| FR-027 | Plan an area for me | Pick a plot and an area, list what to plant and how many; the app places everything: tall plants away from the midday sun, sun lovers in the sunniest spots, corn in blocks, pollinator plants among the crops that need bees, similar watering needs together, companions side by side. Preview, then plant or cancel. | All | FR-028 (direction), FR-005/006 (sun) | **Implemented — Awaiting Your Confirmation** |
| FR-028 | Plot direction and ZIP | Say which way the plot's top edge faces (creator, direction dialog, Site tab); compass on the layout; warning until set. ZIP fills latitude/longitude offline and the zone offline or online. | All | — | **Implemented — Awaiting Your Confirmation** |
| FR-029 | Portable plan files | Save one or all plots to a `.sgp.json` file and open such files as new plots, on any device. | All | — | **Implemented — Awaiting Your Confirmation** |
| FR-030 | Planner on a computer | No-install browser planner for Windows, macOS, Linux and ChromeOS, sharing the same rules and plan files (docs/CROSS_PLATFORM_PLAN.md, option A). One file: `web/dist/smart-garden-planner.html`. | All | FR-029 | **Implemented — Awaiting Your Confirmation** (tested in Chromium/Linux; other browsers to test) |
| FIX | Obstacles can be moved; undo covers obstacles, areas and outline | From the owner's report: a placed fence, building or tree couldn't be moved or undone. | — | — | **Implemented — Awaiting Your Confirmation** |

---

## Open Questions — Answered

Your answers below, plus what each one means architecturally. **FR-019 and FR-007 both need the
same underlying capability: a network connection the user explicitly controls, not an always-on
one.** See the "Toggleable Network Connection — Options" section immediately after this one for
that design discussion, since you asked to see it before I build anything.

1. **Weather/rainfall data (FR-019):** Deferred as a fully-live feature for now, but you want the
   *plumbing* built now: a temporary, user-killable connection mechanism, not a permanent one.
   Scoped as: build the on/off-switchable network capability now (see options below), wire
   FR-019's actual weather-fetching logic later once that capability exists and you decide to use
   it for this feature specifically.
2. **Historical sunlight hours (FR-007):** Use an external source now, with the connection
   structured so the user can activate/deactivate it. Same underlying capability as #1.
3. **Nutritional data (FR-020):** USDA FoodData Central confirmed. Needs to be "ready to be
   updated when user requests" — read as: the data should be refreshable on demand (likely via
   the same toggleable connection), not just baked in once at build time with no update path.
4. **Vendor links (FR-023/024):** Placeholder structure only for now. I'll build the data model
   and UI slots so a real vendor integration can be dropped in later without restructuring
   anything, but no live vendor site will be linked to yet.
5. **Interplanting groups (FR-009):** Generalize beyond just Three Sisters — support multiple
   named "guild" groups (I'll research and include other well-established real companion-guild
   groupings, not just corn/beans/squash), and make the feature explicitly deactivatable with a
   highly visible on/off indicator, not a buried settings toggle.

---

## Toggleable Network Connection — Options (for your decision before implementation)

You asked for "some sort of connection now that we can deactivate or cut when the user kills a
switch... no need for permanent connections." Here's what that could mean concretely, since
"temporary connection" can point at a few genuinely different designs:

### Option A — Master on/off switch, always-manual
A single Settings toggle ("Enable internet features"). When off (the default), the app never
attempts any network call, full stop — every network-capable feature (weather, sunlight data,
nutrition refresh, future vendor links) silently falls back to its offline behavior (cached data,
or "not available offline" messaging). When on, those features work normally until the user
turns it back off. No automatic timing — purely user-driven.
- **Simplest to build and to explain to a user.** One switch, one mental model.
- Doesn't literally "cut" an active connection mid-use — if the user turns it off while something
  is mid-fetch, that one call finishes, then the switch takes effect for the next call.

### Option B — Per-action confirmation ("connect just this once")
No persistent toggle at all. Every time a feature would need the network (refreshing weather,
pulling a nutrition update, etc.), the app asks first: "This needs an internet connection — allow
it now?" One-time yes/no per action, nothing persisted as "on."
- **Most conservative** — closest to a truly offline-first app that only reaches out when
  explicitly told to, every single time.
- More friction for the user if they use a network feature often (repeated prompts).

### Option C — Session-scoped toggle (hybrid of A and B)
A toggle like Option A, but it resets to off automatically when the app is closed/backgrounded
past some period, rather than staying on indefinitely once enabled. Network access is "on for
this session" rather than "on until I turn it off."
- Balances convenience (don't re-confirm every single action within one sitting) against your
  "no permanent connections" intent (it can't silently stay on forever in the background).
- Slightly more moving parts than A — needs session/lifecycle tracking, not just a stored boolean.

### Option D — Per-feature toggles instead of one master switch
Instead of one global switch, each network-capable feature gets its own toggle (weather: on/off,
sunlight data: on/off, nutrition refresh: on/off). More granular control — e.g. someone could
allow nutrition data refreshes but never weather.
- Most flexible, but more settings-screen surface area and more for the user to think about.
- Can be layered on top of any of A/B/C as the underlying mechanism, rather than being a
  standalone alternative to them.

**My recommendation, open to being overruled:** Option A (simple master switch) as the
underlying mechanism, with Option D's per-feature granularity added only if/when you actually
have more than one or two live network features and it starts to matter. Starting with the
simplest thing that satisfies "not permanent, user controls it" and adding granularity later is
lower-risk than building a more complex system now for features that aren't live yet.

**Either way, technically:** this needs the `INTERNET` Android permission added to the manifest
(currently absent — the app has never made a network call), a small `NetworkGateway`-style class
that every future network-capable feature routes through (so the on/off logic lives in one place,
not duplicated per feature), and a clear on-canvas/on-screen indicator whenever a feature is
actually about to make a live call versus using cached/offline data — consistent with your
general preference (echoed in the FR-009 answer above) for network/live-data states to be
obvious, not silent.

Let me know which option you want (or a different one entirely) before I build the underlying
switch — everything else in this document can proceed without that decision being made yet.

---

## Suggested Build Order (proposed, pending your review)

Given dependencies above, a sensible order would be:
1. **FR-025** (tier flag system) — infrastructure several other items need.
2. **FR-008** (companion names by code→name) — small, contained, immediately useful.
3. **FR-001** (polygon area-select) — reuses existing point-drawing code, contained.
4. **FR-014** (zone-aware recommendations) — makes existing unused catalog data functional.
5. Everything else, roughly in the order listed above within each section.

This is a proposal, not a commitment — reorder however matters most to you.

---

## Change Log for This Document

- **2026-09-27 (computer planner)**: FR-030 built as option A: one portable HTML file (`web/dist/smart-garden-planner.html`)
  that runs in any modern browser, offline, with no install. It uses the same planning code as the phone (compiled
  to JavaScript) and opens/saves the same `.sgp.json` files. Includes plots, planting, paths, obstacles and areas
  with move/undo, shade, Plan an area for me, Harmony, Suggestions, Care and Food.

- **2026-09-27 (guided planting)**: Added and implemented FR-027 (Plan an area for me), FR-028 (plot direction
  and ZIP), FR-029 (portable plan files), and the obstacle move/undo fix. Added FR-030 (computer planner) as
  planned, pending your choice in docs/CROSS_PLATFORM_PLAN.md. New system requirements T2-FUN-160 to 190 and
  T2-PLT-040, with HLRs and LLRs.

- **2026-09-27 (all remaining items)**: On your instruction to implement every pending feature now, FR-002
  to FR-026 were implemented and moved to "Implemented — Awaiting Your Confirmation". Where to find them:
  canvas menu → **Site tools** (plot outline FR-002; sun/shade, flood and slope areas FR-003/004/005;
  trees, fences, walls, buildings FR-006; shade overlay) and **Plot insights** (Site: zone, location,
  orientation, soil FR-013, sunlight FR-007; Harmony FR-011; Suggest FR-014; Care FR-017/018/019;
  Food FR-016/020/021/022). The area tool has **Recommend for this area** (FR-015); the variety picker greys
  out clashing varieties (FR-010); the canvas shows a **GUILDS ON/OFF** badge (FR-009); plant details show
  the vendor slot (FR-023/024). **FR-026:** built as option A (my recommendation above): one master switch
  in Settings → Online features, **off by default**, HTTPS only to Open-Meteo and USDA FoodData Central,
  with a "Connecting…" bar whenever a call is made. System requirement T2-CON-010 was revised from
  "no network connections" back to its original intent, "zero network dependencies" (every feature works
  offline); please review that change. Tier gating stays as proposed: the Pro catalog tier unlocks all of
  them. Yield, nutrition and pest data are bundled reference values (approximate), not lab data.

- **This round**: Answered all 5 open questions (see above). Implemented and delivered: FR-025
  (tier feature-flag system), FR-012 (companion-rule toggle restricted to Pro, with defense-in-depth
  enforcement beyond just the UI), FR-008 (companion names resolved to full names instead of raw
  codes), FR-001 (polygon area-select for auto-populate — tap points, "Finish Area" button from 3+
  points, fills the actual drawn shape via point-in-polygon, not just its bounding rectangle).
  Added FR-026 (toggleable network connection layer) as a new infrastructure item, blocked on your
  choice of design option. Corresponding HLR/LLR entries added to
  `SGP_Knowledge_Base_11_Settings_ZoomPan_Catalog_Requirements.md` for the pre-existing subsystems
  (Settings, Zoom/Pan, Tiered Catalog) that predated this roadmap document.
- **Initial version**: Captured 25 new feature requests plus 2 flagged already-implemented items,
  organized into 7 categories, with proposed tiers, dependencies, and 5 open questions requiring
  your input before certain items could be scoped precisely.
