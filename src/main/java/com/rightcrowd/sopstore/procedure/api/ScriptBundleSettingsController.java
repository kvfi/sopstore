package com.rightcrowd.sopstore.procedure.api;

import com.rightcrowd.sopstore.procedure.internal.ScriptBundleSettings;
import com.rightcrowd.sopstore.procedure.internal.ScriptBundleSettingsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin configuration for how a procedure's RUN_SCRIPT scripts are named and linked in the exported
 * SOP bundle and PDF. A single settings object per tenant: reads are open to any authenticated user
 * (so the export pipeline and previews can resolve it); the write requires {@code TENANT_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/script-bundle-settings")
@Tag(name = "Script bundle settings")
public class ScriptBundleSettingsController {

  private final ScriptBundleSettingsService service;

  /** Creates the controller with its service. */
  public ScriptBundleSettingsController(ScriptBundleSettingsService service) {
    this.service = service;
  }

  /** API representation of the tenant's bundle/naming settings. */
  public record Dto(
      String bundleFolder, String filenamePattern, String bundleName, String linkBaseUrl) {
    static Dto from(ScriptBundleSettings s) {
      return new Dto(s.bundleFolder(), s.filenamePattern(), s.bundleName(), s.linkBaseUrl());
    }
  }

  /** Returns the tenant's settings, falling back to the application defaults when unset. */
  @GetMapping
  public Dto get() {
    ScriptBundleSettings s = service.current().orElseGet(ScriptBundleSettings::defaults);
    return Dto.from(s);
  }

  /** Upserts the tenant's settings; blank fields reset to the application defaults. */
  @PutMapping
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public Dto save(@RequestBody Dto req) {
    return Dto.from(
        service.save(
            req.bundleFolder(), req.filenamePattern(), req.bundleName(), req.linkBaseUrl()));
  }
}
