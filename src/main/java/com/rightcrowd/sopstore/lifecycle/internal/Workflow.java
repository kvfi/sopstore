package com.rightcrowd.sopstore.lifecycle.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

/**
 * A named, tenant-owned approval workflow template. The ordered stage definition is stored as JSON
 * in {@code stages_json}; the engine parses it with {@code WorkflowDefinitionCodec}.
 */
@Entity
@Table(name = "workflow")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Workflow {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "stages_json", nullable = false, columnDefinition = "jsonb")
  private String stagesJson = "{\"stages\":[]}";

  /** Creates an empty workflow for use by the persistence provider. */
  protected Workflow() {}

  /** Creates a workflow template with the given identity and JSON stage definition. */
  public Workflow(UUID id, UUID tenantId, String name, String stagesJson) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
    this.stagesJson = stagesJson;
  }

  /** Returns the workflow identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the workflow name. */
  public String name() {
    return name;
  }

  /** Returns the JSON stage definition. */
  public String stagesJson() {
    return stagesJson;
  }

  /** Replaces the JSON stage definition. */
  public void setStagesJson(String stagesJson) {
    this.stagesJson = stagesJson;
  }
}
