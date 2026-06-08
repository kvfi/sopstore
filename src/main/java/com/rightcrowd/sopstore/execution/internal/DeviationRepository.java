package com.rightcrowd.sopstore.execution.internal;

import com.rightcrowd.sopstore.execution.Deviation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Deviation} records raised during a run. */
public interface DeviationRepository extends JpaRepository<Deviation, UUID> {

  /** Returns all deviations raised for the given run. */
  List<Deviation> findByRunId(UUID runId);

  /** Counts deviations with no corrective action recorded (still open). */
  long countByCorrectiveActionIsNull();

  /** Returns the most recently logged deviations. */
  List<Deviation> findTop20ByOrderByLoggedAtDesc();
}
