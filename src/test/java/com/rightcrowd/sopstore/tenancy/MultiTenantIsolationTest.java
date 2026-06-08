package com.rightcrowd.sopstore.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.rightcrowd.sopstore.identity.User;
import com.rightcrowd.sopstore.identity.internal.UserRepository;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.internal.ProcedureRepository;
import com.rightcrowd.sopstore.tenancy.internal.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Acceptance criterion from the spec:
 *
 * <blockquote>
 *
 * A user in tenant A cannot read, list, or mutate anything in tenant B even by guessing IDs.
 *
 * </blockquote>
 *
 * <p>Runs against a dedicated Postgres container (so it never touches a developer's database). Two
 * tenants are provisioned, each while its own context is active. Cross-tenant access returns empty,
 * blocked at the Hibernate {@code @TenantId} filter and RLS (the {@code security} migration
 * forces RLS onto the owner role, and the tenant-aware DataSource binds {@code app.tenant_id} per
 * connection). The DataSource user is intentionally NOT granted {@code sopstore_bypass_rls}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class MultiTenantIsolationTest {

  // Runs as the container superuser at startup (postgres /docker-entrypoint-initdb.d).
  // Creates a non-superuser app role: RLS is only enforced for non-superusers, so the app
  // must not connect as the bootstrap superuser. Extensions + the BYPASSRLS role need
  // superuser, so they are created here (the module migrations' IF NOT EXISTS then no-op).
  private static final String RLS_BOOTSTRAP =
      """
      CREATE EXTENSION IF NOT EXISTS pgcrypto;
      CREATE EXTENSION IF NOT EXISTS pg_trgm;
      CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
      CREATE ROLE sopstore_bypass_rls NOLOGIN BYPASSRLS;
      CREATE ROLE sopstore_app LOGIN PASSWORD 'sopstore_app' NOSUPERUSER;
      GRANT CREATE, USAGE ON SCHEMA public TO sopstore_app;
      """;

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16.4")
          .withCopyToContainer(
              Transferable.of(RLS_BOOTSTRAP), "/docker-entrypoint-initdb.d/10-rls.sql");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    // Connect as the non-superuser role the init script created (NOT the container's
    // bootstrap superuser, which would bypass RLS). It owns the schema it migrates, and
    // the security migration forces RLS onto it.
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "sopstore_app");
    registry.add("spring.datasource.password", () -> "sopstore_app");
  }

  @Autowired ProcedureRepository procedures;
  @Autowired TenantRepository tenants;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  @Test
  void secondTenantSeesNothingFromFirstTenant() {
    UUID tenantA = UUID.randomUUID();
    UUID tenantB = UUID.randomUUID();
    UUID owner = UUID.randomUUID();
    UUID procedureId = UUID.randomUUID();

    // Each tenant is provisioned under its own context: the RLS WITH CHECK clause
    // requires the inserted id/tenant_id to equal current_tenant_id().
    runAs(
        tenantA,
        () -> {
          tenants.save(new Tenant(tenantA, "Tenant A", "tenant-a"));
          users.save(new User(owner, tenantA, "owner@a.test", "Owner A", "x"));
          procedures.save(new Procedure(procedureId, tenantA, "SOP-A-1", "secret-a", owner));
        });
    runAs(tenantB, () -> tenants.save(new Tenant(tenantB, "Tenant B", "tenant-b")));

    runAs(
        tenantB,
        () -> {
          assertThat(procedures.findById(procedureId))
              .as("tenant B must not see tenant A's procedure even by id")
              .isEmpty();
          assertThat(procedures.findAll())
              .as("tenant B's listing must exclude tenant A's rows")
              .extracting(Procedure::title)
              .doesNotContain("secret-a");
          // Bypass the Hibernate @TenantId filter entirely: a raw SQL count must still
          // be zero, proving Postgres RLS — not just the ORM filter — hides the row.
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from procedure where id = ?", Long.class, procedureId))
              .as("Postgres RLS must hide tenant A's row from a raw query run as tenant B")
              .isZero();
        });

    // Positive control: tenant A still sees its own row.
    runAs(
        tenantA,
        () ->
            assertThat(procedures.findById(procedureId))
                .as("tenant A must still see its own procedure")
                .isPresent());
  }

  private static void runAs(UUID tenant, Runnable body) {
    TenantContext.set(new TenantId(tenant));
    try {
      body.run();
    } finally {
      TenantContext.clear();
    }
  }
}
