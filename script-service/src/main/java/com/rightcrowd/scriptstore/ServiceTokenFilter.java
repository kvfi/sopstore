package com.rightcrowd.scriptstore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Minimal service-to-service auth: callers (i.e. sopstore) must present a shared
 * {@code X-Service-Token}. Health/info endpoints are open for probes. A full mTLS/JWT scheme is
 * deferred hardening; this gates the API for the first increment.
 */
@Component
public class ServiceTokenFilter extends OncePerRequestFilter {

  static final String TOKEN_HEADER = "X-Service-Token";

  private final String expectedToken;

  public ServiceTokenFilter(@Value("${scriptstore.service-token}") String expectedToken) {
    this.expectedToken = expectedToken;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String token = request.getHeader(TOKEN_HEADER);
    if (expectedToken.equals(token)) {
      chain.doFilter(request, response);
    } else {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid service token");
    }
  }
}
