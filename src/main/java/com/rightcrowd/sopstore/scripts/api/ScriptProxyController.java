package com.rightcrowd.sopstore.scripts.api;

import com.rightcrowd.sopstore.scripts.internal.ScriptServiceClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Authenticated BFF proxy from the SPA to the standalone script-service. Forwards the session
 * user's tenant + identity (via {@link ScriptServiceClient}) so the service stays tenant-scoped,
 * relays JSON bodies verbatim (as strings), and surfaces the service's status codes to the browser.
 */
@RestController
@RequestMapping("/api/v1/scripts")
@Tag(name = "Scripts")
public class ScriptProxyController {

  private final ScriptServiceClient client;

  /** Creates the proxy with the script-service client. */
  public ScriptProxyController(ScriptServiceClient client) {
    this.client = client;
  }

  private static ResponseEntity<String> json(String body) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
  }

  /** Lists the tenant's scripts. */
  @GetMapping
  public ResponseEntity<String> list(Principal principal) {
    return json(client.list(author(principal)));
  }

  /** Creates a script. */
  @PostMapping
  public ResponseEntity<String> create(@RequestBody String body, Principal principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(client.create(body, author(principal)));
  }

  /** Fetches one script's metadata. */
  @GetMapping("/{id}")
  public ResponseEntity<String> get(@PathVariable UUID id, Principal principal) {
    return json(client.get(id, author(principal)));
  }

  /** Updates a script's metadata. */
  @PutMapping("/{id}")
  public ResponseEntity<String> updateMeta(
      @PathVariable UUID id, @RequestBody String body, Principal principal) {
    return json(client.updateMeta(id, body, author(principal)));
  }

  /** Saves new content as the next version. */
  @PutMapping("/{id}/content")
  public ResponseEntity<String> saveContent(
      @PathVariable UUID id, @RequestBody String body, Principal principal) {
    return json(client.saveContent(id, body, author(principal)));
  }

  /** Restores an older version as a new one. */
  @PostMapping("/{id}/restore/{no}")
  public ResponseEntity<String> restore(
      @PathVariable UUID id, @PathVariable int no, Principal principal) {
    return json(client.restore(id, no, author(principal)));
  }

  /** Lists a script's versions. */
  @GetMapping("/{id}/versions")
  public ResponseEntity<String> versions(@PathVariable UUID id, Principal principal) {
    return json(client.versions(id, author(principal)));
  }

  /** Fetches one version's content. */
  @GetMapping("/{id}/versions/{no}")
  public ResponseEntity<String> versionContent(
      @PathVariable UUID id, @PathVariable int no, Principal principal) {
    return json(client.versionContent(id, no, author(principal)));
  }

  /** Deletes a script. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
    client.delete(id, author(principal));
    return ResponseEntity.noContent().build();
  }

  /** Relay the script-service's HTTP status + body to the browser instead of a generic 500. */
  @ExceptionHandler(RestClientResponseException.class)
  public ResponseEntity<String> relay(RestClientResponseException e) {
    return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
  }

  /** The script-service is unreachable/failed at the transport level — report a clear 502. */
  @ExceptionHandler(RestClientException.class)
  public ResponseEntity<String> upstream(RestClientException e) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body("script-service unavailable: " + e.getMessage());
  }

  private static @Nullable String author(@Nullable Principal principal) {
    return principal == null ? null : principal.getName();
  }
}
