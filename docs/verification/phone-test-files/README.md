# Phone test files

Plan files for the phone checks in `../PIXEL_10_PRO_CHECKLIST.md` and SGP-TPR-001. On the phone, open this folder
on github.com, tap a file, then "Download raw file", and open it in the app with **Open plan file**.

| File | Used by | What it holds | What the app should do |
|---|---|---|---|
| `unknown-variety.sgp.json` | TP-14.7 | A 6 × 4 m plot with one Brandywine tomato and one plant whose code (`GONE-001`) is in no catalog | Open the plot with the tomato only and say 1 plant was skipped |
| `damaged.sgp.json` | TP-21.9 | A plan file cut off in the middle | Refuse it with a message, import nothing, and record IMPORT_REJECTED in the audit log |
