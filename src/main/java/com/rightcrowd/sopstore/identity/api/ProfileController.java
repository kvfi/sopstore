package com.rightcrowd.sopstore.identity.api;

import com.rightcrowd.sopstore.identity.AuthenticatedUser;
import com.rightcrowd.sopstore.identity.internal.ProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-service profile endpoints for the signed-in user (name, password, notifications). */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Profile")
public class ProfileController {

  private final ProfileService profile;

  /** Creates the controller with the profile service. */
  public ProfileController(ProfileService profile) {
    this.profile = profile;
  }

  /** Request body carrying a new display name. */
  public record DisplayNameRequest(String displayName) {}

  /** Request body for a password change. */
  public record PasswordRequest(String currentPassword, String newPassword) {}

  /** Notification preferences as the SPA exchanges them. */
  public record NotificationPrefsDto(boolean emailNotifications, List<String> mutedCategories) {}

  /** Updates the signed-in user's display name. */
  @PutMapping("/display-name")
  public ResponseEntity<Void> setDisplayName(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestBody DisplayNameRequest req) {
    String name = req.displayName() == null ? "" : req.displayName().trim();
    if (name.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    profile.updateDisplayName(user.user().id(), name);
    return ResponseEntity.noContent().build();
  }

  /** Changes the signed-in user's password (403 if the current password is wrong). */
  @PutMapping("/password")
  public ResponseEntity<Void> changePassword(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestBody PasswordRequest req) {
    if (req.newPassword() == null || req.newPassword().length() < 8) {
      return ResponseEntity.badRequest().build();
    }
    String current = req.currentPassword() == null ? "" : req.currentPassword();
    boolean ok = profile.changePassword(user.user().id(), current, req.newPassword());
    return ok ? ResponseEntity.noContent().build() : ResponseEntity.status(403).build();
  }

  /** Returns the signed-in user's notification preferences. */
  @GetMapping("/notification-preferences")
  public NotificationPrefsDto notificationPreferences(
      @AuthenticationPrincipal AuthenticatedUser user) {
    ProfileService.NotificationPrefs p = profile.notificationPrefs(user.user().id());
    List<String> muted =
        p.mutedCategories().isBlank()
            ? List.of()
            : Arrays.stream(p.mutedCategories().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    return new NotificationPrefsDto(p.emailNotifications(), muted);
  }

  /** Replaces the signed-in user's notification preferences. */
  @PutMapping("/notification-preferences")
  public ResponseEntity<Void> setNotificationPreferences(
      @AuthenticationPrincipal AuthenticatedUser user, @RequestBody NotificationPrefsDto req) {
    String csv = req.mutedCategories() == null ? "" : String.join(",", req.mutedCategories());
    profile.updateNotificationPrefs(user.user().id(), req.emailNotifications(), csv);
    return ResponseEntity.noContent().build();
  }
}
