package com.rightcrowd.sopstore.identity.api;

import com.rightcrowd.sopstore.identity.internal.SessionPolicy;
import com.rightcrowd.sopstore.identity.internal.SessionPolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for the per-tenant session timeout policy. The read is open to any authenticated user
 * (so the settings screen can show the current values); the write requires {@code TENANT_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/session-policy")
public class SessionPolicyController {

  private final SessionPolicyService service;

  /** Creates the controller with the session-policy service. */
  public SessionPolicyController(SessionPolicyService service) {
    this.service = service;
  }

  /** The session timeouts, in seconds. */
  public record Dto(int idleTimeoutSeconds, int absoluteTimeoutSeconds) {}

  /** Returns the effective policy: the tenant's saved row, or the platform defaults. */
  @GetMapping
  public Dto get() {
    SessionPolicyService.EffectivePolicy p = service.effective();
    return new Dto(p.idleSeconds(), p.absoluteSeconds());
  }

  /** Upserts the tenant's policy. Returns 400 when the timeouts are out of range. */
  @PutMapping
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<Dto> save(@RequestBody Dto req) {
    try {
      SessionPolicy p = service.save(req.idleTimeoutSeconds(), req.absoluteTimeoutSeconds());
      return ResponseEntity.ok(new Dto(p.idleTimeoutSeconds(), p.absoluteTimeoutSeconds()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }
}
