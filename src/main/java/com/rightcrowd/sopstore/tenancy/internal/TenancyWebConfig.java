package com.rightcrowd.sopstore.tenancy.internal;

import com.rightcrowd.sopstore.platform.PlatformProperties;
import com.rightcrowd.sopstore.tenancy.TenantResolverFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers tenancy-related servlet filters. */
@Configuration
class TenancyWebConfig {

  /**
   * Registers the tenant resolver filter after Spring Security (order 20). The filter is built with
   * {@code new} so it never becomes a container-managed bean and is therefore never CGLIB-proxied
   * by Spring Modulith observability — a proxy would leave the inherited {@code logger} field null
   * and break the servlet container's {@code init()} call.
   */
  @Bean
  FilterRegistrationBean<TenantResolverFilter> tenantResolverFilter(PlatformProperties props) {
    var registration = new FilterRegistrationBean<>(new TenantResolverFilter(props));
    registration.setOrder(20); // After Spring Security, before MVC.
    return registration;
  }
}
