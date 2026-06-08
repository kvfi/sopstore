package com.rightcrowd.sopstore.training;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** Records a user's acknowledged completion of a training assignment. */
@Entity
@Table(name = "training_completion")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class TrainingCompletion {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "assignment_id", nullable = false)
  private UUID assignmentId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** Hash of the procedure version body that was acknowledged. */
  @Column(name = "version_sha256", nullable = false, length = 64)
  private String versionSha256;

  /** Optional witness/trainer signing the competency demonstration. */
  @Column(name = "witnessed_by")
  private @Nullable UUID witnessedBy;

  @Column(name = "completed_at", nullable = false)
  private Instant completedAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected TrainingCompletion() {}

  /** Creates a training completion record with the given details. */
  public TrainingCompletion(
      UUID id,
      UUID tenantId,
      UUID assignmentId,
      UUID userId,
      String versionSha256,
      @Nullable UUID witnessedBy) {
    this.id = id;
    this.tenantId = tenantId;
    this.assignmentId = assignmentId;
    this.userId = userId;
    this.versionSha256 = versionSha256;
    this.witnessedBy = witnessedBy;
  }

  /** Returns the completion identifier. */
  public UUID id() {
    return id;
  }
}
