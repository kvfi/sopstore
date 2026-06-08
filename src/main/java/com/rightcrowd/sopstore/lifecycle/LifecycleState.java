package com.rightcrowd.sopstore.lifecycle;

/**
 * Lifecycle states for a procedure. Sealed so the state machine's switch is exhaustively checked by
 * the compiler.
 *
 * <pre>
 *  Draft ──submit─▶ InReview ──approve─▶ Approved ──publish─▶ Effective
 *    ▲                  │                                          │
 *    │                reject                                     periodic
 *    └──────────────────┘                                          │
 *                                                                  ▼
 *                                                            UnderReview
 *                                                                  │
 *                                                                retire
 *                                                                  ▼
 *                                                              Retired
 * </pre>
 */
public sealed interface LifecycleState
    permits LifecycleState.Draft,
        LifecycleState.InReview,
        LifecycleState.Approved,
        LifecycleState.Effective,
        LifecycleState.UnderReview,
        LifecycleState.Retired {

  /** Returns the canonical name of this lifecycle state. */
  String name();

  /** The initial state of a procedure before it is submitted for review. */
  record Draft() implements LifecycleState {
    @Override
    public String name() {
      return "DRAFT";
    }
  }

  /** The state of a procedure that has been submitted and is awaiting approval. */
  record InReview() implements LifecycleState {
    @Override
    public String name() {
      return "IN_REVIEW";
    }
  }

  /** The state of a procedure that has been approved and may be published. */
  record Approved() implements LifecycleState {
    @Override
    public String name() {
      return "APPROVED";
    }
  }

  /** The state of a published procedure that is currently in effect. */
  record Effective() implements LifecycleState {
    @Override
    public String name() {
      return "EFFECTIVE";
    }
  }

  /** The state of an effective procedure undergoing periodic review. */
  record UnderReview() implements LifecycleState {
    @Override
    public String name() {
      return "UNDER_REVIEW";
    }
  }

  /** The terminal state of a procedure that has been retired. */
  record Retired() implements LifecycleState {
    @Override
    public String name() {
      return "RETIRED";
    }
  }

  /** Parses the given canonical name into its corresponding lifecycle state. */
  static LifecycleState parse(String s) {
    return switch (s) {
      case "DRAFT" -> new Draft();
      case "IN_REVIEW" -> new InReview();
      case "APPROVED" -> new Approved();
      case "EFFECTIVE" -> new Effective();
      case "UNDER_REVIEW" -> new UnderReview();
      case "RETIRED" -> new Retired();
      default -> throw new IllegalArgumentException("Unknown state: " + s);
    };
  }
}
