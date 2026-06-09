package com.rightcrowd.sopstore.procedure.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.rightcrowd.sopstore.procedure.DocTemplate;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureVersion;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Verifies a template's custom HTML drives the PDF, and a broken one falls back to the built-in. */
class PdfExporterCustomTemplateTest {

  private static final String BODY =
      "{\"purpose\":\"Purpose text\",\"scope\":\"Scope text\","
          + "\"steps\":[{\"title\":\"FirstStep\",\"type\":\"ACTION\","
          + "\"description\":{\"type\":\"doc\",\"content\":[]}}]}";

  private static Procedure proc() {
    return new Procedure(UUID.randomUUID(), UUID.randomUUID(), "SOP-1", "MyTitle", UUID.randomUUID());
  }

  private static byte[] export(DocTemplate template) {
    return PdfExporter.export(
        proc(), (ProcedureVersion) null, BODY, template, List.of(), Map.of(), null,
        ScriptBundleConfig.DEFAULTS);
  }

  private static String text(byte[] pdf) throws IOException {
    try (PDDocument doc = PDDocument.load(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }

  @Test
  void customHtmlDrivesThePage() throws IOException {
    DocTemplate t = new DocTemplate(UUID.randomUUID(), UUID.randomUUID(), "T", "#123456", null);
    t.setCustomHtml(
        "<html><head><style>{{{builtinCss}}}</style></head>"
            + "<body><h1>UNIQUEHEADER {{title}}</h1>{{{defaultBody}}}</body></html>");
    String text = text(export(t));
    assertThat(text).contains("UNIQUEHEADER").contains("MyTitle").contains("FirstStep");
  }

  @Test
  void brokenCustomHtmlFallsBackToBuiltInLayout() throws IOException {
    DocTemplate t = new DocTemplate(UUID.randomUUID(), UUID.randomUUID(), "T", "#123456", null);
    // An unclosed Mustache section fails to compile — the export must not throw.
    t.setCustomHtml("<html><body>{{#steps}} never closed");
    String text = text(export(t));
    assertThat(text).contains("MyTitle").contains("Purpose").doesNotContain("UNIQUEHEADER");
  }
}
