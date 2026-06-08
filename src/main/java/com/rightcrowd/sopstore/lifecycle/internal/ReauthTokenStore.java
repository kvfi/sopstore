package com.rightcrowd.sopstore.lifecycle.internal;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for single-use re-authentication tokens. Abstracted so the token lifecycle can be unit
 * tested with an in-memory fake while production uses a cluster-safe Redis implementation.
 */
interface ReauthTokenStore {

  /** Stores a token bound to a user, expiring after {@code ttl}. */
  void store(UUID tokenId, UUID userId, Duration ttl);

  /** Atomically returns and removes the user bound to the token, or empty if absent/expired. */
  Optional<UUID> takeUser(UUID tokenId);
}
