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

/** Represents a logged deviation from the expected execution of a run step. */
@Entity
@Table(
    name = "deviation",
    indexes = @Index(name = "idx_dev_run_step", columnList = "run_id,step_id"))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Deviation {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @Column(name = "step_id", nullable = false)
  private UUID stepId;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private Category category;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "root_cause", columnDefinition = "text")
  private @Nullable String rootCause;

  @Column(name = "corrective_action", columnDefinition = "text")
  private @Nullable String correctiveAction;

  @Column(name = "linked_capa_id")
  private @Nullable UUID linkedCapaId;

  @Column(name = "logged_at", nullable = false)
  private Instant loggedAt = Instant.now();

  /** Creates an empty deviation for use by the persistence provider. */
  protected Deviation() {}

  /** Creates a deviation with the given identifiers, category, and description. */
  public Deviation(
      UUID id, UUID tenantId, UUID runId, UUID stepId, Category category, String description) {
    this.id = id;
    this.tenantId = tenantId;
    this.runId = runId;
    this.stepId = stepId;
    this.category = category;
    this.description = description;
  }

  /** Returns the deviation identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the identifier of the run this deviation was raised in. */
  public UUID runId() {
    return runId;
  }

  /** Returns when the deviation was logged. */
  public Instant loggedAt() {
    return loggedAt;
  }

  /** Returns whether the deviation is still open (no corrective action recorded). */
  public boolean open() {
    return correctiveAction == null;
  }

  /** Returns the deviation category. */
  public Category category() {
    return category;
  }

  /** Returns the human-readable deviation description. */
  public String description() {
    return description;
  }

  /** Enumerates the possible categories of a deviation. */
  public enum Category {
    OUT_OF_TOLERANCE,
    STEP_SKIPPED,
    EQUIPMENT_FAILURE,
    OPERATOR_ERROR,
    OTHER
  }
}
