package com.rightcrowd.sopstore.execution;

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

/** Represents a single execution run of a procedure by an operator. */
@Entity
@Table(
    name = "procedure_run",
    indexes = @Index(name = "idx_run_op_state", columnList = "operator_id,state"))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class ProcedureRun {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "procedure_id", nullable = false)
  private UUID procedureId;

  @Column(name = "procedure_version_id", nullable = false)
  private UUID procedureVersionId;

  @Column(name = "operator_id", nullable = false)
  private UUID operatorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private State state = State.IN_PROGRESS;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt = Instant.now();

  @Column(name = "completed_at")
  private @Nullable Instant completedAt;

  /** Creates an empty run for use by the persistence provider. */
  protected ProcedureRun() {}

  /** Creates a new procedure run in the in-progress state. */
  public ProcedureRun(UUID id, UUID tenantId, UUID procedureId, UUID versionId, UUID operatorId) {
    this.id = id;
    this.tenantId = tenantId;
    this.procedureId = procedureId;
    this.procedureVersionId = versionId;
    this.operatorId = operatorId;
  }

  /** Returns the run identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the procedure identifier. */
  public UUID procedureId() {
    return procedureId;
  }

  /** Returns the operator identifier. */
  public UUID operatorId() {
    return operatorId;
  }

  /** Returns the current state of the run. */
  public State state() {
    return state;
  }

  /** Returns when the run started. */
  public Instant startedAt() {
    return startedAt;
  }

  /** Returns when the run completed or was abandoned, or null if still in progress. */
  public @Nullable Instant completedAt() {
    return completedAt;
  }

  /** Marks the run as completed and records the completion time. */
  public void complete() {
    this.state = State.COMPLETED;
    this.completedAt = Instant.now();
  }

  /** Marks the run as abandoned and records the completion time. */
  public void abandon() {
    this.state = State.ABANDONED;
    this.completedAt = Instant.now();
  }

  /** Enumerates the possible lifecycle states of a procedure run. */
  public enum State {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
  }
}
