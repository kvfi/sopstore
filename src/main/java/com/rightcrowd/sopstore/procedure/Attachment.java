package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Represents a file attachment associated with a procedure version. */
@Entity
@Table(name = "attachment")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Attachment {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "procedure_version_id", nullable = false)
  private UUID procedureVersionId;

  @Column(name = "filename", nullable = false)
  private String filename = "";

  @Column(name = "storage_key", nullable = false)
  private String storageKey;

  @Column(name = "mime", nullable = false)
  private String mime;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "sha256", nullable = false, length = 64)
  private String sha256;

  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt = Instant.now();

  /** Creates an empty attachment for use by the persistence provider. */
  protected Attachment() {}

  /** Creates an attachment with the given identifiers and file metadata. */
  public Attachment(
      UUID id,
      UUID tenantId,
      UUID procedureVersionId,
      String filename,
      String storageKey,
      String mime,
      long size,
      String sha256) {
    this.id = id;
    this.tenantId = tenantId;
    this.procedureVersionId = procedureVersionId;
    this.filename = filename;
    this.storageKey = storageKey;
    this.mime = mime;
    this.sizeBytes = size;
    this.sha256 = sha256;
  }

  /** Returns the attachment identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the original filename of the attachment. */
  public String filename() {
    return filename;
  }

  /** Returns the procedure version this attachment belongs to. */
  public UUID procedureVersionId() {
    return procedureVersionId;
  }

  /** Returns when the attachment was uploaded. */
  public Instant uploadedAt() {
    return uploadedAt;
  }

  /** Returns the storage key locating the attachment content. */
  public String storageKey() {
    return storageKey;
  }

  /** Returns the MIME type of the attachment. */
  public String mime() {
    return mime;
  }

  /** Returns the size of the attachment in bytes. */
  public long sizeBytes() {
    return sizeBytes;
  }

  /** Returns the SHA-256 hash of the attachment content. */
  public String sha256() {
    return sha256;
  }
}
