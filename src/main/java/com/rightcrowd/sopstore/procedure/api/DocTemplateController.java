package com.rightcrowd.sopstore.procedure.api;

import com.rightcrowd.sopstore.procedure.DocTemplate;
import com.rightcrowd.sopstore.procedure.internal.DocTemplateRepository;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin CRUD for the tenant's Word-export templates (themes). Reads are open to any authenticated
 * user (authors pick a template per procedure); writes require {@code TENANT_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/export-templates")
@Tag(name = "Export templates")
public class DocTemplateController {

  private final DocTemplateRepository repo;

  /** Creates the controller with its repository. */
  public DocTemplateController(DocTemplateRepository repo) {
    this.repo = repo;
  }

  /** API representation of a template (logo bytes are fetched separately). */
  public record Dto(
      UUID id,
      String name,
      String accentColor,
      @Nullable String footerText,
      double bodyFontPt,
      double headingFontPt,
      double tableFontPt,
      boolean hasLogo) {
    static Dto from(DocTemplate t) {
      return new Dto(
          t.id(),
          t.name(),
          t.accentColor(),
          t.footerText(),
          t.bodyFontPt(),
          t.headingFontPt(),
          t.tableFontPt(),
          t.hasLogo());
    }
  }

  /** Request body for creating or editing a template. */
  public record SaveRequest(
      String name,
      String accentColor,
      @Nullable String footerText,
      @Nullable Double bodyFontPt,
      @Nullable Double headingFontPt,
      @Nullable Double tableFontPt) {}

  /** Lists the tenant's export templates. */
  @GetMapping
  public List<Dto> list() {
    return repo.findAllByOrderByNameAsc().stream().map(Dto::from).toList();
  }

  /** Creates a template. Returns 409 if the name already exists for the tenant. */
  @PostMapping
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> create(@RequestBody SaveRequest req) {
    String name = req.name() == null ? "" : req.name().trim();
    if (name.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    DocTemplate t =
        new DocTemplate(
            UUID.randomUUID(),
            TenantContext.current().value(),
            name,
            req.accentColor() == null ? "" : req.accentColor(),
            req.footerText());
    applyFontSizes(t, req);
    try {
      return ResponseEntity.status(201).body(Dto.from(repo.save(t)));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).build();
    }
  }

  /** Updates a template's name, accent colour, and footer text. */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> update(@PathVariable UUID id, @RequestBody SaveRequest req) {
    DocTemplate t = repo.findById(id).orElse(null);
    if (t == null) {
      return ResponseEntity.notFound().build();
    }
    String name = req.name() == null ? "" : req.name().trim();
    if (name.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    t.setName(name);
    t.setAccentColor(req.accentColor() == null ? "" : req.accentColor());
    t.setFooterText(req.footerText());
    applyFontSizes(t, req);
    try {
      return ResponseEntity.ok(Dto.from(repo.save(t)));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).build();
    }
  }

  /** Applies any supplied font sizes (body/heading/table); the entity clamps each to its range. */
  private static void applyFontSizes(DocTemplate t, SaveRequest req) {
    if (req.bodyFontPt() != null) {
      t.setBodyFontPt(req.bodyFontPt());
    }
    if (req.headingFontPt() != null) {
      t.setHeadingFontPt(req.headingFontPt());
    }
    if (req.tableFontPt() != null) {
      t.setTableFontPt(req.tableFontPt());
    }
  }

  /** Deletes a template. Procedures referencing it fall back to the default export style. */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    repo.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  /** Uploads (replaces) the template's logo image. */
  @PostMapping("/{id}/logo")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> uploadLogo(
      @PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
    DocTemplate t = repo.findById(id).orElse(null);
    if (t == null) {
      return ResponseEntity.notFound().build();
    }
    String mime = file.getContentType();
    t.setLogo(file.getBytes(), mime == null || mime.isBlank() ? "image/png" : mime);
    return ResponseEntity.ok(Dto.from(repo.save(t)));
  }

  /** Serves the template's logo image for preview. */
  @GetMapping("/{id}/logo")
  public ResponseEntity<byte[]> logo(@PathVariable UUID id) {
    DocTemplate t = repo.findById(id).orElse(null);
    if (t == null || !t.hasLogo()) {
      return ResponseEntity.notFound().build();
    }
    String mime = t.logoMime();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(mime == null || mime.isBlank() ? "image/png" : mime))
        .body(t.logo());
  }
}
