package com.rightcrowd.sopstore.tenancy.internal;

import com.rightcrowd.sopstore.tenancy.TenantContext;
import com.rightcrowd.sopstore.tenancy.TenantId;
import java.util.UUID;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Hibernate hook: returns the current tenant for the discriminator-based {@code multiTenancy} mode.
 * Used by Hibernate's {@code @TenantId} filter, whose annotated fields are {@link UUID}, so the
 * resolved identifier must also be a {@link UUID} (not a String) or Hibernate rejects it as the
 * wrong filter-parameter type.
 */
@Component
public class HibernateTenantResolver implements CurrentTenantIdentifierResolver<UUID> {

  /** UUID used when no tenant is set (e.g. login page); never matches real data. */
  public static final UUID ANONYMOUS = new UUID(0L, 0L);

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    TenantId t = TenantContext.currentOrNull();
    return t == null ? ANONYMOUS : t.value();
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }
}
