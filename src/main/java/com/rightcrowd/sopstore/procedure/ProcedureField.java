package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * A tenant-defined custom procedure-form element (a flat, ordered field — there is no section
 * nesting). The {@code type} (TEXT, RICHTEXT, NUMBER, DATE, SELECT, or CHECKBOX) drives which input
 * the editor renders and how the value is shown in the PDF. {@code options} is a newline-separated
 * list, used only by {@code SELECT}.
 */
@Entity
@Table(name = "procedure_field")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class ProcedureField {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "label", nullable = false)
  private String label;

  @Column(name = "type", nullable = false)
  private String type = "TEXT";

  @Column(name = "options", nullable = false)
  private String options = "";

  @Column(name = "required", nullable = false)
  private boolean required;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty instance for the persistence provider. */
  protected ProcedureField() {}

  /** Creates a flat custom-form field. */
  public ProcedureField(
      UUID id,
      UUID tenantId,
      String label,
      String type,
      String options,
      boolean required,
      int sortOrder) {
    this.id = id;
    this.tenantId = tenantId;
    this.label = label;
    this.type = normalizeType(type);
    this.options = options == null ? "" : options;
    this.required = required;
    this.sortOrder = sortOrder;
  }

  /** Returns the field id (also its stable key in the procedure body JSON). */
  public UUID id() {
    return id;
  }

  /** Returns the field label shown above the input and as the PDF heading. */
  public String label() {
    return label;
  }

  /** Returns the field type keyword. */
  public String type() {
    return type;
  }

  /** Returns the newline-separated option list (used by {@code SELECT} fields). */
  public String options() {
    return options;
  }

  /** Whether the field is marked required (advisory in the editor). */
  public boolean required() {
    return required;
  }

  /** Returns the ordering position within the section (ascending). */
  public int sortOrder() {
    return sortOrder;
  }

  /** Renames the field. */
  public void setLabel(String label) {
    this.label = label;
  }

  /** Sets the field type (normalised to a known keyword; defaults to {@code TEXT}). */
  public void setType(String type) {
    this.type = normalizeType(type);
  }

  /** Sets the newline-separated option list. */
  public void setOptions(String options) {
    this.options = options == null ? "" : options;
  }

  /** Sets whether the field is required. */
  public void setRequired(boolean required) {
    this.required = required;
  }

  /** Sets the ordering position. */
  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }

  /** Restricts the field type to a supported keyword, defaulting to {@code TEXT}. */
  public static String normalizeType(@Nullable String type) {
    if (type == null) {
      return "TEXT";
    }
    String t = type.trim().toUpperCase(Locale.ROOT);
    return switch (t) {
      case "TEXT", "RICHTEXT", "NUMBER", "DATE", "SELECT", "CHECKBOX" -> t;
      default -> "TEXT";
    };
  }
}
