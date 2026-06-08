package com.rightcrowd.sopstore.lifecycle.jobs;

import com.rightcrowd.sopstore.lifecycle.internal.WorkflowService;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import com.rightcrowd.sopstore.tenancy.TenantId;
import com.rightcrowd.sopstore.tenancy.TenantLookup;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Escalates overdue approval tasks. Runs hourly: any pending task whose SLA due time has passed and
 * that has not already been escalated triggers an overdue notification to its assignees. Idempotent
 * via the {@code escalated} flag, so a task is escalated at most once.
 *
 * <p>The scheduler thread has no tenant context, and the task/notification queries are
 * tenant-scoped (Hibernate {@code @TenantId} + RLS), so the job iterates active tenants and runs
 * each one's escalation with that tenant bound — one tenant's failure never aborts the others.
 */
@Component
public class WorkflowEscalationJob {

  private static final Logger log = LoggerFactory.getLogger(WorkflowEscalationJob.class);

  private final WorkflowService workflow;
  private final TenantLookup tenants;

  /** Creates the job with the workflow engine and tenant registry. */
  public WorkflowEscalationJob(WorkflowService workflow, TenantLookup tenants) {
    this.workflow = workflow;
    this.tenants = tenants;
  }

  /** Scans for overdue approval tasks and notifies their assignees, per tenant. */
  @Scheduled(cron = "0 0 * * * *")
  public void run() {
    Instant now = Instant.now();
    int escalated = 0;
    for (TenantId tenant : tenants.activeTenantIds()) {
      TenantContext.set(tenant);
      try {
        escalated += workflow.escalateOverdue(now);
      } catch (RuntimeException e) {
        log.error("WorkflowEscalationJob failed for tenant {}", tenant, e);
      } finally {
        TenantContext.clear();
      }
    }
    if (escalated > 0) {
      log.info("WorkflowEscalationJob escalated {} overdue task(s)", escalated);
    }
  }
}
