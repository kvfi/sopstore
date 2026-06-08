package com.rightcrowd.sopstore.execution.internal;

import com.rightcrowd.sopstore.execution.ProcedureRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for accessing and persisting procedure runs. */
public interface RunRepository extends JpaRepository<ProcedureRun, UUID> {
  /** Returns the procedure runs for the given operator in the given state. */
  List<ProcedureRun> findByOperatorIdAndState(UUID operatorId, ProcedureRun.State state);

  /** Returns the most recent runs across all procedures, newest first. */
  List<ProcedureRun> findTop200ByOrderByStartedAtDesc();
}
