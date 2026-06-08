package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Represents a procedure category scoped to a tenant. */
@Entity
@Table(
    name = "category",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Category {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  /** Creates an empty category for use by the persistence provider. */
  protected Category() {}

  /** Creates a category with the given identifier, tenant, and name. */
  public Category(UUID id, UUID tenantId, String name) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
  }

  /** Returns the category identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the category name. */
  public String name() {
    return name;
  }
}
