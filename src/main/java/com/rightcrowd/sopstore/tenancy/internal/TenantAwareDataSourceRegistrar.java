package com.rightcrowd.sopstore.tenancy.internal;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Wraps the auto-configured {@link DataSource} with {@link TenantAwareDataSource} so every
 * connection carries the current tenant for Postgres row-level security.
 */
@Component
class TenantAwareDataSourceRegistrar implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof DataSource dataSource && !(bean instanceof TenantAwareDataSource)) {
      return new TenantAwareDataSource(dataSource);
    }
    return bean;
  }
}
