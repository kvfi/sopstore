package com.rightcrowd.sopstore.identity.internal;

import com.rightcrowd.sopstore.identity.Role;
import com.rightcrowd.sopstore.identity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for accessing and managing {@link User} entities. */
public interface UserRepository extends JpaRepository<User, UUID> {
  /** Finds a user by their email address. */
  Optional<User> findByEmail(String email);

  /** Returns ids of active users holding the given role (tenant filter applied by Hibernate). */
  @Query(
      "select u.id from User u join u.roles r where r = :role and u.status = "
          + "com.rightcrowd.sopstore.identity.User$Status.ACTIVE")
  List<UUID> findActiveIdsByRole(@Param("role") Role role);
}
