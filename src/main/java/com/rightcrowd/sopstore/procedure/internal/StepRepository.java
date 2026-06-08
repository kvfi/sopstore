package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.Step;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Step} entities. */
public interface StepRepository extends JpaRepository<Step, UUID> {

  /** Returns the steps of a procedure version in execution order. */
  List<Step> findByProcedureVersionIdOrderByOrderIndexAsc(UUID procedureVersionId);
}
