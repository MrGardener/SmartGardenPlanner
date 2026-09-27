# SGP-SQAP-001 — Software Quality Assurance Plan (SQAP)

| Field | Value |
|---|---|
| Document ID | SGP-SQAP-001 |
| Revision | A — **Provisionally adopted** 2026-09-27 by Project Owner direction; formal review pending (SGP-DWR-001 WP-01) |
| DO-178C reference | §8, §11.5, Table A-9; also serves as the ARP4754A **Process Assurance Plan** |
| Replaces | 11-Perspective Audit and Review Board records (withdrawn); T2-GOV-*/HLR-GOV-* process obligations (moved here) |
| Control category | CC1 |

## 1. Purpose

SQA gives confidence that:
- the processes follow the approved plans and standards
- the transition criteria are met
- a conformity review is done before each baseline

## 2. Independence and authority

- SQA must be performed by someone **other than** the developer, with the authority to stop a baseline
  (DO-178C §8.1, Table A-9 independence).
- **Current state:** no independent SQA person exists (SGP-CB-001 DEV-04). Until one is assigned, the
  developer performs self-audits using the checklists below, and every record states "SQA independence:
  NOT satisfied". The Table A-9 objectives stay open in the SAS.

## 3. SQA activities

| Activity | When | Output |
|---|---|---|
| Plan and standard review | Before approval of each plan/standard | SQA record |
| Process audits | At least once per REVAMP phase, and for every baseline | Audit record with findings |
| Transition criteria audit | Sampled per increment (SDP §4) | Audit record |
| Problem report audit | Per baseline: every PR has analysis, disposition and verification | Audit record |
| Deviation audit | Per baseline: deviations match SGP-CB-001 and are reported | Audit record |
| Conformity review | Before every `rel-` baseline and the SAS | Conformity review record |
| Tool/environment audit | Per baseline: SECI matches the environment actually used | Audit record |

## 4. Audit checklists (summary)

**Process audit:**
- [ ] Work followed the SDP phases/transition criteria
- [ ] Reviews used the SVP checklists
- [ ] Review records exist for the sampled items and identify the version reviewed
- [ ] Independence recorded correctly
- [ ] PRs raised for all findings

**Conformity review:**
- [ ] Life cycle data complete per the PSAC §5 list, and at the correct revisions (SCI)
- [ ] Trace report shows no orphans
- [ ] Verification results exist for every test/analysis in the SVP scope
- [ ] Coverage gaps resolved
- [ ] EOC hash matches the SCI
- [ ] SECI matches the build environment
- [ ] Open PRs dispositioned
- [ ] Deviations listed
- [ ] No compliance claim in any document lacks supporting records (the lesson from the withdrawn
      "Review Board" documents)

## 5. Records

`docs/sqa/records/<date>-<type>-<topic>.md`, CC2. Each record contains:
- the scope and the items/versions examined
- the checklist results
- the findings (linked PRs)
- the auditor, with an independence statement
- the date

## 6. Process obligations moved from the product requirements

- *Former T2-GOV-010 / HLR-GOV-010 / LLR-GOV-010-A ("11-perspective audits"):* replaced by the audits in §3.
  A multi-perspective review (architecture, security, UX, agronomy, test) may still be used as an input to
  design reviews, but only with named reviewers and records.
- *Former T2-GOV-020 / HLR-GOV-020 / LLR-GOV-020-A ("review artifacts at handoff"):* replaced by baselines
  and conformity reviews (SCMP §5, §3 above).
