package com.rightcrowd.sopstore.procedure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.TenantId;
import org.jspecify.annotations.Nullable;

/**
 * A tenant-defined Word-export theme: a name, an accent colour (applied to the title and headings),
 * optional footer text, and an optional logo image embedded at the top of the exported document. A
 * procedure may select one of its tenant's templates for its {@code Download Word} output.
 */
@Entity
@Table(name = "doc_template")
@SuppressWarnings({"UnusedVariable", "NullAway.Init"})
public class DocTemplate {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "accent_color", nullable = false)
  private String accentColor = "215db0";

  @Column(name = "footer_text")
  private @Nullable String footerText;

  @Column(name = "body_font_pt", nullable = false)
  private double bodyFontPt = 10;

  @Column(name = "heading_font_pt", nullable = false)
  private double headingFontPt = 12;

  @Column(name = "table_font_pt", nullable = false)
  private double tableFontPt = 9.5;

  @Column(name = "logo", nullable = false)
  private byte[] logo = new byte[0];

  @Column(name = "logo_mime")
  private @Nullable String logoMime;

  @Column(name = "custom_css", nullable = false)
  private String customCss = "";

  @Column(name = "custom_html", nullable = false)
  private String customHtml = "";

  @Column(name = "font_family", nullable = false)
  private String fontFamily = "IBM Plex Sans";

  @Column(name = "body_font", nullable = false)
  private byte[] bodyFont = new byte[0];

  @Column(name = "body_font_name")
  private @Nullable String bodyFontName;

  @Column(name = "cover_enabled", nullable = false)
  private boolean coverEnabled = false;

  @Column(name = "cover_text", nullable = false)
  private String coverText = "";

  @Column(name = "cover_align", nullable = false)
  private String coverAlign = "bottom";

  @Column(name = "cover_html", nullable = false)
  private String coverHtml = "";

  @Column(name = "cover_logo_size", nullable = false)
  private String coverLogoSize = "medium";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  /** Creates an empty instance for use by the persistence provider. */
  protected DocTemplate() {}

  /** Creates a new template with the given name, accent colour, and optional footer text. */
  public DocTemplate(
      UUID id, UUID tenantId, String name, String accentColor, @Nullable String footerText) {
    this.id = id;
    this.tenantId = tenantId;
    this.name = name;
    this.accentColor = normalizeColor(accentColor);
    this.footerText = footerText;
  }

  /** Returns the template id. */
  public UUID id() {
    return id;
  }

  /** Returns the template name. */
  public String name() {
    return name;
  }

  /** Returns the accent colour as a 6-hex-digit string (no leading '#'). */
  public String accentColor() {
    return accentColor;
  }

  /** Returns the footer text, or null when none is set. */
  public @Nullable String footerText() {
    return footerText;
  }

  /** Returns the body font size in points for the PDF export. */
  public double bodyFontPt() {
    return bodyFontPt;
  }

  /** Returns the heading base font size in points (drives the title and section headings). */
  public double headingFontPt() {
    return headingFontPt;
  }

  /** Returns the table font size in points for the PDF export. */
  public double tableFontPt() {
    return tableFontPt;
  }

  /** Returns the logo's MIME type, or null when no logo is set. */
  public @Nullable String logoMime() {
    return logoMime;
  }

  /** Whether a logo image has been uploaded. */
  public boolean hasLogo() {
    return logo.length > 0;
  }

  /** Returns a copy of the logo bytes (empty when none). */
  public byte[] logo() {
    return logo.clone();
  }

  /** Returns the custom CSS appended after the built-in stylesheet (empty when none). */
  public String customCss() {
    return customCss;
  }

  /** Returns the custom full-page HTML template (empty when the built-in layout is used). */
  public String customHtml() {
    return customHtml;
  }

  /** Returns the body/heading font family name (defaults to {@code IBM Plex Sans}). */
  public String fontFamily() {
    return fontFamily;
  }

  /** Whether a custom body font file has been uploaded for {@link #fontFamily()}. */
  public boolean hasBodyFont() {
    return bodyFont.length > 0;
  }

  /** Returns a copy of the uploaded body font bytes (empty when none). */
  public byte[] bodyFont() {
    return bodyFont.clone();
  }

  /** Returns the uploaded body font's original file name, or null when none. */
  public @Nullable String bodyFontName() {
    return bodyFontName;
  }

  /** Whether a back cover page is rendered as the last page of the export. */
  public boolean coverEnabled() {
    return coverEnabled;
  }

  /** Returns the customizable cover-page text (empty when none). */
  public String coverText() {
    return coverText;
  }

  /** Returns the cover-page vertical alignment: {@code top}, {@code middle}, or {@code bottom}. */
  public String coverAlign() {
    return coverAlign;
  }

  /**
   * Returns the custom cover-page HTML template (Mustache), or empty to use the built-in cover
   * markup. Rendered only when {@link #coverEnabled()} is set.
   */
  public String coverHtml() {
    return coverHtml;
  }

  /** Returns the cover logo size keyword: {@code small}, {@code medium}, {@code large}, or
   * {@code xlarge}. Drives the {@code {{{logo}}}} image's CSS size class. */
  public String coverLogoSize() {
    return coverLogoSize;
  }

  /** Renames the template. */
  public void setName(String name) {
    this.name = name;
  }

  /** Sets the accent colour (normalised to 6 hex digits). */
  public void setAccentColor(String accentColor) {
    this.accentColor = normalizeColor(accentColor);
  }

  /** Sets the footer text (null clears it). */
  public void setFooterText(@Nullable String footerText) {
    this.footerText = footerText;
  }

  /** Sets the body font size in points, clamped to a readable 7–16pt range. */
  public void setBodyFontPt(double pt) {
    this.bodyFontPt = Math.min(16, Math.max(7, pt));
  }

  /** Sets the heading base font size in points, clamped to a readable 8–28pt range. */
  public void setHeadingFontPt(double pt) {
    this.headingFontPt = Math.min(28, Math.max(8, pt));
  }

  /** Sets the table font size in points, clamped to a readable 6–14pt range. */
  public void setTableFontPt(double pt) {
    this.tableFontPt = Math.min(14, Math.max(6, pt));
  }

  /** Replaces the logo image bytes and its MIME type. */
  public void setLogo(byte[] bytes, @Nullable String mime) {
    this.logo = bytes.clone();
    this.logoMime = mime;
  }

  /** Sets the custom CSS (null is stored as empty). */
  public void setCustomCss(@Nullable String css) {
    this.customCss = css == null ? "" : css;
  }

  /** Sets the custom full-page HTML template (null is stored as empty). */
  public void setCustomHtml(@Nullable String html) {
    this.customHtml = html == null ? "" : html;
  }

  /** Sets the font family name (blank/null falls back to {@code IBM Plex Sans}). */
  public void setFontFamily(@Nullable String family) {
    String f = family == null ? "" : family.trim();
    this.fontFamily = f.isEmpty() ? "IBM Plex Sans" : f;
  }

  /** Replaces the uploaded body font bytes and its original file name. */
  public void setBodyFont(byte[] bytes, @Nullable String name) {
    this.bodyFont = bytes.clone();
    this.bodyFontName = name;
  }

  /** Clears any uploaded body font, reverting to the bundled IBM Plex Sans. */
  public void clearBodyFont() {
    this.bodyFont = new byte[0];
    this.bodyFontName = null;
  }

  /** Enables or disables the back cover page. */
  public void setCoverEnabled(boolean enabled) {
    this.coverEnabled = enabled;
  }

  /** Sets the cover-page text (null is stored as empty). */
  public void setCoverText(@Nullable String text) {
    this.coverText = text == null ? "" : text;
  }

  /** Sets the cover-page vertical alignment (defaults to {@code bottom} when blank/invalid). */
  public void setCoverAlign(@Nullable String align) {
    this.coverAlign = normalizeAlign(align);
  }

  /** Sets the custom cover-page HTML template (null is stored as empty). */
  public void setCoverHtml(@Nullable String html) {
    this.coverHtml = html == null ? "" : html;
  }

  /** Sets the cover logo size (defaults to {@code medium} when blank/invalid). */
  public void setCoverLogoSize(@Nullable String size) {
    this.coverLogoSize = normalizeLogoSize(size);
  }

  /** Strips a leading '#' and lower-cases; falls back to the brand blue when blank/invalid. */
  private static String normalizeColor(@Nullable String c) {
    if (c == null) {
      return "215db0";
    }
    String h = c.startsWith("#") ? c.substring(1) : c;
    return h.matches("[0-9a-fA-F]{6}") ? h.toLowerCase(Locale.ROOT) : "215db0";
  }

  /** Restricts the cover alignment to a valid table-cell vertical-align keyword. */
  private static String normalizeAlign(@Nullable String a) {
    if (a == null) {
      return "bottom";
    }
    String v = a.trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "top", "middle", "bottom" -> v;
      default -> "bottom";
    };
  }

  /** Restricts the cover logo size to a known keyword (used directly in a CSS class name). */
  private static String normalizeLogoSize(@Nullable String s) {
    if (s == null) {
      return "medium";
    }
    String v = s.trim().toLowerCase(Locale.ROOT);
    return switch (v) {
      case "small", "medium", "large", "xlarge" -> v;
      default -> "medium";
    };
  }
}
