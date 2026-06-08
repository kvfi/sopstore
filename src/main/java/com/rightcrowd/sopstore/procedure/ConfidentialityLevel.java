package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * A tenant-managed confidentiality level (e.g. Public, Internal, Confidential, Restricted). Levels
 * are ordered by {@code rank} — lower is less sensitive — and a procedure may be classified with
 * one. The chosen level is marked on the exported PDF.
 */
@Entity
@Table(
    name = "confidentiality_level",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class ConfidentialityLevel {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "rank", nullable = false)
  private int rank;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected ConfidentialityLevel() {}

  /** Creates a confidentiality level with the given identifier, tenant, name, and rank. */
  public ConfidentialityLevel(UUID id, UUID tenantId, String name, int rank) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
    this.rank = rank;
  }

  /** Returns the identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the display name. */
  public String name() {
    return name;
  }

  /** Returns the sensitivity rank (lower is less sensitive). */
  public int rank() {
    return rank;
  }

  /** Renames the level. */
  public void setName(String n) {
    this.name = n;
  }

  /** Sets the sensitivity rank. */
  public void setRank(int r) {
    this.rank = r;
  }
}
