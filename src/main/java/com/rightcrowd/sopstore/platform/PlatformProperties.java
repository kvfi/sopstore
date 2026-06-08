package com.rightcrowd.sopstore.platform;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for the SOP store platform. */
@ConfigurationProperties(prefix = "sopstore")
public record PlatformProperties(
    String deploymentMode,
    String buildVersion,
    Security security,
    Session session,
    Storage storage,
    Tenancy tenancy) {
  /** Returns the resolved deployment mode. */
  public DeploymentMode mode() {
    return DeploymentMode.fromConfig(deploymentMode);
  }

  /** Security related platform settings. */
  public record Security(String csp, long hstsMaxAge) {}

  /** Session lifetime and concurrency settings. */
  public record Session(Duration idleTimeout, Duration absoluteTimeout, int concurrentLimit) {}

  /** Object storage connection settings. */
  public record Storage(String bucket, String endpoint, String accessKey, String secretKey) {}

  /** Tenancy and isolation settings. */
  public record Tenancy(String isolation, boolean multiTenant, String fixedTenantId) {}
}
