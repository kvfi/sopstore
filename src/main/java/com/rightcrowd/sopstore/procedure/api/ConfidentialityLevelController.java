package com.rightcrowd.sopstore.procedure.api;

import com.rightcrowd.sopstore.procedure.ConfidentialityLevel;
import com.rightcrowd.sopstore.procedure.internal.ConfidentialityLevelRepository;
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
 * Admin CRUD for the tenant's confidentiality-level catalogue. Reads are open to any authenticated
 * user (authors classify documents); writes require {@code TENANT_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/confidentiality-levels")
@Tag(name = "Confidentiality levels")
public class ConfidentialityLevelController {

  private final ConfidentialityLevelRepository repo;

  /** Creates the controller with its repository. */
  public ConfidentialityLevelController(ConfidentialityLevelRepository repo) {
    this.repo = repo;
  }

  /** API representation of a confidentiality level. */
  public record Dto(UUID id, String name, int rank) {
    static Dto from(ConfidentialityLevel l) {
      return new Dto(l.id(), l.name(), l.rank());
    }
  }

  /** Request body for creating or editing a level. */
  public record SaveRequest(String name, Integer rank) {}

  /** Lists the tenant's confidentiality levels, least sensitive first. */
  @GetMapping
  public List<Dto> list() {
    return repo.findAllByOrderByRankAscNameAsc().stream().map(Dto::from).toList();
  }

  /** Creates a new level. Returns 409 if the name already exists. */
  @PostMapping
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> create(@RequestBody SaveRequest req) {
    String name = req.name() == null ? "" : req.name().trim();
    if (name.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    int rank = req.rank() == null ? 0 : req.rank();
    try {
      ConfidentialityLevel saved =
          repo.save(
              new ConfidentialityLevel(
                  UUID.randomUUID(), TenantContext.current().value(), name, rank));
      return ResponseEntity.status(201).body(Dto.from(saved));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).build();
    }
  }

  /** Renames or re-ranks an existing level. */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> update(@PathVariable UUID id, @RequestBody SaveRequest req) {
    String name = req.name() == null ? "" : req.name().trim();
    if (name.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    ConfidentialityLevel level = repo.findById(id).orElse(null);
    if (level == null) {
      return ResponseEntity.notFound().build();
    }
    level.setName(name);
    if (req.rank() != null) {
      level.setRank(req.rank());
    }
    try {
      return ResponseEntity.ok(Dto.from(repo.save(level)));
    } catch (DataIntegrityViolationException e) {
      return ResponseEntity.status(409).build();
    }
  }

  /** Deletes a level. Procedures classified with it fall back to unclassified. */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    repo.deleteById(id);
    return ResponseEntity.noContent().build();
  }
}
