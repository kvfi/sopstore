package com.rightcrowd.scriptstore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A tenant-owned script. Content lives in {@link ScriptVersion}; this row tracks metadata only. */
@Entity
@Table(name = "script")
public class Script {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "language", nullable = false)
  private String language = "text";

  @Column(name = "description")
  private String description;

  @Column(name = "current_version", nullable = false)
  private int currentVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** For JPA. */
  protected Script() {}

  /** Creates a new script with metadata; the first version is added by the service. */
  public Script(UUID id, UUID tenantId, String name, String language, String description) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
    this.language = language == null || language.isBlank() ? "text" : language;
    this.description = description;
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public String name() {
    return name;
  }

  public String language() {
    return language;
  }

  public String description() {
    return description;
  }

  public int currentVersion() {
    return currentVersion;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setLanguage(String language) {
    this.language = language == null || language.isBlank() ? "text" : language;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  /** Records that a new current version number was published and bumps the updated timestamp. */
  public void setCurrentVersion(int version) {
    this.currentVersion = version;
    this.updatedAt = Instant.now();
  }
}
