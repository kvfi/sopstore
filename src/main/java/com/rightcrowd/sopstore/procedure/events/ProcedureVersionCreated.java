package com.rightcrowd.sopstore.procedure.events;

import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/** Event published when a new procedure version is created. */
@Externalized("procedure-version-created::tenant.{tenantId}")
public record ProcedureVersionCreated(UUID procedureId, UUID versionId, UUID tenantId) {}
