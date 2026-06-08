package com.rightcrowd.sopstore.scripts.internal;

import com.rightcrowd.sopstore.tenancy.TenantContext;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Server-side client for the standalone script-service (ADR-0009). Keeps the shared service token
 * on the backend and injects the caller's tenant ({@link TenantContext}) and author so the service
 * stays tenant-scoped. Bodies are relayed as raw JSON strings (no parsing) — the proxy is a
 * pass-through, which also avoids coupling to a specific Jackson version.
 */
@Component
public class ScriptServiceClient {

  private final RestClient http;

  /** Builds the client against the configured base URL with the shared service token. */
  public ScriptServiceClient(
      @Value("${scriptservice.base-url}") String baseUrl,
      @Value("${scriptservice.service-token}") String token) {
    this.http =
        RestClient.builder().baseUrl(baseUrl).defaultHeader("X-Service-Token", token).build();
  }

  private static Consumer<HttpHeaders> ctx(@Nullable String author) {
    UUID tenant = TenantContext.current().value();
    return h -> {
      h.set("X-Tenant-Id", tenant.toString());
      if (author != null && !author.isBlank()) {
        h.set("X-Author", author);
      }
    };
  }

  /** The script-service always returns a JSON body for these calls; guard against an empty one. */
  private static String required(@Nullable String body) {
    if (body == null) {
      throw new IllegalStateException("empty response from script-service");
    }
    return body;
  }

  /** Lists the tenant's scripts. */
  public String list(@Nullable String author) {
    return required(
        http.get().uri("/api/v1/scripts").headers(ctx(author)).retrieve().body(String.class));
  }

  /** Fetches one script's metadata. */
  public String get(UUID id, @Nullable String author) {
    return required(
        http.get()
            .uri("/api/v1/scripts/{id}", id)
            .headers(ctx(author))
            .retrieve()
            .body(String.class));
  }

  /** Lists a script's versions, newest first. */
  public String versions(UUID id, @Nullable String author) {
    return required(
        http.get()
            .uri("/api/v1/scripts/{id}/versions", id)
            .headers(ctx(author))
            .retrieve()
            .body(String.class));
  }

  /** Fetches one version's content. */
  public String versionContent(UUID id, int no, @Nullable String author) {
    return required(
        http.get()
            .uri("/api/v1/scripts/{id}/versions/{no}", id, no)
            .headers(ctx(author))
            .retrieve()
            .body(String.class));
  }

  /** Creates a script (seeds version 1 from the supplied content). */
  public String create(String body, @Nullable String author) {
    return required(
        http.post()
            .uri("/api/v1/scripts")
            .headers(ctx(author))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class));
  }

  /** Updates a script's metadata (no new version). */
  public String updateMeta(UUID id, String body, @Nullable String author) {
    return required(
        http.put()
            .uri("/api/v1/scripts/{id}", id)
            .headers(ctx(author))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class));
  }

  /** Saves new content as the next immutable version. */
  public String saveContent(UUID id, String body, @Nullable String author) {
    return required(
        http.put()
            .uri("/api/v1/scripts/{id}/content", id)
            .headers(ctx(author))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(String.class));
  }

  /** Re-publishes an older version's content as a new version. */
  public String restore(UUID id, int no, @Nullable String author) {
    return required(
        http.post()
            .uri("/api/v1/scripts/{id}/restore/{no}", id, no)
            .headers(ctx(author))
            .retrieve()
            .body(String.class));
  }

  /** Deletes a script and its versions. */
  public void delete(UUID id, @Nullable String author) {
    http.delete()
        .uri("/api/v1/scripts/{id}", id)
        .headers(ctx(author))
        .retrieve()
        .toBodilessEntity();
  }
}
