package com.rightcrowd.sopstore.platform.persistence;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

/**
 * Migrates each application module with its own Flyway instance and history table.
 *
 * <p>Every module ships an independent migration timeline (each starting at {@code V001}), which a
 * single shared Flyway instance cannot support — versions must be globally unique. Instead, one
 * Flyway runs per {@code spring.flyway.locations} entry, tracking state in a dedicated {@code
 * flyway_schema_history_<module>} table. The locations are migrated in declared order, which must
 * be a topological order over cross-module foreign keys.
 *
 * <p>All modules share one schema, so after the first module runs the schema is non-empty. When a
 * later module's Flyway finds no history table in a non-empty schema it requires a baseline, so
 * {@code baselineOnMigrate} is enabled — but with {@code baselineVersion = 0} so that each module's
 * {@code V001} (being greater than the baseline) is still applied rather than skipped.
 *
 * <p><b>Migration vs runtime credentials.</b> Migrations need DDL + {@code FORCE ROW LEVEL
 * SECURITY} privileges, which only the schema owner has — but at runtime the app should connect as
 * a non-superuser so RLS actually bites (see {@code security/V001}). When {@code spring.flyway.url}
 * and {@code spring.flyway.user} are set they migrate as the owner; otherwise migrations fall back
 * to the application {@link DataSource} (the single-credential default used by tests and simple
 * setups).
 */
@Component
class PerModuleFlywayMigrationStrategy implements FlywayMigrationStrategy {

  private final DataSource dataSource;
  private final FlywayProperties properties;

  PerModuleFlywayMigrationStrategy(DataSource dataSource, FlywayProperties properties) {
    this.dataSource = dataSource;
    this.properties = properties;
  }

  @Override
  public void migrate(Flyway autoConfigured) {
    DataSource migrationDataSource = migrationDataSource();
    for (String location : properties.getLocations()) {
      String module = location.substring(location.lastIndexOf('/') + 1);
      Flyway.configure()
          .dataSource(migrationDataSource)
          .locations(location)
          .table("flyway_schema_history_" + module)
          .baselineOnMigrate(true)
          .baselineVersion("0")
          .load()
          .migrate();
    }
  }

  /**
   * The owner-credentialed DataSource to migrate with, or the application DataSource when no
   * dedicated Flyway credentials are configured.
   */
  private DataSource migrationDataSource() {
    String url = properties.getUrl();
    String user = properties.getUser();
    if (url == null || url.isBlank() || user == null || user.isBlank()) {
      return dataSource;
    }
    String password = properties.getPassword();
    return new DriverManagerDataSource(url, user, password == null ? "" : password);
  }
}
