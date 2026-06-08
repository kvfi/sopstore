package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.Attachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for attachment metadata. */
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
  /** Returns the attachments of a procedure version, newest first. */
  List<Attachment> findByProcedureVersionIdOrderByUploadedAtDesc(UUID procedureVersionId);
}
