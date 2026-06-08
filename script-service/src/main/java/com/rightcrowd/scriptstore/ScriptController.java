package com.rightcrowd.scriptstore;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-scoped REST API for the script repository. The tenant is supplied by the caller via
 * {@code X-Tenant-Id}; the optional {@code X-Author} records who saved a version.
 */
@RestController
@RequestMapping("/api/v1/scripts")
public class ScriptController {

  private final ScriptService service;

  public ScriptController(ScriptService service) {
    this.service = service;
  }

  /** A script row (metadata only). */
  public record ScriptDto(
      UUID id, String name, String language, String description, int currentVersion, Instant updatedAt) {
    static ScriptDto from(Script s) {
      return new ScriptDto(s.id(), s.name(), s.language(), s.description(), s.currentVersion(), s.updatedAt());
    }
  }

  /** A version row. */
  public record VersionDto(int versionNo, String note, String createdBy, Instant createdAt) {
    static VersionDto from(ScriptVersion v) {
      return new VersionDto(v.versionNo(), v.note(), v.createdBy(), v.createdAt());
    }
  }

  /** A version with its content. */
  public record VersionContentDto(int versionNo, String content, Instant createdAt) {}

  /** Create payload. */
  public record CreateRequest(
      @NotBlank String name, String language, String description, String content) {}

  /** Metadata-update payload. */
  public record MetaRequest(@NotBlank String name, String language, String description) {}

  /** Save-content payload (creates a new version). */
  public record ContentRequest(String content, String note) {}

  @GetMapping
  public List<ScriptDto> list(@RequestHeader("X-Tenant-Id") UUID tenant) {
    return service.list(tenant).stream().map(ScriptDto::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ScriptDto create(
      @RequestHeader("X-Tenant-Id") UUID tenant,
      @RequestHeader(value = "X-Author", required = false) String author,
      @RequestBody CreateRequest req) {
    return ScriptDto.from(
        service.create(
            tenant, req.name().trim(), req.language(), req.description(), nullToEmpty(req.content()), author));
  }

  @GetMapping("/{id}")
  public ScriptDto get(@RequestHeader("X-Tenant-Id") UUID tenant, @PathVariable UUID id) {
    return ScriptDto.from(service.get(tenant, id));
  }

  @PutMapping("/{id}")
  public ScriptDto updateMeta(
      @RequestHeader("X-Tenant-Id") UUID tenant, @PathVariable UUID id, @RequestBody MetaRequest req) {
    return ScriptDto.from(service.updateMeta(tenant, id, req.name().trim(), req.language(), req.description()));
  }

  @PutMapping("/{id}/content")
  public VersionDto saveContent(
      @RequestHeader("X-Tenant-Id") UUID tenant,
      @RequestHeader(value = "X-Author", required = false) String author,
      @PathVariable UUID id,
      @RequestBody ContentRequest req) {
    return VersionDto.from(service.saveContent(tenant, id, nullToEmpty(req.content()), req.note(), author));
  }

  @PostMapping("/{id}/restore/{versionNo}")
  public VersionDto restore(
      @RequestHeader("X-Tenant-Id") UUID tenant,
      @RequestHeader(value = "X-Author", required = false) String author,
      @PathVariable UUID id,
      @PathVariable int versionNo) {
    return VersionDto.from(service.restore(tenant, id, versionNo, author));
  }

  @GetMapping("/{id}/versions")
  public List<VersionDto> versions(@RequestHeader("X-Tenant-Id") UUID tenant, @PathVariable UUID id) {
    return service.versions(tenant, id).stream().map(VersionDto::from).toList();
  }

  @GetMapping("/{id}/versions/{versionNo}")
  public VersionContentDto version(
      @RequestHeader("X-Tenant-Id") UUID tenant, @PathVariable UUID id, @PathVariable int versionNo) {
    ScriptVersion v = service.version(tenant, id, versionNo);
    return new VersionContentDto(v.versionNo(), v.content(), v.createdAt());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@RequestHeader("X-Tenant-Id") UUID tenant, @PathVariable UUID id) {
    service.delete(tenant, id);
  }

  @ExceptionHandler(java.util.NoSuchElementException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public String notFound(java.util.NoSuchElementException e) {
    return e.getMessage();
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<String> conflict() {
    return ResponseEntity.status(HttpStatus.CONFLICT).body("a script with that name already exists");
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }
}
