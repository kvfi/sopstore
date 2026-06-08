package com.rightcrowd.sopstore.training.api;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** Sibling-facing API: used by execution to gate runs on qualification. */
@NamedInterface("training-api")
public interface TrainingApi {
  /** Returns whether the user is qualified on the procedure as of the given date. */
  boolean isUserQualifiedOn(UUID userId, UUID procedureId, LocalDate onDate);
}
