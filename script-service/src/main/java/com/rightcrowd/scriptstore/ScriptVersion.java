package com.rightcrowd.scriptstore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An immutable snapshot of a {@link Script}'s content at a version number. */
@Entity
@Table(name = "script_version")
public class ScriptVersion {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "script_id", nullable = false, updatable = false)
  private UUID scriptId;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "version_no", nullable = false, updatable = false)
  private int versionNo;

  @Column(name = "content", nullable = false, updatable = false)
  private String content = "";

  @Column(name = "note", updatable = false)
  private String note;

  @Column(name = "created_by", updatable = false)
  private String createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** For JPA. */
  protected ScriptVersion() {}

  /** Creates an immutable version snapshot. */
  public ScriptVersion(
      UUID id, UUID scriptId, UUID tenantId, int versionNo, String content, String note, String createdBy) {
    this.id = id;
    this.scriptId = scriptId;
    this.tenantId = tenantId;
    this.versionNo = versionNo;
    this.content = content == null ? "" : content;
    this.note = note;
    this.createdBy = createdBy;
  }

  public UUID id() {
    return id;
  }

  public int versionNo() {
    return versionNo;
  }

  public String content() {
    return content;
  }

  public String note() {
    return note;
  }

  public String createdBy() {
    return createdBy;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
