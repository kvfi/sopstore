package com.rightcrowd.sopstore.procedure.internal;

import com.samskivert.mustache.Mustache;
import java.util.Map;

/**
 * Renders a tenant's custom PDF page template (Mustache syntax) against a procedure's data. Used by
 * {@link PdfExporter} when a {@code DocTemplate} supplies custom HTML. {@code {{x}}} HTML-escapes,
 * {@code {{{x}}}} emits raw HTML (for the body fragments), and {@code {{#steps}}} loops the steps.
 * Missing or null keys render as empty strings so a partial template never throws.
 */
final class PdfTemplateEngine {

  private static final Mustache.Compiler COMPILER =
      Mustache.compiler().escapeHTML(true).nullValue("").defaultValue("");

  private PdfTemplateEngine() {}

  /** Compiles the template to surface syntax errors early (e.g. an unclosed {@code {{#steps}}}). */
  static void validate(String template) {
    COMPILER.compile(template == null ? "" : template);
  }

  /** Compiles and renders the template against the context. Throws on a compile or render error. */
  static String render(String template, Map<String, Object> context) {
    return COMPILER.compile(template).execute(context);
  }
}
