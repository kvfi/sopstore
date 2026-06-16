package com.rightcrowd.sopstore.identity.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Tenant-scoped session lifetime policy: how long a signed-in session may stay idle before it is
 * expired, and the absolute lifetime after which it is expired regardless of activity. Exactly one
 * row per tenant (enforced by a unique constraint on {@code tenant_id}); created lazily the first
 * time an admin saves. When no row exists the platform defaults apply.
 */
@Entity
@Table(name = "session_policy")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class SessionPolicy {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "idle_timeout_seconds", nullable = false)
  private int idleTimeoutSeconds;

  @Column(name = "absolute_timeout_seconds", nullable = false)
  private int absoluteTimeoutSeconds;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected SessionPolicy() {}

  /** Creates a fresh row for a tenant with the given timeouts (seconds). */
  SessionPolicy(UUID id, UUID tenantId, int idleTimeoutSeconds, int absoluteTimeoutSeconds) {
    this.id = id;
    this.tenantId = tenantId;
    this.idleTimeoutSeconds = idleTimeoutSeconds;
    this.absoluteTimeoutSeconds = absoluteTimeoutSeconds;
  }

  /** Returns the idle timeout in seconds. */
  public int idleTimeoutSeconds() {
    return idleTimeoutSeconds;
  }

  /** Returns the absolute timeout in seconds. */
  public int absoluteTimeoutSeconds() {
    return absoluteTimeoutSeconds;
  }

  /** Sets the idle timeout in seconds. */
  public void setIdleTimeoutSeconds(int value) {
    this.idleTimeoutSeconds = value;
  }

  /** Sets the absolute timeout in seconds. */
  public void setAbsoluteTimeoutSeconds(int value) {
    this.absoluteTimeoutSeconds = value;
  }

  /** Records that the row was just modified. */
  public void touch() {
    this.updatedAt = Instant.now();
  }
}
