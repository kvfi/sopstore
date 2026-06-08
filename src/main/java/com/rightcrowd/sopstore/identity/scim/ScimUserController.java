package com.rightcrowd.sopstore.identity.scim;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rightcrowd.sopstore.identity.User;
import com.rightcrowd.sopstore.identity.internal.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound SCIM 2.0 endpoint for IdP-driven user provisioning. Conformance with RFC 7643/7644 is a
 * Phase 6 deliverable; this controller implements the URL shape and a minimal happy path (list,
 * get, create, delete). Patch, filter expressions, and the bulk endpoint return 501.
 */
@RestController
@RequestMapping(value = "/scim/v2/Users", produces = "application/scim+json")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimUserController {

  private final UserRepository users;

  /** Creates the controller backed by the given user repository. */
  public ScimUserController(UserRepository users) {
    this.users = users;
  }

  /** Returns a paginated SCIM ListResponse of users. */
  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "0") int startIndex,
      @RequestParam(defaultValue = "50") int count) {
    List<User> page = users.findAll().stream().skip(startIndex).limit(count).toList();
    return Map.of(
        "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse"),
        "totalResults", users.count(),
        "startIndex", startIndex,
        "itemsPerPage", page.size(),
        "Resources", page.stream().map(ScimUserController::toScim).toList());
  }

  /** Returns the SCIM representation of a single user, or 404 if not found. */
  @GetMapping("/{id}")
  public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
    return users
        .findById(java.util.UUID.fromString(id))
        .map(u -> ResponseEntity.ok(toScim(u)))
        .orElseGet(
            () ->
                ResponseEntity.status(404)
                    .contentType(MediaType.valueOf("application/scim+json"))
                    .body(error(404, "User not found")));
  }

  /** Creates a user; currently returns 501 as creation is not yet implemented. */
  @PostMapping
  public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    // Phase-6 deliverable: full create with required attributes, role mapping, audit.
    return ResponseEntity.status(501).body(error(501, "SCIM create not yet implemented"));
  }

  /** Patches a user; currently returns 501 as patching is not yet implemented. */
  @PatchMapping("/{id}")
  public ResponseEntity<Map<String, Object>> patch(
      @PathVariable String id, @RequestBody Map<String, Object> body) {
    return ResponseEntity.status(501).body(error(501, "SCIM patch not yet implemented"));
  }

  /** Soft-deletes the user with the given id and returns 204. */
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable String id) {
    users
        .findById(java.util.UUID.fromString(id))
        .ifPresent(
            u -> {
              u.softDelete();
              users.save(u);
            });
    return ResponseEntity.noContent().build();
  }

  private static Map<String, Object> toScim(User u) {
    return Map.of(
        "schemas", List.of("urn:ietf:params:scim:schemas:core:2.0:User"),
        "id", u.id().toString(),
        "userName", u.email(),
        "displayName", u.displayName(),
        "active", u.status() == User.Status.ACTIVE,
        "emails", List.of(Map.of("value", u.email(), "primary", true)));
  }

  private static Map<String, Object> error(int status, String detail) {
    return Map.of(
        "schemas", List.of("urn:ietf:params:scim:api:messages:2.0:Error"),
        "status", String.valueOf(status),
        "detail", detail);
  }
}
