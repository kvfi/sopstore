package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the tenant's {@link SessionPolicy}. On the first authenticated request of a session it
 * applies the tenant's idle timeout (the servlet session's max-inactive interval) and records the
 * absolute-expiry instant as a session attribute; on every later request it expires the session
 * once that instant passes, returning 401 so the SPA redirects to sign-in. The policy is read once
 * per session (cached in the attribute), so there is no per-request database hit.
 */
public class SessionPolicyFilter extends OncePerRequestFilter {

  static final String ABSOLUTE_EXPIRY_ATTR = "sopstore.session.absoluteExpiry";

  private final SessionPolicyService service;

  /** Creates the filter with the session-policy service. */
  SessionPolicyFilter(SessionPolicyService service) {
    this.service = service;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    HttpSession session = request.getSession(false);
    if (session != null && authenticated() && TenantContext.currentOrNull() != null) {
      Object stored = session.getAttribute(ABSOLUTE_EXPIRY_ATTR);
      long absoluteExpiry;
      if (stored instanceof Long existing) {
        absoluteExpiry = existing;
      } else {
        SessionPolicyService.EffectivePolicy policy = service.effective();
        session.setMaxInactiveInterval(policy.idleSeconds());
        absoluteExpiry = session.getCreationTime() + policy.absoluteSeconds() * 1000L;
        session.setAttribute(ABSOLUTE_EXPIRY_ATTR, absoluteExpiry);
      }
      if (System.currentTimeMillis() > absoluteExpiry) {
        session.invalidate();
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean authenticated() {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
  }
}
