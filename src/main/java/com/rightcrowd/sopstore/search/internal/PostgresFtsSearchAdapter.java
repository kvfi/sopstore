package com.rightcrowd.sopstore.search.internal;

import com.rightcrowd.sopstore.search.SearchPort;
import com.rightcrowd.sopstore.tenancy.TenantId;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Postgres FTS adapter — searches the {@code procedure_version.search_vec} gin index. */
@Component
public class PostgresFtsSearchAdapter implements SearchPort {

  private final JdbcTemplate jdbc;

  /** Creates the adapter backed by the given data source. */
  public PostgresFtsSearchAdapter(DataSource ds) {
    this.jdbc = new JdbcTemplate(ds);
  }

  @Override
  public SearchPage<ProcedureHit> searchProcedures(SearchQuery q, Pageable p, TenantId tenant) {
    String sql =
        """
        SELECT proc.id, proc.title,
               ts_headline('simple', pv.body_json::text,
                           plainto_tsquery('simple', ?), 'MaxFragments=2'),
               ts_rank(pv.search_vec, plainto_tsquery('simple', ?))
        FROM procedure proc
        JOIN procedure_version pv ON pv.id = proc.current_version_id
        WHERE proc.tenant_id = ? AND pv.search_vec @@ plainto_tsquery('simple', ?)
        ORDER BY 4 DESC
        LIMIT ? OFFSET ?
        """;
    List<ProcedureHit> hits =
        jdbc.query(
            sql,
            (rs, i) ->
                new ProcedureHit(
                    UUID.fromString(rs.getString(1)),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getFloat(4)),
            q.text(),
            q.text(),
            tenant.value(),
            q.text(),
            p.getPageSize(),
            p.getOffset());
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM procedure_version pv WHERE pv.tenant_id = ? AND"
                + " pv.search_vec @@ plainto_tsquery('simple', ?)",
            Long.class,
            tenant.value(),
            q.text());
    return new SearchPage<>(hits, total == null ? 0 : total);
  }

  @Override
  public void indexProcedureVersion(UUID versionId) {
    /* search_vec is GENERATED */
  }

  @Override
  public void remove(UUID procedureId) {
    /* cascading from procedure delete */
  }
}
