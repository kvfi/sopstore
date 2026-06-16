package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.platform.PlatformProperties;
import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the current tenant's {@link SessionPolicy}. The session-enforcement filter asks
 * for {@link #effective()} (the saved row, or the platform defaults); the admin API uses
 * {@link #current()} and {@link #save} to view and upsert the single row.
 */
@Service
@Transactional
public class SessionPolicyService {

  /** Lower/upper bounds (seconds) for the configurable timeouts. */
  static final int MIN_IDLE = 60; // 1 minute
  static final int MAX_IDLE = 86_400; // 24 hours
  static final int MAX_ABSOLUTE = 2_592_000; // 30 days

  private final SessionPolicyRepository repo;
  private final PlatformProperties props;

  /** Creates the service with its repository and the platform defaults. */
  public SessionPolicyService(SessionPolicyRepository repo, PlatformProperties props) {
    this.repo = repo;
    this.props = props;
  }

  /** The resolved timeouts a session is subject to, in seconds. */
  public record EffectivePolicy(int idleSeconds, int absoluteSeconds) {}

  /** Returns the tenant's policy row, or empty when it has not been customised. */
  @Transactional(readOnly = true)
  public Optional<SessionPolicy> current() {
    return repo.findFirstByOrderByCreatedAtAsc();
  }

  /** Returns the effective timeouts: the saved row, or the platform defaults when none exists. */
  @Transactional(readOnly = true)
  public EffectivePolicy effective() {
    return current()
        .map(p -> new EffectivePolicy(p.idleTimeoutSeconds(), p.absoluteTimeoutSeconds()))
        .orElseGet(
            () -> {
              PlatformProperties.Session s = props.session();
              int idle = (int) s.idleTimeout().toSeconds();
              int absolute = (int) s.absoluteTimeout().toSeconds();
              return new EffectivePolicy(idle, absolute);
            });
  }

  /**
   * Upserts the tenant's policy, creating the row on first save. The idle timeout must be 1 minute
   * to 24 hours; the absolute timeout must be at least the idle timeout and at most 30 days.
   */
  public SessionPolicy save(int idleSeconds, int absoluteSeconds) {
    if (idleSeconds < MIN_IDLE || idleSeconds > MAX_IDLE) {
      throw new IllegalArgumentException("idle timeout must be between 1 minute and 24 hours");
    }
    if (absoluteSeconds < idleSeconds || absoluteSeconds > MAX_ABSOLUTE) {
      throw new IllegalArgumentException(
          "absolute timeout must be at least the idle timeout and at most 30 days");
    }
    UUID tenant = TenantContext.current().value();
    SessionPolicy p =
        current()
            .orElseGet(
                () -> new SessionPolicy(UUID.randomUUID(), tenant, idleSeconds, absoluteSeconds));
    p.setIdleTimeoutSeconds(idleSeconds);
    p.setAbsoluteTimeoutSeconds(absoluteSeconds);
    p.touch();
    return repo.save(p);
  }
}
