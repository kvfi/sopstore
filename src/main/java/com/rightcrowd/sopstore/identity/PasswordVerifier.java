package com.rightcrowd.sopstore.identity;

import java.util.UUID;

/**
 * Verifies a user's password. Exposed by the identity module so sibling modules can require a fresh
 * credential challenge (e.g. the lifecycle module's 21 CFR Part 11 re-authentication on signature)
 * without reaching into identity's credential storage.
 */
public interface PasswordVerifier {

  /** Returns {@code true} if {@code rawPassword} matches the stored credential for the user. */
  boolean matches(UUID userId, String rawPassword);
}
