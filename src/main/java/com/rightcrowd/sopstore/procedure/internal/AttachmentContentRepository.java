package com.rightcrowd.sopstore.procedure.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for attachment file bytes. */
public interface AttachmentContentRepository extends JpaRepository<AttachmentContent, UUID> {}
