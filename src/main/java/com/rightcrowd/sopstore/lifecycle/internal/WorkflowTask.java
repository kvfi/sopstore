package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.lifecycle.SignatureMeaning;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * A single approval task: one approver role's decision on one workflow stage. Approving a task
 * captures a Part 11 e-signature ({@link #signatureId()}). All tasks of a stage must be approved
 * before the workflow advances; any rejection fails the whole instance.
 */
@Entity
@Table(
    name = "workflow_task",
    indexes = {
      @Index(name = "idx_wftask_queue", columnList = "assignee_role,status"),
      @Index(name = "idx_wftask_instance", columnList = "instance_id,stage_index")
    })
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class WorkflowTask {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "instance_id", nullable = false)
  private UUID instanceId;

  @Column(name = "procedure_id", nullable = false)
  private UUID procedureId;

  @Column(name = "change_request_id", nullable = false)
  private UUID changeRequestId;

  @Column(name = "stage_index", nullable = false)
  private int stageIndex;

  @Column(name = "stage_name", nullable = false)
  private String stageName;

  @Enumerated(EnumType.STRING)
  @Column(name = "assignee_role", nullable = false)
  private Role assigneeRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "meaning", nullable = false)
  private SignatureMeaning meaning;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.PENDING;

  @Column(name = "decided_by")
  private @Nullable UUID decidedBy;

  @Column(name = "decided_at")
  private @Nullable Instant decidedAt;

  @Column(name = "signature_id")
  private @Nullable UUID signatureId;

  @Column(name = "reason", columnDefinition = "text")
  private @Nullable String reason;

  @Column(name = "due_at")
  private @Nullable Instant dueAt;

  @Column(name = "escalated", nullable = false)
  private boolean escalated = false;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty task for use by the persistence provider. */
  protected WorkflowTask() {}

  /** Creates a pending task for one role on one stage of a workflow instance. */
  public WorkflowTask(
      UUID id,
      UUID tenantId,
      UUID instanceId,
      UUID procedureId,
      UUID changeRequestId,
      int stageIndex,
      String stageName,
      Role assigneeRole,
      SignatureMeaning meaning,
      @Nullable Instant dueAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.instanceId = instanceId;
    this.procedureId = procedureId;
    this.changeRequestId = changeRequestId;
    this.stageIndex = stageIndex;
    this.stageName = stageName;
    this.assigneeRole = assigneeRole;
    this.meaning = meaning;
    this.dueAt = dueAt;
  }

  /** Returns the task identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the workflow instance this task belongs to. */
  public UUID instanceId() {
    return instanceId;
  }

  /** Returns the procedure under approval. */
  public UUID procedureId() {
    return procedureId;
  }

  /** Returns the originating change request. */
  public UUID changeRequestId() {
    return changeRequestId;
  }

  /** Returns the index of the stage this task belongs to. */
  public int stageIndex() {
    return stageIndex;
  }

  /** Returns the human-readable stage name. */
  public String stageName() {
    return stageName;
  }

  /** Returns the role permitted to act on this task. */
  public Role assigneeRole() {
    return assigneeRole;
  }

  /** Returns the e-signature meaning recorded when this task is approved. */
  public SignatureMeaning meaning() {
    return meaning;
  }

  /** Returns the current task status. */
  public Status status() {
    return status;
  }

  /** Returns the e-signature captured when this task was approved, if any. */
  public @Nullable UUID signatureId() {
    return signatureId;
  }

  /** Returns the time after which this pending task is overdue, if set. */
  public @Nullable Instant dueAt() {
    return dueAt;
  }

  /** Returns whether this task has already been escalated for being overdue. */
  public boolean escalated() {
    return escalated;
  }

  /** Records an approval decision bound to the given signature. */
  public void approve(UUID actor, UUID signatureId) {
    this.status = Status.APPROVED;
    this.decidedBy = actor;
    this.signatureId = signatureId;
    this.decidedAt = Instant.now();
  }

  /** Records a rejection decision with the given reason. */
  public void reject(UUID actor, String reason) {
    this.status = Status.REJECTED;
    this.decidedBy = actor;
    this.reason = reason;
    this.decidedAt = Instant.now();
  }

  /** Marks this task as escalated so a single overdue task is not re-escalated each scan. */
  public void markEscalated() {
    this.escalated = true;
  }

  /** Lifecycle status of an approval task. */
  public enum Status {
    PENDING,
    APPROVED,
    REJECTED
  }
}
