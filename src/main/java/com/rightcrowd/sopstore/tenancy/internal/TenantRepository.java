package com.rightcrowd.sopstore.tenancy.internal;

import com.rightcrowd.sopstore.tenancy.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA repository for {@link Tenant} entities. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
  /** Finds a tenant by its unique slug. */
  Optional<Tenant> findBySlug(String slug);

  /**
   * Ids of all ACTIVE tenants, read via the {@code active_tenant_ids()} SECURITY DEFINER function
   * so it works with no tenant context and is not hidden by the {@code tenant} table's RLS policy.
   */
  @Query(value = "SELECT * FROM active_tenant_ids()", nativeQuery = true)
  List<UUID> activeTenantIds();
}
