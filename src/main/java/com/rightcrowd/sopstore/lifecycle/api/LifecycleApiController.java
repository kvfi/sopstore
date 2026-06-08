package com.rightcrowd.sopstore.lifecycle.api;

import com.rightcrowd.sopstore.identity.AuthenticatedUser;
import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.lifecycle.ChangeRequest;
import com.rightcrowd.sopstore.lifecycle.internal.WorkflowService;
import com.rightcrowd.sopstore.lifecycle.internal.WorkflowTask;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureApi;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** JSON API for change control and the approval queue, backing the SvelteKit portal. */
@RestController
public class LifecycleApiController {

  private final WorkflowService workflow;
  private final ProcedureApi procedures;

  /** Creates the controller with the workflow engine and procedure read API. */
  public LifecycleApiController(WorkflowService workflow, ProcedureApi procedures) {
    this.workflow = workflow;
    this.procedures = procedures;
  }

  /** A change request as the SPA shows it. */
  public record ChangeRequestDto(
      UUID id,
      String title,
      String status,
      String classification,
      boolean trainingImpact,
      String createdAt) {}

  /** An approval task as the SPA shows it. */
  public record TaskDto(
      UUID id,
      UUID procedureId,
      String procedureTitle,
      String stage,
      String role,
      String meaning,
      String status,
      @Nullable String due) {}

  /** Change requests + tasks for one procedure. */
  public record ChangeControl(List<ChangeRequestDto> changeRequests, List<TaskDto> tasks) {}

  /** Payload to open a change request. */
  public record OpenChangeRequest(
      String title, String reason, String classification, boolean trainingImpact) {}

  /** Payload for an approval decision. */
  public record Decision(boolean approve, @Nullable String password, @Nullable String reason) {}

  /** Returns the change requests and workflow tasks recorded against a procedure. */
  @GetMapping("/api/v1/procedures/{id}/change-requests")
  public ChangeControl changeControl(@PathVariable UUID id) {
    List<ChangeRequestDto> crs =
        workflow.changeRequestsForProcedure(id).stream().map(LifecycleApiController::toCr).toList();
    List<TaskDto> tasks =
        workflow.tasksForProcedure(id).stream().map(this::toTask).toList();
    return new ChangeControl(crs, tasks);
  }

  /** Opens a change request against a procedure and starts its approval workflow. */
  @PostMapping("/api/v1/procedures/{id}/change-requests")
  public void open(
      @PathVariable UUID id,
      @RequestBody OpenChangeRequest req,
      @AuthenticationPrincipal AuthenticatedUser user) {
    ChangeRequest.Classification classification =
        ChangeRequest.Classification.valueOf(req.classification());
    workflow.openChangeRequest(
        id, req.title(), req.reason(), classification, req.trainingImpact(), user.user().id());
  }

  /** Returns the signed-in user's pending approval queue across the roles they hold. */
  @GetMapping("/api/v1/approvals")
  public List<TaskDto> approvals(@AuthenticationPrincipal AuthenticatedUser user) {
    List<Role> roles = List.copyOf(user.user().roles());
    return workflow.pendingTasksFor(roles).stream().map(this::toTask).toList();
  }

  /** Records an approval decision. Approving signs a Part 11 e-signature (fresh password). */
  @PostMapping("/api/v1/approvals/{taskId}/decide")
  public void decide(
      @PathVariable UUID taskId,
      @RequestBody Decision decision,
      @AuthenticationPrincipal AuthenticatedUser user) {
    try {
      workflow.decide(
          taskId, decision.approve(), decision.password(), decision.reason(), user.user().id());
    } catch (SecurityException e) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
    }
  }

  private static ChangeRequestDto toCr(ChangeRequest cr) {
    return new ChangeRequestDto(
        cr.id(),
        cr.title(),
        cr.status().name(),
        cr.classification().name(),
        cr.trainingImpact(),
        cr.createdAt().toString());
  }

  private TaskDto toTask(WorkflowTask t) {
    String title =
        procedures.findById(t.procedureId()).map(Procedure::title).orElse("(unknown procedure)");
    Instant due = t.dueAt();
    return new TaskDto(
        t.id(),
        t.procedureId(),
        title,
        t.stageName(),
        t.assigneeRole().name(),
        t.meaning().name(),
        t.status().name(),
        due == null ? null : due.toString());
  }
}
