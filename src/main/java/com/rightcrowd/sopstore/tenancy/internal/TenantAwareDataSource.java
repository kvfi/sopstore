package com.rightcrowd.sopstore.tenancy.internal;

import com.rightcrowd.sopstore.tenancy.TenantContext;
import com.rightcrowd.sopstore.tenancy.TenantId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Binds the current tenant to every JDBC connection so Postgres row-level security is enforced.
 *
 * <p>Before a borrowed connection is handed out, {@code set_config('app.tenant_id', <uuid>, false)}
 * sets the session GUC read by the {@code current_tenant_id()} RLS policies. When no tenant is in
 * context the anonymous all-zeros id is bound, which matches no real row. Every borrow re-binds, so
 * a pooled connection never leaks one tenant's id into another's request.
 */
class TenantAwareDataSource extends DelegatingDataSource {

  private static final String BIND_SQL = "select set_config('app.tenant_id', ?, false)";

  TenantAwareDataSource(DataSource target) {
    super(target);
  }

  @Override
  public Connection getConnection() throws SQLException {
    return bind(super.getConnection());
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    return bind(super.getConnection(username, password));
  }

  private static Connection bind(Connection connection) throws SQLException {
    TenantId tenant = TenantContext.currentOrNull();
    UUID id = tenant == null ? HibernateTenantResolver.ANONYMOUS : tenant.value();
    try (PreparedStatement statement = connection.prepareStatement(BIND_SQL)) {
      statement.setString(1, id.toString());
      statement.execute();
    } catch (SQLException e) {
      connection.close();
      throw e;
    }
    return connection;
  }
}
