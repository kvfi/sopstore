package com.rightcrowd.sopstore.procedure.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.rightcrowd.sopstore.procedure.DocTemplate;
import com.rightcrowd.sopstore.procedure.Procedure;
import com.rightcrowd.sopstore.procedure.ProcedureVersion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a procedure to a styled PDF via HTML/CSS (openhtmltopdf + PDFBox). The structured body
 * JSON (purpose, scope, prerequisites doc, typed steps) becomes well-formed XHTML, themed by the
 * optional {@link DocTemplate} (logo, accent colour, footer) and set in IBM Plex Sans/Mono — the
 * fonts are embedded from {@code resources/fonts} so output is identical everywhere.
 */
final class PdfExporter {

  private static final Logger LOG = LoggerFactory.getLogger(PdfExporter.class);
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String DEFAULT_ACCENT = "215db0";
  private static final String SANS = "IBM Plex Sans";
  private static final String MONO = "IBM Plex Mono";
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  private PdfExporter() {}

  /** Builds the themed PDF bytes from a procedure, its version, body, and history. */
  static byte[] export(
      Procedure p,
      @Nullable ProcedureVersion version,
      String bodyJson,
      @Nullable DocTemplate template,
      List<ProcedureVersion> history,
      Map<UUID, String> authorNames,
      @Nullable String confidentiality,
      ScriptBundleConfig bundleConfig) {
    JsonNode body = parse(bodyJson);
    String accent = "#" + (template == null ? DEFAULT_ACCENT : template.accentColor());

    // Try the template's custom HTML first (if any); a render failure drops to the built-in layout.
    String custom = null;
    if (template != null) {
      String html = template.customHtml();
      if (html != null && !html.isBlank()) {
        try {
          custom =
              PdfTemplateEngine.render(
                  html,
                  context(
                      p, version, body, template, accent, history, authorNames, confidentiality,
                      bundleConfig));
        } catch (RuntimeException e) {
          LOG.warn("custom PDF template failed to render; falling back to the built-in layout", e);
        }
      }
    }

    String builtin =
        buildXhtml(
            p, version, body, template, accent, history, authorNames, confidentiality,
            bundleConfig);
    if (custom == null) {
      return renderPdf(builtin);
    }
    try {
      return renderPdf(custom);
    } catch (RuntimeException e) {
      LOG.warn("custom PDF template produced invalid output; using the built-in layout", e);
      return renderPdf(builtin);
    }
  }

  /** Runs the HTML/CSS through openhtmltopdf with the embedded Plex fonts and returns PDF bytes. */
  private static byte[] renderPdf(String xhtml) {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PdfRendererBuilder b = new PdfRendererBuilder();
      b.useFastMode();
      b.withHtmlContent(xhtml, null);
      b.useFont(() -> font("IBMPlexSans-Regular.ttf"), SANS, 400, FontStyle.NORMAL, true);
      b.useFont(() -> font("IBMPlexSans-SemiBold.ttf"), SANS, 600, FontStyle.NORMAL, true);
      b.useFont(() -> font("IBMPlexSans-Bold.ttf"), SANS, 700, FontStyle.NORMAL, true);
      b.useFont(() -> font("IBMPlexMono-Regular.ttf"), MONO, 400, FontStyle.NORMAL, true);
      b.toStream(out);
      b.run();
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("failed to render pdf", e);
    }
  }

  private static InputStream font(String name) {
    InputStream in = PdfExporter.class.getResourceAsStream("/fonts/" + name);
    if (in == null) {
      throw new IllegalStateException("missing bundled font: " + name);
    }
    return in;
  }

  // ----------------------------------------------------------------- document assembly
  private static final String[] SECTIONS = {
    "Purpose", "Scope", "Prerequisites", "Steps", "Document history"
  };

  private static String buildXhtml(
      Procedure p,
      @Nullable ProcedureVersion version,
      JsonNode body,
      @Nullable DocTemplate template,
      String accent,
      List<ProcedureVersion> history,
      Map<UUID, String> authorNames,
      @Nullable String confidentiality,
      ScriptBundleConfig bundleConfig) {
    StringBuilder sb = new StringBuilder(4096);
    String custom = template == null ? "" : template.customCss();
    sb.append("<html><head><style>")
        .append(builtinCss(accent, template, confidentiality))
        .append(custom == null || custom.isBlank() ? "" : "\n/* custom */\n" + custom)
        .append("</style></head><body>");

    // Header: optional logo + accent rule.
    sb.append("<div class=\"head\">");
    if (template != null && template.hasLogo()) {
      String mime = template.logoMime() == null ? "image/png" : template.logoMime();
      sb.append("<img class=\"logo\" src=\"data:")
          .append(mime)
          .append(";base64,")
          .append(Base64.getEncoder().encodeToString(template.logo()))
          .append("\" />");
    }
    sb.append("<div class=\"accent\"></div></div>");

    sb.append("<div class=\"meta\">")
        .append(esc(p.documentNumber()))
        .append(" &#183; v")
        .append(esc(version == null ? "—" : version.versionLabel()))
        .append(" &#183; ")
        .append(esc(p.state()));
    if (confidentiality != null && !confidentiality.isBlank()) {
      sb.append(" &#183; <span class=\"classification\">")
          .append(esc(confidentiality.toUpperCase(Locale.ROOT)))
          .append("</span>");
    }
    sb.append("</div>");
    sb.append("<h1>").append(esc(p.title())).append("</h1>");

    // Table of contents (page numbers filled by target-counter at render time).
    sb.append("<div class=\"toc\"><div class=\"toc-h\">Contents</div>");
    for (int i = 0; i < SECTIONS.length; i++) {
      sb.append("<div class=\"toc-row\"><a href=\"#sec-")
          .append(i + 1)
          .append("\">")
          .append(i + 1)
          .append(". ")
          .append(esc(SECTIONS[i]))
          .append("</a></div>");
    }
    sb.append("</div>");

    sb.append(defaultBody(body, version, history, authorNames, bundleConfig));

    sb.append("</body></html>");
    return sb.toString();
  }

  /**
   * The five numbered content sections (Purpose → Document history). Shared by the built-in layout
   * and exposed to custom templates as {@code {{{defaultBody}}}} so a template can restyle the page
   * shell without re-implementing the body.
   */
  private static String defaultBody(
      JsonNode body,
      @Nullable ProcedureVersion version,
      List<ProcedureVersion> history,
      Map<UUID, String> authorNames,
      ScriptBundleConfig bundleConfig) {
    StringBuilder sb = new StringBuilder(2048);
    sb.append(h2str(1)).append(textBlock(body.path("purpose").asText("")));
    sb.append(h2str(2)).append(textBlock(body.path("scope").asText("")));
    sb.append(h2str(3)).append(prerequisites(body.path("prerequisites")));

    sb.append(h2str(4));
    JsonNode steps = body.path("steps");
    if (steps.isArray() && !steps.isEmpty()) {
      int n = 1;
      for (JsonNode s : steps) {
        sb.append(step(s, n++, bundleConfig));
      }
    } else {
      sb.append("<p class=\"muted\">No steps authored yet.</p>");
    }

    sb.append(h2str(5)).append(historyTable(history, version, authorNames));
    return sb.toString();
  }

  /** Builds the Mustache context a custom PDF template renders against. */
  private static Map<String, Object> context(
      Procedure p,
      @Nullable ProcedureVersion version,
      JsonNode body,
      DocTemplate template,
      String accent,
      List<ProcedureVersion> history,
      Map<UUID, String> authorNames,
      @Nullable String confidentiality,
      ScriptBundleConfig bundleConfig) {
    Map<String, Object> m = new HashMap<>();
    m.put("title", p.title());
    m.put("documentNumber", p.documentNumber());
    m.put("version", version == null ? "—" : version.versionLabel());
    m.put("state", p.state());
    m.put(
        "confidentiality",
        confidentiality == null ? "" : confidentiality.toUpperCase(Locale.ROOT));
    m.put("accent", accent);
    boolean hasLogo = template.hasLogo();
    m.put("hasLogo", hasLogo);
    m.put("logoDataUri", hasLogo ? logoDataUri(template) : "");
    m.put("footer", footerText(template, confidentiality));
    m.put("builtinCss", builtinCss(accent, template, confidentiality));
    m.put("customCss", template.customCss());
    m.put("purposeHtml", textBlock(body.path("purpose").asText("")));
    m.put("scopeHtml", textBlock(body.path("scope").asText("")));
    m.put("prerequisitesHtml", prerequisites(body.path("prerequisites")));
    m.put("historyHtml", historyTable(history, version, authorNames));
    m.put("defaultBody", defaultBody(body, version, history, authorNames, bundleConfig));
    m.put("steps", stepContexts(body.path("steps"), bundleConfig));
    return m;
  }

  /** Per-step values for the {@code {{#steps}}} loop: scalars plus pre-rendered HTML fragments. */
  private static List<Map<String, Object>> stepContexts(
      JsonNode steps, ScriptBundleConfig bundleConfig) {
    List<Map<String, Object>> list = new ArrayList<>();
    if (!steps.isArray()) {
      return list;
    }
    int n = 1;
    for (JsonNode s : steps) {
      String type = s.path("type").asText("");
      boolean runScript = "RUN_SCRIPT".equals(type);
      Map<String, Object> m = new HashMap<>();
      int number = n++;
      m.put("number", number);
      m.put("title", s.path("title").asText(""));
      m.put("typeLabel", stepTypeLabel(type));
      m.put("isRunScript", runScript);
      m.put("scriptName", s.path("scriptName").asText(""));
      m.put(
          "scriptHref",
          runScript
              ? ScriptBundleNaming.scriptHref(
                  bundleConfig,
                  s.path("scriptRefCode").asText(""),
                  s.path("scriptName").asText(""),
                  s.path("scriptVersionNo").asInt(0),
                  s.path("scriptLanguage").asText(""))
              : "");
      m.put("descriptionHtml", html(s.path("description")));
      m.put("html", step(s, number, bundleConfig));
      list.add(m);
    }
    return list;
  }

  /** The base64 data-URI for the template's logo image. */
  private static String logoDataUri(DocTemplate template) {
    String mime = template.logoMime() == null ? "image/png" : template.logoMime();
    return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(template.logo());
  }

  /** A numbered, anchored section heading, e.g. {@code <h2 id="sec-3">3. Prerequisites</h2>}. */
  private static String h2str(int n) {
    return "<h2 id=\"sec-" + n + "\">" + n + ". " + esc(SECTIONS[n - 1]) + "</h2>";
  }

  /** The version history / change log table, newest first, with the authoring user per version. */
  private static String historyTable(
      List<ProcedureVersion> history,
      @Nullable ProcedureVersion cur,
      Map<UUID, String> authorNames) {
    List<ProcedureVersion> rows =
        history == null || history.isEmpty()
            ? (cur == null ? List.of() : List.of(cur))
            : history;
    if (rows.isEmpty()) {
      return "<p class=\"muted\">No version history.</p>";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("<table class=\"hist\"><thead><tr>")
        .append("<th>Version</th><th>Date</th><th>Author</th><th>Notes</th></tr></thead><tbody>");
    String currentId = cur == null ? "" : cur.id().toString();
    for (ProcedureVersion v : rows) {
      boolean current = v.id().toString().equals(currentId);
      String note = v.summary() == null || v.summary().isBlank() ? "—" : v.summary();
      String author = authorNames.getOrDefault(v.createdBy(), "—");
      sb.append("<tr><td>v")
          .append(esc(v.versionLabel()))
          .append(current ? " <span class=\"cur\">current</span>" : "")
          .append("</td><td>")
          .append(esc(DATE.format(v.createdAt())))
          .append("</td><td>")
          .append(esc(author.isBlank() ? "—" : author))
          .append("</td><td>")
          .append(esc(note))
          .append("</td></tr>");
    }
    return sb.append("</tbody></table>").toString();
  }

  /** A narrative block (Purpose/Scope): plain text with line breaks, or a muted placeholder. */
  private static String textBlock(String text) {
    if (text == null || text.isBlank()) {
      return "<p class=\"muted\">Not specified.</p>";
    }
    return "<p>" + esc(text).replace("\n", "<br/>") + "</p>";
  }

  private static String prerequisites(JsonNode node) {
    if (node.isObject()) {
      String h = html(node);
      return h.isBlank() ? "<p class=\"muted\">No prerequisites.</p>" : h;
    }
    if (node.isArray() && !node.isEmpty()) {
      StringBuilder sb = new StringBuilder("<ul>");
      for (JsonNode item : node) {
        String type = item.isObject() ? item.path("type").asText("") : "";
        String text = item.isObject() ? item.path("text").asText("") : item.asText("");
        if (!text.isBlank()) {
          sb.append("<li>").append(esc(type.isBlank() ? text : type + ": " + text)).append("</li>");
        }
      }
      return sb.append("</ul>").toString();
    }
    return "<p class=\"muted\">No prerequisites.</p>";
  }

  private static String step(JsonNode s, int n, ScriptBundleConfig bundleConfig) {
    String type = s.path("type").asText("ACTION");
    boolean runScript = "RUN_SCRIPT".equals(type);
    String typeLabel = stepTypeLabel(type);
    String title = s.path("title").asText("");
    StringBuilder sb = new StringBuilder("<div class=\"step\"><div class=\"step-h\">");
    sb.append("<span class=\"num\">").append(n).append("</span>");
    if (!typeLabel.isBlank()) {
      // RUN_SCRIPT leads with a play "script" icon; its type label heads the line.
      String icon = runScript ? "<span class=\"ico-script\"></span>" : "";
      sb.append("<span class=\"type\">").append(icon).append(esc(typeLabel)).append("</span>");
    }
    if (runScript) {
      // The pinned script's name sits right next to the RUN SCRIPT label as the clickable
      // reference; a title is optional and only shown when one is explicitly set.
      sb.append(scriptReference(s, bundleConfig));
      if (!title.isBlank()) {
        sb.append("<span class=\"sep\">&#183;</span>");
        sb.append("<span class=\"title\">").append(esc(title)).append("</span>");
      }
    } else if (!title.isBlank()) {
      sb.append("<span class=\"title\">").append(esc(title)).append("</span>");
    } else {
      sb.append("<span class=\"title\">(untitled step)</span>");
    }
    sb.append("</div>");

    JsonNode desc = s.has("description") ? s.path("description") : s.path("instruction");
    String descHtml;
    boolean hasDesc;
    if (desc.isTextual()) {
      String t = desc.asText("");
      hasDesc = !t.isBlank();
      descHtml = hasDesc ? textBlock(t) : "<p class=\"muted\">Not specified.</p>";
    } else {
      descHtml = html(desc);
      hasDesc = !descHtml.isBlank();
    }
    // RUN_SCRIPT carries its meaning in the title + script reference, so the description is
    // optional — omit the block entirely when empty rather than printing a placeholder.
    if (hasDesc || !runScript) {
      sb.append("<div class=\"desc\">").append(descHtml).append("</div>");
    }
    return sb.append("</div>").toString();
  }

  /**
   * The RUN_SCRIPT step's pinned script name, rendered inline next to the RUN SCRIPT label as a
   * hyperlink into the bundle's {@code scripts/} directory so the reader can open the exact script
   * from the unzipped SOP bundle.
   */
  private static String scriptReference(JsonNode s, ScriptBundleConfig bundleConfig) {
    String scriptName = s.path("scriptName").asText("");
    String code = s.path("scriptRefCode").asText("");
    int ver = s.path("scriptVersionNo").asInt(0);
    String scriptRef = s.path("scriptRefId").asText("");

    if (scriptName.isBlank() && scriptRef.isBlank()) {
      return "<span class=\"script-none\">(no script selected)</span>";
    }
    String name = scriptName.isBlank() ? scriptRef : scriptName;
    String label = name + (ver > 0 ? " @ v" + ver : "");
    String language = s.path("scriptLanguage").asText("");
    String href = ScriptBundleNaming.scriptHref(bundleConfig, code, scriptName, ver, language);
    return "<a class=\"script-link\" href=\"" + escAttr(href) + "\">" + esc(label) + "</a>";
  }

  // ----------------------------------------------------------------- TipTap JSON → XHTML
  private static String html(JsonNode doc) {
    if (doc == null || !doc.isObject()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    renderChildren(doc, sb);
    return sb.toString();
  }

  private static void renderChildren(JsonNode n, StringBuilder sb) {
    JsonNode content = n.path("content");
    if (content.isArray()) {
      for (JsonNode c : content) {
        renderNode(c, sb);
      }
    }
  }

  private static void renderNode(JsonNode n, StringBuilder sb) {
    switch (n.path("type").asText("")) {
      case "text" -> renderText(n, sb);
      case "paragraph" -> wrap(n, sb, "p");
      case "heading" -> wrap(n, sb, "h3");
      case "bulletList" -> wrap(n, sb, "ul");
      case "orderedList" -> wrap(n, sb, "ol");
      case "listItem" -> wrap(n, sb, "li");
      case "blockquote" -> wrap(n, sb, "blockquote");
      case "codeBlock" -> appendCodeBlock(n, sb);
      case "horizontalRule" -> sb.append("<hr/>");
      case "hardBreak" -> sb.append("<br/>");
      case "attachmentRef" ->
          sb.append("<span class=\"chip\">[")
              .append(esc(n.path("attrs").path("refId").asText("")))
              .append("]</span>");
      case "prerequisiteRef" -> {
        String pt = n.path("attrs").path("ptype").asText("");
        String tx = n.path("attrs").path("text").asText("");
        String label = pt.isBlank() ? tx : pt + ": " + tx;
        sb.append("<span class=\"chip\">").append(esc(label)).append("</span>");
      }
      default -> renderChildren(n, sb);
    }
  }

  /** Renders a fenced code block: an optional language label plus syntax-highlighted source. */
  private static void appendCodeBlock(JsonNode n, StringBuilder sb) {
    String lang = n.path("attrs").path("language").asText("");
    boolean labelled =
        !lang.isBlank() && !"plaintext".equalsIgnoreCase(lang) && !"null".equalsIgnoreCase(lang);
    sb.append("<div class=\"code\">");
    if (labelled) {
      sb.append("<div class=\"code-lang\">").append(esc(lang)).append("</div>");
    }
    // CodeHighlighter returns already-escaped XHTML, so do not escape it again here.
    sb.append("<pre>").append(CodeHighlighter.highlight(textOf(n), lang)).append("</pre></div>");
  }

  private static void wrap(JsonNode n, StringBuilder sb, String tag) {
    sb.append('<').append(tag).append('>');
    renderChildren(n, sb);
    sb.append("</").append(tag).append('>');
  }

  private static void renderText(JsonNode n, StringBuilder sb) {
    Deque<String> closers = new ArrayDeque<>();
    StringBuilder open = new StringBuilder();
    for (JsonNode m : n.path("marks")) {
      switch (m.path("type").asText("")) {
        case "bold" -> {
          open.append("<strong>");
          closers.push("</strong>");
        }
        case "italic" -> {
          open.append("<em>");
          closers.push("</em>");
        }
        case "code" -> {
          open.append("<code>");
          closers.push("</code>");
        }
        case "link" -> {
          String href = escAttr(m.path("attrs").path("href").asText(""));
          open.append("<a href=\"").append(href).append("\">");
          closers.push("</a>");
        }
        default -> { /* unknown mark — render text unstyled */ }
      }
    }
    sb.append(open).append(esc(n.path("text").asText("")));
    while (!closers.isEmpty()) {
      sb.append(closers.pop());
    }
  }

  private static String textOf(JsonNode n) {
    if (n.path("type").asText("").equals("text")) {
      return n.path("text").asText("");
    }
    StringBuilder sb = new StringBuilder();
    for (JsonNode c : n.path("content")) {
      sb.append(textOf(c));
    }
    return sb.toString();
  }

  private static String stepTypeLabel(String type) {
    return switch (type) {
      case "RUN_SCRIPT" -> "Run script";
      case "VERIFICATION" -> "Verification";
      case "WARNING" -> "Warning";
      case "NOTE" -> "Note";
      default -> "";
    };
  }

  // ----------------------------------------------------------------- CSS
  /** Composes the page footer: optional template footer, the controlled-copy notice, and class. */
  private static String footerText(
      @Nullable DocTemplate template, @Nullable String confidentiality) {
    String footer =
        template == null || template.footerText() == null || template.footerText().isBlank()
            ? "Controlled document — printed copies are uncontrolled."
            : template.footerText() + "  —  Controlled document — printed copies are uncontrolled.";
    if (confidentiality != null && !confidentiality.isBlank()) {
      footer = confidentiality.toUpperCase(Locale.ROOT) + "  —  " + footer;
    }
    return footer;
  }

  /** The built-in stylesheet with the template's accent, fonts, and footer substituted in. */
  private static String builtinCss(
      String accent, @Nullable DocTemplate template, @Nullable String confidentiality) {
    double bodyPt = template == null ? 10 : template.bodyFontPt();
    double headingPt = template == null ? 12 : template.headingFontPt();
    double tablePt = template == null ? 9.5 : template.tableFontPt();
    // The heading base drives the title, section headings, and sub-headings proportionally.
    // Footer sits in a CSS string inside <style>, so escape for CSS then for XML.
    return readResource("/pdf/procedure.css")
        .replace("$ACCENT", accent)
        .replace("$FONTPT", fmtPt(bodyPt))
        .replace("$H1PT", fmtPt(headingPt + 9))
        .replace("$H2PT", fmtPt(headingPt))
        .replace("$H3PT", fmtPt(Math.max(7, headingPt - 1)))
        .replace("$TABLEPT", fmtPt(tablePt))
        .replace("$FOOTER", esc(cssString(footerText(template, confidentiality))));
  }

  /** Formats a point size, dropping a trailing {@code .0} so whole sizes read as integers. */
  private static String fmtPt(double pt) {
    return pt == Math.floor(pt) ? String.valueOf((int) pt) : String.valueOf(pt);
  }

  private static String readResource(String path) {
    try (InputStream in = PdfExporter.class.getResourceAsStream(path)) {
      if (in == null) {
        throw new IllegalStateException("missing resource: " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("failed to read " + path, e);
    }
  }

  // ----------------------------------------------------------------- escaping
  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static String escAttr(String s) {
    return esc(s).replace("\"", "&quot;");
  }

  /** Escapes a value for use inside a CSS string literal (content: "..."). */
  private static String cssString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static JsonNode parse(String json) {
    try {
      return JSON.readTree(json == null || json.isBlank() ? "{}" : json);
    } catch (Exception e) {
      return JSON.createObjectNode();
    }
  }
}
