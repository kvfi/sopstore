package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.identity.AuthenticatedUser;
import com.rightcrowd.sopstore.identity.User;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads application users for Spring Security authentication. */
@Service
public class AppUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  /** Creates the service backed by the given user repository. */
  public AppUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    User user =
        users
            .findByEmail(username.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    return new AuthenticatedUser(user);
  }
}
