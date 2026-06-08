package com.rightcrowd.sopstore.execution.api;

import com.rightcrowd.sopstore.execution.ProcedureRun;
import com.rightcrowd.sopstore.execution.internal.RunService;
import com.rightcrowd.sopstore.identity.AuthenticatedUser;
import com.rightcrowd.sopstore.procedure.Step;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** JSON API for run history, analytics, and run execution, backing the SvelteKit portal. */
@RestController
@RequestMapping("/api/v1/runs")
public class RunsApiController {

  private final RunService runs;

  /** Creates the controller with the run service. */
  public RunsApiController(RunService runs) {
    this.runs = runs;
  }

  /** Run history list + per-procedure analytics. */
  public record RunsOverview(
      List<RunService.RunRow> history, List<RunService.ProcedureStats> analytics) {}

  /** A step shown on the run page. */
  public record StepDto(
      UUID id, int order, String title, String instruction, @Nullable String evidenceSpec) {}

  /** A deviation raised during the run. */
  public record DeviationDto(UUID id, String category, String description, boolean open) {}

  /** A run with its steps and deviations. */
  public record RunDetail(
      UUID id,
      UUID procedureId,
      String state,
      String startedAt,
      int evidenceCount,
      List<StepDto> steps,
      List<DeviationDto> deviations) {}

  /** Payloads. */
  public record StartRequest(UUID procedureId) {}

  /** Numeric measurement capture. */
  public record MeasurementRequest(BigDecimal value, String unit) {}

  /** Free-text evidence note. */
  public record NoteRequest(String text) {}

  /** Returns run history and analytics. */
  @GetMapping
  public RunsOverview overview() {
    return new RunsOverview(runs.history(), runs.analytics());
  }

  /** Returns one run with steps, deviations, and an evidence count. */
  @GetMapping("/{id}")
  public RunDetail run(@PathVariable UUID id) {
    ProcedureRun r = runs.get(id);
    List<StepDto> steps =
        runs.steps(r.procedureId()).stream().map(RunsApiController::toStep).toList();
    List<DeviationDto> devs =
        runs.deviationsFor(id).stream()
            .map(d -> new DeviationDto(d.id(), d.category().name(), d.description(), d.open()))
            .toList();
    return new RunDetail(
        r.id(),
        r.procedureId(),
        r.state().name(),
        r.startedAt().toString(),
        runs.evidenceFor(id).size(),
        steps,
        devs);
  }

  /** Starts a run (gated on training qualification). */
  @PostMapping("/start")
  public RunDetail start(
      @RequestBody StartRequest req, @AuthenticationPrincipal AuthenticatedUser user) {
    try {
      ProcedureRun r = runs.start(req.procedureId(), user.user().id());
      return run(r.id());
    } catch (SecurityException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    }
  }

  /** Records a numeric measurement for a step (auto-flags out-of-tolerance deviations). */
  @PostMapping("/{id}/steps/{stepId}/measurement")
  public RunDetail measurement(
      @PathVariable UUID id, @PathVariable UUID stepId, @RequestBody MeasurementRequest req) {
    runs.recordMeasurement(id, stepId, req.value(), req.unit());
    return run(id);
  }

  /** Records a free-text evidence note for a step. */
  @PostMapping("/{id}/steps/{stepId}/note")
  public RunDetail note(
      @PathVariable UUID id, @PathVariable UUID stepId, @RequestBody NoteRequest req) {
    runs.recordNote(id, stepId, req.text());
    return run(id);
  }

  /** Completes the run. */
  @PostMapping("/{id}/complete")
  public RunDetail complete(@PathVariable UUID id) {
    runs.complete(id);
    return run(id);
  }

  private static StepDto toStep(Step s) {
    return new StepDto(s.id(), s.order(), s.title(), s.instruction(), s.evidenceSpec());
  }
}
