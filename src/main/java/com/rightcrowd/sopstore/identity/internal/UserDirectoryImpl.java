package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.identity.User;
import com.rightcrowd.sopstore.identity.UserDirectory;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Default {@link UserDirectory} backed by the user repository. */
@Service
class UserDirectoryImpl implements UserDirectory {

  private final UserRepository users;

  UserDirectoryImpl(UserRepository users) {
    this.users = users;
  }

  @Override
  public Optional<UUID> idByEmail(String email) {
    return users.findByEmail(email.toLowerCase(Locale.ROOT)).map(User::id);
  }

  @Override
  public Optional<String> emailById(UUID userId) {
    return users.findById(userId).map(User::email);
  }

  @Override
  public Optional<String> displayNameById(UUID userId) {
    return users.findById(userId).map(User::displayName);
  }

  @Override
  public List<UUID> idsByRole(Role role) {
    return users.findActiveIdsByRole(role);
  }

  @Override
  public boolean emailNotificationsEnabled(UUID userId) {
    return users.findById(userId).map(User::emailNotifications).orElse(true);
  }

  @Override
  public boolean isNotificationCategoryMuted(UUID userId, String category) {
    if (category == null || category.isBlank()) {
      return false;
    }
    return users.findById(userId).map(User::mutedNotificationCategories).stream()
        .flatMap(csv -> java.util.Arrays.stream(csv.split(",")))
        .map(String::trim)
        .anyMatch(category::equalsIgnoreCase);
  }
}
