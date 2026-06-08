package com.rightcrowd.sopstore.lifecycle.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for running workflow instances. */
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {}
