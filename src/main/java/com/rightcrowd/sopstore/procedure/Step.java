package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** A single step within a procedure version. */
@Entity
@Table(
    name = "procedure_step",
    indexes = {
      @Index(name = "idx_step_version_order", columnList = "procedure_version_id,order_index")
    })
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Step {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "procedure_version_id", nullable = false)
  private UUID procedureVersionId;

  @Column(name = "parent_step_id")
  private @Nullable UUID parentStepId;

  @Column(name = "order_index", nullable = false)
  private int orderIndex;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "instruction", nullable = false, columnDefinition = "text")
  private String instruction;

  @Column(name = "expected_outcome", columnDefinition = "text")
  private @Nullable String expectedOutcome;

  @Column(name = "warning", columnDefinition = "text")
  private @Nullable String warning;

  /**
   * JSON specification of evidence the step demands. Shape: {@code {kind: "PHOTO" | "SIGNATURE" |
   * "MEASUREMENT" | "FILE" | "TEXT" | ..., unit?, lowerBound?, upperBound?, options?}}.
   */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "evidence_spec", columnDefinition = "jsonb")
  private @Nullable String evidenceSpecJson;

  @Column(name = "estimated_minutes")
  private @Nullable Integer estimatedMinutes;

  @Column(name = "responsible_role")
  private @Nullable String responsibleRole;

  /** Creates an empty step for use by the persistence provider. */
  protected Step() {}

  /** Creates a step with the given identity, ordering, title, and instruction. */
  public Step(
      UUID id,
      UUID tenantId,
      UUID procedureVersionId,
      int order,
      String title,
      String instruction) {
    this.id = id;
    this.tenantId = tenantId;
    this.procedureVersionId = procedureVersionId;
    this.orderIndex = order;
    this.title = title;
    this.instruction = instruction;
  }

  /** Returns the step identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the identifier of the procedure version this step belongs to. */
  public UUID procedureVersionId() {
    return procedureVersionId;
  }

  /** Returns the order index of the step. */
  public int order() {
    return orderIndex;
  }

  /** Returns the step title. */
  public String title() {
    return title;
  }

  /** Returns the step instruction. */
  public String instruction() {
    return instruction;
  }

  /** Returns the expected outcome, if specified. */
  public @Nullable String expectedOutcome() {
    return expectedOutcome;
  }

  /** Returns the warning/caution, if specified. */
  public @Nullable String warning() {
    return warning;
  }

  /** Returns the evidence specification JSON ({@code {kind, unit?, lowerBound?, upperBound?}}). */
  public @Nullable String evidenceSpec() {
    return evidenceSpecJson;
  }

  /** Sets the execution order index of this step. */
  public void setOrder(int order) {
    this.orderIndex = order;
  }

  /** Sets the expected outcome. */
  public void setExpectedOutcome(@Nullable String value) {
    this.expectedOutcome = value;
  }

  /** Sets the warning/caution. */
  public void setWarning(@Nullable String value) {
    this.warning = value;
  }

  /** Sets the evidence specification JSON. */
  public void setEvidenceSpec(@Nullable String json) {
    this.evidenceSpecJson = json;
  }
}
