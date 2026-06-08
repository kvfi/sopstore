package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.identity.Role;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for individual workflow approval tasks. */
public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {

  /** Returns all tasks for a workflow instance at the given stage. */
  List<WorkflowTask> findByInstanceIdAndStageIndex(UUID instanceId, int stageIndex);

  /** Returns the pending approval queue for a set of roles, oldest first. */
  List<WorkflowTask> findByStatusAndAssigneeRoleInOrderByCreatedAtAsc(
      WorkflowTask.Status status, Collection<Role> roles);

  /** Counts tasks in the given status. */
  long countByStatus(WorkflowTask.Status status);

  /** Returns tasks in the given status, oldest first. */
  List<WorkflowTask> findByStatusOrderByCreatedAtAsc(WorkflowTask.Status status);

  /** Returns all tasks for a procedure, newest first, for the procedure detail view. */
  List<WorkflowTask> findByProcedureIdOrderByCreatedAtDesc(UUID procedureId);

  /** Returns pending tasks whose due time has passed and that have not yet been escalated. */
  List<WorkflowTask> findByStatusAndEscalatedFalseAndDueAtBefore(
      WorkflowTask.Status status, Instant cutoff);
}
