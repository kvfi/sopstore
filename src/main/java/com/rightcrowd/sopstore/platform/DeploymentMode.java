package com.rightcrowd.sopstore.platform;

import java.util.Locale;

/** Identifies how the application is deployed and operated. */
public enum DeploymentMode {
  SAAS,
  SINGLE_TENANT,
  ONPREM;

  /** Parses a configuration string into the matching deployment mode. */
  public static DeploymentMode fromConfig(String raw) {
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "saas" -> SAAS;
      case "single-tenant", "single_tenant", "singletenant" -> SINGLE_TENANT;
      case "onprem", "on-prem", "air-gapped", "airgapped" -> ONPREM;
      default ->
          throw new IllegalArgumentException(
              "Unknown deployment mode: " + raw + ". Use one of: saas, single-tenant, onprem.");
    };
  }

  /** Returns whether this mode hosts multiple tenants. */
  public boolean isMultiTenant() {
    return this == SAAS;
  }

  /** Returns whether this mode runs in an air-gapped environment. */
  public boolean isAirGapped() {
    return this == ONPREM;
  }
}
