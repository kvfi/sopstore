package com.rightcrowd.sopstore.reporting;

import com.rightcrowd.sopstore.execution.ExecutionApi;
import com.rightcrowd.sopstore.lifecycle.LifecycleApi;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureApi;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** JSON compliance dashboard for the SPA — same aggregation as the Thymeleaf dashboard. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardApiController {

  private final ProcedureApi procedures;
  private final LifecycleApi lifecycle;
  private final ExecutionApi execution;

  /** Creates the controller with the procedure, lifecycle, and execution read APIs. */
  public DashboardApiController(
      ProcedureApi procedures, LifecycleApi lifecycle, ExecutionApi execution) {
    this.procedures = procedures;
    this.lifecycle = lifecycle;
    this.execution = execution;
  }

  /** A single dashboard KPI. */
  public record Kpi(String label, long value) {}

  /** A procedure overdue for periodic review. */
  public record OverdueReview(
      UUID procedureId, String documentNumber, String title, String due) {}

  /** A pending approval. */
  public record Approval(UUID procedureId, String procedureTitle, String stage, String role) {}

  /** An open change request. */
  public record ChangeRequest(
      UUID procedureId,
      String procedureTitle,
      String title,
      String status,
      String classification) {}

  /** The full dashboard payload. */
  public record Dashboard(
      List<Kpi> kpis,
      List<OverdueReview> overdueReviews,
      List<Approval> approvals,
      List<ChangeRequest> changeRequests,
      List<ExecutionApi.DeviationSummary> deviations) {
    /** Defensive copies so the response DTO can't be mutated through its lists. */
    public Dashboard {
      kpis = List.copyOf(kpis);
      overdueReviews = List.copyOf(overdueReviews);
      approvals = List.copyOf(approvals);
      changeRequests = List.copyOf(changeRequests);
      deviations = List.copyOf(deviations);
    }
  }

  /** Builds the dashboard from live cross-module data. */
  @GetMapping
  public Dashboard dashboard() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<Procedure> effective = procedures.findByState("EFFECTIVE");
    List<OverdueReview> overdue =
        effective.stream()
            .filter(p -> p.nextReviewDate() != null && !today.isBefore(p.nextReviewDate()))
            .map(
                p ->
                    new OverdueReview(
                        p.id(),
                        p.documentNumber(),
                        p.title(),
                        String.valueOf(p.nextReviewDate())))
            .toList();
    long underReview = procedures.findByState("UNDER_REVIEW").size();

    List<Approval> approvals =
        lifecycle.pendingApprovals().stream()
            .map(
                a ->
                    new Approval(
                        a.procedureId(), titleOf(a.procedureId()), a.stageName(), a.role()))
            .toList();
    List<ChangeRequest> crs =
        lifecycle.openChangeRequests().stream()
            .map(
                c ->
                    new ChangeRequest(
                        c.procedureId(),
                        titleOf(c.procedureId()),
                        c.title(),
                        c.status(),
                        c.classification()))
            .toList();

    List<Kpi> kpis =
        List.of(
            new Kpi("Total procedures", procedures.count()),
            new Kpi("Effective", effective.size()),
            new Kpi("Overdue reviews", overdue.size()),
            new Kpi("Under review", underReview),
            new Kpi("Open change requests", lifecycle.openChangeRequestCount()),
            new Kpi("Pending approvals", lifecycle.pendingApprovalCount()),
            new Kpi("Open deviations", execution.openDeviationCount()));

    return new Dashboard(kpis, overdue, approvals, crs, execution.recentDeviations());
  }

  private String titleOf(UUID procedureId) {
    return procedures.findById(procedureId).map(Procedure::title).orElse("(unknown)");
  }
}
