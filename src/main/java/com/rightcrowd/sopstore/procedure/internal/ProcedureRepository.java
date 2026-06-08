package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.Procedure;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repository for accessing and querying {@link Procedure} entities. */
public interface ProcedureRepository extends JpaRepository<Procedure, UUID> {
  /** Returns a page of procedures matching the given state. */
  Page<Procedure> findByState(String state, Pageable p);

  /** Searches for procedures whose title contains the given query, case-insensitively. */
  @Query("select p from Procedure p where lower(p.title) like lower(concat('%', :q, '%'))")
  List<Procedure> searchByTitle(String q);

  /** Whether a procedure already carries the given document number (within the active tenant). */
  boolean existsByDocumentNumber(String documentNumber);
}
