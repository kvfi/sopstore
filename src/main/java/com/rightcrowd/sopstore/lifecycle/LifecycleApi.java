package com.rightcrowd.sopstore.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Sibling-facing read API over lifecycle data (change requests + approval tasks), used by
 * reporting/compliance dashboards. Returns DTOs so the lifecycle module's persistence stays
 * encapsulated.
 */
public interface LifecycleApi {

  /** Returns the number of change requests still open or in progress. */
  long openChangeRequestCount();

  /** Returns the number of approval tasks awaiting a decision. */
  long pendingApprovalCount();

  /** Returns open / in-progress change requests, newest first. */
  List<ChangeRequestSummary> openChangeRequests();

  /** Returns pending approval tasks, oldest first. */
  List<ApprovalSummary> pendingApprovals();

  /** Compact, boundary-safe view of a change request. */
  record ChangeRequestSummary(
      UUID id,
      UUID procedureId,
      String title,
      String status,
      String classification,
      Instant createdAt) {}

  /** Compact, boundary-safe view of a pending approval task. */
  record ApprovalSummary(
      UUID taskId, UUID procedureId, String stageName, String role, @Nullable Instant dueAt) {}
}
