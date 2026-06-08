package com.rightcrowd.sopstore.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * Single-table hierarchy modelling Organization → BusinessUnit → Site → Department → Team. {@link
 * Level} discriminator + {@code parent_id} self-reference. Tenant-scoped via {@link
 * org.hibernate.annotations.TenantId}.
 */
@Entity
@Table(name = "org_unit")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class OrgUnit {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false)
  private Level level;

  @Column(name = "name", nullable = false)
  private String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_id")
  private @Nullable OrgUnit parent;

  /** Protected no-argument constructor required by JPA. */
  protected OrgUnit() {}

  /** Creates an org unit with the given identity, level, name, and optional parent. */
  public OrgUnit(UUID id, UUID tenantId, Level level, String name, @Nullable OrgUnit parent) {
    this.id = id;
    this.tenantId = tenantId;
    this.level = level;
    this.name = name;
    this.parent = parent;
  }

  /** Returns the unit identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the owning tenant identifier. */
  public UUID tenantId() {
    return tenantId;
  }

  /** Returns the hierarchy level of this unit. */
  public Level level() {
    return level;
  }

  /** Returns the unit name. */
  public String name() {
    return name;
  }

  /** Returns the parent unit, or {@code null} if this is a root unit. */
  public @Nullable OrgUnit parent() {
    return parent;
  }

  /** Hierarchy levels for an organization unit. */
  public enum Level {
    ORGANIZATION,
    BUSINESS_UNIT,
    SITE,
    DEPARTMENT,
    TEAM
  }
}
