package com.rightcrowd.sopstore.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only directory for resolving users across modules (e.g. the training module recording who is
 * qualified) without reaching into identity's persistence.
 */
public interface UserDirectory {

  /** Resolves a user id by email, if a user with that email exists in the current tenant. */
  Optional<UUID> idByEmail(String email);

  /** Resolves a user's email by id, if present. */
  Optional<String> emailById(UUID userId);

  /** Resolves a user's display name by id, if present. */
  Optional<String> displayNameById(UUID userId);

  /** Returns the ids of active users in the current tenant who hold the given role. */
  List<UUID> idsByRole(Role role);

  /** Whether the user accepts email notifications (defaults to {@code true} if unknown). */
  boolean emailNotificationsEnabled(UUID userId);

  /** Whether the user has muted the given notification category (defaults to {@code false}). */
  boolean isNotificationCategoryMuted(UUID userId, String category);
}
