package com.rightcrowd.sopstore.lifecycle;

import java.util.UUID;

/**
 * Events that drive {@link LifecycleState} transitions. Carry the actor and, where required, the
 * e-signature meaning binding the transition to a person.
 */
public sealed interface LifecycleEvent
    permits LifecycleEvent.SubmitForReview,
        LifecycleEvent.RejectReview,
        LifecycleEvent.Approve,
        LifecycleEvent.Publish,
        LifecycleEvent.StartPeriodicReview,
        LifecycleEvent.CompletePeriodicReview,
        LifecycleEvent.Retire {

  /** Returns the identifier of the actor that triggered this event. */
  UUID actor();

  /** Event requesting that a draft be submitted for review. */
  record SubmitForReview(UUID actor) implements LifecycleEvent {}

  /** Event rejecting a review, carrying the reason for rejection. */
  record RejectReview(UUID actor, String reason) implements LifecycleEvent {}

  /** Event approving a reviewed item, bound to a signature meaning. */
  record Approve(UUID actor, SignatureMeaning meaning) implements LifecycleEvent {}

  /** Event publishing an approved item with its effective date and signature meaning. */
  record Publish(UUID actor, SignatureMeaning meaning, java.time.LocalDate effectiveDate)
      implements LifecycleEvent {}

  /** Event starting a periodic review of a published item. */
  record StartPeriodicReview(UUID actor) implements LifecycleEvent {}

  /** Event completing a periodic review, bound to a signature meaning. */
  record CompletePeriodicReview(UUID actor, SignatureMeaning meaning) implements LifecycleEvent {}

  /** Event retiring an item, carrying the reason and signature meaning. */
  record Retire(UUID actor, SignatureMeaning meaning, String reason) implements LifecycleEvent {}
}
