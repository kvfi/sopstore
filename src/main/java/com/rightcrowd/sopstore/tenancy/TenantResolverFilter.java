package com.rightcrowd.sopstore.tenancy;

import com.rightcrowd.sopstore.platform.PlatformProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the current tenant for each request and binds it to the tenant context.
 *
 * <p>Registered with the servlet container via {@code FilterRegistrationBean} rather than as a
 * {@code @Component}: Spring Modulith observability CGLIB-proxies exposed module beans, and an
 * Objenesis-instantiated proxy leaves {@link org.springframework.web.filter.GenericFilterBean}'s
 * {@code logger} field null, causing the container's {@code final init()} call to NPE.
 */
public class TenantResolverFilter extends OncePerRequestFilter {

  private final PlatformProperties props;

  /** Creates a filter using the given platform properties. */
  public TenantResolverFilter(PlatformProperties props) {
    this.props = props;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    TenantId resolved = resolve();
    if (resolved != null) {
      TenantContext.set(resolved);
      org.slf4j.MDC.put("tenantId", resolved.toString());
    }
    try {
      chain.doFilter(req, res);
    } finally {
      TenantContext.clear();
      org.slf4j.MDC.remove("tenantId");
    }
  }

  private @Nullable TenantId resolve() {
    // Onprem / single-tenant: fixed tenant injected from config.
    if (props.tenancy() != null && !props.tenancy().multiTenant()) {
      return TenantId.of(props.tenancy().fixedTenantId());
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return null;
    }

    Object principal = auth.getPrincipal();
    if (principal instanceof Jwt jwt) {
      Object claim = jwt.getClaim("tenant_id");
      if (claim != null) {
        return TenantId.of(UUID.fromString(claim.toString()));
      }
    }
    // Form-login session: the authenticated principal carries the tenant.
    if (principal instanceof TenantAware ta) {
      return ta.tenantId();
    }
    // Some flows attach the tenant claim to the authentication details instead.
    Object details = auth.getDetails();
    if (details instanceof TenantAware ta) {
      return ta.tenantId();
    }
    return null;
  }

  /**
   * Marker for principals that carry the tenant; identity module's UserDetails impl implements
   * this.
   */
  public interface TenantAware {
    /** Returns the tenant identifier carried by this principal. */
    TenantId tenantId();
  }
}
