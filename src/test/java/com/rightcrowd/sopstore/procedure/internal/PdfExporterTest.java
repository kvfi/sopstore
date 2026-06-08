package com.rightcrowd.sopstore.procedure.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.rightcrowd.sopstore.procedure.DocTemplate;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureVersion;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Offline render checks for {@link PdfExporter}: exercises the full HTML/CSS→PDF pipeline (Plex font
 * embedding, themed CSS, TipTap walking, logo image embedding) and asserts a valid PDF comes out.
 * Needs no database or Spring context.
 */
class PdfExporterTest {

  // 1x1 transparent PNG, to exercise the data-URI logo / image-embedding path.
  private static final byte[] PNG_1X1 =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

  private static final String BODY =
      """
      {"purpose":"Ensure consistent calibration across sites.",
       "scope":"Applies to all QC laboratories.",
       "prerequisites":{"type":"doc","content":[{"type":"bulletList","content":[
         {"type":"listItem","content":[{"type":"paragraph","content":[
           {"type":"prerequisiteRef","attrs":{"ptype":"Software","text":"SSMS"}}]}]}]}]},
       "steps":[{"title":"Import the dataset","type":"RUN_SCRIPT","scriptRefId":"A1",
         "description":{"type":"doc","content":[{"type":"paragraph","content":[
           {"type":"text","text":"Run "},
           {"type":"text","text":"load.sql","marks":[{"type":"code"}]},
           {"type":"text","text":" then "},
           {"type":"text","text":"verify","marks":[{"type":"bold"}]},
           {"type":"text","text":" the row count, see "},
           {"type":"attachmentRef","attrs":{"refId":"A1"}}]},
         {"type":"codeBlock","attrs":{"language":"sql"},"content":[{"type":"text",
           "text":"-- load rows\\nSELECT count(*) FROM sample WHERE id = 42;"}]}]}}]}
      """;

  private static Procedure procedure() {
    return new Procedure(
        UUID.randomUUID(), UUID.randomUUID(), "SOP-0001", "Calibration Procedure", UUID.randomUUID());
  }

  private static String header(byte[] pdf) {
    return new String(pdf, 0, Math.min(5, pdf.length), StandardCharsets.ISO_8859_1);
  }

  @Test
  void rendersThemedPdfWithLogo() {
    UUID proc = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    ProcedureVersion v2 =
        new ProcedureVersion(UUID.randomUUID(), tenant, proc, 2, 0, UUID.randomUUID());
    ProcedureVersion v1 =
        new ProcedureVersion(UUID.randomUUID(), tenant, proc, 1, 0, UUID.randomUUID());
    DocTemplate t =
        new DocTemplate(UUID.randomUUID(), tenant, "RightCrowd GMP", "#0c7a5a", "Confidential");
    t.setLogo(PNG_1X1, "image/png");
    t.setBodyFontPt(9.5);

    byte[] pdf = PdfExporter.export(procedure(), v2, BODY, t, List.of(v2, v1), Map.of(), "Confidential");

    assertThat(header(pdf)).isEqualTo("%PDF-");
    assertThat(pdf.length).isGreaterThan(2000);
  }

  @Test
  void rendersWithoutTemplateOrBody() {
    byte[] pdf = PdfExporter.export(procedure(), null, "{}", null, List.of(), Map.of(), null);
    assertThat(header(pdf)).isEqualTo("%PDF-");
  }
}
