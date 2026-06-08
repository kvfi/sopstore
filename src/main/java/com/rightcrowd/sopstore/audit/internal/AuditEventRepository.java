package com.rightcrowd.sopstore.audit.internal;

import com.rightcrowd.sopstore.audit.AuditEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for accessing and querying persisted audit events. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  /** Most recent event for the same (tenant, entityType, entityId) — its hash becomes prev_hash. */
  Optional<AuditEvent> findFirstByEntityTypeAndEntityIdOrderByOccurredAtDesc(
      String entityType, String entityId);

  /** Returns all events for the given entity ordered by occurrence time ascending. */
  List<AuditEvent> findByEntityTypeAndEntityIdOrderByOccurredAtAsc(
      String entityType, String entityId);
}
