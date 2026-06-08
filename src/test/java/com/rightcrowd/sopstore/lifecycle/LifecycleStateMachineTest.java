package com.rightcrowd.sopstore.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exhaustively pins the lifecycle contract. For every (state, event) pair, either a legal target
 * state or an {@link IllegalTransitionException} is asserted. This is the test the spec calls out
 * as required acceptance.
 *
 * <p>Acceptance criterion: "the state machine rejects illegal transitions and the test suite
 * enumerates every illegal transition."
 */
class LifecycleStateMachineTest {

  private static final UUID ACTOR = UUID.randomUUID();

  private final LifecycleStateMachine sm = new LifecycleStateMachine();

  static List<LifecycleState> allStates() {
    return List.of(
        new LifecycleState.Draft(),
        new LifecycleState.InReview(),
        new LifecycleState.Approved(),
        new LifecycleState.Effective(),
        new LifecycleState.UnderReview(),
        new LifecycleState.Retired());
  }

  static List<LifecycleEvent> allEvents() {
    return List.of(
        new LifecycleEvent.SubmitForReview(ACTOR),
        new LifecycleEvent.RejectReview(ACTOR, "no"),
        new LifecycleEvent.Approve(ACTOR, SignatureMeaning.APPROVED),
        new LifecycleEvent.Publish(
            ACTOR, SignatureMeaning.PUBLISHED, LocalDate.now(ZoneOffset.UTC)),
        new LifecycleEvent.StartPeriodicReview(ACTOR),
        new LifecycleEvent.CompletePeriodicReview(ACTOR, SignatureMeaning.PERIODIC_REVIEWED),
        new LifecycleEvent.Retire(ACTOR, SignatureMeaning.RETIRED, "EOL"));
  }

  /** Legal transitions per the spec; everything else must throw. */
  static Stream<Arguments> legalTransitions() {
    return Stream.of(
        Arguments.of("DRAFT", "SubmitForReview", "IN_REVIEW"),
        Arguments.of("IN_REVIEW", "Approve", "APPROVED"),
        Arguments.of("IN_REVIEW", "RejectReview", "DRAFT"),
        Arguments.of("APPROVED", "Publish", "EFFECTIVE"),
        Arguments.of("APPROVED", "Retire", "RETIRED"),
        Arguments.of("EFFECTIVE", "StartPeriodicReview", "UNDER_REVIEW"),
        Arguments.of("EFFECTIVE", "Retire", "RETIRED"),
        Arguments.of("UNDER_REVIEW", "CompletePeriodicReview", "EFFECTIVE"),
        Arguments.of("UNDER_REVIEW", "Retire", "RETIRED"));
  }

  @ParameterizedTest(name = "{0} --[{1}]-> {2}")
  @MethodSource("legalTransitions")
  void legal(String fromName, String eventName, String toName) {
    LifecycleState from = LifecycleState.parse(fromName);
    LifecycleEvent event = findEvent(eventName);
    assertThat(sm.apply(from, event).name()).isEqualTo(toName);
  }

  /**
   * Pin every (state × event) pair that is NOT in {@link #legalTransitions} as illegal. The "(s,e)
   * illegal iff not legal" generator means new legal arrows added to the spec must be added in BOTH
   * places, or the test fails.
   */
  @Test
  void everyOtherPairIsIllegal() {
    var legalKeys = legalTransitions().map(a -> a.get()[0] + "::" + a.get()[1]).toList();

    for (var state : allStates()) {
      for (var event : allEvents()) {
        String key = state.name() + "::" + event.getClass().getSimpleName();
        if (legalKeys.contains(key)) {
          continue;
        }

        assertThatThrownBy(() -> sm.apply(state, event))
            .as("expected illegal: %s", key)
            .isInstanceOf(IllegalTransitionException.class);
      }
    }
  }

  @Test
  void signatureRequirementMatchesPart11() {
    assertThat(sm.requiresSignature(new LifecycleEvent.SubmitForReview(ACTOR))).isFalse();
    assertThat(sm.requiresSignature(new LifecycleEvent.RejectReview(ACTOR, "x"))).isFalse();
    assertThat(sm.requiresSignature(new LifecycleEvent.StartPeriodicReview(ACTOR))).isFalse();

    assertThat(sm.requiresSignature(new LifecycleEvent.Approve(ACTOR, SignatureMeaning.APPROVED)))
        .isTrue();
    assertThat(
            sm.requiresSignature(
                new LifecycleEvent.Publish(
                    ACTOR, SignatureMeaning.PUBLISHED, LocalDate.now(ZoneOffset.UTC))))
        .isTrue();
    assertThat(
            sm.requiresSignature(
                new LifecycleEvent.CompletePeriodicReview(
                    ACTOR, SignatureMeaning.PERIODIC_REVIEWED)))
        .isTrue();
    assertThat(
            sm.requiresSignature(new LifecycleEvent.Retire(ACTOR, SignatureMeaning.RETIRED, "x")))
        .isTrue();
  }

  private static LifecycleEvent findEvent(String simpleName) {
    return allEvents().stream()
        .filter(e -> e.getClass().getSimpleName().equals(simpleName))
        .findFirst()
        .orElseThrow();
  }
}
