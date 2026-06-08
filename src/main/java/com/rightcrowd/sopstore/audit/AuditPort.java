package com.rightcrowd.sopstore.audit;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

/** Sibling-module entry point into the audit log. */
@NamedInterface("audit-port")
public interface AuditPort {

  /** Records a single audit entry into the audit log. */
  void record(AuditEntry entry);

  /** Verifies the hash chain for one entity over the period since {@code sinceMillis}. */
  boolean verifyChain(String entityType, String entityId, long sinceMillis);

  /** Immutable description of a single audit event. */
  record AuditEntry(
      String entityType, String entityId, String action, UUID actorId, String detailJson) {
    /** Creates a new audit entry from the given fields. */
    public static AuditEntry of(
        String entityType, String entityId, String action, UUID actorId, String detailJson) {
      return new AuditEntry(entityType, entityId, action, actorId, detailJson);
    }
  }
}
