package com.rightcrowd.sopstore.training.internal;

import com.rightcrowd.sopstore.training.Qualification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Qualification} entities. */
public interface QualificationRepository extends JpaRepository<Qualification, UUID> {
  /** Returns all qualifications for the given procedure. */
  List<Qualification> findByProcedureId(UUID procedureId);

  /** Returns the qualification for the given user and procedure, if present. */
  Optional<Qualification> findByUserIdAndProcedureId(UUID userId, UUID procedureId);
}
