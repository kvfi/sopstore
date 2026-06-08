package com.rightcrowd.sopstore.tenancy;

import org.jspecify.annotations.Nullable;

/**
 * Per-request tenant scope. Set by {@link TenantResolverFilter} from the authenticated principal;
 * read by Hibernate ({@code HibernateTenantResolver}) and the RLS statement inspector.
 *
 * <p>Backed by a thread-local for the platform thread and inherited into virtual threads spawned
 * within the request. Cleared in a finally-block by the filter.
 */
public final class TenantContext {
  private static final ThreadLocal<TenantId> CURRENT = new InheritableThreadLocal<>();

  private TenantContext() {}

  /** Sets the tenant for the current thread scope. */
  public static void set(TenantId id) {
    CURRENT.set(id);
  }

  /** Clears the tenant from the current thread scope. */
  public static void clear() {
    CURRENT.remove();
  }

  /** Returns the current tenant, or {@code null} if none is set. */
  public static @Nullable TenantId currentOrNull() {
    return CURRENT.get();
  }

  /** Returns the current tenant, throwing if none is set. */
  public static TenantId current() {
    TenantId t = CURRENT.get();
    if (t == null) {
      throw new IllegalStateException("No tenant in context");
    }
    return t;
  }
}
