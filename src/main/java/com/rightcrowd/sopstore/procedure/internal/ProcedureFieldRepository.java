package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.ProcedureField;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped CRUD for the flat custom procedure-form fields (RLS-filtered). */
public interface ProcedureFieldRepository extends JpaRepository<ProcedureField, UUID> {

  /** All custom-form fields, in display order. */
  List<ProcedureField> findAllByOrderBySortOrderAsc();
}
