package com.rightcrowd.sopstore.lifecycle;

import static com.rightcrowd.sopstore.lifecycle.LifecycleEvent.Approve;
import static com.rightcrowd.sopstore.lifecycle.LifecycleEvent.CompletePeriodicReview;
import static com.rightcrowd.sopstore.lifecycle.LifecycleEvent.Publish;
import static com.rightcrowd.sopstore.lifecycle.LifecycleEvent.RejectReview;
import static com.rightcrowd.sopstore.lifecycle.LifecycleEvent.Retire;
import static com.rightcrowd.sopstore.lifecycle.LifecycleEvent.StartPeriodicReview;
import static com.rightcrowd.sopstore.lifecycle.LifecycleEvent.SubmitForReview;
import static com.rightcrowd.sopstore.lifecycle.LifecycleState.Approved;
import static com.rightcrowd.sopstore.lifecycle.LifecycleState.Draft;
import static com.rightcrowd.sopstore.lifecycle.LifecycleState.Effective;
import static com.rightcrowd.sopstore.lifecycle.LifecycleState.InReview;
import static com.rightcrowd.sopstore.lifecycle.LifecycleState.Retired;
import static com.rightcrowd.sopstore.lifecycle.LifecycleState.UnderReview;

import org.springframework.stereotype.Component;

/**
 * The lifecycle backbone for procedure versions. One method: {@link #apply(LifecycleState,
 * LifecycleEvent)}. The switch is exhaustive by virtue of sealed types — adding a new state or
 * event is a compile error until every case is handled here.
 *
 * <p>Side effects (audit, signature persistence) happen in {@code LifecycleService}; this class is
 * a pure function.
 */
// Not final: Spring Modulith observability CGLIB-proxies this exposed module bean, and CGLIB
// cannot subclass a final class.
@Component
public class LifecycleStateMachine {

  /** Returns the next lifecycle state for the given state and event, or throws if illegal. */
  public LifecycleState apply(LifecycleState from, LifecycleEvent event) {
    return switch (from) {
      case Draft _ -> fromDraft(from, event);
      case InReview _ -> fromInReview(from, event);
      case Approved _ -> fromApproved(from, event);
      case Effective _ -> fromEffective(from, event);
      case UnderReview _ -> fromUnderReview(from, event);
      case Retired _ -> throw illegal(from, event); // terminal
    };
  }

  private LifecycleState fromDraft(LifecycleState from, LifecycleEvent event) {
    return switch (event) {
      case SubmitForReview _ -> new InReview();
      case Approve _,
          Publish _,
          RejectReview _,
          StartPeriodicReview _,
          CompletePeriodicReview _,
          Retire _ ->
          throw illegal(from, event);
    };
  }

  private LifecycleState fromInReview(LifecycleState from, LifecycleEvent event) {
    return switch (event) {
      case Approve _ -> new Approved();
      case RejectReview _ -> new Draft();
      case SubmitForReview _,
          Publish _,
          StartPeriodicReview _,
          CompletePeriodicReview _,
          Retire _ ->
          throw illegal(from, event);
    };
  }

  private LifecycleState fromApproved(LifecycleState from, LifecycleEvent event) {
    return switch (event) {
      case Publish _ -> new Effective();
      case Retire _ -> new Retired();
      case SubmitForReview _,
          Approve _,
          RejectReview _,
          StartPeriodicReview _,
          CompletePeriodicReview _ ->
          throw illegal(from, event);
    };
  }

  private LifecycleState fromEffective(LifecycleState from, LifecycleEvent event) {
    return switch (event) {
      case StartPeriodicReview _ -> new UnderReview();
      case Retire _ -> new Retired();
      case SubmitForReview _, Approve _, Publish _, RejectReview _, CompletePeriodicReview _ ->
          throw illegal(from, event);
    };
  }

  private LifecycleState fromUnderReview(LifecycleState from, LifecycleEvent event) {
    return switch (event) {
      case CompletePeriodicReview _ -> new Effective();
      case Retire _ -> new Retired();
      case SubmitForReview _, Approve _, Publish _, RejectReview _, StartPeriodicReview _ ->
          throw illegal(from, event);
    };
  }

  /** Whether the (state, event) pair requires re-authentication for an e-signature. */
  public boolean requiresSignature(LifecycleEvent event) {
    return switch (event) {
      case Approve _, Publish _, CompletePeriodicReview _, Retire _ -> true;
      case SubmitForReview _, RejectReview _, StartPeriodicReview _ -> false;
    };
  }

  private IllegalTransitionException illegal(LifecycleState from, LifecycleEvent event) {
    return new IllegalTransitionException(from, event);
  }
}
