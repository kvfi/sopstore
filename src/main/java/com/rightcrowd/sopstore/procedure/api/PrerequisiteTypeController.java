package com.rightcrowd.sopstore.procedure.api;

import com.rightcrowd.sopstore.procedure.PrerequisiteType;
import com.rightcrowd.sopstore.procedure.internal.PrerequisiteTypeRepository;
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
 * Admin CRUD for the tenant's prerequisite-type catalogue. Reads are open to any authenticated user
 * (authors need them to populate the prerequisite dropdown); writes require {@code TENANT_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/prerequisite-types")
@Tag(name = "Prerequisite types")
public class PrerequisiteTypeController {

  private final PrerequisiteTypeRepository repo;

  /** Creates the controller with its repository. */
  public PrerequisiteTypeController(PrerequisiteTypeRepository repo) {
    this.repo = repo;
  }

  /** API representation of a prerequisite type. */
  public record Dto(UUID id, String name) {
    static Dto from(PrerequisiteType t) {
      return new Dto(t.id(), t.name());
    }
  }

  /** Request body carrying a (new) name. */
  public record NameRequest(String name) {}

  /** Lists the tenant's prerequisite types, alphabetically. */
  @GetMapping
  public List<Dto> list() {
    return repo.findAllByOrderByNameAsc().stream().map(Dto::from).toList();
  }

  /** Creates a new prerequisite type. Returns 409 if the name already exists. */
  @PostMapping
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> create(@RequestBody NameRequest req) {
    String name = req.name() == null ? "" : req.name().trim();
    if (name.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    try {
      PrerequisiteType saved =
          repo.save(new PrerequisiteType(UUID.randomUUID(), TenantContext.current().value(), name));
      return ResponseEntity.status(201).body(Dto.from(saved));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).build();
    }
  }

  /** Renames an existing prerequisite type. */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> rename(@PathVariable UUID id, @RequestBody NameRequest req) {
    String name = req.name() == null ? "" : req.name().trim();
    if (name.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    PrerequisiteType type = repo.findById(id).orElse(null);
    if (type == null) {
      return ResponseEntity.notFound().build();
    }
    type.setName(name);
    try {
      return ResponseEntity.ok(Dto.from(repo.save(type)));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).build();
    }
  }

  /** Deletes a prerequisite type. Existing procedures keep their snapshotted type name. */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    repo.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
