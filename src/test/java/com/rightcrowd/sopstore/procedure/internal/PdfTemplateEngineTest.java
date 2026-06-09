package com.rightcrowd.sopstore.procedure.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests for the Mustache-based custom PDF template engine. */
class PdfTemplateEngineTest {

  @Test
  void rendersScalarsAndStepsLoop() {
    String out =
        PdfTemplateEngine.render(
            "{{title}}|{{#steps}}[{{number}}:{{title}}]{{/steps}}",
            Map.of(
                "title", "SOP",
                "steps",
                    List.of(
                        Map.of("number", 1, "title", "A"), Map.of("number", 2, "title", "B"))));
    assertThat(out).isEqualTo("SOP|[1:A][2:B]");
  }

  @Test
  void doubleStacheEscapesAndTripleStacheEmitsRaw() {
    assertThat(PdfTemplateEngine.render("{{x}}|{{{x}}}", Map.of("x", "<b>")))
        .isEqualTo("&lt;b&gt;|<b>");
  }

  @Test
  void missingKeysRenderEmptyRatherThanThrow() {
    assertThat(PdfTemplateEngine.render("[{{nope}}]", Map.of())).isEqualTo("[]");
  }

  @Test
  void validateRejectsUnclosedSection() {
    assertThatThrownBy(() -> PdfTemplateEngine.validate("{{#steps}}no end"))
        .isInstanceOf(RuntimeException.class);
  }
}
