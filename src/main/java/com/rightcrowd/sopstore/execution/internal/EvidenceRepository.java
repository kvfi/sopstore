package com.rightcrowd.sopstore.execution.internal;

import com.rightcrowd.sopstore.execution.EvidenceItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link EvidenceItem} records captured during a run. */
public interface EvidenceRepository extends JpaRepository<EvidenceItem, UUID> {

  /** Returns all evidence captured for the given run. */
  List<EvidenceItem> findByRunId(UUID runId);
}
