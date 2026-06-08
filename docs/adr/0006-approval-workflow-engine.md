# ADR-0006: Configurable approval workflow engine

- Status: **Accepted**
- Date: 2026-05-31
- Relates to: ADR-0004 (lifecycle state machine), ADR-0003 (tenancy)

## Context

Regulated change control requires more than the linear lifecycle state machine
(ADR-0004): a change to a controlled procedure must go through a configurable,
multi-stage approval — sequential stages, several approvers in parallel within a
stage, and conditional stages (e.g. a QA Director sign-off only for *major* or
training-impacting changes) — each approval a 21 CFR Part 11 e-signature. The
state machine deliberately does **not** model this; it only gates the
`InReview → Approved` transition.

## Decision

A `WorkflowService` in `lifecycle.internal` drives approvals over a
`ChangeRequest`:

- A **workflow** is an ordered list of `WorkflowStage`s stored as
  `workflow.stages_json` (tenant-editable; a code default is seeded on first
  use). Each stage carries approver roles, the e-signature `meaning`, an SLA, and
  a `StageCondition` (`ALWAYS` / `CLASSIFICATION_MAJOR` / `TRAINING_IMPACT` /
  `MAJOR_OR_TRAINING_IMPACT`).
- Stages run **sequentially**; within a stage one `WorkflowTask` is opened per
  approver role and **all must approve** (parallel, all-required).
- Approving a task requires a **fresh password challenge** (`ReauthService`) and
  mints a `Signature` bound to the procedure's canonical current version — reusing
  the existing Part 11 primitives rather than inventing a second signature path.
- When the last *applicable* stage completes, the engine drives the lifecycle
  `Approve` transition via `LifecycleService.approveViaWorkflow` (no second
  signature — the stage signatures are the record). Any rejection sends the
  procedure back to `Draft` and closes the change request `REJECTED`.
- An hourly `WorkflowEscalationJob` notifies assignees of overdue tasks (idempotent
  via an `escalated` flag).

## Rationale

- Stage definitions as data (JSON) give tenant configurability without code
  changes, while a code default keeps the common path zero-config.
- Reusing `ReauthService` + `Signature` keeps one auditable signature mechanism.
- The engine **gates** the state machine rather than replacing it (per ADR-0004's
  consequence note), so illegal transitions remain compiler-impossible.
- Multi-role-per-stage models "parallel" approvals without a separate mode flag;
  sequencing is just stage order; conditionality is one enum per stage.

## Tests / verification

- Verified end-to-end (headless): MINOR → 2 stages → `APPROVED` with
  `REVIEWED,APPROVED` signatures; MAJOR+training → 3 stages incl. the conditional
  QA Director sign-off; rejection → `Draft` + CR `REJECTED`; wrong password → 403
  with no signature and no transition.
- Static gates (Checkstyle/SpotBugs/ErrorProne/NullAway) and `ModulithVerify` pass;
  lifecycle's new `notification :: notification-port` dependency is declared.

## Consequences

- Cross-module reads for dashboards go through a new exposed `LifecycleApi`
  (open CRs, pending approvals) rather than the internal repositories.
- The escalation job runs without tenant context today (like `PeriodicReviewJob`);
  per-tenant iteration is a follow-up.
- The boundary API still passes the `Procedure`/`ChangeRequest` shapes the spec
  would eventually want as DTOs in a couple of places — tracked in PHASE-STATUS.
