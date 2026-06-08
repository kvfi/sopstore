package com.rightcrowd.sopstore.training.internal;

import com.rightcrowd.sopstore.training.api.TrainingApi;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class TrainingApiImpl implements TrainingApi {

  private final QualificationRepository qualifications;

  TrainingApiImpl(QualificationRepository qualifications) {
    this.qualifications = qualifications;
  }

  @Override
  public boolean isUserQualifiedOn(UUID userId, UUID procedureId, LocalDate onDate) {
    return qualifications
        .findByUserIdAndProcedureId(userId, procedureId)
        .map(q -> q.isValidOn(onDate))
        .orElse(false);
  }
}
