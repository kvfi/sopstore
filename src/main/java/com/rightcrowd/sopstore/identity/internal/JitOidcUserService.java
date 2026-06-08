package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.identity.User;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import com.rightcrowd.sopstore.tenancy.TenantId;
import com.rightcrowd.sopstore.tenancy.TenantLookup;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Just-in-time provisioning for OIDC single sign-on. On each login the application {@link User} is
 * created or updated from the OIDC claims: the tenant is resolved from the {@code tenant_slug}
 * claim (falling back to the development tenant), and roles from the IdP {@code roles} claim are
 * synced into the user and the session authorities. The principal carries the tenant so the tenant
 * filter resolves it.
 *
 * <p>The {@code tenant_slug} lookup runs before a tenant context exists; in production (a
 * non-owner DB role) that registry read needs the break-glass / bypass role — see PHASE-STATUS.
 */
@Service
class JitOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private static final TenantId DEV_TENANT = new TenantId(new UUID(0L, 0L));

  private final OidcUserService delegate = new OidcUserService();
  private final UserRepository users;
  private final TenantLookup tenants;

  JitOidcUserService(UserRepository users, TenantLookup tenants) {
    this.users = users;
    this.tenants = tenants;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest request) {
    OidcUser oidc = delegate.loadUser(request);
    String email = oidc.getEmail();
    if (email == null || email.isBlank()) {
      throw new OAuth2AuthenticationException("OIDC profile is missing an email claim");
    }
    String normalized = email.toLowerCase(Locale.ROOT);
    TenantId tenant = resolveTenant(oidc);
    Set<Role> roles = mapRoles(oidc);
    provision(normalized, displayName(oidc, normalized), tenant, roles);
    return new SsoPrincipal(authorities(roles), oidc.getIdToken(), tenant);
  }

  private TenantId resolveTenant(OidcUser oidc) {
    String slug = oidc.getClaimAsString("tenant_slug");
    return slug == null || slug.isBlank() ? DEV_TENANT : tenants.bySlug(slug).orElse(DEV_TENANT);
  }

  private void provision(String email, String displayName, TenantId tenant, Set<Role> roles) {
    TenantContext.set(tenant);
    try {
      User user =
          users
              .findByEmail(email)
              .orElseGet(() -> new User(UUID.randomUUID(), tenant.value(), email, displayName, ""));
      roles.forEach(user::addRole);
      users.save(user);
    } finally {
      TenantContext.clear();
    }
  }

  private static String displayName(OidcUser oidc, String fallback) {
    String fullName = oidc.getFullName();
    return fullName == null || fullName.isBlank() ? fallback : fullName;
  }

  private static Set<Role> mapRoles(OidcUser oidc) {
    List<String> claim = oidc.getClaimAsStringList("roles");
    if (claim == null) {
      return Set.of();
    }
    Set<Role> roles = EnumSet.noneOf(Role.class);
    for (String name : claim) {
      try {
        roles.add(Role.valueOf(name));
      } catch (IllegalArgumentException ignored) {
        // IdP role with no application equivalent (e.g. Keycloak's offline_access) — skip.
      }
    }
    return roles;
  }

  private static List<GrantedAuthority> authorities(Set<Role> roles) {
    if (roles.isEmpty()) {
      return List.of(new SimpleGrantedAuthority("ROLE_VIEWER"));
    }
    return roles.stream()
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.name()))
        .toList();
  }
}
