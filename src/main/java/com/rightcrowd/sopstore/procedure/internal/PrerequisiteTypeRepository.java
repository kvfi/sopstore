package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.PrerequisiteType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the tenant's prerequisite-type catalogue. */
public interface PrerequisiteTypeRepository extends JpaRepository<PrerequisiteType, UUID> {

  /** Returns the tenant's prerequisite types ordered alphabetically. */
  List<PrerequisiteType> findAllByOrderByNameAsc();
}
