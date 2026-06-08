package com.rightcrowd.sopstore.identity;

import com.rightcrowd.sopstore.tenancy.TenantId;
import com.rightcrowd.sopstore.tenancy.TenantResolverFilter;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring Security principal for an authenticated application user. Exposed by the identity module
 * so web controllers in sibling modules can resolve the current user via
 * {@code @AuthenticationPrincipal}. Also carries the user's tenant for the tenant-resolution
 * filter.
 */
public record AuthenticatedUser(User user)
    implements UserDetails, TenantResolverFilter.TenantAware {

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return user.roles().stream()
        .map(Role::name)
        .map(n -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + n))
        .toList();
  }

  @Override
  public String getPassword() {
    return user.passwordHash() == null ? "" : user.passwordHash();
  }

  @Override
  public String getUsername() {
    return user.email();
  }

  @Override
  public boolean isAccountNonExpired() {
    return user.status() == User.Status.ACTIVE;
  }

  @Override
  public boolean isAccountNonLocked() {
    return user.status() != User.Status.SUSPENDED;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return user.status() == User.Status.ACTIVE;
  }

  @Override
  public TenantId tenantId() {
    return new TenantId(user.tenantId());
  }
}
