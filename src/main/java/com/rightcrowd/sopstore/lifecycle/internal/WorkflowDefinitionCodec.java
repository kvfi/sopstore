package com.rightcrowd.sopstore.lifecycle.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.lifecycle.SignatureMeaning;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializes and parses a workflow's ordered stage list to and from the {@code stages_json} column.
 *
 * <p>Owns its own {@link ObjectMapper} (Jackson 2) rather than injecting Spring's primary mapper,
 * which under Spring Boot 4 is Jackson 3 — keeping this codec independent of that choice. The JSON
 * shape is intentionally small and hand-built so a tenant admin can edit it directly:
 *
 * <pre>{"stages":[{"name":"Quality Review","roles":["REVIEWER"],"meaning":"REVIEWED",
 *               "slaHours":48,"condition":"ALWAYS"}, ...]}</pre>
 */
final class WorkflowDefinitionCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private WorkflowDefinitionCodec() {}

  /** Serializes the stage list to the {@code stages_json} representation. */
  static String toJson(List<WorkflowStage> stages) {
    ObjectNode root = MAPPER.createObjectNode();
    ArrayNode arr = root.putArray("stages");
    for (WorkflowStage s : stages) {
      ObjectNode node = arr.addObject();
      node.put("name", s.name());
      ArrayNode roles = node.putArray("roles");
      s.approverRoles().forEach(r -> roles.add(r.name()));
      node.put("meaning", s.meaning().name());
      node.put("slaHours", s.slaHours());
      node.put("condition", s.condition().name());
    }
    try {
      return MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      throw new IllegalStateException("failed to serialize workflow stages", e);
    }
  }

  /** Parses a {@code stages_json} value into an ordered stage list. */
  static List<WorkflowStage> fromJson(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      JsonNode stages = root.path("stages");
      List<WorkflowStage> out = new ArrayList<>();
      for (JsonNode s : stages) {
        List<Role> roles = new ArrayList<>();
        for (JsonNode r : s.path("roles")) {
          roles.add(Role.valueOf(r.asText()));
        }
        out.add(
            new WorkflowStage(
                s.path("name").asText(),
                List.copyOf(roles),
                SignatureMeaning.valueOf(s.path("meaning").asText("APPROVED")),
                s.path("slaHours").asInt(72),
                StageCondition.valueOf(s.path("condition").asText("ALWAYS"))));
      }
      return out;
    } catch (Exception e) {
      throw new IllegalStateException("failed to parse workflow stages: " + json, e);
    }
  }
}
