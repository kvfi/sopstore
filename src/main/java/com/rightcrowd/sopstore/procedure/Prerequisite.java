package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** A reusable prerequisite in the tenant's library: a type plus its text. */
@Entity
@Table(
    name = "prerequisite",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "type", "label"}))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Prerequisite {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "type", nullable = false)
  private String type;

  @Column(name = "label", nullable = false)
  private String text;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected Prerequisite() {}

  /** Creates a library prerequisite with the given identifier, tenant, type, and text. */
  public Prerequisite(UUID id, UUID tenantId, String type, String text) {
    this.id = id;
    this.tenantId = tenantId;
    this.type = type;
    this.text = text;
  }

  /** Returns the identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the prerequisite type (a prerequisite_type name snapshot). */
  public String type() {
    return type;
  }

  /** Returns the prerequisite text. */
  public String text() {
    return text;
  }

  /** Updates the type and text. */
  public void update(String newType, String newText) {
    this.type = newType;
    this.text = newText;
  }
}
