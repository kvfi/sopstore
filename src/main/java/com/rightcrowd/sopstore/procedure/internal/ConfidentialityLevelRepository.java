package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.ConfidentialityLevel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for the tenant's confidentiality-level catalogue. */
public interface ConfidentialityLevelRepository extends JpaRepository<ConfidentialityLevel, UUID> {

  /** Returns the tenant's confidentiality levels ordered by rank then name. */
  List<ConfidentialityLevel> findAllByOrderByRankAscNameAsc();
}
