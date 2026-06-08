package com.rightcrowd.sopstore.procedure.api;

import com.rightcrowd.sopstore.procedure.Prerequisite;
import com.rightcrowd.sopstore.procedure.internal.PrerequisiteRepository;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin CRUD for the tenant's reusable prerequisite library. Reads are open to any authenticated
 * user (authors pick from the library when creating/authoring procedures); writes require {@code
 * TENANT_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/prerequisites")
@Tag(name = "Prerequisites")
public class PrerequisiteController {

  private final PrerequisiteRepository repo;

  /** Creates the controller with its repository. */
  public PrerequisiteController(PrerequisiteRepository repo) {
    this.repo = repo;
  }

  /** API representation of a library prerequisite. */
  public record Dto(UUID id, String type, String text) {
    static Dto from(Prerequisite p) {
      return new Dto(p.id(), p.type(), p.text());
    }
  }

  /** Request body for creating or editing a library prerequisite. */
  public record PrerequisiteRequest(String type, String text) {}

  /** Lists the tenant's library prerequisites, by type then text. */
  @GetMapping
  public List<Dto> list() {
    return repo.findAllByOrderByTypeAscTextAsc().stream().map(Dto::from).toList();
  }

  /** Creates a new library prerequisite. Returns 409 if an identical type+text exists. */
  @PostMapping
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> create(@RequestBody PrerequisiteRequest req) {
    String type = req.type() == null ? "" : req.type().trim();
    String text = req.text() == null ? "" : req.text().trim();
    if (text.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    try {
      Prerequisite saved =
          repo.save(
              new Prerequisite(UUID.randomUUID(), TenantContext.current().value(), type, text));
      return ResponseEntity.status(201).body(Dto.from(saved));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).build();
    }
  }

  /** Edits an existing library prerequisite's type and text. */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> update(@PathVariable UUID id, @RequestBody PrerequisiteRequest req) {
    String type = req.type() == null ? "" : req.type().trim();
    String text = req.text() == null ? "" : req.text().trim();
    if (text.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    Prerequisite existing = repo.findById(id).orElse(null);
    if (existing == null) {
      return ResponseEntity.notFound().build();
    }
    existing.update(type, text);
    try {
      return ResponseEntity.ok(Dto.from(repo.save(existing)));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).build();
    }
  }

  /** Deletes a library prerequisite. Procedures keep their snapshotted prerequisites. */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    repo.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
