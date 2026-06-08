package com.rightcrowd.sopstore.execution.internal;

import com.rightcrowd.sopstore.execution.Deviation;
import com.rightcrowd.sopstore.execution.EvidenceItem;
import com.rightcrowd.sopstore.execution.ProcedureRun;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureApi;
import com.rightcrowd.sopstore.procedure.Step;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import com.rightcrowd.sopstore.training.api.TrainingApi;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for starting, performing, and completing procedure runs. */
@Service
@Transactional
public class RunService {

  private final ProcedureApi procedures;
  private final RunRepository runs;
  private final EvidenceRepository evidence;
  private final DeviationRepository deviations;
  private final TrainingApi training;

  /** Creates a run service with its repositories and the training gate. */
  public RunService(
      ProcedureApi procedures,
      RunRepository runs,
      EvidenceRepository evidence,
      DeviationRepository deviations,
      TrainingApi training) {
    this.procedures = procedures;
    this.runs = runs;
    this.evidence = evidence;
    this.deviations = deviations;
    this.training = training;
  }

  /**
   * Start a run. Enforces the spec acceptance criterion: a trainee who is not qualified on the
   * procedure cannot start a run on it.
   */
  public ProcedureRun start(UUID procedureId, UUID operatorId) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    boolean qualified =
        training.isUserQualifiedOn(operatorId, procedureId, LocalDate.now(ZoneOffset.UTC));
    if (!qualified) {
      throw new SecurityException(
          "Operator " + operatorId + " is not qualified on procedure " + procedureId);
    }
    UUID versionId = p.currentVersionId();
    if (versionId == null) {
      throw new IllegalStateException("Procedure has no current version");
    }
    ProcedureRun run =
        new ProcedureRun(
            UUID.randomUUID(), TenantContext.current().value(), procedureId, versionId, operatorId);
    runs.save(run);
    return run;
  }

  /** Returns the run with the given id. */
  @Transactional(readOnly = true)
  public ProcedureRun get(UUID runId) {
    return runs.findById(runId).orElseThrow();
  }

  /** Returns the steps to perform for a procedure (its current version), in order. */
  @Transactional(readOnly = true)
  public List<Step> steps(UUID procedureId) {
    return procedures.currentVersionSteps(procedureId);
  }

  /** Returns the evidence captured for the run. */
  @Transactional(readOnly = true)
  public List<EvidenceItem> evidenceFor(UUID runId) {
    return evidence.findByRunId(runId);
  }

  /** Returns the deviations raised for the run. */
  @Transactional(readOnly = true)
  public List<Deviation> deviationsFor(UUID runId) {
    return deviations.findByRunId(runId);
  }

  /** Captures a free-text evidence note for a step. */
  public void recordNote(UUID runId, UUID stepId, String text) {
    evidence.save(EvidenceItem.text(TenantContext.current().value(), runId, stepId, text));
  }

  /**
   * Captures a numeric measurement for a step and raises an out-of-tolerance {@link Deviation} when
   * the value falls outside the step's {@code lowerBound}/{@code upperBound} evidence spec.
   */
  public void recordMeasurement(UUID runId, UUID stepId, BigDecimal value, String unit) {
    UUID tenant = TenantContext.current().value();
    Step step = procedures.step(stepId).orElseThrow();
    evidence.save(EvidenceItem.measurement(tenant, runId, stepId, value, unit));
    outOfTolerance(step, value)
        .ifPresent(
            reason ->
                deviations.save(
                    new Deviation(
                        UUID.randomUUID(),
                        tenant,
                        runId,
                        stepId,
                        Deviation.Category.OUT_OF_TOLERANCE,
                        reason)));
  }

  /** Returns the most recent runs with computed duration and deviation counts, newest first. */
  @Transactional(readOnly = true)
  public List<RunRow> history() {
    List<RunRow> rows = new ArrayList<>();
    for (ProcedureRun r : runs.findTop200ByOrderByStartedAtDesc()) {
      String title =
          procedures.findById(r.procedureId()).map(Procedure::title).orElse("(unknown)");
      Long durationSeconds =
          r.completedAt() == null
              ? null
              : Duration.between(r.startedAt(), r.completedAt()).toSeconds();
      int devs = deviations.findByRunId(r.id()).size();
      rows.add(
          new RunRow(
              r.id(),
              r.procedureId(),
              title,
              r.state().name(),
              r.startedAt().toString(),
              durationSeconds,
              devs));
    }
    return rows;
  }

  /** Returns per-procedure run analytics (counts, completion rate, average duration). */
  @Transactional(readOnly = true)
  public List<ProcedureStats> analytics() {
    Map<UUID, List<ProcedureRun>> byProcedure = new LinkedHashMap<>();
    for (ProcedureRun r : runs.findTop200ByOrderByStartedAtDesc()) {
      byProcedure.computeIfAbsent(r.procedureId(), k -> new ArrayList<>()).add(r);
    }
    List<ProcedureStats> stats = new ArrayList<>();
    byProcedure.forEach(
        (procedureId, list) -> {
          String title =
              procedures.findById(procedureId).map(Procedure::title).orElse("(unknown)");
          long completed =
              list.stream().filter(r -> r.state() == ProcedureRun.State.COMPLETED).count();
          long totalDeviations =
              list.stream().mapToLong(r -> deviations.findByRunId(r.id()).size()).sum();
          List<Long> durations =
              list.stream()
                  .filter(r -> r.completedAt() != null)
                  .map(r -> Duration.between(r.startedAt(), r.completedAt()).toSeconds())
                  .toList();
          Long avgDuration =
              durations.isEmpty()
                  ? null
                  : durations.stream().mapToLong(Long::longValue).sum() / durations.size();
          int completionPct = list.isEmpty() ? 0 : (int) (completed * 100 / list.size());
          stats.add(
              new ProcedureStats(
                  procedureId, title, list.size(), completionPct, avgDuration, totalDeviations));
        });
    return stats;
  }

  /** Marks the run complete. */
  public ProcedureRun complete(UUID runId) {
    ProcedureRun run = runs.findById(runId).orElseThrow();
    run.complete();
    return runs.save(run);
  }

  private static Optional<String> outOfTolerance(Step step, BigDecimal value) {
    String spec = step.evidenceSpec();
    if (spec == null || spec.isBlank()) {
      return Optional.empty();
    }
    Optional<BigDecimal> lower = numberField(spec, "lowerBound");
    Optional<BigDecimal> upper = numberField(spec, "upperBound");
    if (lower.isPresent() && value.compareTo(lower.get()) < 0) {
      return Optional.of(
          "Measured " + value + " below lower tolerance " + lower.get() + " on \"" + step.title()
              + "\"");
    }
    if (upper.isPresent() && value.compareTo(upper.get()) > 0) {
      return Optional.of(
          "Measured " + value + " above upper tolerance " + upper.get() + " on \"" + step.title()
              + "\"");
    }
    return Optional.empty();
  }

  private static Optional<BigDecimal> numberField(String json, String field) {
    Matcher matcher =
        Pattern.compile("\"" + field + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
    return matcher.find() ? Optional.of(new BigDecimal(matcher.group(1))) : Optional.empty();
  }

  /** A row in the run-history list. */
  public record RunRow(
      UUID runId,
      UUID procedureId,
      String procedureTitle,
      String state,
      String startedAt,
      @Nullable Long durationSeconds,
      int deviationCount) {}

  /** Per-procedure run analytics. */
  public record ProcedureStats(
      UUID procedureId,
      String procedureTitle,
      int runCount,
      int completionPct,
      @Nullable Long avgDurationSeconds,
      long deviationCount) {}
}
