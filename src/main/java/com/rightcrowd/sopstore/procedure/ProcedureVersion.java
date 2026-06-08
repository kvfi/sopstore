package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** A versioned snapshot of a procedure's content for a tenant. */
@Entity
@Table(
    name = "procedure_version",
    indexes = {
      @Index(
          name = "idx_pv_procedure",
          columnList = "procedure_id,version_major,version_minor",
          unique = true)
    })
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class ProcedureVersion {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "procedure_id", nullable = false)
  private UUID procedureId;

  @Column(name = "version_major", nullable = false)
  private int versionMajor;

  @Column(name = "version_minor", nullable = false)
  private int versionMinor;

  /** TipTap JSON document body. Stored as jsonb. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "body_json", nullable = false, columnDefinition = "jsonb")
  private String bodyJson = "{}";

  @Column(name = "summary")
  private @Nullable String summary;

  /** Author-editable free-form version label; overrides the computed major.minor when set. */
  @Column(name = "label")
  private @Nullable String label;

  @Column(name = "change_request_id")
  private @Nullable UUID changeRequestId;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected ProcedureVersion() {}

  /** Creates a new procedure version with the given identifiers and version numbers. */
  public ProcedureVersion(
      UUID id, UUID tenantId, UUID procedureId, int major, int minor, UUID createdBy) {
    this.id = id;
    this.tenantId = tenantId;
    this.procedureId = procedureId;
    this.versionMajor = major;
    this.versionMinor = minor;
    this.createdBy = createdBy;
  }

  /** Returns the unique identifier of this version. */
  public UUID id() {
    return id;
  }

  /** Returns the identifier of the procedure this version belongs to. */
  public UUID procedureId() {
    return procedureId;
  }

  /** Returns the major version number. */
  public int versionMajor() {
    return versionMajor;
  }

  /** Returns the minor version number. */
  public int versionMinor() {
    return versionMinor;
  }

  /** Returns the TipTap JSON document body. */
  public String bodyJson() {
    return bodyJson;
  }

  /** Returns the identifier of the linked change request, or null if none. */
  public @Nullable UUID changeRequestId() {
    return changeRequestId;
  }

  /** Returns when this version was created. */
  public Instant createdAt() {
    return createdAt;
  }

  /** Returns the id of the user who authored this version. */
  public UUID createdBy() {
    return createdBy;
  }

  /** Returns the version summary/change note, or null when none is set. */
  public @Nullable String summary() {
    return summary;
  }

  /** Sets the TipTap JSON document body. */
  public void setBody(String json) {
    this.bodyJson = json;
  }

  /** Sets the summary describing this version. */
  public void setSummary(String s) {
    this.summary = s;
  }

  /** Sets the free-form version label (null/blank clears it, reverting to major.minor). */
  public void setLabel(@Nullable String label) {
    this.label = label == null || label.isBlank() ? null : label.trim();
  }

  /** Links this version to the given change request. */
  public void linkChangeRequest(UUID cr) {
    this.changeRequestId = cr;
  }

  /** Returns the author-set label when present, otherwise the computed "major.minor" form. */
  public String versionLabel() {
    return label != null && !label.isBlank() ? label : versionMajor + "." + versionMinor;
  }
}
