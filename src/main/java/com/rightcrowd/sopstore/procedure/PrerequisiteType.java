package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** A tenant-managed prerequisite category authors can assign to a procedure prerequisite. */
@Entity
@Table(
    name = "prerequisite_type",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class PrerequisiteType {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected PrerequisiteType() {}

  /** Creates a prerequisite type with the given identifier, tenant, and name. */
  public PrerequisiteType(UUID id, UUID tenantId, String name) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
  }

  /** Returns the identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the display name. */
  public String name() {
    return name;
  }

  /** Renames the type. */
  public void setName(String n) {
    this.name = n;
  }
}
