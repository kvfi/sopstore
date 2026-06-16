package com.rightcrowd.sopstore.dataops.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rightcrowd.sopstore.audit.AuditPort;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-tenant data export/import. Export serialises the current tenant's rows (RLS auto-scopes the
 * reads) from every tenant-scoped table to a JSON bundle; import replaces them in one transaction.
 * Excluded by design: the append-only Part 11 audit trail, Modulith event internals, and identity
 * (users/roles — so an import can't overwrite the signed-in admin or move password hashes).
 */
@Service
public class TenantDataService {

  static final int FORMAT_VERSION = 1;

  private static final Set<String> DENYLIST =
      Set.of(
          "audit_event",
          "event_publication",
          "app_user",
          "user_role",
          "user_group",
          "user_group_member",
          "mfa_credential");

  // Local Jackson 2 mapper: Boot 4's primary mapper is Jackson 3, so there is no Jackson 2
  // ObjectMapper bean to inject (the rest of the app constructs its own the same way).
  private static final ObjectMapper JSON = new ObjectMapper();

  private final JdbcTemplate jdbc;
  private final AuditPort audit;

  /** Creates the service with its JDBC template and the audit port. */
  public TenantDataService(JdbcTemplate jdbc, AuditPort audit) {
    this.jdbc = jdbc;
    this.audit = audit;
  }

  /** Summary of an import: how many tables and rows were written. */
  public record ImportResult(int tables, int rows) {}

  /** Serialises the current tenant's data to a JSON bundle string. */
  @Transactional(readOnly = true)
  public String export() {
    ObjectNode bundle = JSON.createObjectNode();
    bundle.put("formatVersion", FORMAT_VERSION);
    bundle.put("exportedAt", Instant.now().toString());
    bundle.put("sourceTenantId", TenantContext.current().value().toString());
    ObjectNode tables = bundle.putObject("tables");
    for (String t : tenantTables()) {
      String arr =
          jdbc.queryForObject(
              "SELECT COALESCE(jsonb_agg(to_jsonb(x)), '[]'::jsonb)::text FROM " + ident(t) + " x",
              String.class);
      tables.set(t, readJson(arr == null ? "[]" : arr));
    }
    try {
      return JSON.writeValueAsString(bundle);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialise export bundle", e);
    }
  }

  /**
   * Replaces the current tenant's data from a previously exported bundle. All-or-nothing: deletes
   * the tenant's rows (children first) then inserts the bundle's rows (parents first), forcing each
   * row's {@code tenant_id} to the current tenant so RLS makes cross-tenant writes impossible.
   */
  @Transactional
  public ImportResult importBundle(JsonNode bundle, UUID actor) {
    if (bundle.path("formatVersion").asInt(-1) != FORMAT_VERSION) {
      throw new IllegalArgumentException("Unsupported or missing bundle formatVersion");
    }
    JsonNode tablesNode = bundle.path("tables");
    if (!tablesNode.isObject()) {
      throw new IllegalArgumentException("Bundle has no tables object");
    }
    UUID tenant = TenantContext.current().value();
    Set<String> present = new LinkedHashSet<>();
    for (String t : tenantTables()) {
      if (tablesNode.has(t)) {
        present.add(t);
      }
    }
    List<String> ordered = topoSort(present); // parents before children

    for (int i = ordered.size() - 1; i >= 0; i--) {
      jdbc.update("DELETE FROM " + ident(ordered.get(i)));
    }
    int rows = 0;
    for (String t : ordered) {
      // List insertable columns explicitly: a generic SELECT * would try to write generated
      // columns (e.g. the procedure search_vec tsvector), which Postgres rejects.
      String cols =
          insertColumns(t).stream().map(TenantDataService::quote).collect(Collectors.joining(", "));
      String sql =
          "INSERT INTO "
              + ident(t)
              + " ("
              + cols
              + ") SELECT "
              + cols
              + " FROM jsonb_populate_record(NULL::"
              + ident(t)
              + ", jsonb_set(?::jsonb, '{tenant_id}', to_jsonb(?::uuid))) AS t";
      for (JsonNode row : tablesNode.path(t)) {
        jdbc.update(sql, row.toString(), tenant.toString());
        rows++;
      }
    }
    String detail = "{\"tables\":" + ordered.size() + ",\"rows\":" + rows + "}";
    audit.record(
        AuditPort.AuditEntry.of("tenant", tenant.toString(), "DATA_IMPORT", actor, detail));
    return new ImportResult(ordered.size(), rows);
  }

  /** The writable (non-generated, non-identity) columns of a table, in ordinal order. */
  private List<String> insertColumns(String table) {
    return jdbc.queryForList(
        "SELECT column_name FROM information_schema.columns "
            + "WHERE table_schema = 'public' AND table_name = ? "
            + "AND is_generated = 'NEVER' AND is_identity = 'NO' "
            + "ORDER BY ordinal_position",
        String.class,
        table);
  }

  /** Tenant-scoped tables (those with a {@code tenant_id} column), minus the denylist. */
  private List<String> tenantTables() {
    List<String> all =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND column_name = 'tenant_id' "
                + "ORDER BY table_name",
            String.class);
    List<String> out = new ArrayList<>();
    for (String t : all) {
      if (!DENYLIST.contains(t)) {
        out.add(t);
      }
    }
    return out;
  }

  /** Orders the given tables parents-before-children using their FK edges. */
  private List<String> topoSort(Set<String> tables) {
    Map<String, Set<String>> parents = new HashMap<>();
    for (String t : tables) {
      parents.put(t, new HashSet<>());
    }
    jdbc.query(
        "SELECT tc.table_name AS child, ccu.table_name AS parent "
            + "FROM information_schema.table_constraints tc "
            + "JOIN information_schema.constraint_column_usage ccu "
            + "  ON tc.constraint_name = ccu.constraint_name "
            + "  AND tc.table_schema = ccu.table_schema "
            + "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public'",
        rs -> {
          String child = rs.getString("child");
          String parent = rs.getString("parent");
          if (tables.contains(child) && tables.contains(parent) && !child.equals(parent)) {
            parents.computeIfAbsent(child, k -> new HashSet<>()).add(parent);
          }
        });
    List<String> out = new ArrayList<>();
    Set<String> done = new HashSet<>();
    while (out.size() < tables.size()) {
      boolean progressed = false;
      for (String t : tables) {
        if (done.contains(t) || !done.containsAll(parents.getOrDefault(t, Set.of()))) {
          continue;
        }
        out.add(t);
        done.add(t);
        progressed = true;
      }
      if (!progressed) { // FK cycle — append the remainder in any order
        for (String t : tables) {
          if (done.add(t)) {
            out.add(t);
          }
        }
        break;
      }
    }
    return out;
  }

  private JsonNode readJson(String json) {
    try {
      return JSON.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to read exported rows", e);
    }
  }

  /** Quotes a table identifier; rejects anything that is not a plain lower_snake_case name. */
  private static String ident(String table) {
    return quote(table);
  }

  /** Quotes a SQL identifier; rejects anything that is not a plain lower_snake_case name. */
  private static String quote(String name) {
    if (!name.matches("[a-z_][a-z0-9_]*")) {
      throw new IllegalArgumentException("Invalid identifier: " + name);
    }
    return "\"" + name + "\"";
  }
}
