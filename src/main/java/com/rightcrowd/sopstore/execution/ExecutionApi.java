package com.rightcrowd.sopstore.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sibling-facing read API over execution data, used by reporting/compliance dashboards. Keeps the
 * execution module's persistence encapsulated behind DTOs.
 */
public interface ExecutionApi {

  /** Returns the total number of deviations recorded under the active tenant. */
  long deviationCount();

  /** Returns the number of open deviations (no corrective action recorded). */
  long openDeviationCount();

  /** Returns the most recently logged deviations for a dashboard feed. */
  List<DeviationSummary> recentDeviations();

  /** Compact, boundary-safe view of a deviation. */
  record DeviationSummary(
      UUID id, UUID runId, String category, String description, Instant loggedAt, boolean open) {}
}
