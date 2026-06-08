package com.rightcrowd.sopstore.training.internal;

import com.rightcrowd.sopstore.tenancy.TenantContext;
import com.rightcrowd.sopstore.training.Qualification;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for training &amp; competency: granting and listing qualifications. */
@Service
@Transactional
public class TrainingService {

  private final QualificationRepository qualifications;

  /** Creates the service backed by the qualification repository. */
  public TrainingService(QualificationRepository qualifications) {
    this.qualifications = qualifications;
  }

  /** Records that a user is qualified on a procedure as of today (idempotent). */
  @PreAuthorize(
      "hasAnyRole('TRAINER', 'QUALITY_MANAGER', 'TENANT_ADMIN', 'SUPER_ADMIN')")
  public void qualify(UUID userId, UUID procedureId, @Nullable UUID trainerId) {
    if (qualifications.findByUserIdAndProcedureId(userId, procedureId).isPresent()) {
      return;
    }
    qualifications.save(
        new Qualification(
            UUID.randomUUID(),
            TenantContext.current().value(),
            userId,
            procedureId,
            LocalDate.now(ZoneOffset.UTC),
            null,
            trainerId));
  }

  /** Returns the qualifications recorded for a procedure. */
  @Transactional(readOnly = true)
  public List<Qualification> forProcedure(UUID procedureId) {
    return qualifications.findByProcedureId(procedureId);
  }
}
