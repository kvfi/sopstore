package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.lifecycle.ChangeRequest;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for accessing and persisting change requests. */
public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {
  /** Finds change requests for the given procedure that have the given status. */
  List<ChangeRequest> findByProcedureIdAndStatus(UUID procedureId, ChangeRequest.Status status);

  /** Finds all change requests for the given procedure, newest first. */
  List<ChangeRequest> findByProcedureIdOrderByCreatedAtDesc(UUID procedureId);

  /** Counts change requests in any of the given statuses. */
  long countByStatusIn(Collection<ChangeRequest.Status> statuses);

  /** Returns change requests in any of the given statuses, newest first. */
  List<ChangeRequest> findByStatusInOrderByCreatedAtDesc(Collection<ChangeRequest.Status> statuses);
}
