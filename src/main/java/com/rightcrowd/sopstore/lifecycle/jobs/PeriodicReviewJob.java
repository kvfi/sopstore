package com.rightcrowd.sopstore.lifecycle.jobs;

import com.rightcrowd.sopstore.lifecycle.LifecycleEvent;
import com.rightcrowd.sopstore.lifecycle.internal.LifecycleService;
import com.rightcrowd.sopstore.procedure.ProcedureApi;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import com.rightcrowd.sopstore.tenancy.TenantId;
import com.rightcrowd.sopstore.tenancy.TenantLookup;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marks effective procedures whose next-review date has passed as {@code UnderReview} and notifies
 * owners. Runs daily at 02:00 server time. Idempotent: only acts on rows still in EFFECTIVE state.
 *
 * <p>Runs per tenant: the scheduler thread has no tenant context, and both the procedure query and
 * the lifecycle transition (which notifies owners) are tenant-scoped, so each active tenant is
 * processed with its id bound — one tenant's failure never aborts the others.
 */
@Component
public class PeriodicReviewJob {

  private static final Logger log = LoggerFactory.getLogger(PeriodicReviewJob.class);
  private static final UUID SYSTEM = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final ProcedureApi procedures;
  private final LifecycleService lifecycle;
  private final TenantLookup tenants;

  /** Creates the job with the procedure API, lifecycle service, and tenant registry. */
  public PeriodicReviewJob(
      ProcedureApi procedures, LifecycleService lifecycle, TenantLookup tenants) {
    this.procedures = procedures;
    this.lifecycle = lifecycle;
    this.tenants = tenants;
  }

  /** Starts periodic review for effective procedures whose next-review date has passed. */
  @Scheduled(cron = "0 0 2 * * *")
  public void run() {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    int started = 0;
    for (TenantId tenant : tenants.activeTenantIds()) {
      TenantContext.set(tenant);
      try {
        for (var p : procedures.findByState("EFFECTIVE")) {
          if (p.nextReviewDate() != null && !today.isBefore(p.nextReviewDate())) {
            lifecycle.apply(p.id(), new LifecycleEvent.StartPeriodicReview(SYSTEM), null);
            started++;
          }
        }
      } catch (RuntimeException e) {
        log.error("PeriodicReviewJob failed for tenant {}", tenant, e);
      } finally {
        TenantContext.clear();
      }
    }
    log.info("PeriodicReviewJob started {} reviews", started);
  }
}
