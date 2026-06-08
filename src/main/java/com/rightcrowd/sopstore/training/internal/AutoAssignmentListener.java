package com.rightcrowd.sopstore.training.internal;

import com.rightcrowd.sopstore.procedure.events.ProcedureVersionCreated;
import com.rightcrowd.sopstore.training.TrainingAssignment;
import com.rightcrowd.sopstore.training.TrainingAssignment.Source;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link ProcedureVersionCreated} from the procedure module and auto-creates
 * re-training assignments for everyone currently qualified on the procedure. Runs after the
 * originating transaction commits.
 */
@Component
public class AutoAssignmentListener {

  private static final Logger log = LoggerFactory.getLogger(AutoAssignmentListener.class);
  private final TrainingAssignmentRepository assignments;
  private final QualificationRepository qualifications;

  /** Creates the listener with the training assignment and qualification repositories. */
  public AutoAssignmentListener(
      TrainingAssignmentRepository assignments, QualificationRepository qualifications) {
    this.assignments = assignments;
    this.qualifications = qualifications;
  }

  /** Creates re-training assignments for everyone qualified on the procedure version. */
  @ApplicationModuleListener
  public void onVersionPublished(ProcedureVersionCreated e) {
    var quals = qualifications.findByProcedureId(e.procedureId());
    int created = 0;
    for (var q : quals) {
      assignments.save(
          new TrainingAssignment(
              UUID.randomUUID(),
              e.tenantId(),
              q.userId(),
              e.procedureId(),
              e.versionId(),
              Source.AUTOMATIC_VERSION_CHANGE,
              null));
      created++;
    }
    log.info("Auto-assigned {} re-training tasks for procedure version {}", created, e.versionId());
  }
}
