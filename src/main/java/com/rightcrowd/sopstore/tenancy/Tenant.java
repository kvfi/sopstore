package com.rightcrowd.sopstore.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Represents a tenant in the system. */
@Entity
@Table(name = "tenant")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Tenant {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "slug", nullable = false, unique = true)
  private String slug;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TenantStatus status = TenantStatus.ACTIVE;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty tenant for use by the persistence provider. */
  protected Tenant() {}

  /** Creates a tenant with the given identifier, name, and slug. */
  public Tenant(UUID id, String name, String slug) {
    this.id = id;
    this.name = name;
    this.slug = slug;
  }

  /** Returns the tenant identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the tenant identifier wrapped as a {@link TenantId}. */
  public TenantId tenantId() {
    return new TenantId(id);
  }

  /** Returns the tenant name. */
  public String name() {
    return name;
  }

  /** Returns the tenant slug. */
  public String slug() {
    return slug;
  }

  /** Returns the tenant status. */
  public TenantStatus status() {
    return status;
  }

  /** Enumerates the possible states of a tenant. */
  public enum TenantStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED
  }
}
