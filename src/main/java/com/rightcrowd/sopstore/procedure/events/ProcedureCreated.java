package com.rightcrowd.sopstore.procedure.events;

import java.util.UUID;
import org.springframework.modulith.events.Externalized;

/** Emitted when a new Procedure aggregate is created. */
@Externalized("procedure-created::tenant.{tenantId}")
public record ProcedureCreated(UUID procedureId, UUID tenantId, UUID createdBy) {}
