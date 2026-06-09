package com.rightcrowd.sopstore.procedure.internal;

import com.rightcrowd.sopstore.procedure.DocTemplate;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureVersion;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Validation and preview rendering for a {@link DocTemplate}'s custom PDF template. Lets the admin
 * API check a template's HTML for syntax errors on save and render a representative sample PDF so
 * the look can be iterated without a real procedure.
 */
@Service
public class PdfTemplateService {

  private static final String SAMPLE_BODY =
      """
      {"purpose":"Ensure consistent calibration of analytical balances across all QC laboratories.",
       "scope":"Applies to every GMP balance listed in the asset register, at all sites.",
       "prerequisites":{"type":"doc","content":[{"type":"bulletList","content":[
         {"type":"listItem","content":[{"type":"paragraph","content":[
           {"type":"text","text":"Certified reference weights (OIML E2), in calibration."}]}]},
         {"type":"listItem","content":[{"type":"paragraph","content":[
           {"type":"text","text":"Operator qualified on this SOP."}]}]}]}]},
       "steps":[
         {"title":"Stabilise the instrument","type":"ACTION",
          "description":{"type":"doc","content":[{"type":"paragraph","content":[
            {"type":"text","text":"Power on and let it equilibrate for 30 minutes."}]}]}},
         {"title":"Run the calibration check","type":"RUN_SCRIPT",
          "scriptId":"7b3f6e2a-0000-0000-0000-000000000001","scriptName":"Calibration Check",
          "scriptRefCode":"c7","scriptVersionNo":3,"scriptLanguage":"python",
          "description":{"type":"doc","content":[{"type":"paragraph","content":[
            {"type":"text","text":"Run the pinned script; record the deviations."}]}]}},
         {"title":"Record any deviation","type":"WARNING",
          "description":{"type":"doc","content":[{"type":"paragraph","content":[
            {"type":"text","text":"If a reading exceeds tolerance, quarantine it."}]}]}}]}
      """;

  private final ScriptBundleSettingsService bundleSettings;

  /** Creates the service with the script-bundle settings used by the export. */
  public PdfTemplateService(ScriptBundleSettingsService bundleSettings) {
    this.bundleSettings = bundleSettings;
  }

  /**
   * Checks a custom HTML template for Mustache syntax errors (e.g. an unclosed section). No-op when
   * blank. Throws {@link IllegalArgumentException} with a readable message when invalid.
   */
  public void validateHtml(@Nullable String html) {
    if (html == null || html.isBlank()) {
      return;
    }
    try {
      PdfTemplateEngine.validate(html);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid HTML template: " + e.getMessage(), e);
    }
  }

  /** Renders a sample PDF using the given template, so its styling can be previewed standalone. */
  public byte[] preview(DocTemplate template) {
    UUID tenant = UUID.randomUUID();
    UUID author = UUID.randomUUID();
    UUID procId = UUID.randomUUID();
    Procedure p =
        new Procedure(procId, tenant, "SOP-DEMO-001", "Balance Calibration (preview)", author);
    ProcedureVersion v = new ProcedureVersion(UUID.randomUUID(), tenant, procId, 2, 0, author);
    v.setSummary("Sample version for template preview.");
    return PdfExporter.export(
        p,
        v,
        SAMPLE_BODY,
        template,
        List.of(v),
        Map.of(author, "Jane Author"),
        "Confidential",
        bundleSettings.effectiveConfig());
  }
}
