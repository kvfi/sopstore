package com.rightcrowd.sopstore.tenancy.internal;

import com.rightcrowd.sopstore.tenancy.TenantContext;
import com.rightcrowd.sopstore.tenancy.TenantId;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Defense-in-depth pair to {@link HibernateTenantResolver}: every statement issued by Hibernate is
 * preceded by {@code SET LOCAL app.tenant_id = '<uuid>'} via a separate listener registered with
 * the JDBC connection — so even if a developer forgets {@code @TenantId}, Postgres RLS still
 * rejects the row.
 *
 * <p>Production-grade implementation is in Phase 1 follow-up: requires a {@code
 * ConnectionAcquisitionListener} to issue the SET, not statement-level inspection. This class is
 * currently a passthrough placeholder so the JPA config wiring resolves; the actual SET LOCAL is
 * issued by a {@code TenantConnectionInitializer} (HikariCP {@code connectionInitSql} +
 * per-acquisition hook) that the tenancy module will add.
 */
public class TenantRlsStatementInspector implements StatementInspector {

  private static final long serialVersionUID = 1L;

  @Override
  public String inspect(String sql) {
    // Statement-level prepending is unsafe (breaks bind parameter ordering
    // and complicates EXPLAIN). The SET LOCAL is issued via the connection
    // acquisition hook instead. We intentionally return the SQL unchanged.
    TenantId t = TenantContext.currentOrNull();
    if (t != null) {
      org.slf4j.MDC.put("tenantId", t.toString());
    }
    return sql;
  }
}
