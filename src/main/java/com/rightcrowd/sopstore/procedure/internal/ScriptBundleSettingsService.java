package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the current tenant's {@link ScriptBundleSettings}. The export pipeline asks for
 * {@link #effectiveConfig()} (settings-or-defaults); the admin settings API uses {@link #current()}
 * and {@link #save} to view and upsert the single row.
 */
@Service
@Transactional
public class ScriptBundleSettingsService {

  private final ScriptBundleSettingsRepository repo;

  /** Creates the service with its repository. */
  public ScriptBundleSettingsService(ScriptBundleSettingsRepository repo) {
    this.repo = repo;
  }

  /** Returns the tenant's settings row, or empty when it has not been customised. */
  @Transactional(readOnly = true)
  public Optional<ScriptBundleSettings> current() {
    return repo.findFirstByOrderByCreatedAtAsc();
  }

  /** Returns the config used by the export: the saved row, or the application defaults. */
  @Transactional(readOnly = true)
  ScriptBundleConfig effectiveConfig() {
    return current().map(ScriptBundleSettings::toConfig).orElse(ScriptBundleConfig.DEFAULTS);
  }

  /** Upserts the tenant's settings, creating the row on first save. Returns the saved entity. */
  public ScriptBundleSettings save(
      String bundleFolder, String filenamePattern, String bundleName, String linkBaseUrl) {
    ScriptBundleSettings s =
        current()
            .orElseGet(
                () ->
                    new ScriptBundleSettings(
                        UUID.randomUUID(), TenantContext.current().value()));
    s.setBundleFolder(bundleFolder);
    s.setFilenamePattern(filenamePattern);
    s.setBundleName(bundleName);
    s.setLinkBaseUrl(linkBaseUrl);
    s.touch();
    return repo.save(s);
  }
}
