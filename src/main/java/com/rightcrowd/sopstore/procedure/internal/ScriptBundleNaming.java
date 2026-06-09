package com.rightcrowd.sopstore.procedure.internal;

import java.util.Locale;

/**
 * Single source of truth for where a RUN_SCRIPT step's script file lives inside the exported SOP
 * bundle ({@code bundle.zip}) and what the PDF hyperlinks to. The PDF export and the bundle writer
 * both derive the path from the same persisted step fields and the tenant's {@link
 * ScriptBundleConfig}, so a hyperlink resolves once the bundle is unzipped.
 *
 * <p>The filename comes from the tenant's {@code filenamePattern} via {@link #path}, expanding the
 * tokens {@code {code}}, {@code {name}}, {@code {version}} and {@code {ext}}. Empty tokens (no
 * reference code, an unpinned version) collapse cleanly so the name never carries stray separators.
 */
final class ScriptBundleNaming {

  private static final String EXT_SENTINEL = "EXTSENTINEL";

  private ScriptBundleNaming() {}

  /**
   * Returns the bundle-relative path for a script under the configured folder, e.g. {@code
   * scripts/k3_load-dataset_v4.sql} for the default pattern.
   */
  static String path(ScriptBundleConfig cfg, String code, String name, int version, String lang) {
    String ext = extension(lang);
    String file =
        render(cfg.filenamePattern(), slug(code), slug(name), versionToken(version), ext);
    return folder(cfg) + file;
  }

  /**
   * Returns the href the PDF links a RUN_SCRIPT step to. A blank {@code linkBaseUrl} stays relative
   * to the bundle (resolves only in the unzipped bundle); otherwise the path is prefixed with the
   * configured absolute base URL so the link opens from the PDF alone.
   */
  static String scriptHref(
      ScriptBundleConfig cfg, String code, String name, int version, String lang) {
    String path = path(cfg, code, name, version, lang);
    String base = cfg.linkBaseUrl() == null ? "" : cfg.linkBaseUrl().trim();
    if (base.isBlank()) {
      return path;
    }
    return base.replaceAll("/+$", "") + "/" + path;
  }

  /** Returns the zip filename from the {@code bundleName} pattern (token {@code {document}}). */
  static String bundleFileName(ScriptBundleConfig cfg, String documentNumber) {
    String doc = documentNumber == null ? "" : documentNumber;
    String name = cfg.bundleName().replace("{document}", doc).replaceAll("[^A-Za-z0-9._-]", "_");
    name = name.replaceAll("^[_-]+", "");
    if (name.isBlank()) {
      name = "bundle.zip";
    }
    return name;
  }

  /** Maps a script-service language to a conventional file extension (default {@code txt}). */
  static String extension(String language) {
    if (language == null) {
      return "txt";
    }
    return switch (language.trim().toLowerCase(Locale.ROOT)) {
      case "sql" -> "sql";
      case "python", "py" -> "py";
      case "javascript", "js" -> "js";
      case "typescript", "ts" -> "ts";
      case "bash", "sh", "shell", "zsh" -> "sh";
      case "powershell", "ps1" -> "ps1";
      case "java" -> "java";
      case "kotlin", "kt" -> "kt";
      case "go", "golang" -> "go";
      case "ruby", "rb" -> "rb";
      case "rust", "rs" -> "rs";
      case "csharp", "cs", "c#" -> "cs";
      case "c" -> "c";
      case "cpp", "c++" -> "cpp";
      case "yaml", "yml" -> "yml";
      case "json" -> "json";
      case "xml" -> "xml";
      case "html" -> "html";
      case "css" -> "css";
      case "r" -> "r";
      case "php" -> "php";
      case "perl", "pl" -> "pl";
      default -> "txt";
    };
  }

  /** Expands the filename pattern's tokens, tidies separators, and guarantees a non-empty stem. */
  private static String render(
      String pattern, String code, String name, String version, String ext) {
    String s =
        pattern
            .replace("{code}", code)
            .replace("{name}", name)
            .replace("{version}", version)
            .replace("{ext}", EXT_SENTINEL);
    s = tidy(s);
    if (s.isBlank() || s.startsWith(".") || s.startsWith(EXT_SENTINEL)) {
      s = "script" + s;
    }
    boolean hasExt = pattern.contains("{ext}");
    s = s.replace(EXT_SENTINEL, ext);
    return hasExt ? s : s + "." + ext;
  }

  /** Returns the configured folder, normalised to {@code no-leading-slash + trailing-slash}. */
  private static String folder(ScriptBundleConfig cfg) {
    String f = cfg.folder() == null ? "" : cfg.folder().trim();
    if (f.isBlank()) {
      return "";
    }
    f = f.replaceAll("^/+", "");
    return f.endsWith("/") ? f : f + "/";
  }

  /** A pinned version renders as {@code v4}; an unpinned one (0) contributes nothing. */
  private static String versionToken(int version) {
    return version > 0 ? "v" + version : "";
  }

  /** Collapses separator runs and trims them from the ends and around the extension dot. */
  private static String tidy(String s) {
    String t = s.replace(' ', '_');
    t = t.replaceAll("[_-]{2,}", "_"); // a run of separators (e.g. from an empty token) becomes one
    t = t.replaceAll("[_-]+\\.", "."); // drop a separator immediately before the extension dot
    t = t.replaceAll("\\.[_-]+", "."); // drop a separator immediately after a dot
    return t.replaceAll("(^[_-]+|[_-]+$)", ""); // trim leading/trailing separators
  }

  /** Lower-cases and reduces a label to {@code [a-z0-9-]} runs, trimming dashes at the ends. */
  private static String slug(String s) {
    if (s == null) {
      return "";
    }
    String t =
        s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    return t.length() > 60 ? t.substring(0, 60) : t;
  }
}
