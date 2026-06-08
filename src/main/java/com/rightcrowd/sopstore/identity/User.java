package com.rightcrowd.sopstore.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/** Represents an application user belonging to a tenant. */
@Entity
@Table(
    name = "app_user",
    indexes = {
      @Index(name = "idx_user_email", columnList = "tenant_id,email", unique = true),
      @Index(name = "idx_user_emp", columnList = "tenant_id,employee_id")
    })
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class User {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "email", nullable = false)
  private String email;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "employee_id")
  private @Nullable String employeeId;

  @Column(name = "password_hash")
  private @Nullable String passwordHash;

  @Column(name = "locale", nullable = false)
  private String locale = Locale.ENGLISH.toLanguageTag();

  @Column(name = "timezone", nullable = false)
  private String timezone = "UTC";

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private Status status = Status.ACTIVE;

  @Column(name = "mfa_required", nullable = false)
  private boolean mfaRequired = false;

  @Column(name = "email_notifications", nullable = false)
  private boolean emailNotifications = true;

  /** Comma-separated notification category keys the user has muted (e.g. {@code workflow}). */
  @Column(name = "muted_notification_categories", nullable = false)
  private String mutedNotificationCategories = "";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "deleted_at")
  private @Nullable Instant deletedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role")
  @Enumerated(EnumType.STRING)
  private Set<Role> roles = new HashSet<>();

  /** Creates an empty user for use by the persistence provider. */
  protected User() {}

  /** Creates a user with the given identity and credential details. */
  public User(UUID id, UUID tenantId, String email, String displayName, String passwordHash) {
    this.id = id;
    this.tenantId = tenantId;
    this.email = email;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
  }

  /** Returns the user identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the tenant identifier. */
  public UUID tenantId() {
    return tenantId;
  }

  /** Returns the user email address. */
  public String email() {
    return email;
  }

  /** Returns the user display name. */
  public String displayName() {
    return displayName;
  }

  /** Whether the user receives email notifications. */
  public boolean emailNotifications() {
    return emailNotifications;
  }

  /** Returns the raw comma-separated list of muted notification category keys. */
  public String mutedNotificationCategories() {
    return mutedNotificationCategories;
  }

  /** Updates the display name. */
  public void setDisplayName(String name) {
    this.displayName = name;
  }

  /** Replaces the stored (already-hashed) password credential. */
  public void setPasswordHash(@Nullable String hash) {
    this.passwordHash = hash;
  }

  /** Enables or disables email notifications for the user. */
  public void setEmailNotifications(boolean enabled) {
    this.emailNotifications = enabled;
  }

  /** Sets the comma-separated list of muted notification category keys. */
  public void setMutedNotificationCategories(String csv) {
    this.mutedNotificationCategories = csv == null ? "" : csv;
  }

  /** Returns the stored password hash, if any. */
  public @Nullable String passwordHash() {
    return passwordHash;
  }

  /** Returns a copy of the roles assigned to the user. */
  public Set<Role> roles() {
    return Set.copyOf(roles);
  }

  /** Returns whether multi-factor authentication is required. */
  public boolean mfaRequired() {
    return mfaRequired;
  }

  /** Returns the current user status. */
  public Status status() {
    return status;
  }

  /** Adds the given role to the user. */
  public void addRole(Role r) {
    roles.add(r);
  }

  /** Marks the user as requiring multi-factor authentication. */
  public void enforceMfa() {
    this.mfaRequired = true;
  }

  /** Marks the user as deleted and records the deletion time. */
  public void softDelete() {
    this.status = Status.DELETED;
    this.deletedAt = Instant.now();
  }

  /** Enumerates the possible lifecycle states of a user. */
  public enum Status {
    ACTIVE,
    INVITED,
    SUSPENDED,
    DELETED
  }
}
