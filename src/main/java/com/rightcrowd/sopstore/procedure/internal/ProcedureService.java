package com.rightcrowd.sopstore.procedure.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.difflib.text.DiffRowGenerator;
import com.rightcrowd.sopstore.identity.UserDirectory;
import com.rightcrowd.sopstore.procedure.ConfidentialityLevel;
import com.rightcrowd.sopstore.procedure.DiffLine;
import com.rightcrowd.sopstore.procedure.DocTemplate;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureVersion;
import com.rightcrowd.sopstore.procedure.Step;
import com.rightcrowd.sopstore.procedure.events.ProcedureCreated;
import com.rightcrowd.sopstore.procedure.events.ProcedureVersionCreated;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for creating and versioning procedures. */
@Service
@Transactional
public class ProcedureService {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final ProcedureRepository procedures;
  private final ProcedureVersionRepository versions;
  private final StepRepository steps;
  private final DocumentNumberAllocator numbers;
  private final ApplicationEventPublisher events;
  private final DocTemplateRepository docTemplates;
  private final ConfidentialityLevelRepository confidentialityLevels;
  private final UserDirectory users;
  private final ScriptBundleSettingsService bundleSettings;

  /** Creates the service with its repositories, allocator, event publisher, and user directory. */
  public ProcedureService(
      ProcedureRepository procedures,
      ProcedureVersionRepository versions,
      StepRepository steps,
      DocumentNumberAllocator numbers,
      ApplicationEventPublisher events,
      DocTemplateRepository docTemplates,
      ConfidentialityLevelRepository confidentialityLevels,
      UserDirectory users,
      ScriptBundleSettingsService bundleSettings) {
    this.procedures = procedures;
    this.versions = versions;
    this.steps = steps;
    this.numbers = numbers;
    this.events = events;
    this.docTemplates = docTemplates;
    this.confidentialityLevels = confidentialityLevels;
    this.users = users;
    this.bundleSettings = bundleSettings;
  }

  /** A prerequisite line ({@code type} + {@code text}) to seed into a new procedure's body. */
  public record NewPrerequisite(String type, String text) {}

  /**
   * Creates a new procedure of the given type with an initial version and publishes the related
   * events. The document number is generated automatically (e.g. {@code SOP_7F3KQ9}) from the type
   * prefix and a random token — callers do not supply it. An administrator may later override it
   * via {@link #renameDocumentNumber}. Any supplied prerequisites are seeded into the initial
   * version body.
   */
  public Procedure create(
      String title,
      Procedure.DocumentType type,
      UUID ownerId,
      List<NewPrerequisite> prerequisites) {
    UUID tenant = TenantContext.current().value();
    String documentNumber = numbers.allocate(type);
    Procedure p = new Procedure(UUID.randomUUID(), tenant, documentNumber, title, ownerId);
    p.setType(type);
    procedures.save(p);

    ProcedureVersion v = new ProcedureVersion(UUID.randomUUID(), tenant, p.id(), 0, 1, ownerId);
    String seededBody = bodyWithPrerequisites(prerequisites);
    if (seededBody != null) {
      v.setBody(seededBody);
    }
    versions.save(v);
    p.setCurrentVersion(v.id());
    procedures.save(p); // re-save: assigned-id save() merges, so the pointer must be flushed back

    events.publishEvent(new ProcedureCreated(p.id(), tenant, ownerId));
    events.publishEvent(new ProcedureVersionCreated(p.id(), v.id(), tenant));
    return p;
  }

  /**
   * Renames a procedure's document number. This is an admin-only override (enforced at the
   * controller via {@code hasRole('TENANT_ADMIN')}). The new number is trimmed, must be non-blank,
   * at most 64 characters, and unique within the tenant.
   */
  public Procedure renameDocumentNumber(UUID procedureId, String newNumber) {
    String trimmed = newNumber == null ? "" : newNumber.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("document number must not be blank");
    }
    if (trimmed.length() > 64) {
      throw new IllegalArgumentException("document number must be at most 64 characters");
    }
    Procedure p = procedures.findById(procedureId).orElseThrow();
    if (!trimmed.equals(p.documentNumber()) && procedures.existsByDocumentNumber(trimmed)) {
      throw new IllegalArgumentException("document number already in use: " + trimmed);
    }
    p.setDocumentNumber(trimmed);
    return procedures.save(p);
  }

  /**
   * Classifies a procedure with a confidentiality level (or clears it when {@code levelId} null).
   * A non-null level must exist in the tenant's catalogue.
   */
  public Procedure setConfidentiality(UUID procedureId, @Nullable UUID levelId) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    if (levelId != null && confidentialityLevels.findById(levelId).isEmpty()) {
      throw new IllegalArgumentException("unknown confidentiality level: " + levelId);
    }
    p.setConfidentialityLevel(levelId);
    return procedures.save(p);
  }

  /** Returns the name of a procedure's confidentiality level, or null when unclassified. */
  public @Nullable String confidentialityName(UUID procedureId) {
    UUID levelId =
        procedures.findById(procedureId).map(Procedure::confidentialityLevelId).orElse(null);
    if (levelId == null) {
      return null;
    }
    return confidentialityLevels.findById(levelId).map(ConfidentialityLevel::name).orElse(null);
  }

  /**
   * Builds an initial body JSON ({@code {"prerequisites":[{type,text},…]}}) from the given lines,
   * skipping blank text; returns null when there is nothing to seed (keep the default empty body).
   */
  private static @Nullable String bodyWithPrerequisites(List<NewPrerequisite> prerequisites) {
    if (prerequisites == null || prerequisites.isEmpty()) {
      return null;
    }
    ObjectNode body = JSON.createObjectNode();
    ArrayNode arr = body.putArray("prerequisites");
    for (NewPrerequisite pr : prerequisites) {
      String text = pr.text() == null ? "" : pr.text().trim();
      if (text.isEmpty()) {
        continue;
      }
      ObjectNode node = arr.addObject();
      node.put("type", pr.type() == null ? "" : pr.type().trim());
      node.put("text", text);
    }
    return arr.isEmpty() ? null : body.toString();
  }

  /** Creates a new draft version for the given procedure and publishes a version-created event. */
  public ProcedureVersion newDraftVersion(UUID procedureId, UUID byUser, String bodyJson) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    var latest =
        versions
            .findTopByProcedureIdOrderByVersionMajorDescVersionMinorDesc(procedureId)
            .orElseThrow();
    int major = latest.versionMajor();
    int minor = latest.versionMinor() + 1;
    ProcedureVersion v =
        new ProcedureVersion(UUID.randomUUID(), p.tenantId(), p.id(), major, minor, byUser);
    v.setBody(bodyJson);
    versions.save(v);
    events.publishEvent(new ProcedureVersionCreated(p.id(), v.id(), p.tenantId()));
    return v;
  }

  /** Returns the TipTap JSON body of the procedure's current version. */
  public String currentVersionBody(UUID procedureId) {
    return currentVersion(procedureId).bodyJson();
  }

  /** Returns the current-version body JSON, or an empty doc if the procedure has no version. */
  public String bodyOrEmpty(UUID procedureId) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    UUID versionId = p.currentVersionId();
    if (versionId == null) {
      return "{}";
    }
    return versions.findById(versionId).map(ProcedureVersion::bodyJson).orElse("{}");
  }

  /** Renders the procedure (cover + body + steps) to a themed PDF document. */
  public byte[] exportPdf(UUID procedureId) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    UUID versionId = p.currentVersionId();
    ProcedureVersion version = versionId == null ? null : versions.findById(versionId).orElse(null);
    String body = version == null ? "{}" : version.bodyJson();
    var history = versions.findByProcedureIdOrderByVersionMajorDescVersionMinorDesc(procedureId);
    Map<UUID, String> authorNames = authorNames(history, version);
    String confidentiality = confidentialityName(procedureId);
    return PdfExporter.export(
        p,
        version,
        body,
        templateFromBody(body),
        history,
        authorNames,
        confidentiality,
        bundleSettings.effectiveConfig());
  }

  /** Resolves each version author's display name once, for the PDF document-history table. */
  private Map<UUID, String> authorNames(
      List<ProcedureVersion> history, @Nullable ProcedureVersion version) {
    Map<UUID, String> names = new HashMap<>();
    List<ProcedureVersion> all =
        history.isEmpty() && version != null ? List.of(version) : history;
    for (ProcedureVersion v : all) {
      names.computeIfAbsent(v.createdBy(), id -> users.displayNameById(id).orElse("—"));
    }
    return names;
  }

  /** Loads the export template referenced by the body's {@code templateId}, if any. */
  private @Nullable DocTemplate templateFromBody(String body) {
    try {
      JsonNode node = JSON.readTree(body == null || body.isBlank() ? "{}" : body);
      String tid = node.path("templateId").asText("");
      if (!tid.isBlank()) {
        return docTemplates.findById(UUID.fromString(tid)).orElse(null);
      }
    } catch (IOException | RuntimeException e) {
      // malformed body or id — fall back to the default (unthemed) export
    }
    return null;
  }

  /** Saves the editor's JSON document into the procedure's current version. */
  public void saveCurrentVersionBody(UUID procedureId, String bodyJson) {
    ProcedureVersion version = currentVersion(procedureId);
    version.setBody(bodyJson);
    versions.save(version);
  }

  /** Sets the free-form version label on the procedure's current version. */
  public void setCurrentVersionLabel(UUID procedureId, String label) {
    ProcedureVersion version = currentVersion(procedureId);
    version.setLabel(label);
    versions.save(version);
  }

  /** Canonical serialization of the current version, used as the e-signature payload. */
  public byte[] currentVersionCanonical(UUID procedureId) {
    ProcedureVersion v = currentVersion(procedureId);
    String canonical =
        v.id() + "|" + v.versionMajor() + "." + v.versionMinor() + "|" + v.bodyJson();
    return canonical.getBytes(StandardCharsets.UTF_8);
  }

  /** Returns all versions of the procedure, newest first. */
  public List<ProcedureVersion> listVersions(UUID procedureId) {
    return versions.findByProcedureIdOrderByVersionMajorDescVersionMinorDesc(procedureId);
  }

  /** Returns the version with the given id. */
  public ProcedureVersion version(UUID versionId) {
    return versions.findById(versionId).orElseThrow();
  }

  /**
   * Creates a new draft version seeded from the procedure's current body and makes it current. Used
   * to revise a controlled document so prior versions remain immutable and diffable.
   */
  public ProcedureVersion newVersionFromCurrent(UUID procedureId, UUID byUser) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    String body = currentVersion(procedureId).bodyJson();
    ProcedureVersion v = newDraftVersion(procedureId, byUser, body);
    p.setCurrentVersion(v.id());
    procedures.save(p);
    return v;
  }

  /** Produces a side-by-side diff of two versions' (pretty-printed) bodies. */
  public List<DiffLine> diff(UUID fromVersionId, UUID toVersionId) {
    List<String> oldLines = prettyLines(version(fromVersionId).bodyJson());
    List<String> newLines = prettyLines(version(toVersionId).bodyJson());
    DiffRowGenerator generator =
        DiffRowGenerator.create().showInlineDiffs(false).build();
    return generator.generateDiffRows(oldLines, newLines).stream()
        .map(r -> new DiffLine(r.getTag().name(), r.getOldLine(), r.getNewLine()))
        .toList();
  }

  private static List<String> prettyLines(String json) {
    try {
      String pretty = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(json));
      return Arrays.asList(pretty.split("\n", -1));
    } catch (Exception e) {
      return Arrays.asList(json.split("\n", -1));
    }
  }

  private ProcedureVersion currentVersion(UUID procedureId) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    UUID versionId = p.currentVersionId();
    if (versionId == null) {
      throw new IllegalStateException("procedure " + procedureId + " has no current version");
    }
    return versions.findById(versionId).orElseThrow();
  }

  /** Returns the procedure's current-version steps in order, or empty if it has no version yet. */
  public List<Step> listSteps(UUID procedureId) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    UUID versionId = p.currentVersionId();
    if (versionId == null) {
      return List.of();
    }
    return steps.findByProcedureVersionIdOrderByOrderIndexAsc(versionId);
  }

  /** Appends a step to the procedure's current version and returns it. */
  public Step addStep(
      UUID procedureId,
      String title,
      String instruction,
      @Nullable String expectedOutcome,
      @Nullable String warning,
      String evidenceKind,
      @Nullable String unit,
      @Nullable BigDecimal lowerBound,
      @Nullable BigDecimal upperBound) {
    ProcedureVersion version = currentVersion(procedureId);
    int nextOrder =
        steps.findByProcedureVersionIdOrderByOrderIndexAsc(version.id()).size() + 1;
    Step step =
        new Step(
            UUID.randomUUID(),
            TenantContext.current().value(),
            version.id(),
            nextOrder,
            title,
            instruction);
    step.setExpectedOutcome(expectedOutcome);
    step.setWarning(warning);
    step.setEvidenceSpec(evidenceSpec(evidenceKind, unit, lowerBound, upperBound));
    return steps.save(step);
  }

  /** Deletes the step with the given id. */
  public void deleteStep(UUID stepId) {
    steps.deleteById(stepId);
  }

  /**
   * Moves a step one position up or down by swapping its order index with the adjacent step. A
   * no-op at the ends of the list.
   */
  public void moveStep(UUID stepId, boolean up) {
    Step step = steps.findById(stepId).orElseThrow();
    List<Step> ordered = steps.findByProcedureVersionIdOrderByOrderIndexAsc(versionIdOf(step));
    int idx = -1;
    for (int i = 0; i < ordered.size(); i++) {
      if (ordered.get(i).id().equals(stepId)) {
        idx = i;
        break;
      }
    }
    int swapWith = up ? idx - 1 : idx + 1;
    if (idx < 0 || swapWith < 0 || swapWith >= ordered.size()) {
      return;
    }
    Step other = ordered.get(swapWith);
    int tmp = step.order();
    step.setOrder(other.order());
    other.setOrder(tmp);
    steps.save(step);
    steps.save(other);
  }

  private static UUID versionIdOf(Step step) {
    return step.procedureVersionId();
  }

  private static String evidenceSpec(
      String kind, @Nullable String unit, @Nullable BigDecimal lower, @Nullable BigDecimal upper) {
    StringBuilder spec = new StringBuilder("{\"kind\":\"").append(kind).append('"');
    if (unit != null && !unit.isBlank()) {
      spec.append(",\"unit\":\"").append(unit).append('"');
    }
    if (lower != null) {
      spec.append(",\"lowerBound\":").append(lower);
    }
    if (upper != null) {
      spec.append(",\"upperBound\":").append(upper);
    }
    return spec.append('}').toString();
  }
}
