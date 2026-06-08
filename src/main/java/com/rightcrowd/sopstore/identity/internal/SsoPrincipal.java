package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.tenancy.TenantId;
import com.rightcrowd.sopstore.tenancy.TenantResolverFilter;
import java.util.Collection;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** OIDC principal that also carries the resolved tenant for {@link TenantResolverFilter}. */
final class SsoPrincipal extends DefaultOidcUser implements TenantResolverFilter.TenantAware {

  private static final long serialVersionUID = 1L;

  private final TenantId tenant;

  SsoPrincipal(
      Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken, TenantId tenant) {
    super(authorities, idToken);
    this.tenant = tenant;
  }

  @Override
  public TenantId tenantId() {
    return tenant;
  }

  @Override
  public boolean equals(Object obj) {
    return super.equals(obj)
        && obj instanceof SsoPrincipal other
        && tenant.equals(other.tenant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), tenant);
  }
}
