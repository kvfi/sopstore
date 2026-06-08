package com.rightcrowd.sopstore.audit.internal;

import com.rightcrowd.sopstore.audit.AuditEvent;
import com.rightcrowd.sopstore.audit.AuditPort;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Audit service that records events into a tamper-evident SHA-256 hash chain. */
@Service
public class HashChainedAuditService implements AuditPort {

  private final AuditEventRepository repo;

  /** Creates a service backed by the given audit event repository. */
  public HashChainedAuditService(AuditEventRepository repo) {
    this.repo = repo;
  }

  /**
   * REQUIRES_NEW: the audit entry persists even if the originating transaction rolls back (so
   * failed approvals still leave a trace) — subject to the boundary that the originating
   * transaction must not have already committed RLS-relevant side effects we want to keep.
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(AuditEntry entry) {
    String prevHash =
        repo.findFirstByEntityTypeAndEntityIdOrderByOccurredAtDesc(
                entry.entityType(), entry.entityId())
            .map(AuditEvent::hash)
            .orElse(null);

    Instant now = Instant.now();
    String canonical =
        TenantContext.current().value()
            + "|"
            + entry.entityType()
            + "|"
            + entry.entityId()
            + "|"
            + entry.action()
            + "|"
            + entry.actorId()
            + "|"
            + now
            + "|"
            + entry.detailJson();
    String hash = sha256Hex((prevHash == null ? "" : prevHash) + canonical);

    AuditEvent e =
        new AuditEvent(
            UUID.randomUUID(),
            TenantContext.current().value(),
            entry.entityType(),
            entry.entityId(),
            entry.action(),
            entry.actorId(),
            entry.detailJson(),
            prevHash,
            hash);
    repo.save(e);
  }

  @Override
  public boolean verifyChain(String entityType, String entityId, long sinceMillis) {
    String expectedPrev = null;
    for (AuditEvent e :
        repo.findByEntityTypeAndEntityIdOrderByOccurredAtAsc(entityType, entityId)) {
      String canonical =
          e.tenantId()
              + "|"
              + e.entityType()
              + "|"
              + e.entityId()
              + "|"
              + e.action()
              + "|"
              + e.actorId()
              + "|"
              + e.occurredAt()
              + "|"
              + e.detailJson();
      String expected = sha256Hex((expectedPrev == null ? "" : expectedPrev) + canonical);
      if (!expected.equals(e.hash())) {
        return false;
      }
      if (!java.util.Objects.equals(expectedPrev, e.prevHash())) {
        return false;
      }
      expectedPrev = e.hash();
    }
    return true;
  }

  private static String sha256Hex(String s) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
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
