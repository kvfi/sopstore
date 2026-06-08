package com.rightcrowd.sopstore.training.internal;

import com.rightcrowd.sopstore.training.TrainingAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for accessing and managing training assignments. */
public interface TrainingAssignmentRepository extends JpaRepository<TrainingAssignment, UUID> {
  /** Returns the training assignments for the given user. */
  List<TrainingAssignment> findByUserId(UUID userId);

  /** Returns the training assignments for the given procedure in the given state. */
  List<TrainingAssignment> findByProcedureIdAndState(
      UUID procedureId, TrainingAssignment.State state);
}
