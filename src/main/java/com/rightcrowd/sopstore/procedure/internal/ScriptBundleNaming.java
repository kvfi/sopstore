package com.rightcrowd.sopstore.procedure.internal;

import java.util.Locale;

/**
 * Single source of truth for where a RUN_SCRIPT step's script file lives inside the exported SOP
 * bundle (the {@code bundle.zip}). The PDF export links to this path and the bundle writes the
 * script content to the same path, so the hyperlink resolves once the bundle is unzipped.
 *
 * <p>The name is derived purely from fields persisted in the step body (reference code, name,
 * pinned version) so both producers agree without any I/O.
 */
final class ScriptBundleNaming {

  /** Folder, relative to the bundle root, that holds the run-script files. */
  static final String DIR = "scripts/";

  private ScriptBundleNaming() {}

  /** Returns the bundle-relative path for a script, e.g. {@code scripts/k3_load-dataset_v4.sql}. */
  static String path(String code, String name, int version, String language) {
    String prefix = code == null ? "" : code.trim();
    String slug = slug(name);
    String stem;
    if (prefix.isBlank()) {
      stem = slug.isBlank() ? "script" : slug;
    } else {
      stem = slug.isBlank() ? slug(prefix) : slug(prefix) + "_" + slug;
    }
    String ver = version > 0 ? "_v" + version : "";
    return DIR + stem + ver + "." + extension(language);
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
