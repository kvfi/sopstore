package com.rightcrowd.sopstore.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** Represents a training assignment for a user against a procedure. */
@Entity
@Table(
    name = "training_assignment",
    indexes = {
      @Index(name = "idx_ta_user_state", columnList = "user_id,state"),
      @Index(name = "idx_ta_proc", columnList = "procedure_id")
    })
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class TrainingAssignment {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "procedure_id", nullable = false)
  private UUID procedureId;

  @Column(name = "procedure_version_id")
  private @Nullable UUID procedureVersionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false)
  private Source source;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private State state = State.PENDING;

  @Column(name = "due_at")
  private @Nullable LocalDate dueAt;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt = Instant.now();

  /** Creates an empty assignment for use by the persistence provider. */
  protected TrainingAssignment() {}

  /** Creates a training assignment with the given identifiers, source, and due date. */
  public TrainingAssignment(
      UUID id,
      UUID tenantId,
      UUID userId,
      UUID procedureId,
      @Nullable UUID versionId,
      Source source,
      @Nullable LocalDate dueAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.userId = userId;
    this.procedureId = procedureId;
    this.procedureVersionId = versionId;
    this.source = source;
    this.dueAt = dueAt;
  }

  /** Returns the assignment identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the identifier of the assigned user. */
  public UUID userId() {
    return userId;
  }

  /** Returns the identifier of the assigned procedure. */
  public UUID procedureId() {
    return procedureId;
  }

  /** Returns the current state of the assignment. */
  public State state() {
    return state;
  }

  /** Marks the assignment as completed. */
  public void complete() {
    this.state = State.COMPLETED;
  }

  /** Marks the assignment as overdue. */
  public void overdue() {
    this.state = State.OVERDUE;
  }

  /** Identifies how the assignment was created. */
  public enum Source {
    AUTOMATIC_HIRE,
    AUTOMATIC_ROLE_CHANGE,
    AUTOMATIC_VERSION_CHANGE,
    MANUAL
  }

  /** Represents the lifecycle state of the assignment. */
  public enum State {
    PENDING,
    COMPLETED,
    OVERDUE,
    REVOKED
  }
}
