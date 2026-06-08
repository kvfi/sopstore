package com.rightcrowd.sopstore.lifecycle.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rightcrowd.sopstore.identity.PasswordVerifier;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins the 21 CFR Part 11 re-authentication contract: a token is issued only after a fresh password
 * is verified, is single-use, and is bound to the issuing user.
 */
class ReauthServiceTest {

  private static final UUID USER = UUID.randomUUID();

  // Accepts only USER with the correct password.
  private final PasswordVerifier verifier =
      (userId, raw) -> USER.equals(userId) && "correct-horse".equals(raw);
  private final InMemoryStore store = new InMemoryStore();
  private final ReauthService reauth = new ReauthService(verifier, store);

  @Test
  void issuesForCorrectPasswordAndConsumesExactlyOnce() {
    UUID token = reauth.issue(USER, "correct-horse");

    reauth.consumeOrThrow(token, USER);

    assertThatThrownBy(() -> reauth.consumeOrThrow(token, USER))
        .as("token is single-use")
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void rejectsWrongPasswordAndIssuesNothing() {
    assertThatThrownBy(() -> reauth.issue(USER, "wrong"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("re-authentication failed");
    assertThat(store.map).as("no token persisted on failed challenge").isEmpty();
  }

  @Test
  void rejectsTokenPresentedByAnotherUser() {
    UUID token = reauth.issue(USER, "correct-horse");

    assertThatThrownBy(() -> reauth.consumeOrThrow(token, UUID.randomUUID()))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("user mismatch");
  }

  @Test
  void rejectsUnknownToken() {
    assertThatThrownBy(() -> reauth.consumeOrThrow(UUID.randomUUID(), USER))
        .isInstanceOf(SecurityException.class);
  }

  private static final class InMemoryStore implements ReauthTokenStore {
    private final Map<UUID, UUID> map = new HashMap<>();

    @Override
    public void store(UUID tokenId, UUID userId, Duration ttl) {
      map.put(tokenId, userId);
    }

    @Override
    public Optional<UUID> takeUser(UUID tokenId) {
      return Optional.ofNullable(map.remove(tokenId));
    }
  }
}
