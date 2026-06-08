package com.rightcrowd.sopstore.procedure.api;

import com.rightcrowd.sopstore.identity.AuthenticatedUser;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureVersion;
import com.rightcrowd.sopstore.procedure.Step;
import com.rightcrowd.sopstore.procedure.internal.ProcedureRepository;
import com.rightcrowd.sopstore.procedure.internal.ProcedureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller exposing procedure resources under the v1 API. */
@RestController
@RequestMapping(value = "/api/v1/procedures")
@Tag(name = "Procedures")
public class ProcedureApiController {

  private final ProcedureRepository repo;
  private final ProcedureService service;
  private final com.rightcrowd.sopstore.identity.UserDirectory users;

  /** Creates the controller with the given repository, service, and user directory. */
  public ProcedureApiController(
      ProcedureRepository repo,
      ProcedureService service,
      com.rightcrowd.sopstore.identity.UserDirectory users) {
    this.repo = repo;
    this.service = service;
    this.users = users;
  }

  /** API representation of a procedure. */
  public record ProcedureDto(UUID id, String documentNumber, String title, String state) {
    static ProcedureDto from(Procedure p) {
      return new ProcedureDto(p.id(), p.documentNumber(), p.title(), p.state());
    }
  }

  /** A data-dense row for the procedures list. */
  public record ProcedureRow(
      UUID id,
      String documentNumber,
      String title,
      String type,
      String owner,
      String state,
      @Nullable String effectiveDate,
      @Nullable String nextReviewDate) {}

  /** A step within a procedure version. */
  public record StepDto(
      UUID id,
      int order,
      String title,
      String instruction,
      @Nullable String expectedOutcome,
      @Nullable String warning,
      @Nullable String evidenceSpec) {}

  /** A version in the procedure's history. */
  public record VersionDto(
      UUID id, String label, String createdAt, @Nullable UUID changeRequestId) {}

  /** Full procedure detail with steps and version history. */
  public record DetailDto(
      UUID id,
      String documentNumber,
      String title,
      String state,
      @Nullable UUID currentVersionId,
      @Nullable UUID confidentialityLevelId,
      List<StepDto> steps,
      List<VersionDto> versions) {}

  /**
   * Payload for creating a new procedure. The document number is generated server-side from the
   * type; {@code type} is optional and defaults to {@code SOP}. {@code prerequisites} are optional
   * and seeded into the initial version body.
   */
  public record CreateRequest(
      String title, @Nullable String type, @Nullable List<PrerequisiteInput> prerequisites) {}

  /** A prerequisite line supplied at creation: a type and its text. */
  public record PrerequisiteInput(@Nullable String type, String text) {}

  /** The rich-text (TipTap/ProseMirror JSON) body of the current version. */
  public record BodyDto(String body) {}

  /** Lists procedures filtered by state (or {@code ALL}) with paging, as data-dense rows. */
  @GetMapping
  public List<ProcedureRow> list(
      @RequestParam(defaultValue = "EFFECTIVE") String state,
      @RequestParam(defaultValue = "0") int page) {
    PageRequest pageReq = PageRequest.of(page, 200);
    List<Procedure> rows =
        "ALL".equalsIgnoreCase(state)
            ? repo.findAll(pageReq).getContent()
            : repo.findByState(state, pageReq).getContent();
    return rows.stream()
        .map(
            p ->
                new ProcedureRow(
                    p.id(),
                    p.documentNumber(),
                    p.title(),
                    p.type().name(),
                    users.emailById(p.ownerId()).orElse("—"),
                    p.state(),
                    p.effectiveDate() == null ? null : p.effectiveDate().toString(),
                    p.nextReviewDate() == null ? null : p.nextReviewDate().toString()))
        .toList();
  }

  /** Returns a single procedure by its identifier. */
  @GetMapping("/{id}")
  public ResponseEntity<ProcedureDto> get(@PathVariable UUID id) {
    return repo.findById(id)
        .map(p -> ResponseEntity.ok(ProcedureDto.from(p)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Returns full detail (metadata + steps + versions) for a procedure. */
  @GetMapping("/{id}/detail")
  public ResponseEntity<DetailDto> detail(@PathVariable UUID id) {
    return repo.findById(id)
        .map(
            p -> {
              List<StepDto> steps =
                  service.listSteps(id).stream().map(ProcedureApiController::toStep).toList();
              List<VersionDto> versions =
                  service.listVersions(id).stream()
                      .map(ProcedureApiController::toVersion)
                      .toList();
              return ResponseEntity.ok(
                  new DetailDto(
                      p.id(),
                      p.documentNumber(),
                      p.title(),
                      p.state(),
                      p.currentVersionId(),
                      p.confidentialityLevelId(),
                      steps,
                      versions));
            })
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Creates a new procedure owned by the signed-in user; its document number is auto-generated. */
  @PostMapping
  public ResponseEntity<ProcedureDto> create(
      @RequestBody CreateRequest req, @AuthenticationPrincipal AuthenticatedUser user) {
    Procedure.DocumentType type;
    try {
      type =
          req.type() == null || req.type().isBlank()
              ? Procedure.DocumentType.SOP
              : Procedure.DocumentType.valueOf(req.type());
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
    List<ProcedureService.NewPrerequisite> prerequisites =
        req.prerequisites() == null
            ? List.of()
            : req.prerequisites().stream()
                .map(
                    pr ->
                        new ProcedureService.NewPrerequisite(
                            pr.type() == null ? "" : pr.type(), pr.text()))
                .toList();
    Procedure p = service.create(req.title(), type, user.user().id(), prerequisites);
    return ResponseEntity.created(java.net.URI.create("/api/v1/procedures/" + p.id()))
        .body(ProcedureDto.from(p));
  }

  /** An admin-supplied override of a procedure's document number. */
  public record DocumentNumberDto(String documentNumber) {}

  /**
   * Overrides the procedure's document number. Restricted to tenant administrators; the number is
   * validated and must be unique within the tenant.
   */
  @PutMapping("/{id}/document-number")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<ProcedureDto> setDocumentNumber(
      @PathVariable UUID id, @RequestBody DocumentNumberDto req) {
    try {
      Procedure p = service.renameDocumentNumber(id, req.documentNumber());
      return ResponseEntity.ok(ProcedureDto.from(p));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  /** A confidentiality classification for a document (null clears it). */
  public record ConfidentialityDto(@Nullable UUID levelId) {}

  /** Classifies the procedure with a confidentiality level (null clears it). */
  @PutMapping("/{id}/confidentiality")
  public ResponseEntity<Void> setConfidentiality(
      @PathVariable UUID id, @RequestBody ConfidentialityDto req) {
    try {
      service.setConfidentiality(id, req.levelId());
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  /** Returns the procedure's current-version rich-text body. */
  @GetMapping("/{id}/body")
  public BodyDto body(@PathVariable UUID id) {
    return new BodyDto(service.bodyOrEmpty(id));
  }

  /** Saves the rich-text body onto the procedure's current version. */
  @PutMapping("/{id}/body")
  public ResponseEntity<Void> saveBody(@PathVariable UUID id, @RequestBody BodyDto req) {
    service.saveCurrentVersionBody(id, req.body());
    return ResponseEntity.noContent().build();
  }

  /** A free-form version label for the current version. */
  public record VersionLabelDto(String label) {}

  /** Sets the author-editable version label on the procedure's current version. */
  @PutMapping("/{id}/version")
  public ResponseEntity<Void> setVersion(@PathVariable UUID id, @RequestBody VersionLabelDto req) {
    service.setCurrentVersionLabel(id, req.label());
    return ResponseEntity.noContent().build();
  }

  /** Exports the procedure (cover + body + steps) as a themed PDF download. */
  @GetMapping("/{id}/export.pdf")
  public ResponseEntity<byte[]> exportPdf(@PathVariable UUID id) {
    Procedure p = repo.findById(id).orElseThrow();
    byte[] bytes = service.exportPdf(id);
    String safe = p.documentNumber().replaceAll("[^A-Za-z0-9._-]", "_");
    String disposition =
        ContentDisposition.attachment()
            .filename(safe + ".pdf", StandardCharsets.UTF_8)
            .build()
            .toString();
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
        .body(bytes);
  }

  private static StepDto toStep(Step s) {
    return new StepDto(
        s.id(), s.order(), s.title(), s.instruction(), s.expectedOutcome(), s.warning(),
        s.evidenceSpec());
  }

  private static VersionDto toVersion(ProcedureVersion v) {
    return new VersionDto(
        v.id(), v.versionLabel(), v.createdAt().toString(), v.changeRequestId());
  }
}
