package com.rightcrowd.sopstore.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** A named curriculum scoped to a tenant, optionally targeting a role. */
@Entity
@Table(
    name = "curriculum",
    indexes = @Index(name = "idx_curr_tenant_name", columnList = "tenant_id,name", unique = true))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Curriculum {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "role_target")
  private String roleTarget;

  /** Creates an empty curriculum for use by the persistence provider. */
  protected Curriculum() {}

  /** Creates a curriculum with the given identifier, tenant, name, and role target. */
  public Curriculum(UUID id, UUID tenantId, String name, String roleTarget) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
    this.roleTarget = roleTarget;
  }

  /** Returns the curriculum identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the curriculum name. */
  public String name() {
    return name;
  }

  /** Returns the target role for this curriculum. */
  public String roleTarget() {
    return roleTarget;
  }
}
