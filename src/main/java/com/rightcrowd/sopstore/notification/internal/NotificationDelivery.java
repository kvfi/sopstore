package com.rightcrowd.sopstore.notification.internal;

import com.rightcrowd.sopstore.notification.NotificationPort.Channel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** JPA entity recording the delivery of a notification to a recipient over a channel. */
@Entity
@Table(name = "notification_delivery")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class NotificationDelivery {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "recipient_id", nullable = false)
  private UUID recipientId;

  @Column(name = "template_key", nullable = false)
  private String templateKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false)
  private Channel channel;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private State state = State.QUEUED;

  @Column(name = "queued_at", nullable = false)
  private Instant queuedAt = Instant.now();

  @Column(name = "delivered_at")
  private @Nullable Instant deliveredAt;

  @Column(name = "error", columnDefinition = "text")
  private @Nullable String error;

  /** Lifecycle state of a notification delivery. */
  public enum State {
    QUEUED,
    SENT,
    FAILED
  }

  /** Creates an empty delivery instance for use by the persistence provider. */
  protected NotificationDelivery() {}

  /** Creates a queued delivery record for a recipient over a channel. */
  public NotificationDelivery(
      UUID id, UUID tenantId, UUID recipientId, String templateKey, Channel channel) {
    this.id = id;
    this.tenantId = tenantId;
    this.recipientId = recipientId;
    this.templateKey = templateKey;
    this.channel = channel;
  }

  /** Marks the delivery as successfully sent. */
  public void markSent() {
    this.state = State.SENT;
    this.deliveredAt = Instant.now();
  }

  /** Marks the delivery as failed with the given error detail. */
  public void markFailed(String error) {
    this.state = State.FAILED;
    this.error = error;
  }
}
