package com.rightcrowd.sopstore.lifecycle;

/** Thrown when a lifecycle event is not a valid transition from the current state. */
public class IllegalTransitionException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final transient LifecycleState from;
  private final transient LifecycleEvent event;

  /** Creates an exception for the given originating state and triggering event. */
  public IllegalTransitionException(LifecycleState from, LifecycleEvent event) {
    super(
        "Illegal transition: "
            + from.name()
            + " --["
            + event.getClass().getSimpleName()
            + "]-> ???");
    this.from = from;
    this.event = event;
  }

  /** Returns the state the illegal transition originated from. */
  public LifecycleState from() {
    return from;
  }

  /** Returns the event that triggered the illegal transition. */
  public LifecycleEvent event() {
    return event;
  }
}
