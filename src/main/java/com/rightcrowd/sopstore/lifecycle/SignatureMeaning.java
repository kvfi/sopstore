package com.rightcrowd.sopstore.lifecycle;

/** Required by 21 CFR Part 11 §11.50: the meaning of an electronic signature must be recorded. */
public enum SignatureMeaning {
  AUTHORED,
  REVIEWED,
  APPROVED,
  PUBLISHED,
  PERIODIC_REVIEWED,
  RETIRED
}
