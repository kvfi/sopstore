package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.audit.AuditPort;
import com.rightcrowd.sopstore.audit.AuditPort.AuditEntry;
import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.identity.UserDirectory;
import com.rightcrowd.sopstore.lifecycle.ChangeRequest;
import com.rightcrowd.sopstore.lifecycle.ChangeRequest.Classification;
import com.rightcrowd.sopstore.lifecycle.LifecycleEvent;
import com.rightcrowd.sopstore.lifecycle.Signature;
import com.rightcrowd.sopstore.notification.NotificationPort;
import com.rightcrowd.sopstore.notification.NotificationPort.Channel;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureApi;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives configurable, multi-stage approval workflows for procedure change requests.
 *
 * <p>Stages run sequentially; within a stage every approver role must approve (parallel,
 * all-required). A stage may be conditional (e.g. a QA Director sign-off only for major changes).
 * Each approval is a fresh-credential Part 11 e-signature bound to the procedure's canonical
 * current version. When the last applicable stage completes the procedure transitions to APPROVED;
 * any rejection sends it back to DRAFT and closes the change request as REJECTED.
 */
@Service
@Transactional
public class WorkflowService {

  private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
  private static final String DEFAULT_WORKFLOW = "default";

  private final WorkflowRepository workflows;
  private final WorkflowInstanceRepository instances;
  private final WorkflowTaskRepository tasks;
  private final ChangeRequestRepository changeRequests;
  private final SignatureRepository signatures;
  private final LifecycleService lifecycle;
  private final ReauthService reauth;
  private final ProcedureApi procedures;
  private final NotificationPort notifications;
  private final UserDirectory users;
  private final AuditPort audit;

  /** Creates the workflow engine with its collaborators. */
  public WorkflowService(
      WorkflowRepository workflows,
      WorkflowInstanceRepository instances,
      WorkflowTaskRepository tasks,
      ChangeRequestRepository changeRequests,
      SignatureRepository signatures,
      LifecycleService lifecycle,
      ReauthService reauth,
      ProcedureApi procedures,
      NotificationPort notifications,
      UserDirectory users,
      AuditPort audit) {
    this.workflows = workflows;
    this.instances = instances;
    this.tasks = tasks;
    this.changeRequests = changeRequests;
    this.signatures = signatures;
    this.lifecycle = lifecycle;
    this.reauth = reauth;
    this.procedures = procedures;
    this.notifications = notifications;
    this.users = users;
    this.audit = audit;
  }

  /**
   * Opens a change request against a draft procedure, submits it for review, and starts its
   * approval workflow. Returns the new change request id.
   */
  public UUID openChangeRequest(
      UUID procedureId,
      String title,
      String reason,
      Classification classification,
      boolean trainingImpact,
      UUID requestedBy) {
    UUID tenant = TenantContext.current().value();
    ChangeRequest cr =
        new ChangeRequest(
            UUID.randomUUID(), tenant, procedureId, title, reason, classification, requestedBy);
    if (trainingImpact) {
      cr.markTrainingImpact();
    }
    changeRequests.save(cr);

    // Draft -> InReview (no signature). Skip if it is already under review.
    Procedure p = procedures.findById(procedureId).orElseThrow();
    if ("DRAFT".equals(p.state())) {
      lifecycle.apply(procedureId, new LifecycleEvent.SubmitForReview(requestedBy), null);
    }

    Workflow wf = resolveDefaultWorkflow(tenant);
    WorkflowInstance instance =
        new WorkflowInstance(UUID.randomUUID(), tenant, wf.id(), procedureId, cr.id());
    instances.save(instance);
    cr.markInProgress();
    changeRequests.save(cr);

    audit.record(
        AuditEntry.of(
            "change_request",
            cr.id().toString(),
            "change_request.opened",
            requestedBy,
            "{\"procedureId\":\""
                + procedureId
                + "\",\"classification\":\""
                + classification
                + "\"}"));

    openNextStage(instance, cr, requestedBy);
    return cr.id();
  }

  /**
   * Records a decision on one approval task. Approving requires a fresh password challenge and
   * mints the Part 11 e-signature; rejecting fails the whole workflow.
   *
   * @throws SecurityException if the actor lacks the task's role, the task is already decided, or
   *     re-authentication fails
   */
  public void decide(
      UUID taskId,
      boolean approve,
      @Nullable String password,
      @Nullable String reason,
      UUID actor) {
    WorkflowTask task = tasks.findById(taskId).orElseThrow();
    if (task.status() != WorkflowTask.Status.PENDING) {
      throw new IllegalStateException("task " + taskId + " is already " + task.status());
    }
    if (!users.idsByRole(task.assigneeRole()).contains(actor)) {
      throw new SecurityException("actor does not hold role " + task.assigneeRole());
    }
    WorkflowInstance instance = instances.findById(task.instanceId()).orElseThrow();
    ChangeRequest cr = changeRequests.findById(task.changeRequestId()).orElseThrow();

    if (!approve) {
      String why = (reason == null || reason.isBlank()) ? "rejected at review" : reason;
      task.reject(actor, why);
      tasks.save(task);
      instance.reject();
      instances.save(instance);
      cr.markRejected();
      changeRequests.save(cr);
      lifecycle.rejectViaWorkflow(task.procedureId(), actor, why);
      notify(cr.requestedBy(), "workflow.rejected", cr, task.stageName());
      audit.record(
          AuditEntry.of(
              "workflow_task",
              task.id().toString(),
              "workflow.task.rejected",
              actor,
              "{\"stage\":\"" + task.stageName() + "\",\"reason\":" + jsonString(why) + "}"));
      return;
    }

    // Approve: fresh-credential challenge -> single-use token -> bound e-signature.
    if (password == null || password.isBlank()) {
      throw new SecurityException("re-authentication required to sign approval");
    }
    UUID tokenId = reauth.issue(actor, password);
    reauth.consumeOrThrow(tokenId, actor);
    String sha = sha256Hex(procedures.currentVersionCanonical(task.procedureId()));
    UUID signatureId = UUID.randomUUID();
    signatures.save(
        new Signature(
            signatureId,
            TenantContext.current().value(),
            actor,
            task.procedureId(),
            "PROCEDURE",
            task.meaning(),
            sha,
            tokenId));
    task.approve(actor, signatureId);
    tasks.save(task);
    audit.record(
        AuditEntry.of(
            "workflow_task",
            task.id().toString(),
            "workflow.task.approved",
            actor,
            "{\"stage\":\""
                + task.stageName()
                + "\",\"meaning\":\""
                + task.meaning()
                + "\",\"signatureId\":\""
                + signatureId
                + "\"}"));

    boolean stageComplete =
        tasks.findByInstanceIdAndStageIndex(instance.id(), task.stageIndex()).stream()
            .allMatch(t -> t.status() == WorkflowTask.Status.APPROVED);
    if (stageComplete) {
      openNextStage(instance, cr, actor);
    }
  }

  /** Advances to the next applicable stage, or completes the workflow if none remain. */
  private void openNextStage(WorkflowInstance instance, ChangeRequest cr, UUID actor) {
    List<WorkflowStage> stages = stagesOf(instance);
    int next = instance.currentStage() + 1;
    while (next < stages.size() && !stages.get(next).condition().appliesTo(cr)) {
      next++;
    }
    if (next >= stages.size()) {
      instance.complete();
      instances.save(instance);
      cr.markApproved();
      changeRequests.save(cr);
      lifecycle.approveViaWorkflow(Objects.requireNonNull(instance.procedureId()), actor);
      notify(cr.requestedBy(), "workflow.completed", cr, "Approved");
      audit.record(
          AuditEntry.of(
              "change_request",
              cr.id().toString(),
              "workflow.completed",
              actor,
              "{\"procedureId\":\"" + instance.procedureId() + "\"}"));
      return;
    }
    instance.moveToStage(next);
    instances.save(instance);
    openStageTasks(instance, cr, next, stages.get(next));
  }

  private void openStageTasks(
      WorkflowInstance instance, ChangeRequest cr, int stageIndex, WorkflowStage stage) {
    UUID tenant = TenantContext.current().value();
    Instant due = Instant.now().plus(Duration.ofHours(stage.slaHours()));
    UUID procedureId = Objects.requireNonNull(instance.procedureId());
    for (Role role : stage.approverRoles()) {
      WorkflowTask task =
          new WorkflowTask(
              UUID.randomUUID(),
              tenant,
              instance.id(),
              procedureId,
              cr.id(),
              stageIndex,
              stage.name(),
              role,
              stage.meaning(),
              due);
      tasks.save(task);
      for (UUID recipient : users.idsByRole(role)) {
        notify(recipient, "workflow.task.assigned", cr, stage.name());
      }
    }
    log.info(
        "Workflow {} opened stage {} ({}) with roles {}",
        instance.id(),
        stageIndex,
        stage.name(),
        stage.approverRoles());
  }

  /** Returns the pending approval queue for the given roles. */
  @Transactional(readOnly = true)
  public List<WorkflowTask> pendingTasksFor(List<Role> roles) {
    if (roles.isEmpty()) {
      return List.of();
    }
    return tasks.findByStatusAndAssigneeRoleInOrderByCreatedAtAsc(
        WorkflowTask.Status.PENDING, roles);
  }

  /** Returns every workflow task recorded against a procedure, newest first. */
  @Transactional(readOnly = true)
  public List<WorkflowTask> tasksForProcedure(UUID procedureId) {
    return tasks.findByProcedureIdOrderByCreatedAtDesc(procedureId);
  }

  /** Returns every change request recorded against a procedure, newest first. */
  @Transactional(readOnly = true)
  public List<ChangeRequest> changeRequestsForProcedure(UUID procedureId) {
    return changeRequests.findByProcedureIdOrderByCreatedAtDesc(procedureId);
  }

  /** Notifies all current assignees of overdue pending tasks. Invoked by the escalation job. */
  public int escalateOverdue(Instant now) {
    var overdue =
        tasks.findByStatusAndEscalatedFalseAndDueAtBefore(WorkflowTask.Status.PENDING, now);
    for (WorkflowTask task : overdue) {
      ChangeRequest cr = changeRequests.findById(task.changeRequestId()).orElse(null);
      for (UUID recipient : users.idsByRole(task.assigneeRole())) {
        notifications.send(
            recipient,
            "workflow.task.overdue",
            Map.of(
                "procedureId", task.procedureId(),
                "stage", task.stageName(),
                "title", cr == null ? "" : cr.title()),
            Channel.IN_APP);
      }
      task.markEscalated();
      tasks.save(task);
    }
    if (!overdue.isEmpty()) {
      log.info("Escalated {} overdue approval task(s)", overdue.size());
    }
    return overdue.size();
  }

  private Workflow resolveDefaultWorkflow(UUID tenant) {
    return workflows
        .findByName(DEFAULT_WORKFLOW)
        .orElseGet(
            () ->
                workflows.save(
                    new Workflow(
                        UUID.randomUUID(),
                        tenant,
                        DEFAULT_WORKFLOW,
                        WorkflowDefinitionCodec.toJson(defaultStages()))));
  }

  private List<WorkflowStage> stagesOf(WorkflowInstance instance) {
    Workflow wf = workflows.findById(instance.workflowId()).orElseThrow();
    return WorkflowDefinitionCodec.fromJson(wf.stagesJson());
  }

  /**
   * The platform default workflow: a sequential Quality Review then Approval, with a conditional QA
   * Director sign-off for major or training-impacting changes.
   */
  private static List<WorkflowStage> defaultStages() {
    return List.of(
        new WorkflowStage(
            "Quality Review",
            List.of(Role.REVIEWER),
            com.rightcrowd.sopstore.lifecycle.SignatureMeaning.REVIEWED,
            48,
            StageCondition.ALWAYS),
        new WorkflowStage(
            "Approval",
            List.of(Role.APPROVER),
            com.rightcrowd.sopstore.lifecycle.SignatureMeaning.APPROVED,
            72,
            StageCondition.ALWAYS),
        new WorkflowStage(
            "QA Director Sign-off",
            List.of(Role.QUALITY_MANAGER),
            com.rightcrowd.sopstore.lifecycle.SignatureMeaning.APPROVED,
            72,
            StageCondition.MAJOR_OR_TRAINING_IMPACT));
  }

  private void notify(UUID recipient, String templateKey, ChangeRequest cr, String stage) {
    notifications.send(
        recipient,
        templateKey,
        Map.of(
            "procedureId", cr.procedureId(),
            "changeRequestId", cr.id(),
            "title", cr.title(),
            "stage", stage),
        Channel.IN_APP);
  }

  private static String jsonString(String raw) {
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String sha256Hex(byte[] data) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
      StringBuilder sb = new StringBuilder(64);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
