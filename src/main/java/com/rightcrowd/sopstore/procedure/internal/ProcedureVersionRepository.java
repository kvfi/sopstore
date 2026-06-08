package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.ProcedureVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for accessing and persisting procedure versions. */
public interface ProcedureVersionRepository extends JpaRepository<ProcedureVersion, UUID> {
  /** Returns the latest version of the given procedure ordered by major then minor version. */
  Optional<ProcedureVersion> findTopByProcedureIdOrderByVersionMajorDescVersionMinorDesc(
      UUID procedureId);

  /** Returns all versions of the given procedure, newest first. */
  List<ProcedureVersion> findByProcedureIdOrderByVersionMajorDescVersionMinorDesc(UUID procedureId);
}
