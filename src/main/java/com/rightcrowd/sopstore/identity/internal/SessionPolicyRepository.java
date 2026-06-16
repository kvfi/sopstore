package com.rightcrowd.sopstore.identity.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persists the single per-tenant {@link SessionPolicy} row (RLS scopes reads to the tenant). */
public interface SessionPolicyRepository extends JpaRepository<SessionPolicy, UUID> {

  /** Returns the current tenant's policy row, if one has been saved. */
  Optional<SessionPolicy> findFirstByOrderByCreatedAtAsc();
}
