# ADR-0004: Hand-rolled lifecycle state machine

- Status: **Accepted**
- Date: 2026-05-28
- Relates to: ADR-0001, ADR-0006 (e-signatures, future)

## Context

Procedure lifecycle: `Draft → InReview → Approved → Effective → UnderReview →
Retired`. Spec offered Spring Statemachine or hand-rolled. Lifecycle is the
backbone of GxP compliance — illegal transitions must be impossible, not just
unlikely.

## Decision

Hand-rolled state machine using Java 25 sealed types:

```java
public sealed interface LifecycleState
    permits Draft, InReview, Approved, Effective, UnderReview, Retired {}

public sealed interface LifecycleEvent
    permits SubmitForReview, Approve, Publish, RequestPeriodicReview,
            Retire, RejectReview {}

public record Transition(LifecycleState from, LifecycleEvent event,
                         LifecycleState to, Set<Role> rolesAllowed,
                         boolean requiresSignature) {}
```

`LifecycleStateMachine#apply(state, event, principal)` is one exhaustive
switch on `(state, event)` — the compiler proves all `(state × event)` pairs
are handled.

## Rationale

- Compiler-enforced exhaustiveness (Java 25 pattern matching for switch on
  sealed types) is stronger than runtime config validation.
- E-signature requirements are per-transition data; expressing as records
  beats fluent builder config.
- One sealed-type file = one place to read the regulatory contract; easier
  for auditors than a config DSL.
- ArchUnit asserts no other code constructs `Transition` or mutates state
  directly; all state changes go through `LifecycleStateMachine.apply(...)`.

## Tests

- JUnit5 `@ParameterizedTest` enumerates every `(state × event)` pair; legal
  pairs assert the resulting state, illegal pairs assert
  `IllegalTransitionException`. This pins the spec.
- Mutation testing (Pitest) targets the state-machine package at 100%
  surviving-mutant tolerance = 0.

## Consequences

- Adding a new state or event is a deliberate sealed-type edit + recompile;
  every switch site forces a decision. Good.
- The state machine is intentionally simple and doesn't model parallel
  workflow stages — those live in the `lifecycle.workflow` package as
  separate `WorkflowInstance` aggregates that **gate** transitions but don't
  replace them.
