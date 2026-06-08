package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.DocTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for tenant {@link DocTemplate} export themes. */
public interface DocTemplateRepository extends JpaRepository<DocTemplate, UUID> {

  /** Lists the tenant's templates alphabetically by name. */
  List<DocTemplate> findAllByOrderByNameAsc();
}
