package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.identity.PasswordVerifier;
import com.rightcrowd.sopstore.identity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Verifies passwords against stored Argon2id hashes. */
@Service
class PasswordVerifierImpl implements PasswordVerifier {

  private final UserRepository users;
  private final PasswordEncoder encoder;

  PasswordVerifierImpl(UserRepository users, PasswordEncoder encoder) {
    this.users = users;
    this.encoder = encoder;
  }

  @Override
  public boolean matches(UUID userId, String rawPassword) {
    Optional<User> found = users.findById(userId);
    if (found.isEmpty()) {
      return false;
    }
    String hash = found.get().passwordHash();
    return hash != null && !hash.isBlank() && encoder.matches(rawPassword, hash);
  }
}
