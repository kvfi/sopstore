package com.rightcrowd.sopstore.identity.internal;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the {@link SessionPolicyFilter} just after tenant resolution in the filter chain. */
@Configuration
class SessionPolicyWebConfig {

  /**
   * Registers the session-policy enforcement filter at order 21 — immediately after the tenant
   * resolver (order 20) and still inside the Spring Security chain, so both the authentication and
   * the tenant context are populated.
   */
  @Bean
  FilterRegistrationBean<SessionPolicyFilter> sessionPolicyFilter(SessionPolicyService service) {
    FilterRegistrationBean<SessionPolicyFilter> registration =
        new FilterRegistrationBean<>(new SessionPolicyFilter(service));
    registration.setOrder(21);
    return registration;
  }
}
