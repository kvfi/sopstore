package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureApi;
import com.rightcrowd.sopstore.procedure.Step;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Default {@link ProcedureApi} delegating to the internal {@link ProcedureRepository}. */
@Service
class ProcedureApiImpl implements ProcedureApi {

  private final ProcedureRepository repository;
  private final ProcedureService service;
  private final StepRepository steps;

  ProcedureApiImpl(ProcedureRepository repository, ProcedureService service, StepRepository steps) {
    this.repository = repository;
    this.service = service;
    this.steps = steps;
  }

  @Override
  public Optional<Procedure> findById(UUID id) {
    return repository.findById(id);
  }

  @Override
  public List<Procedure> findByState(String state) {
    return repository.findByState(state, Pageable.unpaged()).getContent();
  }

  @Override
  public Procedure save(Procedure procedure) {
    return repository.save(procedure);
  }

  @Override
  public long count() {
    return repository.count();
  }

  @Override
  public byte[] currentVersionCanonical(UUID procedureId) {
    return service.currentVersionCanonical(procedureId);
  }

  @Override
  public List<Step> currentVersionSteps(UUID procedureId) {
    var found = repository.findById(procedureId);
    if (found.isEmpty()) {
      return List.of();
    }
    UUID versionId = found.get().currentVersionId();
    return versionId == null
        ? List.of()
        : steps.findByProcedureVersionIdOrderByOrderIndexAsc(versionId);
  }

  @Override
  public Optional<Step> step(UUID stepId) {
    return steps.findById(stepId);
  }
}
