# Smart Garden Planner

Offline Android garden planner (plots, to-scale plant layout, spacing and companion rules, harvest and
germination tracking), developed under a DO-178C / ARP4754A DAL A process.

## Where things are

| Folder | Contents |
|---|---|
| `Android App/` | **The Android Studio project.** Open *this* folder in Android Studio (File → Open → `Android App`), not the repository root. |
| `web/` | **Portable planner for computers**: `web/dist/smart-garden-planner.html` is the whole app in one file. Double-click it; no install. See `web/README.md`. |
| `docs/requirements/` | System requirements (T2), software high-level (HLR) and low-level (LLR) requirements |
| `docs/compliance/` | Plans, standards, deferred work register (start with `README.md` there) |
| `docs/cm/` | GitHub / configuration-management setup |
| `tools/` | `req_trace.py` — requirements trace generator and checker |
| `REVAMP_PLAN_Pixel10Pro.md` | Revamp plan for the Pixel 10 Pro |

## Build requirements

Android Studio with the bundled JetBrains Runtime 21, Android SDK platforms 37 (compile) and 36 (target).
The Gradle wrapper downloads Gradle 9.8.0 on first sync. CI builds the same project on every pull request
(`.github/workflows/android-ci.yml`).
