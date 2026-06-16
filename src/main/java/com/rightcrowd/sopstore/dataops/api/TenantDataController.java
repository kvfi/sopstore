package com.rightcrowd.sopstore.dataops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rightcrowd.sopstore.dataops.internal.TenantDataService;
import com.rightcrowd.sopstore.identity.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin-only per-tenant data export/import. Export downloads an RLS-scoped JSON bundle of the
 * current tenant's data; import replaces that data from a previously exported bundle (destructive,
 * one transaction). The audit trail and identity (users/roles) are never included.
 */
@RestController
@RequestMapping("/api/v1/tenant-data")
@PreAuthorize("hasRole('TENANT_ADMIN')")
@Tag(name = "Tenant data")
public class TenantDataController {

  // Local Jackson 2 mapper (no Jackson 2 ObjectMapper bean under Boot 4 — same as elsewhere).
  private static final ObjectMapper JSON = new ObjectMapper();

  private final TenantDataService service;

  /** Creates the controller with the data service. */
  public TenantDataController(TenantDataService service) {
    this.service = service;
  }

  /** Downloads the current tenant's data as a JSON bundle. */
  @GetMapping("/export")
  public ResponseEntity<String> export() {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tenant-data.json\"")
        .contentType(MediaType.APPLICATION_JSON)
        .body(service.export());
  }

  /** Replaces the current tenant's data from an uploaded bundle. Returns a small summary. */
  @PostMapping("/import")
  public ResponseEntity<TenantDataService.ImportResult> importData(
      @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthenticatedUser user)
      throws IOException {
    JsonNode bundle = JSON.readTree(file.getInputStream());
    return ResponseEntity.ok(service.importBundle(bundle, user.user().id()));
  }
}
