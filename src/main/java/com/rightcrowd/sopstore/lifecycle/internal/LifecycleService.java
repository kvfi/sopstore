package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.audit.AuditPort;
import com.rightcrowd.sopstore.audit.AuditPort.AuditEntry;
import com.rightcrowd.sopstore.lifecycle.LifecycleEvent;
import com.rightcrowd.sopstore.lifecycle.LifecycleState;
import com.rightcrowd.sopstore.lifecycle.LifecycleStateMachine;
import com.rightcrowd.sopstore.lifecycle.Signature;
import com.rightcrowd.sopstore.lifecycle.SignatureMeaning;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureApi;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.security.MessageDigest;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies lifecycle events to procedures, enforcing e-signature and re-authentication rules. */
@Service
@Transactional
public class LifecycleService {

  private final ProcedureApi procedures;
  private final SignatureRepository signatures;
  private final LifecycleStateMachine sm;
  private final AuditPort audit;
  private final ReauthService reauth;

  /** Creates a lifecycle service with its required collaborators. */
  public LifecycleService(
      ProcedureApi procedures,
      SignatureRepository signatures,
      LifecycleStateMachine sm,
      AuditPort audit,
      ReauthService reauth) {
    this.procedures = procedures;
    this.signatures = signatures;
    this.sm = sm;
    this.audit = audit;
    this.reauth = reauth;
  }

  /**
   * Apply a lifecycle event. If the event requires an e-signature, a valid, unconsumed re-auth
   * token must be supplied; it is consumed atomically with the state transition and the signature
   * record is hash-bound to the procedure's canonical current version (the exact content signed).
   */
  public void apply(UUID procedureId, LifecycleEvent event, @Nullable UUID reauthTokenId) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    LifecycleState from = LifecycleState.parse(p.state());
    LifecycleState to = sm.apply(from, event);

    if (sm.requiresSignature(event)) {
      if (reauthTokenId == null) {
        throw new SecurityException(
            "re-authentication required for " + event.getClass().getSimpleName());
      }
      reauth.consumeOrThrow(reauthTokenId, event.actor());
      SignatureMeaning meaning = meaningOf(event);
      String sha = sha256Hex(procedures.currentVersionCanonical(procedureId));
      Signature sig =
          new Signature(
              UUID.randomUUID(),
              TenantContext.current().value(),
              event.actor(),
              p.id(),
              "PROCEDURE",
              meaning,
              sha,
              reauthTokenId);
      signatures.save(sig);
    }

    p.setState(to.name());
    procedures.save(p);

    audit.record(
        AuditEntry.of(
            "procedure",
            p.id().toString(),
            "lifecycle.transition",
            event.actor(),
            "{\"from\":\""
                + from.name()
                + "\",\"to\":\""
                + to.name()
                + "\",\"event\":\""
                + event.getClass().getSimpleName()
                + "\"}"));
  }

  /**
   * Transitions a procedure to APPROVED on behalf of a completed approval workflow. The
   * per-stage e-signatures captured by the workflow are the controlled record of approval, so this
   * transition does not mint a fresh signature; it records the workflow-driven transition in the
   * audit log.
   */
  public void approveViaWorkflow(UUID procedureId, UUID actor) {
    transitionViaWorkflow(
        procedureId, new LifecycleEvent.Approve(actor, SignatureMeaning.APPROVED));
  }

  /** Transitions a procedure back to DRAFT on behalf of a rejected approval workflow. */
  public void rejectViaWorkflow(UUID procedureId, UUID actor, String reason) {
    transitionViaWorkflow(procedureId, new LifecycleEvent.RejectReview(actor, reason));
  }

  private void transitionViaWorkflow(UUID procedureId, LifecycleEvent event) {
    Procedure p = procedures.findById(procedureId).orElseThrow();
    LifecycleState from = LifecycleState.parse(p.state());
    LifecycleState to = sm.apply(from, event);
    p.setState(to.name());
    procedures.save(p);
    audit.record(
        AuditEntry.of(
            "procedure",
            p.id().toString(),
            "lifecycle.transition.workflow",
            event.actor(),
            "{\"from\":\""
                + from.name()
                + "\",\"to\":\""
                + to.name()
                + "\",\"event\":\""
                + event.getClass().getSimpleName()
                + "\"}"));
  }

  private static SignatureMeaning meaningOf(LifecycleEvent e) {
    return switch (e) {
      case LifecycleEvent.Approve a -> a.meaning();
      case LifecycleEvent.Publish p -> p.meaning();
      case LifecycleEvent.CompletePeriodicReview c -> c.meaning();
      case LifecycleEvent.Retire r -> r.meaning();
      case LifecycleEvent.SubmitForReview _,
          LifecycleEvent.RejectReview _,
          LifecycleEvent.StartPeriodicReview _ ->
          throw new IllegalStateException("no signature meaning for " + e);
    };
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
