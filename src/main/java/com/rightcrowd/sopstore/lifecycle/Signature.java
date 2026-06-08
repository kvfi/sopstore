package com.rightcrowd.sopstore.lifecycle;

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

/**
 * Part-11 e-signature. Cryptographically binds a person + meaning + timestamp to a payload hash
 * (the procedure version JSON or a change-request body).
 */
@Entity
@Table(
    name = "signature",
    indexes = @Index(name = "idx_sig_subject", columnList = "subject_id,signed_at"))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Signature {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "signer_id", nullable = false)
  private UUID signerId;

  @Column(name = "subject_id", nullable = false)
  private UUID subjectId;

  @Column(name = "subject_type", nullable = false)
  private String subjectType;

  @Enumerated(EnumType.STRING)
  @Column(name = "meaning", nullable = false)
  private SignatureMeaning meaning;

  @Column(name = "payload_sha256", nullable = false, length = 64)
  private String payloadSha256;

  @Column(name = "reauth_token_id", nullable = false)
  private UUID reauthTokenId;

  @Column(name = "signed_at", nullable = false)
  private Instant signedAt = Instant.now();

  /** Creates an empty signature for JPA. */
  protected Signature() {}

  /** Creates a signature binding a signer, subject, meaning, and payload hash. */
  public Signature(
      UUID id,
      UUID tenantId,
      UUID signerId,
      UUID subjectId,
      String subjectType,
      SignatureMeaning meaning,
      String payloadSha256,
      UUID reauthTokenId) {
    this.id = id;
    this.tenantId = tenantId;
    this.signerId = signerId;
    this.subjectId = subjectId;
    this.subjectType = subjectType;
    this.meaning = meaning;
    this.payloadSha256 = payloadSha256;
    this.reauthTokenId = reauthTokenId;
  }

  /** Returns the signature identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the identifier of the person who signed. */
  public UUID signerId() {
    return signerId;
  }

  /** Returns the meaning of this signature. */
  public SignatureMeaning meaning() {
    return meaning;
  }

  /** Returns the SHA-256 hash of the signed payload. */
  public String payloadSha256() {
    return payloadSha256;
  }

  /** Returns the timestamp when the signature was created. */
  public Instant signedAt() {
    return signedAt;
  }
}
