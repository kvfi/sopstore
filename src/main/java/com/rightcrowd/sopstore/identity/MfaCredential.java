package com.rightcrowd.sopstore.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Persisted multi-factor authentication credential for a user. */
@Entity
@Table(
    name = "mfa_credential",
    indexes = @Index(name = "idx_mfa_user", columnList = "user_id,type"))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class MfaCredential {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private Type type;

  @Column(name = "secret_or_credential", nullable = false, columnDefinition = "bytea")
  private byte[] secretOrCredential;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected MfaCredential() {}

  /** Creates a credential with the given identifiers, type, payload, and label. */
  public MfaCredential(
      UUID id, UUID tenantId, UUID userId, Type type, byte[] payload, String label) {
    this.id = id;
    this.tenantId = tenantId;
    this.userId = userId;
    this.type = type;
    this.secretOrCredential = payload.clone();
    this.label = label;
  }

  /** Returns the credential identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the owning user identifier. */
  public UUID userId() {
    return userId;
  }

  /** Returns the credential type. */
  public Type type() {
    return type;
  }

  /** Returns a copy of the secret or credential payload. */
  public byte[] payload() {
    return secretOrCredential.clone();
  }

  /** Supported multi-factor credential types. */
  public enum Type {
    TOTP,
    WEBAUTHN
  }
}
