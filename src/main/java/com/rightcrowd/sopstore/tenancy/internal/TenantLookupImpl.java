package com.rightcrowd.sopstore.tenancy.internal;

import com.rightcrowd.sopstore.tenancy.TenantId;
import com.rightcrowd.sopstore.tenancy.TenantLookup;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Default {@link TenantLookup} backed by the tenant repository. */
@Service
class TenantLookupImpl implements TenantLookup {

  private final TenantRepository tenants;

  TenantLookupImpl(TenantRepository tenants) {
    this.tenants = tenants;
  }

  @Override
  public Optional<TenantId> bySlug(String slug) {
    return tenants.findBySlug(slug).map(tenant -> new TenantId(tenant.id()));
  }

  @Override
  public List<TenantId> activeTenantIds() {
    return tenants.activeTenantIds().stream().map(TenantId::new).toList();
  }
}
