package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** A controlled document procedure aggregate carrying lifecycle state and metadata. */
@Entity
@Table(
    name = "procedure",
    indexes = {
      @Index(
          name = "idx_procedure_doc_no",
          columnList = "tenant_id,document_number",
          unique = true),
      @Index(name = "idx_procedure_status", columnList = "tenant_id,state")
    })
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class Procedure {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "document_number", nullable = false)
  private String documentNumber;

  @Column(name = "title", nullable = false)
  private String title;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private DocumentType type = DocumentType.SOP;

  @Column(name = "category_id")
  private @Nullable UUID categoryId;

  /** Optional confidentiality classification (a tenant-managed {@link ConfidentialityLevel}). */
  @Column(name = "confidentiality_level_id")
  private @Nullable UUID confidentialityLevelId;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  /** Logical pointer to the current version; nullable until the first version is created. */
  @Column(name = "current_version_id")
  private @Nullable UUID currentVersionId;

  /**
   * Lifecycle state lives in the procedure aggregate so the procedure table carries the canonical
   * answer. Detailed transition rules and signature binding are enforced by the lifecycle module.
   */
  @Column(name = "state", nullable = false)
  private String state = "DRAFT";

  @Column(name = "effective_date")
  private @Nullable LocalDate effectiveDate;

  @Column(name = "next_review_date")
  private @Nullable LocalDate nextReviewDate;

  @Column(name = "retired_at")
  private @Nullable Instant retiredAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected Procedure() {}

  /** Creates a procedure with the given identity and core metadata. */
  public Procedure(UUID id, UUID tenantId, String documentNumber, String title, UUID ownerId) {
    this.id = id;
    this.tenantId = tenantId;
    this.documentNumber = documentNumber;
    this.title = title;
    this.ownerId = ownerId;
  }

  /** Returns the unique identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the tenant identifier. */
  public UUID tenantId() {
    return tenantId;
  }

  /** Returns the document number. */
  public String documentNumber() {
    return documentNumber;
  }

  /** Returns the title. */
  public String title() {
    return title;
  }

  /** Returns the document type. */
  public DocumentType type() {
    return type;
  }

  /** Returns the category identifier, or null if uncategorized. */
  public @Nullable UUID categoryId() {
    return categoryId;
  }

  /** Returns the confidentiality level identifier, or null if unclassified. */
  public @Nullable UUID confidentialityLevelId() {
    return confidentialityLevelId;
  }

  /** Returns the owner identifier. */
  public UUID ownerId() {
    return ownerId;
  }

  /** Returns the lifecycle state. */
  public String state() {
    return state;
  }

  /** Returns the current version identifier, or null if no version exists yet. */
  public @Nullable UUID currentVersionId() {
    return currentVersionId;
  }

  /** Returns the effective date, or null if not yet effective. */
  public @Nullable LocalDate effectiveDate() {
    return effectiveDate;
  }

  /** Returns the next review date, or null if none is set. */
  public @Nullable LocalDate nextReviewDate() {
    return nextReviewDate;
  }

  /** Sets the title. */
  public void setTitle(String t) {
    this.title = t;
  }

  /**
   * Overrides the document number. Generated automatically at creation; this manual override is an
   * admin-only operation, enforced at the API layer ({@code hasRole('TENANT_ADMIN')}).
   */
  public void setDocumentNumber(String documentNumber) {
    this.documentNumber = documentNumber;
  }

  /** Sets the category identifier. */
  public void setCategory(@Nullable UUID id) {
    this.categoryId = id;
  }

  /** Sets the confidentiality level identifier (null clears the classification). */
  public void setConfidentialityLevel(@Nullable UUID id) {
    this.confidentialityLevelId = id;
  }

  /** Sets the document type. Set once at creation; it drives the document number prefix. */
  public void setType(DocumentType t) {
    this.type = t;
  }

  /** Sets the current version identifier. */
  public void setCurrentVersion(UUID v) {
    this.currentVersionId = v;
  }

  /** Sets the lifecycle state. */
  public void setState(String s) {
    this.state = s;
  }

  /** Marks the procedure effective with the given dates. */
  public void markEffective(LocalDate d, LocalDate nextReview) {
    this.state = "EFFECTIVE";
    this.effectiveDate = d;
    this.nextReviewDate = nextReview;
  }

  /** Transitions the procedure to the retired state. */
  public void retire() {
    this.state = "RETIRED";
    this.retiredAt = Instant.now();
  }

  /** The kind of controlled document a procedure represents. */
  public enum DocumentType {
    POLICY("POL"),
    SOP("SOP"),
    WORK_INSTRUCTION("WI"),
    FORM("FRM"),
    JOB_AID("JOB");

    private final String prefix;

    DocumentType(String prefix) {
      this.prefix = prefix;
    }

    /** Returns the short prefix used in generated document numbers, e.g. {@code SOP}. */
    public String prefix() {
      return prefix;
    }
  }
}
