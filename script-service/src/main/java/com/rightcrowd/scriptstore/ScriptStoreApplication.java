package com.rightcrowd.scriptstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone service hosting a tenant-scoped, versioned repository of scripts. Independent of
 * sopstore: its own database, API, and deploy. sopstore calls it over HTTP (service token +
 * {@code X-Tenant-Id}) to list scripts and pin a script version into a RUN_SCRIPT step.
 */
@SpringBootApplication
public class ScriptStoreApplication {

  /** Boots the script service. */
  public static void main(String[] args) {
    SpringApplication.run(ScriptStoreApplication.class, args);
  }
}
