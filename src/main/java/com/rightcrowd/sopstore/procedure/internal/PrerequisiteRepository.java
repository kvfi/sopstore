package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.Prerequisite;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the tenant's reusable prerequisite library. */
public interface PrerequisiteRepository extends JpaRepository<Prerequisite, UUID> {

  /** Returns the tenant's library prerequisites ordered by type then text. */
  List<Prerequisite> findAllByOrderByTypeAscTextAsc();
}
