package com.rightcrowd.sopstore.lifecycle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** A request to change a procedure within its lifecycle. */
@Entity
@Table(
    name = "change_request",
    indexes = @Index(name = "idx_cr_procedure", columnList = "procedure_id,status"))
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class ChangeRequest {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "procedure_id", nullable = false)
  private UUID procedureId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "reason", nullable = false, columnDefinition = "text")
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "classification", nullable = false)
  private Classification classification;

  @Column(name = "training_impact", nullable = false)
  private boolean trainingImpact = false;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.OPEN;

  @Column(name = "requested_by", nullable = false)
  private UUID requestedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "closed_at")
  private @Nullable Instant closedAt;

  /** Creates an empty change request for JPA use. */
  protected ChangeRequest() {}

  /** Creates a change request with the given details. */
  public ChangeRequest(
      UUID id,
      UUID tenantId,
      UUID procedureId,
      String title,
      String reason,
      Classification classification,
      UUID requestedBy) {
    this.id = id;
    this.tenantId = tenantId;
    this.procedureId = procedureId;
    this.title = title;
    this.reason = reason;
    this.classification = classification;
    this.requestedBy = requestedBy;
  }

  /** Returns the change request identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the identifier of the procedure this request targets. */
  public UUID procedureId() {
    return procedureId;
  }

  /** Returns the change request title. */
  public String title() {
    return title;
  }

  /** Returns the stated reason for the change. */
  public String reason() {
    return reason;
  }

  /** Returns the identifier of the user who requested the change. */
  public UUID requestedBy() {
    return requestedBy;
  }

  /** Returns the time the change request was created. */
  public Instant createdAt() {
    return createdAt;
  }

  /** Returns the current status of the change request. */
  public Status status() {
    return status;
  }

  /** Returns the classification of the change request. */
  public Classification classification() {
    return classification;
  }

  /** Returns whether this change request has a training impact. */
  public boolean trainingImpact() {
    return trainingImpact;
  }

  /** Marks this change request as having a training impact. */
  public void markTrainingImpact() {
    this.trainingImpact = true;
  }

  /** Marks the change request as having an approval workflow in progress. */
  public void markInProgress() {
    this.status = Status.IN_PROGRESS;
  }

  /** Marks the change request approved by its workflow and records the time of closure. */
  public void markApproved() {
    this.status = Status.APPROVED;
    this.closedAt = Instant.now();
  }

  /** Marks the change request rejected by its workflow and records the time of closure. */
  public void markRejected() {
    this.status = Status.REJECTED;
    this.closedAt = Instant.now();
  }

  /** Closes the change request and records the time of closure. */
  public void close() {
    this.status = Status.CLOSED;
    this.closedAt = Instant.now();
  }

  /** The classification of a change request. */
  public enum Classification {
    ADMINISTRATIVE,
    MINOR,
    MAJOR
  }

  /** The lifecycle status of a change request. */
  public enum Status {
    OPEN,
    IN_PROGRESS,
    APPROVED,
    REJECTED,
    CLOSED
  }
}
