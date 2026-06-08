package com.rightcrowd.sopstore.procedure.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** The raw bytes of an {@link com.rightcrowd.sopstore.procedure.Attachment}, kept out of the
 * metadata table so attachment listings don't load file content. */
@Entity
@Table(name = "attachment_content")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class AttachmentContent {

  @Id
  @Column(name = "attachment_id", nullable = false, updatable = false)
  private UUID attachmentId;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "content", nullable = false)
  private byte[] content = new byte[0];

  /** Creates an empty content row for use by the persistence provider. */
  protected AttachmentContent() {}

  /** Creates a content row holding the file bytes for an attachment. */
  public AttachmentContent(UUID attachmentId, UUID tenantId, byte[] content) {
    this.attachmentId = attachmentId;
    this.tenantId = tenantId;
    this.content = content.clone();
  }

  /** Returns a copy of the file bytes. */
  public byte[] content() {
    return content.clone();
  }
}
