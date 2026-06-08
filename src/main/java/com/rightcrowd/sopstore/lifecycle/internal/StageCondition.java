package com.rightcrowd.sopstore.lifecycle.internal;

import com.rightcrowd.sopstore.lifecycle.ChangeRequest;
import com.rightcrowd.sopstore.lifecycle.ChangeRequest.Classification;

/**
 * Predicate gating whether a workflow stage applies to a given change request. This is how a
 * workflow expresses conditional approval — e.g. "require a QA Director sign-off only for major
 * changes or changes that trigger re-training".
 */
public enum StageCondition {
  /** The stage always runs. */
  ALWAYS,
  /** The stage runs only when the change is classified MAJOR. */
  CLASSIFICATION_MAJOR,
  /** The stage runs only when the change has a training impact. */
  TRAINING_IMPACT,
  /** The stage runs when the change is MAJOR or has a training impact. */
  MAJOR_OR_TRAINING_IMPACT;

  /** Returns whether a stage carrying this condition applies to the given change request. */
  public boolean appliesTo(ChangeRequest cr) {
    return switch (this) {
      case ALWAYS -> true;
      case CLASSIFICATION_MAJOR -> cr.classification() == Classification.MAJOR;
      case TRAINING_IMPACT -> cr.trainingImpact();
      case MAJOR_OR_TRAINING_IMPACT ->
          cr.classification() == Classification.MAJOR || cr.trainingImpact();
    };
  }
}
