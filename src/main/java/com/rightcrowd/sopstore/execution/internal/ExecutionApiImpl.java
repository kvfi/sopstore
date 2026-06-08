package com.rightcrowd.sopstore.execution.internal;

import com.rightcrowd.sopstore.execution.ExecutionApi;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default {@link ExecutionApi} backed by the deviation repository. */
@Service
@Transactional(readOnly = true)
class ExecutionApiImpl implements ExecutionApi {

  private final DeviationRepository deviations;

  ExecutionApiImpl(DeviationRepository deviations) {
    this.deviations = deviations;
  }

  @Override
  public long deviationCount() {
    return deviations.count();
  }

  @Override
  public long openDeviationCount() {
    return deviations.countByCorrectiveActionIsNull();
  }

  @Override
  public List<DeviationSummary> recentDeviations() {
    return deviations.findTop20ByOrderByLoggedAtDesc().stream()
        .map(
            d ->
                new DeviationSummary(
                    d.id(),
                    d.runId(),
                    d.category().name(),
                    d.description(),
                    d.loggedAt(),
                    d.open()))
        .toList();
  }
}
