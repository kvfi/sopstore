package com.rightcrowd.sopstore.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** A named group of users scoped to a tenant. */
@Entity
@Table(
    name = "user_group",
    indexes = @Index(name = "idx_group_tenant_name", columnList = "tenant_id,name", unique = true))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Group {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  /** Creates an empty group for use by the persistence provider. */
  protected Group() {}

  /** Creates a group with the given identifier, tenant, and name. */
  public Group(UUID id, UUID tenantId, String name) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
  }

  /** Returns the group identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the tenant identifier. */
  public UUID tenantId() {
    return tenantId;
  }

  /** Returns the group name. */
  public String name() {
    return name;
  }
}
