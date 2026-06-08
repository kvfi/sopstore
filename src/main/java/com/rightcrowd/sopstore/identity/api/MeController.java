package com.rightcrowd.sopstore.identity.api;

import com.rightcrowd.sopstore.identity.AuthenticatedUser;
import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.identity.UserDirectory;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the authenticated principal to the SPA. */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

  private final UserDirectory users;

  /** Creates the controller with the user directory (for live profile fields). */
  public MeController(UserDirectory users) {
    this.users = users;
  }

  /** The current user as the SPA needs it. */
  public record Me(UUID id, String email, String displayName, List<String> roles) {}

  /** Returns the signed-in user and their roles. */
  @GetMapping
  public Me me(@AuthenticationPrincipal AuthenticatedUser user) {
    List<String> roles = user.user().roles().stream().map(Role::name).sorted().toList();
    // Resolve the display name fresh so a profile edit shows without re-login (the session
    // principal is a snapshot taken at login).
    String displayName = users.displayNameById(user.user().id()).orElse(user.user().displayName());
    return new Me(user.user().id(), user.user().email(), displayName, roles);
  }
}
