package com.rightcrowd.sopstore.procedure.api;

import com.rightcrowd.sopstore.procedure.ProcedureField;
import com.rightcrowd.sopstore.procedure.internal.ProcedureFieldRepository;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
 * Tenant-managed definition of the custom procedure-form: a flat, ordered list of fields (rendered
 * after the built-in Purpose/Scope/Prerequisites/Steps). Reads are open to any authenticated user
 * (authors fill the fields per procedure); writes require {@code TENANT_ADMIN}. Field values live
 * in each procedure's body JSON, so deleting a field never corrupts existing procedures.
 */
@RestController
@RequestMapping("/api/v1/procedure-form")
@Tag(name = "Procedure form")
public class ProcedureFormController {

  private final ProcedureFieldRepository fields;

  /** Creates the controller with its field repository. */
  public ProcedureFormController(ProcedureFieldRepository fields) {
    this.fields = fields;
  }

  /** The whole custom-form schema: an ordered list of fields. */
  public record FormDto(List<FieldDto> fields) {
    /** Defensively copies the list so the record is immutable. */
    public FormDto {
      fields = List.copyOf(fields);
    }

    @Override
    public List<FieldDto> fields() {
      return List.copyOf(fields);
    }
  }

  /** A field definition. {@code options} is newline-separated (used by {@code SELECT}). */
  public record FieldDto(
      UUID id, String label, String type, String options, boolean required, int sortOrder) {
    static FieldDto from(ProcedureField f) {
      return new FieldDto(f.id(), f.label(), f.type(), f.options(), f.required(), f.sortOrder());
    }
  }

  /** Create/update payload for a field. */
  public record FieldRequest(
      String label,
      @Nullable String type,
      @Nullable String options,
      @Nullable Boolean required,
      @Nullable Integer sortOrder) {}

  /** Returns the full custom-form schema for the current tenant. */
  @GetMapping
  public FormDto get() {
    return new FormDto(
        fields.findAllByOrderBySortOrderAsc().stream().map(FieldDto::from).toList());
  }

  /** Adds a custom field. */
  @PostMapping("/fields")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<FieldDto> createField(@RequestBody FieldRequest req) {
    String label = req.label() == null ? "" : req.label().trim();
    if (label.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    ProcedureField f =
        fields.save(
            new ProcedureField(
                UUID.randomUUID(),
                TenantContext.current().value(),
                label,
                ProcedureField.normalizeType(req.type()),
                req.options() == null ? "" : req.options(),
                req.required() != null && req.required(),
                req.sortOrder() == null ? nextFieldOrder() : req.sortOrder()));
    return ResponseEntity.status(201).body(FieldDto.from(f));
  }

  /** Updates a field's label, type, options, required flag, or order. */
  @PutMapping("/fields/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<FieldDto> updateField(
      @PathVariable UUID id, @RequestBody FieldRequest req) {
    ProcedureField f = fields.findById(id).orElse(null);
    if (f == null) {
      return ResponseEntity.notFound().build();
    }
    if (req.label() != null && !req.label().trim().isEmpty()) {
      f.setLabel(req.label().trim());
    }
    if (req.type() != null) {
      f.setType(req.type());
    }
    if (req.options() != null) {
      f.setOptions(req.options());
    }
    if (req.required() != null) {
      f.setRequired(req.required());
    }
    if (req.sortOrder() != null) {
      f.setSortOrder(req.sortOrder());
    }
    return ResponseEntity.ok(FieldDto.from(fields.save(f)));
  }

  /** Deletes a field. Existing procedures keep their saved value. */
  @DeleteMapping("/fields/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Void> deleteField(@PathVariable UUID id) {
    fields.deleteById(id);
    return ResponseEntity.noContent().build();
  }

  private int nextFieldOrder() {
    return fields.findAllByOrderBySortOrderAsc().stream()
            .mapToInt(ProcedureField::sortOrder)
            .max()
            .orElse(-1)
        + 1;
  }
}
