package com.rightcrowd.sopstore.lifecycle.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for approval-workflow templates. */
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {
  /** Finds the current tenant's workflow template with the given name. */
  Optional<Workflow> findByName(String name);
}
