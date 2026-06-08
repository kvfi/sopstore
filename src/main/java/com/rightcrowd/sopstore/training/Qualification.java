package com.rightcrowd.sopstore.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** Records that a user is qualified to perform a procedure within a tenant. */
@Entity
@Table(
    name = "qualification",
    indexes =
        @Index(name = "idx_qual_user_proc", columnList = "user_id,procedure_id", unique = true))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Qualification {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "procedure_id", nullable = false)
  private UUID procedureId;

  @Column(name = "qualified_on", nullable = false)
  private LocalDate qualifiedOn;

  @Column(name = "expires_on")
  private @Nullable LocalDate expiresOn;

  @Column(name = "trainer_id")
  private @Nullable UUID trainerId;

  /** Constructs an empty qualification for JPA. */
  protected Qualification() {}

  /** Constructs a qualification with the given identifiers and validity dates. */
  public Qualification(
      UUID id,
      UUID tenantId,
      UUID userId,
      UUID procedureId,
      LocalDate qualifiedOn,
      @Nullable LocalDate expiresOn,
      @Nullable UUID trainerId) {
    this.id = id;
    this.tenantId = tenantId;
    this.userId = userId;
    this.procedureId = procedureId;
    this.qualifiedOn = qualifiedOn;
    this.expiresOn = expiresOn;
    this.trainerId = trainerId;
  }

  /** Returns the qualification identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the identifier of the qualified user. */
  public UUID userId() {
    return userId;
  }

  /** Returns the date the qualification was granted. */
  public LocalDate qualifiedOn() {
    return qualifiedOn;
  }

  /** Returns whether this qualification is valid on the given date. */
  public boolean isValidOn(LocalDate d) {
    return !d.isBefore(qualifiedOn) && (expiresOn == null || !d.isAfter(expiresOn));
  }
}
