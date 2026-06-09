package com.rightcrowd.sopstore.procedure.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureVersion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;

/**
 * Proves a RUN_SCRIPT step's script name becomes a real, clickable PDF link annotation (not just
 * styled text) and that its target follows the tenant's bundle settings — a bundle-relative path by
 * default, or an absolute URL when a link base URL is configured.
 */
class PdfScriptLinkTest {

  private static final String BODY =
      """
      {"steps":[{"title":"Import the dataset","type":"RUN_SCRIPT",
        "scriptId":"7b3f6e2a-0000-0000-0000-000000000001","scriptName":"Load Dataset",
        "scriptRefCode":"k3","scriptVersionNo":4,"scriptLanguage":"sql",
        "description":{"type":"doc","content":[]}}]}
      """;

  @Test
  void scriptNameIsAClickableLinkToTheBundleRelativePathByDefault() throws IOException {
    byte[] pdf = export(ScriptBundleConfig.DEFAULTS);
    assertThat(linkTargets(pdf)).contains("scripts/k3_load-dataset_v4.sql");
  }

  @Test
  void linkBecomesAbsoluteWhenABaseUrlIsConfigured() throws IOException {
    ScriptBundleConfig cfg =
        new ScriptBundleConfig(
            ScriptBundleConfig.DEFAULTS.folder(),
            ScriptBundleConfig.DEFAULTS.filenamePattern(),
            ScriptBundleConfig.DEFAULTS.bundleName(),
            "https://sop.example.com/");
    assertThat(linkTargets(export(cfg)))
        .contains("https://sop.example.com/scripts/k3_load-dataset_v4.sql");
  }

  private static byte[] export(ScriptBundleConfig cfg) {
    Procedure p =
        new Procedure(
            UUID.randomUUID(), UUID.randomUUID(), "SOP-0001", "Calibration", UUID.randomUUID());
    return PdfExporter.export(
        p, (ProcedureVersion) null, BODY, null, List.of(), Map.of(), null, cfg);
  }

  /** Extracts the URI of every link annotation across the document's pages. */
  private static List<String> linkTargets(byte[] pdf) throws IOException {
    List<String> targets = new ArrayList<>();
    try (PDDocument doc = PDDocument.load(pdf)) {
      for (PDPage page : doc.getPages()) {
        for (PDAnnotation annotation : page.getAnnotations()) {
          if (annotation instanceof PDAnnotationLink link
              && link.getAction() instanceof PDActionURI uri) {
            targets.add(uri.getURI());
          }
        }
      }
    }
    return targets;
  }
}
