package com.rightcrowd.sopstore.lifecycle.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** A running approval workflow over a procedure's change request. */
@Entity
@Table(name = "workflow_instance")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class WorkflowInstance {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "workflow_id", nullable = false)
  private UUID workflowId;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "procedure_id")
  private @Nullable UUID procedureId;

  @Column(name = "change_request_id")
  private @Nullable UUID changeRequestId;

  /** Index of the stage currently awaiting decisions; -1 before the first stage opens. */
  @Column(name = "current_stage", nullable = false)
  private int currentStage = -1;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.RUNNING;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt = Instant.now();

  @Column(name = "completed_at")
  private @Nullable Instant completedAt;

  /** Creates an empty instance for use by the persistence provider. */
  protected WorkflowInstance() {}

  /** Creates a running workflow instance for the given change request. */
  public WorkflowInstance(
      UUID id, UUID tenantId, UUID workflowId, UUID procedureId, UUID changeRequestId) {
    this.id = id;
    this.tenantId = tenantId;
    this.workflowId = workflowId;
    this.subjectId = changeRequestId;
    this.procedureId = procedureId;
    this.changeRequestId = changeRequestId;
  }

  /** Returns the instance identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the identifier of the workflow template this instance runs. */
  public UUID workflowId() {
    return workflowId;
  }

  /** Returns the procedure under approval, if set. */
  public @Nullable UUID procedureId() {
    return procedureId;
  }

  /** Returns the originating change request, if set. */
  public @Nullable UUID changeRequestId() {
    return changeRequestId;
  }

  /** Returns the index of the stage currently awaiting decisions. */
  public int currentStage() {
    return currentStage;
  }

  /** Returns the current run status. */
  public Status status() {
    return status;
  }

  /** Advances the pointer to the given stage index. */
  public void moveToStage(int stageIndex) {
    this.currentStage = stageIndex;
  }

  /** Marks the instance approved and complete. */
  public void complete() {
    this.status = Status.APPROVED;
    this.completedAt = Instant.now();
  }

  /** Marks the instance rejected and complete. */
  public void reject() {
    this.status = Status.REJECTED;
    this.completedAt = Instant.now();
  }

  /** Lifecycle status of a workflow instance. */
  public enum Status {
    RUNNING,
    APPROVED,
    REJECTED
  }
}
