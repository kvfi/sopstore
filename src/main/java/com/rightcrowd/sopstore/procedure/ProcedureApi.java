package com.rightcrowd.sopstore.procedure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cross-module access to procedures. Sibling modules depend on this exposed API instead of the
 * internal repository, keeping the procedure module's persistence encapsulated (Spring Modulith
 * forbids cross-module access to {@code internal} types).
 */
public interface ProcedureApi {

  /** Returns the procedure with the given id, if present. */
  Optional<Procedure> findById(UUID id);

  /** Returns all procedures currently in the given lifecycle state. */
  List<Procedure> findByState(String state);

  /** Persists the given procedure and returns the managed instance. */
  Procedure save(Procedure procedure);

  /** Returns the number of procedures visible under the active tenant filter. */
  long count();

  /**
   * Returns a canonical, deterministic serialization of the procedure's current version. Used as
   * the exact payload an e-signature is cryptographically bound to.
   */
  byte[] currentVersionCanonical(UUID procedureId);

  /** Returns the steps of the procedure's current version, in execution order. */
  List<Step> currentVersionSteps(UUID procedureId);

  /** Returns the step with the given id, if present. */
  Optional<Step> step(UUID stepId);
}
