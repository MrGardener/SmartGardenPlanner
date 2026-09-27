# GitHub setup for the pull-request workflow

This implements SGP-SCMP-001 (configuration management) on GitHub. The repository files are already in
place:

| File | Purpose |
|---|---|
| `.github/pull_request_template.md` | Every PR opens with the impact analysis and review checklist (SCMP §6, §8) |
| `.github/ISSUE_TEMPLATE/problem_report.yml` | Problem Report form (SCMP §7) |
| `.github/ISSUE_TEMPLATE/change_request.yml` | Change Request form (SCMP §7) |
| `.github/workflows/requirements-trace.yml` | CI check: T2 → HLR → LLR trace complete and tables up to date |
| `.github/workflows/android-ci.yml` | CI check: build, unit tests and lint, whenever app code changes |
| `tools/req_trace.py` | The trace tool (`--write` regenerates the tables, `--check` verifies them) |

## What only you can switch on (repository settings)

These need the repository owner's account in the GitHub web interface (they can't be set from files).
About 5 minutes.

### 1. Protect the `main` branch

GitHub → the repository → **Settings** → **Rules** → **Rulesets** → **New ruleset** → **New branch ruleset**:

- **Ruleset name:** `main protection`
- **Enforcement status:** Active
- **Target branches:** Add target → **Include default branch** (that's `main`)
- Tick these rules:
  - **Restrict deletions**
  - **Block force pushes**
  - **Require a pull request before merging**
    - **Required approvals: 0** for now. GitHub does not let you approve your own PR, so with 1 required
      approval, a solo owner could never merge. Raise it to 1 once an independent reviewer exists
      (SGP-CB-001 DEV-04 / DW-1507).
    - Tick **Require conversation resolution before merging**
  - **Require status checks to pass**
    - Click **Add checks**, and add **Requirements trace check**. It appears in the list after it has run
      once, which happens on the first PR.
    - Later, after the Phase 1 toolchain upgrade makes it pass, also add **Assemble, unit tests, lint**.
- **Bypass list:** leave empty, so the rules apply to you as well. That is the point of CM.
- Click **Create**.

(Older GitHub screens call this **Settings → Branches → Add branch protection rule**. The same options
apply.)

### 2. Pull request merge options

**Settings** → **General** → **Pull Requests**:
- Allow **merge commits** (keeps the full history of each change) and turn off **squash** and **rebase**
  merging, so the history isn't rewritten (SCMP §5).
- Tick **Automatically delete head branches**.

### 3. Issues and labels

**Settings** → **General** → **Features**: make sure **Issues** is ticked.

**Issues** → **Labels** → create these labels (colours are up to you). The templates apply them
automatically once they exist:
`problem-report`, `change-request`, `status:open`, `status:analysed`, `status:approved`, `status:in-work`,
`status:fixed`, `status:verified`, `status:deferred`, `status:rejected`, `sev:B`, `sev:H`, `sev:M`, `sev:L`.

### 4. Actions

**Settings** → **Actions** → **General**: **Allow all actions and reusable workflows** (or at least GitHub-
and verified-creator actions). Workflow permissions: **Read repository contents** is enough.

## Daily workflow

1. Create or pick an Issue (problem report or change request).
2. Work on a branch (never directly on `main`).
3. Open a PR into `main`, and fill in the template.
4. CI runs the checks. Fix anything red.
5. Review with the checklist. Record "Independence NOT satisfied" until there's an independent reviewer.
6. Merge with a merge commit. The Issue closes if the PR says `Closes #n`.
7. At the end of a phase, tag a baseline (SCMP §5), for example `dev-1`.

## If you edit requirements

After changing anything in `docs/requirements/`, run:

```
python3 tools/req_trace.py --write
```

and commit the updated tables. CI runs `--check` and fails if you forget, or if a requirement has lost its
trace.
