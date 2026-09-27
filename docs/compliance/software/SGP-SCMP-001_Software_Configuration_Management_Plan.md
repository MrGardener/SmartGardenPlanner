# SGP-SCMP-001 — Software Configuration Management Plan (SCMP)

| Field | Value |
|---|---|
| Document ID | SGP-SCMP-001 |
| Revision | A (Draft) |
| DO-178C reference | §7, §11.4, Table A-8; ARP4754A configuration management (system-level CM is also covered by this plan) |
| Replaces | The "exit protocol / save files" procedures in the master plan, `smart_garden_planner_automated_handoff.md`, `rename_gemini_files.py` |
| Control category | CC1 |

## 1. Scope

This plan covers all life cycle data in the PSAC §5 table, the source code, build scripts, PDI files,
development environment definitions (SECI) and the executable object code, for both the system-level
(ARP4754A) and the software-level (DO-178C) data.

## 2. CM environment

| Function | Implementation |
|---|---|
| Version control | Git; the GitHub repository `mrgardener/smartgardenplanner` is the master archive |
| Change requests / problem reports | GitHub Issues with the templates and labels in §7 |
| Change review | GitHub pull requests with a mandatory checklist (§8) |
| Baselines | Annotated, signed git tags (§5) |
| Build | Gradle wrapper with a verified checksum; dependency verification metadata committed (DW-0509) |
| Backup | GitHub remote plus one offline clone refreshed at each baseline |

## 3. Configuration identification

- **Documents:** ID `SGP-<type>-<nnn>`, revision letter (A, B, …), shown in the document header. The
  revision history comes from git.
- **Requirements:** IDs are permanent and never reused (SGP-SRS-STD-001).
- **Source:** identified by path and git hash. A release is identified by `versionName` (semantic version)
  plus `versionCode`, which is monotonically increasing and bumped for every build given to testing.
- **PDI files:** identified by filename, a version header line, and the SHA-256 hash recorded in the SCI.
- **Executable object code:** the signed APK/AAB, identified by its SHA-256 hash in the SCI.

## 4. Control categories

| Category | Meaning | Applies to |
|---|---|---|
| **CC1** | Full problem reporting, change control, baselines and change review | Plans, standards, requirements, design, source, PDI, EOC, test cases/procedures, trace data, SCI/SECI, SAS |
| **CC2** | Identification, protection against unauthorised change, retrieval (no formal change review) | Verification results, review/SQA/SCM records, problem reports |

## 5. Baselines

| Baseline | When | Content |
|---|---|---|
| `plan-A` | End of Phase 0 | Approved plans and standards |
| `dev-<n>` | End of each increment | Requirements, design, source, tests and results for the increment |
| `rel-<version>` | Each release to the target for credit | Everything; SCI + SECI generated |

Each baseline tag message records:
- the approval (Project Owner)
- the SQA conformity check result
- open Problem Reports with their disposition

Baselines are never moved or deleted. History on the protected branch is never rewritten (no force-push).

## 6. Change control

1. Every change to a CC1 item starts from an Issue: a Problem Report or a Change Request.
2. The change is made on a branch and submitted as a pull request that links the Issue.
3. The PR description includes an **impact analysis**: affected requirements, design, code, tests,
   analyses, PDI and documents, and the regression needed (SVP §9).
4. Review per §8. The merge needs: an approving review (independent where the SVP requires it), green CI,
   and a completed checklist.
5. Status accounting: the Issue records the resolution and links the merge commit.

Dependency and toolchain changes (Gradle, AGP, Kotlin, AndroidX, SQLCipher, JaCoCo…) follow the same
process. They also require a SECI update and, for security-relevant libraries, a review of the release
notes.

## 7. Problem reporting

Issue templates:
- **Problem Report (PR):**
  - description
  - how found (review/test/analysis/field)
  - affected items and versions
  - severity (safety effect per the FHA classification, or functional B/H/M/L)
  - analysis
  - corrective action
  - verification of the fix
- **Change Request (CR):** a requested change, rationale, affected requirements.

States: `Open → Analysed → Approved → In work → Fixed (awaiting verification) → Verified → Closed`, or
`Deferred (with rationale)` / `Rejected (with rationale)`. **Every** open or deferred PR at a baseline is
listed in the SCI, and at the end in the SAS.

The Deferred Work Register (SGP-DWR-001) items are converted to Issues when this plan is approved (DW-1501).

## 8. Pull request review checklist (in the PR template)

- [ ] Linked Issue; impact analysis complete
- [ ] Requirements/design updated before or with the code; trace annotations present
- [ ] Code standard and static analysis clean (or justified)
- [ ] Tests added/updated and traced; CI green
- [ ] No untraced code added; dead code removed
- [ ] Documentation updated (manual, CHANGELOG, SECI if tools changed)
- [ ] AI-assisted content identified and fully reviewed (SGP-CB-001 §6)
- [ ] Reviewer independence statement

## 9. Archive, retrieval and release

- Baselines must be retrievable and rebuildable: a checkout of the tag plus the SECI toolchain rebuilds the
  EOC. Byte-identity is shown, or the differences are explained (DW-0509).
- Release APKs, mapping files (R8), signing certificate fingerprints, test reports and coverage reports for
  each `rel-` baseline are attached to a GitHub Release, and copied to the offline archive.
- The signing keystore is kept outside the repository. Its fingerprint is recorded in the SCI.

## 10. Configuration indexes

- **SECI** (`docs/cm/SECI-<baseline>.md`):
  - host OS
  - Android Studio version
  - JDK
  - Gradle, AGP, Kotlin, KSP
  - all dependency versions (generated from Gradle)
  - emulator images
  - target device model, Android version, build number, security patch
  - static analysis and coverage tool versions
- **SCI** (`docs/cm/SCI-<baseline>.md`):
  - baseline tag and commit
  - list of life cycle data with revisions
  - PDI hashes
  - EOC hash
  - open PRs
  - deviations

## 11. Records

SCM records are the git history, tags, PRs and Issues, plus the baseline records described above. The
master-plan "version increment every iteration" directive is replaced by this plan: versions change only
through §6.
