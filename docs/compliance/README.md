# Compliance Document Set — Smart Garden Planner

This folder holds the rewritten planning, standards and tracking documents for developing Smart Garden Planner
under the **DO-178C Level A / ARP4754A DAL A** objectives (elected rigor, self-assessed; see SGP-CB-001).

**Read first:** `SGP-CB-001` (what the compliance claim means, and the deviations), then `SGP-DWR-001` (everything
still to be done).

## Index

| Document | Title | DO-178C / ARP4754A role | Status |
|---|---|---|---|
| [SGP-CB-001](SGP-CB-001_Compliance_Basis_and_Deviations.md) | Compliance Basis, Applicability and Deviations | Basis for all plans | Draft A |
| [SGP-GAP-001](SGP-GAP-001_Existing_Document_Assessment.md) | Assessment of existing plans, documents and requirements | Gap analysis (DO-178C §12.1.4 input) | Draft A |
| [SGP-RSM-001](SGP-RSM-001_Requirements_Status_Matrix.md) | Requirements implementation status (T2 + HLR) | Baseline assessment | Draft A |
| [SGP-DWR-001](SGP-DWR-001_Deferred_Work_Register.md) | Deferred Work Register (195 items) | Work tracking until the PR system is live | Draft A |
| **System level (ARP4754A)** | | | |
| [SGP-SDCP-001](system/SGP-SDCP-001_System_Development_and_Certification_Plan.md) | System Development & Certification Plan | Certification plan + development plan | Draft A |
| [SGP-SAP-001](system/SGP-SAP-001_Safety_Assessment_Plan_and_Preliminary_FHA.md) | Safety Assessment Plan + preliminary FHA | Safety program plan (ARP4761A) | Draft A |
| [SGP-SVVP-001](system/SGP-SVVP-001_System_Validation_and_Verification_Plan.md) | System Validation & Verification Plan | Validation plan + verification plan | Draft A |
| **Software level (DO-178C)** | | | |
| [SGP-PSAC-001](software/SGP-PSAC-001_Plan_for_Software_Aspects_of_Certification.md) | PSAC | §11.1 | Draft A |
| [SGP-SDP-001](software/SGP-SDP-001_Software_Development_Plan.md) | Software Development Plan | §11.2 (replaces the Integration Plan) | Draft A |
| [SGP-SVP-001](software/SGP-SVP-001_Software_Verification_Plan.md) | Software Verification Plan | §11.3 (replaces the v20.19 Verification Plan) | Draft A |
| [SGP-SCMP-001](software/SGP-SCMP-001_Software_Configuration_Management_Plan.md) | SCM Plan (system + software) | §11.4 + ARP4754A CM plan | Draft A |
| [SGP-SQAP-001](software/SGP-SQAP-001_Software_Quality_Assurance_Plan.md) | SQA Plan (system + software) | §11.5 + ARP4754A process assurance plan | Draft A |
| [SGP-SRS-STD-001](software/SGP-SRS-STD-001_Software_Requirements_Standard.md) | Requirements Standard | §11.6 | Draft A |
| [SGP-SDS-001](software/SGP-SDS-001_Software_Design_Standard.md) | Design Standard | §11.7 | Draft A |
| [SGP-SCS-001](software/SGP-SCS-001_Software_Code_Standard.md) | Code Standard (Kotlin) | §11.8 | Draft A |
| [SGP-TQP-001](software/SGP-TQP-001_Tool_Qualification_Plan.md) | Tool Assessment & Qualification Plan | §12.2 / DO-330 | Draft A |

## Life cycle data still to be produced (tracked in SGP-DWR-001)

Software requirements data (§11.9) · Design Description (§11.10) · Verification cases & procedures
(§11.13) · Verification results (§11.14) · SECI/SCI (§11.15/11.16) · Problem Reports (§11.17) ·
SCM/SQA records (§11.18/11.19) · Trace data (§11.21) · PDI specification (§11.22) · FHA/PSSA/CCA/SSA
records · validation matrix · system verification matrix · Software Accomplishment Summary (§11.20).

## Superseded documents (repository root)

Withdrawn as life cycle data (SGP-GAP-001). They are kept for history, carry a supersession notice, and move
to `docs/archive/` at the first baseline (DW-1505):
- `Smart_Garden_Planner_Verification_Plan_and_Test_Specification_v20_19.md`
- `Smart_Garden_Planner_Integration_Plan_v20_19.md`
- `Smart_Garden_Planner_Review_Board_Audit_Record_v20_19.md`
- `smart_garden_planner_audit_report.md`
- IDD/ICD documents (to be rewritten as part of the design description)

## Approval

All documents are **Draft A**. They become effective when the Project Owner approves them (DW-0101…DW-0105).
Until an independent reviewer exists, approvals are recorded with "independence not satisfied" (SGP-CB-001
DEV-04).
