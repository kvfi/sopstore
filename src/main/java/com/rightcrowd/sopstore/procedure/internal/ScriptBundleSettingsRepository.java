package com.rightcrowd.sopstore.procedure.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for the single per-tenant {@link ScriptBundleSettings} row. Row-level security scopes
 * every query to the current tenant, so {@link #findFirstByOrderByCreatedAtAsc()} returns this
 * tenant's settings (there is at most one) or empty when it has not customised them yet.
 */
public interface ScriptBundleSettingsRepository
    extends JpaRepository<ScriptBundleSettings, UUID> {

  /** Returns the current tenant's settings row, if one has been saved. */
  Optional<ScriptBundleSettings> findFirstByOrderByCreatedAtAsc();
}
