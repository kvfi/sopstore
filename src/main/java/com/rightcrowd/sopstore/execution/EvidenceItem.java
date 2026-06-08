package com.rightcrowd.sopstore.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** Persistent record of a single piece of evidence captured for a step run. */
@Entity
@Table(
    name = "evidence_item",
    indexes = @Index(name = "idx_ev_run_step", columnList = "run_id,step_id"))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class EvidenceItem {

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
  @Column(name = "kind", nullable = false)
  private Kind kind;

  @Column(name = "text_value", columnDefinition = "text")
  private @Nullable String textValue;

  @Column(name = "numeric_value", precision = 30, scale = 10)
  private @Nullable BigDecimal numericValue;

  @Column(name = "unit", length = 32)
  private @Nullable String unit;

  @Column(name = "storage_key")
  private @Nullable String storageKey;

  @Column(name = "mime")
  private @Nullable String mime;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "extra", columnDefinition = "jsonb")
  private @Nullable String extraJson;

  @Column(name = "captured_at", nullable = false)
  private Instant capturedAt = Instant.now();

  /** Creates an empty instance for JPA. */
  protected EvidenceItem() {}

  /** Creates a textual evidence item. */
  public static EvidenceItem text(UUID tenantId, UUID runId, UUID stepId, String value) {
    var e = new EvidenceItem();
    e.id = UUID.randomUUID();
    e.tenantId = tenantId;
    e.runId = runId;
    e.stepId = stepId;
    e.kind = Kind.TEXT;
    e.textValue = value;
    return e;
  }

  /** Creates a measurement evidence item with a numeric value and unit. */
  public static EvidenceItem measurement(
      UUID tenantId, UUID runId, UUID stepId, BigDecimal v, String unit) {
    var e = new EvidenceItem();
    e.id = UUID.randomUUID();
    e.tenantId = tenantId;
    e.runId = runId;
    e.stepId = stepId;
    e.kind = Kind.MEASUREMENT;
    e.numericValue = v;
    e.unit = unit;
    return e;
  }

  /** Returns the unique identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the kind of evidence. */
  public Kind kind() {
    return kind;
  }

  /** Returns the numeric value, if any. */
  public @Nullable BigDecimal numericValue() {
    return numericValue;
  }

  /** Returns the textual value, if any. */
  public @Nullable String textValue() {
    return textValue;
  }

  /** Enumerates the supported kinds of evidence. */
  public enum Kind {
    TEXT,
    MEASUREMENT,
    PHOTO,
    FILE,
    SIGNATURE,
    GPS,
    CHECKBOX,
    SELECTION
  }
}
