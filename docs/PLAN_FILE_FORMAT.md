# Smart Garden plan file — format version 1

| | |
|---|---|
| Document | SGP-PFF-001 |
| Status | Draft, 2026-09-27 |
| Implements | T2-DAT-110, T2-PLT-020; HLR-PORT-010/020; LLR-PFILE-010…030 |
| Code | `core/PlanFile.kt` (`PlanFileCodec`), tests in `AutoPlanAndPlanFileTest` |

A plan file holds one or more garden plots, with everything needed to show and edit them on another
device: the Android app today, and the browser planner for computers later (see `CROSS_PLATFORM_PLAN.md`).

## 1. File basics

- **Encoding:** UTF-8 JSON.
- **Extension:** `.sgp.json`. Any file name is accepted when opening.
- **Size limit:** 20 MB.
- **Units:** lengths in metres, angles in compass degrees (0 = north, clockwise), dates in ISO-8601 UTC
  (`2026-09-27T12:00:00Z`).
- **Coordinates:** plot-relative metres. The origin (0, 0) is the plot's top-left corner as drawn on screen;
  x runs to the right, y runs down. Every point in a plot must satisfy 0 ≤ x ≤ lengthM and 0 ≤ y ≤ widthM.
- **Optional fields** may be missing. Readers must ignore unknown fields, so newer writers can add fields
  without breaking older readers of the same version.

## 2. Top level

| Field | Type | Required | Meaning |
|---|---|---|---|
| `format` | string | yes | Always `"smart-garden-plan"`. |
| `version` | integer | yes | `1`. A reader must refuse versions newer than it supports. |
| `exportedAt` | string (date) | no | When the file was written. |
| `app` | string | no | Writer name and version. |
| `units` | string | no | Always `"metres"`. |
| `plots` | array of Plot | yes | 1–100 plots. |
| `customVarieties` | array of Variety | no | User-made varieties used by the plants in this file. |

## 3. Plot

| Field | Type | Required | Meaning / range |
|---|---|---|---|
| `name` | string | yes | Up to 80 characters. |
| `description` | string | no | Up to 500 characters. |
| `lengthM`, `widthM` | number | yes | 0 < value ≤ 1000. The plot's bounding rectangle. |
| `outline` | array of [x, y] | no | Custom shape (3+ corners, in order, edges not crossing), inside the rectangle. |
| `orientation.topFacesDeg` | number | no | Compass bearing that the plot's top edge faces (0–360). |
| `orientation.set` | boolean | no | `true` once the user has confirmed the direction. |
| `location.zip` | string | no | 5-digit US ZIP. |
| `location.latitude`, `.longitude` | number | no | −90…90, −180…180. |
| `location.hardinessZone` | string | no | USDA zone, `"1a"` … `"13b"`. |
| `soil.sandPct`, `.siltPct`, `.clayPct`, `.organicPct` | number | no | 0–100. |
| `soil.ph` | number | no | 3–10. |
| `createdAt`, `modifiedAt` | string (date) | no | |
| `plants` | array of Plant | no | Up to 5000. |
| `paths` | array of Path | no | No-plant paths, up to 2000. |
| `siteFeatures` | array of SiteFeature | no | Obstacles and marked areas, up to 2000. |
| `history` | array of PastPlanting | no | Plants of finished seasons (FR-033), up to 20,000. Omitted when empty; readers that don't know it ignore it. |
| `pests` | array of string | no | Pests seen in the yard (FR-042): `DEER`, `RABBIT`, `RACCOON`, `SQUIRREL`, `GROUNDHOG`, `GOPHER`, `VOLE`, `CHIPMUNK`, `SKUNK`, `ARMADILLO`, `FERAL_HOG`, `PETS`, `BIRDS`, `SLUGS`, `APHIDS`, `CABBAGE_WORMS`, `HORNWORMS`, `SQUASH_PESTS`, `BEETLES`, `MOLES`. Unknown names are ignored. Added 2026-09-27, still version 1. |
| `backdrop` | object | no | Satellite photo (FR-046): `image` (data URL, `data:image/jpeg|png|webp;base64,…`, at most 4,000,000 characters), `x`, `y` (top-left corner, metres, within ±10 km), `widthM` (1–2000), `aspect` (height ÷ width, 0.05–20), `rotationDeg` (clockwise about the corner), `opacity` (0.1–1), `visible`. Dropped with a warning if the image or placement is invalid. Added 2026-09-27, still version 1. |

## 4. Plant

| Field | Type | Required | Meaning |
|---|---|---|---|
| `code` | string | yes | Catalog code, e.g. `"TOM-001"`. |
| `variety` | string | no | Full variety name, e.g. `"Tomato - Brandywine"`. Used to match when the reader's catalog lacks the code. |
| `x`, `y` | number | yes | Position inside the plot. |
| `plantedAt` | string (date) | no | Planting date. |
| `germinationResolved` | boolean | no | A germination problem was dealt with. |

## 5. Path

Either a rectangle:

```json
{ "type": "RECTANGLE", "x": 0, "y": 4.5, "widthM": 6, "heightM": 0.5, "label": "Main path" }
```

or a line through 2 or more points with a width:

```json
{ "type": "POLYLINE", "points": [[0, 1], [3, 2], [6, 1.5]], "widthM": 0.6 }
```

## 6. SiteFeature

| `type` | Points | Other fields |
|---|---|---|
| `FULL_SUN`, `PART_SHADE`, `FULL_SHADE` | 3+ (area) | `label` |
| `FLOOD` | 3+ (area) | `floodMonths`: array of 1–12 |
| `SLOPE` | 3+ (area) | `slopeDirectionDeg` (downhill, 0–360), `slopeGradePct` (0–100) |
| `TREE` | 1 (trunk) | `heightM` (0–100), `radiusM` (crown, 0–30) |
| `FENCE`, `WALL` | 2+ (line) | `heightM` (0–100) |
| `BUILDING` | 2+ (outline; closed when 3+) | `heightM` (0–100) |

### Irrigation site features (added 2026-09-27, still format version 1)

`type` may also be `SPRINKLER` (1 point; `radiusM` = throw radius; `arcWidthDeg` 10–360, default 360; `arcCentreDeg`
= compass bearing of the arc's middle), `DRIP_LINE` (2+ points; `radiusM` = wetted half-width) or `HOSE_BIB` (1 point;
`radiusM` = hose length). `radiusM` is read in 0–60 m. Readers that don't know these types skip them.

## 6b. PastPlanting (history) — added 2026-09-27, still format version 1

| Field | Type | Required | Meaning |
|---|---|---|---|
| `season` | integer | yes | Season year, 1900–3000. |
| `code` | string | yes | Catalog code of the variety at the time. |
| `variety` | string | no | Variety name (kept so the record reads even if the code is unknown). |
| `family` | string | no | Botanical family, e.g. "Solanaceae". |
| `rotationGroup` | string | no | LEGUMES, BRASSICAS, NIGHTSHADES, CUCURBITS, GRAINS, ALLIUMS, ROOTS, BEETS or LETTUCE. |
| `x`, `y` | number | yes | Position in metres; must be inside the plot. |
| `radiusM` | number | no | Spacing radius, (0, 50] m; default 0.3. |
| `plantedAt` | string | no | ISO-8601 UTC. |

The field is optional and additive, so the format version stays 1: an older reader opens the file and simply
doesn't see the history.

## 7. Variety (customVarieties)

| Field | Type | Meaning |
|---|---|---|
| `code`, `name` | string | Required. |
| `family`, `plantType` (`VEGETABLE`/`FRUIT`/`HERB`/`FLOWER`/`ORNAMENTAL`), `lifecycle` (`ANNUAL`/`PERENNIAL`) | string | |
| `zoneMin`, `zoneMax` | integer 1–13 | |
| `radiusM` | number, 0 < r ≤ 50 | Required. Spacing radius. |
| `germinationDays` (1–365), `daysToHarvest` (1–3650) | integer | |
| `companions`, `antagonists` | string | Comma-separated species codes. |
| `pests`, `care`, `colorHex` | string | |

## 8. Reading rules (what the Android app does)

1. Refuse the whole file if it isn't JSON, `format` is wrong, `version` is missing or newer, `plots` is missing,
   or there are more than 100 plots. Nothing is stored.
2. Otherwise range-check every value. Skip and report anything invalid (a plot with a bad size, a plant outside
   the plot, a malformed obstacle). Drop out-of-range optional values.
3. Store each plot as a **new** plot. Existing plots are never overwritten; a name clash gets " (imported)".
4. Match each plant's variety by code, then by name; skip and count plants whose variety is unknown.
5. Do all of this in one database transaction.

## 9. Example

```json
{
  "format": "smart-garden-plan",
  "version": 1,
  "exportedAt": "2026-09-27T12:00:00Z",
  "app": "Smart Garden Planner 21.0.0-dev",
  "units": "metres",
  "plots": [{
    "name": "Back yard",
    "lengthM": 6, "widthM": 4,
    "orientation": { "topFacesDeg": 0, "set": true },
    "location": { "zip": "60601", "latitude": 41.886, "longitude": -87.618, "hardinessZone": "6a" },
    "plants": [
      { "code": "COR-001", "variety": "Sweet Corn - Silver Queen", "x": 1.2, "y": 0.6, "plantedAt": "2026-05-10T00:00:00Z" },
      { "code": "LET-001", "variety": "Lettuce - Buttercrunch", "x": 1.0, "y": 3.4 }
    ],
    "paths": [{ "type": "RECTANGLE", "x": 0, "y": 2, "widthM": 6, "heightM": 0.4 }],
    "siteFeatures": [{ "type": "TREE", "points": [[5.5, 0.5]], "heightM": 6, "radiusM": 2 }],
    "history": [{ "season": 2025, "code": "TOM-001", "variety": "Tomato - Brandywine", "family": "Solanaceae", "rotationGroup": "NIGHTSHADES", "x": 1.0, "y": 0.8, "radiusM": 0.61 }]
  }],
  "customVarieties": []
}
```

## 10. Open points

- **Photos** (T2-DAT-110) aren't included yet. A later version may add them inside a zip or as base64.
- **Signing** (HLR-EXP-020) isn't done yet. Files are plain text and can be edited by hand.
- **Encryption:** files leave the app's encrypted storage. An optional passphrase (AES-GCM, supported on Android
  and in browsers) is proposed for version 2.
