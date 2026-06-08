package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.lifecycle.ChangeRequest.Status;
import com.rightcrowd.sopstore.lifecycle.LifecycleApi;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link LifecycleApi} backed by the change-request and workflow-task repositories. */
@Service
@Transactional(readOnly = true)
class LifecycleApiImpl implements LifecycleApi {

  private static final List<Status> OPEN_STATES = List.of(Status.OPEN, Status.IN_PROGRESS);

  private final ChangeRequestRepository changeRequests;
  private final WorkflowTaskRepository tasks;

  LifecycleApiImpl(ChangeRequestRepository changeRequests, WorkflowTaskRepository tasks) {
    this.changeRequests = changeRequests;
    this.tasks = tasks;
  }

  @Override
  public long openChangeRequestCount() {
    return changeRequests.countByStatusIn(OPEN_STATES);
  }

  @Override
  public long pendingApprovalCount() {
    return tasks.countByStatus(WorkflowTask.Status.PENDING);
  }

  @Override
  public List<ChangeRequestSummary> openChangeRequests() {
    return changeRequests.findByStatusInOrderByCreatedAtDesc(OPEN_STATES).stream()
        .map(
            cr ->
                new ChangeRequestSummary(
                    cr.id(),
                    cr.procedureId(),
                    cr.title(),
                    cr.status().name(),
                    cr.classification().name(),
                    cr.createdAt()))
        .toList();
  }

  @Override
  public List<ApprovalSummary> pendingApprovals() {
    return tasks.findByStatusOrderByCreatedAtAsc(WorkflowTask.Status.PENDING).stream()
        .map(
            t ->
                new ApprovalSummary(
                    t.id(),
                    t.procedureId(),
                    t.stageName(),
                    t.assigneeRole().name(),
                    t.dueAt()))
        .toList();
  }
}
