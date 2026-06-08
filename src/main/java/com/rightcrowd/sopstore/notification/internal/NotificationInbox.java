package com.rightcrowd.sopstore.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** A rendered in-app notification in a user's inbox, with read state. */
@Entity
@Table(
    name = "notification_inbox",
    indexes = @Index(name = "idx_inbox_recipient", columnList = "recipient_id,read_at,created_at"))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class NotificationInbox {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "recipient_id", nullable = false)
  private UUID recipientId;

  @Column(name = "template_key", nullable = false)
  private String templateKey;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "body", nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "link")
  private @Nullable String link;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "read_at")
  private @Nullable Instant readAt;

  /** Creates an empty inbox row for use by the persistence provider. */
  protected NotificationInbox() {}

  /** Creates an unread inbox message for a recipient. */
  public NotificationInbox(
      UUID id,
      UUID tenantId,
      UUID recipientId,
      String templateKey,
      String title,
      String body,
      @Nullable String link) {
    this.id = id;
    this.tenantId = tenantId;
    this.recipientId = recipientId;
    this.templateKey = templateKey;
    this.title = title;
    this.body = body;
    this.link = link;
  }

  /** Returns the message identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the message title. */
  public String title() {
    return title;
  }

  /** Returns the message body. */
  public String body() {
    return body;
  }

  /** Returns the optional deep link for the message. */
  public @Nullable String link() {
    return link;
  }

  /** Returns when the message was created. */
  public Instant createdAt() {
    return createdAt;
  }

  /** Returns when the message was read, or null if unread. */
  public @Nullable Instant readAt() {
    return readAt;
  }

  /** Returns whether the message is unread. */
  public boolean unread() {
    return readAt == null;
  }

  /** Returns whether the given user is the recipient and may act on this message. */
  public boolean recipientIsAccessibleBy(UUID userId) {
    return recipientId.equals(userId);
  }

  /** Marks the message as read now, if not already read. */
  public void markRead() {
    if (readAt == null) {
      this.readAt = Instant.now();
    }
  }
}
