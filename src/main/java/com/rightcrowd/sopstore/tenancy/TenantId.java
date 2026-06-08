package com.rightcrowd.sopstore.tenancy;

import java.io.Serializable;
import java.util.UUID;

/** Identifies a tenant by its unique value. */
public record TenantId(UUID value) implements Serializable {
  /** Creates a tenant id, requiring the value to be non-null. */
  public TenantId {
    java.util.Objects.requireNonNull(value, "tenant id");
  }

  /** Returns a tenant id wrapping the given UUID. */
  public static TenantId of(UUID id) {
    return new TenantId(id);
  }

  /** Returns a tenant id parsed from the given string. */
  public static TenantId of(String id) {
    return new TenantId(UUID.fromString(id));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
