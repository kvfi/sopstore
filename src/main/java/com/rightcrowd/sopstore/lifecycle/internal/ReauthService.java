package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.identity.PasswordVerifier;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived single-use tokens proving the user just re-authenticated to sign a controlled
 * transition (21 CFR Part 11 §11.50: re-authentication required on each signature).
 *
 * <p>Re-authentication requires a <em>fresh credential</em> — the user's password is verified at
 * issue time, not merely an authenticated session. Tokens are held in a {@link ReauthTokenStore}
 * (Redis in production) so they survive nothing and are consumed exactly once across the cluster.
 */
@Service
public class ReauthService {

  private static final Duration TTL = Duration.ofMinutes(2);

  private final PasswordVerifier passwords;
  private final ReauthTokenStore store;

  /** Creates the service with the password verifier and token store. */
  public ReauthService(PasswordVerifier passwords, ReauthTokenStore store) {
    this.passwords = passwords;
    this.store = store;
  }

  /**
   * Verifies the user's password and, if valid, issues a single-use re-authentication token.
   *
   * @throws SecurityException if the password does not match
   */
  public UUID issue(UUID userId, String rawPassword) {
    if (!passwords.matches(userId, rawPassword)) {
      throw new SecurityException("re-authentication failed");
    }
    UUID tokenId = UUID.randomUUID();
    store.store(tokenId, userId, TTL);
    return tokenId;
  }

  /** Consumes the token, throwing if it is unknown, expired, consumed, or owned by another user. */
  public void consumeOrThrow(UUID tokenId, UUID expectedUser) {
    UUID userId =
        store
            .takeUser(tokenId)
            .orElseThrow(
                () ->
                    new SecurityException("reauth token unknown, expired, or already consumed"));
    if (!userId.equals(expectedUser)) {
      throw new SecurityException("reauth token user mismatch");
    }
  }
}
