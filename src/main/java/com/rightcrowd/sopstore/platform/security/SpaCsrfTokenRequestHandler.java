package com.rightcrowd.sopstore.platform.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

/**
 * CSRF handler that serves both server-rendered forms and the SvelteKit single-page app from one
 * {@code CookieCsrfTokenRepository}.
 *
 * <p>Per Spring Security's SPA guidance: render with the BREACH-protected XOR handler (so Thymeleaf
 * form fields carry a masked token), but when a token arrives in the {@code X-XSRF-TOKEN} header —
 * how the SPA sends it, read straight from the {@code XSRF-TOKEN} cookie — resolve it as the plain
 * value. Falling back to XOR resolution for the form parameter keeps the HTML pages working.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

  private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
  private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
    // Always render via the XOR handler so per-render masking (BREACH protection) applies.
    xor.handle(request, response, csrfToken);
  }

  @Override
  public @Nullable String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
    // Header (SPA, raw cookie value) → plain; form parameter (Thymeleaf, masked) → XOR.
    if (request.getHeader(csrfToken.getHeaderName()) != null) {
      return plain.resolveCsrfTokenValue(request, csrfToken);
    }
    return xor.resolveCsrfTokenValue(request, csrfToken);
  }
}
