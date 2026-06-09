package com.rightcrowd.sopstore.procedure.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Tenant-scoped configuration for naming and linking RUN_SCRIPT scripts in the exported SOP bundle
 * (the zip) and the PDF. There is exactly one row per tenant (enforced by a unique constraint on
 * {@code tenant_id}); it is created lazily the first time an admin saves the settings. When no row
 * exists the export falls back to {@link ScriptBundleConfig#DEFAULTS}.
 */
@Entity
@Table(name = "script_bundle_settings")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class ScriptBundleSettings {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "bundle_folder", nullable = false)
  private String bundleFolder = ScriptBundleConfig.DEFAULTS.folder();

  @Column(name = "filename_pattern", nullable = false)
  private String filenamePattern = ScriptBundleConfig.DEFAULTS.filenamePattern();

  @Column(name = "bundle_name", nullable = false)
  private String bundleName = ScriptBundleConfig.DEFAULTS.bundleName();

  @Column(name = "link_base_url", nullable = false)
  private String linkBaseUrl = ScriptBundleConfig.DEFAULTS.linkBaseUrl();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected ScriptBundleSettings() {}

  /** Creates a fresh row for a tenant, seeded with the application defaults. */
  ScriptBundleSettings(UUID id, UUID tenantId) {
    this.id = id;
    this.tenantId = tenantId;
  }

  /** Returns a transient, unsaved instance carrying the application defaults. */
  public static ScriptBundleSettings defaults() {
    return new ScriptBundleSettings();
  }

  /** Returns the folder script files are placed in inside the bundle zip. */
  public String bundleFolder() {
    return bundleFolder;
  }

  /** Returns the token template for each script file's name. */
  public String filenamePattern() {
    return filenamePattern;
  }

  /** Returns the token template for the downloaded zip's filename. */
  public String bundleName() {
    return bundleName;
  }

  /** Returns the absolute prefix for PDF script links, or empty for a bundle-relative link. */
  public String linkBaseUrl() {
    return linkBaseUrl;
  }

  /** Sets the folder script files are placed in (blank falls back to the default). */
  public void setBundleFolder(String value) {
    this.bundleFolder = blankToDefault(value, ScriptBundleConfig.DEFAULTS.folder());
  }

  /** Sets the per-script filename token template (blank falls back to the default). */
  public void setFilenamePattern(String value) {
    this.filenamePattern = blankToDefault(value, ScriptBundleConfig.DEFAULTS.filenamePattern());
  }

  /** Sets the zip filename token template (blank falls back to the default). */
  public void setBundleName(String value) {
    this.bundleName = blankToDefault(value, ScriptBundleConfig.DEFAULTS.bundleName());
  }

  /** Sets the absolute link prefix; null/blank is stored as empty (a relative link). */
  public void setLinkBaseUrl(String value) {
    this.linkBaseUrl = value == null ? "" : value.trim();
  }

  /** Records that the row was just modified. */
  public void touch() {
    this.updatedAt = Instant.now();
  }

  /** Projects this row onto the immutable config used by the export pipeline. */
  ScriptBundleConfig toConfig() {
    return new ScriptBundleConfig(bundleFolder, filenamePattern, bundleName, linkBaseUrl);
  }

  private static String blankToDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
