package com.rightcrowd.sopstore.audit;

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

/**
 * Append-only, hash-chained audit record. Mutations are forbidden after insert (DB trigger {@code
 * audit_event_no_update} guards against it).
 *
 * <p>{@code hash = SHA-256(prev_hash || canonical_payload)} where canonical payload is {@code
 * tenant|entityType|entityId|action|actorId|occurredAt|detailJson}.
 */
@Entity
@Table(
    name = "audit_event",
    indexes = {
      @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id,occurred_at"),
      @Index(name = "idx_audit_tenant_time", columnList = "tenant_id,occurred_at")
    })
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class AuditEvent {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private String entityId;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "actor_id", nullable = false)
  private UUID actorId;

  @Column(name = "actor_ip", length = 45)
  private @Nullable String actorIp;

  @Column(name = "actor_user_agent", length = 512)
  private @Nullable String actorUserAgent;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt = Instant.now();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "detail", nullable = false, columnDefinition = "jsonb")
  private String detailJson = "{}";

  @Column(name = "prev_hash", length = 64)
  private @Nullable String prevHash;

  @Column(name = "hash", nullable = false, length = 64)
  private String hash;

  /** Required by JPA. */
  protected AuditEvent() {}

  /** Creates a new audit event with the given hash-chain and actor details. */
  public AuditEvent(
      UUID id,
      UUID tenantId,
      String entityType,
      String entityId,
      String action,
      UUID actorId,
      String detailJson,
      @Nullable String prevHash,
      String hash) {
    this.id = id;
    this.tenantId = tenantId;
    this.entityType = entityType;
    this.entityId = entityId;
    this.action = action;
    this.actorId = actorId;
    this.detailJson = detailJson;
    this.prevHash = prevHash;
    this.hash = hash;
  }

  /** Returns the event identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the tenant identifier. */
  public UUID tenantId() {
    return tenantId;
  }

  /** Returns the audited entity type. */
  public String entityType() {
    return entityType;
  }

  /** Returns the audited entity identifier. */
  public String entityId() {
    return entityId;
  }

  /** Returns the action that was performed. */
  public String action() {
    return action;
  }

  /** Returns the identifier of the actor who performed the action. */
  public UUID actorId() {
    return actorId;
  }

  /** Returns the time at which the event occurred. */
  public Instant occurredAt() {
    return occurredAt;
  }

  /** Returns the event detail as a JSON string. */
  public String detailJson() {
    return detailJson;
  }

  /** Returns the previous hash in the chain, or {@code null} if this is the first event. */
  public @Nullable String prevHash() {
    return prevHash;
  }

  /** Returns the hash of this event. */
  public String hash() {
    return hash;
  }
}
