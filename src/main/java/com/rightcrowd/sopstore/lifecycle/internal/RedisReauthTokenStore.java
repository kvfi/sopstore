package com.rightcrowd.sopstore.lifecycle.internal;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Cluster-safe re-auth token store backed by Redis; consumption is an atomic {@code GETDEL}. */
@Component
class RedisReauthTokenStore implements ReauthTokenStore {

  private static final String PREFIX = "reauth:token:";

  private final StringRedisTemplate redis;

  RedisReauthTokenStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void store(UUID tokenId, UUID userId, Duration ttl) {
    redis.opsForValue().set(PREFIX + tokenId, userId.toString(), ttl);
  }

  @Override
  public Optional<UUID> takeUser(UUID tokenId) {
    String userId = redis.opsForValue().getAndDelete(PREFIX + tokenId);
    return userId == null ? Optional.empty() : Optional.of(UUID.fromString(userId));
  }
}
