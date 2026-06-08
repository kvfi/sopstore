package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.identity.User;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service profile mutations for the signed-in user: display name, password (with a
 * current-password challenge and Argon2id re-hash), and notification preferences. Exposed to the
 * profile controller within the identity module.
 */
@Service
public class ProfileService {

  private final UserRepository users;
  private final PasswordEncoder encoder;

  ProfileService(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  /** A user's notification preferences: the email toggle and the muted category list (CSV). */
  public record NotificationPrefs(boolean emailNotifications, String mutedCategories) {}

  /** Updates the user's display name. */
  @Transactional
  public void updateDisplayName(UUID userId, String name) {
    User u = users.findById(userId).orElseThrow();
    u.setDisplayName(name);
    users.save(u);
  }

  /**
   * Changes the user's password after verifying the current one. Returns {@code false} (without
   * changing anything) when the current password does not match.
   */
  @Transactional
  public boolean changePassword(UUID userId, String current, String newPassword) {
    User u = users.findById(userId).orElseThrow();
    String hash = u.passwordHash();
    if (hash == null || hash.isBlank() || !encoder.matches(current, hash)) {
      return false;
    }
    u.setPasswordHash(encoder.encode(newPassword));
    users.save(u);
    return true;
  }

  /** Returns the user's current notification preferences. */
  public NotificationPrefs notificationPrefs(UUID userId) {
    User u = users.findById(userId).orElseThrow();
    return new NotificationPrefs(u.emailNotifications(), u.mutedNotificationCategories());
  }

  /** Replaces the user's notification preferences. */
  @Transactional
  public void updateNotificationPrefs(UUID userId, boolean emailNotifications, String mutedCsv) {
    User u = users.findById(userId).orElseThrow();
    u.setEmailNotifications(emailNotifications);
    u.setMutedNotificationCategories(mutedCsv);
    users.save(u);
  }
}
