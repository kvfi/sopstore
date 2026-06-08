package com.rightcrowd.sopstore.platform.security;

import com.rightcrowd.sopstore.platform.PlatformProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XContentTypeOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter.XFrameOptionsMode;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Configures web and method security for the platform. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

  private final PlatformProperties props;
  private final String spaOrigin;

  /** Matches the JSON API surface consumed by the SvelteKit portal and XHR callers. */
  private static final RequestMatcher API = request -> request.getRequestURI().startsWith("/api/");

  /** Creates the security configuration with the given platform properties. */
  public SecurityConfig(
      PlatformProperties props,
      @org.springframework.beans.factory.annotation.Value(
              "${sopstore.spa.origin:http://localhost:5173}")
          String spaOrigin) {
    this.props = props;
    this.spaOrigin = spaOrigin;
  }

  private static boolean wantsJson(HttpServletRequest request) {
    String xrw = request.getHeader("X-Requested-With");
    String accept = request.getHeader("Accept");
    return "XMLHttpRequest".equals(xrw)
        || API.matches(request)
        || (accept != null && accept.contains("application/json"));
  }

  /** CORS for the SvelteKit dev origin (credentialed; the dev proxy avoids it, prod may not). */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(spaOrigin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setExposedHeaders(List.of("X-XSRF-TOKEN"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  /** Provides the Argon2id password encoder used for credential hashing. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    // OWASP-recommended Argon2id parameters (memory 19 MiB, iterations 2, parallelism 1).
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }

  /** Builds the HTTP security filter chain defining authorization, login, CSRF, and headers. */
  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      ObjectProvider<ClientRegistrationRepository> clientRegistrations,
      ObjectProvider<OAuth2UserService<OidcUserRequest, OidcUser>> oidcUserService)
      throws Exception {
    http.cors(Customizer.withDefaults())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/login",
                        "/login/**",
                        "/oauth2/**",
                        "/saml2/**",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/error")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasRole("PLATFORM_OPS")
                    .requestMatchers("/scim/v2/**")
                    .hasRole("SCIM_PROVISIONER")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            form ->
                // No custom login view (API-only backend); the SvelteKit app owns the UI and
                // POSTs to /login. XHR callers get clean status codes; Spring's generated page
                // handles the rare direct browser login.
                form.successHandler(
                        (request, response, authentication) -> {
                          if (wantsJson(request)) {
                            response.setStatus(HttpStatus.NO_CONTENT.value());
                          } else {
                            response.sendRedirect("/swagger-ui/index.html");
                          }
                        })
                    .failureHandler(
                        (request, response, exception) -> {
                          if (wantsJson(request)) {
                            response.sendError(HttpStatus.UNAUTHORIZED.value());
                          } else {
                            response.sendRedirect("/login?error");
                          }
                        })
                    .permitAll())
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> {
                          if (wantsJson(request)) {
                            response.setStatus(HttpStatus.NO_CONTENT.value());
                          } else {
                            response.sendRedirect("/login?logout");
                          }
                        })
                    .deleteCookies("JSESSIONID")
                    .invalidateHttpSession(true))
        .exceptionHandling(
            ex ->
                ex.defaultAuthenticationEntryPointFor(
                    new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                    .ignoringRequestMatchers("/scim/v2/**", "/api/v1/webhooks/inbound/**"))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(props.security().csp()))
                    .httpStrictTransportSecurity(
                        hsts ->
                            hsts.includeSubDomains(true)
                                .maxAgeInSeconds(props.security().hstsMaxAge()))
                    .addHeaderWriter(
                        new ReferrerPolicyHeaderWriter(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                    .addHeaderWriter(new XContentTypeOptionsHeaderWriter())
                    .addHeaderWriter(new XFrameOptionsHeaderWriter(XFrameOptionsMode.DENY)))
        .sessionManagement(
            session ->
                session
                    .sessionFixation(s -> s.changeSessionId())
                    .maximumSessions(props.session().concurrentLimit()));

    // Enable OIDC single sign-on only when an identity provider is configured (the `sso` profile).
    // The default boot has no ClientRegistrationRepository and stays form-login only.
    if (clientRegistrations.getIfAvailable() != null) {
      http.oauth2Login(
          oauth2 ->
              oauth2
                  .defaultSuccessUrl("/swagger-ui/index.html", true)
                  .userInfoEndpoint(
                      userInfo -> userInfo.oidcUserService(oidcUserService.getObject())));
    }
    return http.build();
  }
}
