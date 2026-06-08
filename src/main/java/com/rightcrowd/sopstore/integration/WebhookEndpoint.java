package com.rightcrowd.sopstore.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/** Persistent entity describing a registered webhook endpoint for a tenant. */
@Entity
@Table(name = "webhook_endpoint")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class WebhookEndpoint {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "url", nullable = false)
  private String url;

  @Column(name = "secret", nullable = false)
  private String secret;

  @Column(name = "event_filter", nullable = false)
  private String eventFilter = "*";

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Constructs an empty endpoint for use by the persistence provider. */
  protected WebhookEndpoint() {}

  /** Constructs a webhook endpoint with the given identifiers, target URL, secret, and filter. */
  public WebhookEndpoint(UUID id, UUID tenantId, String url, String secret, String filter) {
    this.id = id;
    this.tenantId = tenantId;
    this.url = url;
    this.secret = secret;
    this.eventFilter = filter;
  }

  /** Returns the endpoint identifier. */
  public UUID id() {
    return id;
  }

  /** Returns the target URL of the endpoint. */
  public String url() {
    return url;
  }

  /** Returns the shared secret used to sign deliveries. */
  public String secret() {
    return secret;
  }

  /** Returns the event filter expression for this endpoint. */
  public String eventFilter() {
    return eventFilter;
  }

  /** Returns whether the endpoint is currently active. */
  public boolean active() {
    return active;
  }
}
