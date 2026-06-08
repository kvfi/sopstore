package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.lifecycle.Signature;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for accessing {@link Signature} entities. */
public interface SignatureRepository extends JpaRepository<Signature, UUID> {
  /** Returns the signatures for the given subject ordered by signing time ascending. */
  List<Signature> findBySubjectIdOrderBySignedAtAsc(UUID subjectId);
}
