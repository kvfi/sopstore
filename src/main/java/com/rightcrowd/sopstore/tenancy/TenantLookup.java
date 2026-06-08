package com.rightcrowd.sopstore.tenancy;

import java.util.List;
import java.util.Optional;

/**
 * Resolves tenants from the tenant registry. Exposed so sibling modules (e.g. identity's SSO
 * provisioning) can map an IdP claim to a tenant without reaching into tenancy internals.
 */
public interface TenantLookup {

  /** Resolves a tenant id by its unique slug, if one exists. */
  Optional<TenantId> bySlug(String slug);

  /**
   * Returns the ids of all ACTIVE tenants. Safe to call with no tenant context — used by scheduled
   * jobs to iterate per-tenant work. Reads the registry via a privileged enumeration, so RLS does
   * not hide tenants other than the one currently in context.
   */
  List<TenantId> activeTenantIds();
}
