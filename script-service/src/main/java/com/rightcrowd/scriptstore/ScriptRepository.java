package com.rightcrowd.scriptstore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped persistence for {@link Script} metadata. */
public interface ScriptRepository extends JpaRepository<Script, UUID> {

  List<Script> findByTenantIdOrderByNameAsc(UUID tenantId);

  Optional<Script> findByIdAndTenantId(UUID id, UUID tenantId);
}
