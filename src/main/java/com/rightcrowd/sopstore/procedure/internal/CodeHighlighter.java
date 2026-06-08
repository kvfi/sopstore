package com.rightcrowd.sopstore.procedure.internal;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Minimal, dependency-free syntax highlighter for fenced code blocks in the PDF export. Tokenizes a
 * snippet for a given language into comments, strings, numbers, and keywords and emits XHTML spans
 * (classes {@code tok-com / tok-str / tok-num / tok-kw}) that the PDF theme colours. Unknown
 * languages and {@code plaintext} fall through to plain, escaped text.
 *
 * <p>This is intentionally a lexer, not a parser — good enough to make controlled-document code
 * listings readable in the exported PDF, never to execute, validate, or fully grammar-check code.
 */
final class CodeHighlighter {

  private CodeHighlighter() {}

  /**
   * Returns the snippet as highlighted, XHTML-escaped markup for the given language. The output is
   * already escaped, so callers must not escape it again before embedding it in the PDF HTML.
   */
  static String highlight(String code, @Nullable String language) {
    if (code == null || code.isEmpty()) {
      return "";
    }
    Lang lang = Lang.of(language);
    StringBuilder out = new StringBuilder(code.length() + 64);
    int n = code.length();
    int i = 0;
    while (i < n) {
      char c = code.charAt(i);

      if (lang.blockComment && c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
        int end = code.indexOf("*/", i + 2);
        int stop = end < 0 ? n : end + 2;
        span(out, "tok-com", code.substring(i, stop));
        i = stop;
        continue;
      }

      String lineComment = lang.lineCommentAt(code, i);
      if (lineComment != null) {
        int end = code.indexOf('\n', i);
        int stop = end < 0 ? n : end;
        span(out, "tok-com", code.substring(i, stop));
        i = stop;
        continue;
      }

      if (lang.tripleQuote && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i))) {
        String quote = code.substring(i, i + 3);
        int end = code.indexOf(quote, i + 3);
        int stop = end < 0 ? n : end + 3;
        span(out, "tok-str", code.substring(i, stop));
        i = stop;
        continue;
      }

      if (c == '"' || c == '\'' || (lang.backtick && c == '`')) {
        int stop = stringEnd(code, i, c);
        span(out, "tok-str", code.substring(i, stop));
        i = stop;
        continue;
      }

      if (isDigit(c) || (c == '.' && i + 1 < n && isDigit(code.charAt(i + 1)))) {
        int j = i + 1;
        while (j < n && isNumberPart(code.charAt(j))) {
          j++;
        }
        span(out, "tok-num", code.substring(i, j));
        i = j;
        continue;
      }

      if (isIdentStart(c)) {
        int j = i + 1;
        while (j < n && isIdentPart(code.charAt(j))) {
          j++;
        }
        String word = code.substring(i, j);
        String probe = lang.caseInsensitive ? word.toLowerCase(Locale.ROOT) : word;
        if (lang.keywords.contains(probe)) {
          span(out, "tok-kw", word);
        } else {
          out.append(esc(word));
        }
        i = j;
        continue;
      }

      escChar(out, c);
      i++;
    }
    return out.toString();
  }

  /** Returns the index just past the end of the string literal opened by {@code quote}. */
  private static int stringEnd(String code, int start, char quote) {
    int n = code.length();
    int j = start + 1;
    while (j < n) {
      char ch = code.charAt(j);
      if (ch == '\\' && j + 1 < n) {
        j += 2;
        continue;
      }
      if (ch == quote) {
        return j + 1;
      }
      if (ch == '\n' && quote != '`') {
        return j; // unterminated single-line string — stop at the newline
      }
      j++;
    }
    return n;
  }

  private static void span(StringBuilder sb, String cls, String text) {
    sb.append("<span class=\"").append(cls).append("\">").append(esc(text)).append("</span>");
  }

  private static String esc(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  private static void escChar(StringBuilder sb, char c) {
    switch (c) {
      case '&' -> sb.append("&amp;");
      case '<' -> sb.append("&lt;");
      case '>' -> sb.append("&gt;");
      default -> sb.append(c);
    }
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isNumberPart(char c) {
    return Character.isLetterOrDigit(c) || c == '.' || c == '_';
  }

  private static boolean isIdentStart(char c) {
    return Character.isLetter(c) || c == '_' || c == '$';
  }

  private static boolean isIdentPart(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '$';
  }

  /** Per-language lexer configuration: comment/string syntax and the keyword set to colour. */
  private static final class Lang {

    private final List<String> lineComments;
    private final boolean blockComment;
    private final boolean tripleQuote;
    private final boolean backtick;
    private final boolean caseInsensitive;
    private final Set<String> keywords;

    private Lang(
        List<String> lineComments,
        boolean blockComment,
        boolean tripleQuote,
        boolean backtick,
        boolean caseInsensitive,
        Set<String> keywords) {
      this.lineComments = lineComments;
      this.blockComment = blockComment;
      this.tripleQuote = tripleQuote;
      this.backtick = backtick;
      this.caseInsensitive = caseInsensitive;
      this.keywords = keywords;
    }

    @Nullable String lineCommentAt(String code, int i) {
      for (String lc : lineComments) {
        if (code.startsWith(lc, i)) {
          return lc;
        }
      }
      return null;
    }

    private static final List<String> SLASH = List.of("//");
    private static final List<String> HASH = List.of("#");
    private static final List<String> DASH = List.of("--");
    private static final List<String> NONE = List.of();

    private static final Lang PLAIN = new Lang(NONE, false, false, false, false, Set.of());

    private static final Lang C_FAMILY =
        new Lang(
            SLASH, true, false, false, false,
            Set.of(
                "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
                "else", "enum", "extern", "float", "for", "goto", "if", "int", "long", "register",
                "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
                "union", "unsigned", "void", "volatile", "while", "NULL", "true", "false"));

    private static final Lang CPP =
        new Lang(
            SLASH, true, false, false, false,
            Set.of(
                "auto", "bool", "break", "case", "catch", "char", "class", "const", "constexpr",
                "continue", "default", "delete", "do", "double", "else", "enum", "explicit",
                "extern", "false", "float", "for", "friend", "if", "inline", "int", "long",
                "namespace", "new", "nullptr", "operator", "private", "protected", "public",
                "return", "short", "sizeof", "static", "struct", "switch", "template", "this",
                "throw", "true", "try", "typedef", "typename", "union", "unsigned", "using",
                "virtual", "void", "volatile", "while"));

    private static final Lang JAVA =
        new Lang(
            SLASH, true, false, false, false,
            Set.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private", "protected", "public",
                "record", "return", "sealed", "short", "static", "super", "switch", "synchronized",
                "this", "throw", "throws", "transient", "try", "var", "void", "volatile", "while",
                "yield", "true", "false", "null"));

    private static final Lang KOTLIN =
        new Lang(
            SLASH, true, false, true, false,
            Set.of(
                "as", "by", "class", "companion", "data", "do", "else", "enum", "false", "for",
                "fun", "get", "if", "import", "in", "init", "interface", "internal", "is", "null",
                "object", "open", "override", "package", "private", "protected", "public", "return",
                "sealed", "set", "suspend", "this", "super", "true", "val", "var", "when",
                "while"));

    private static final Lang JS =
        new Lang(
            SLASH, true, false, true, false,
            Set.of(
                "abstract", "as", "async", "await", "break", "case", "catch", "class", "const",
                "continue", "declare", "default", "delete", "do", "else", "enum", "export",
                "extends", "false", "finally", "for", "from", "function", "if", "implements",
                "import", "in", "instanceof", "interface", "let", "namespace", "new", "null", "of",
                "private", "protected", "public", "readonly", "return", "super", "switch", "this",
                "throw", "true", "try", "type", "typeof", "undefined", "var", "void", "while",
                "yield"));

    private static final Lang PYTHON =
        new Lang(
            HASH, false, true, false, false,
            Set.of(
                "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
                "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
                "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "self", "try",
                "while", "with", "yield", "None", "True", "False"));

    private static final Lang RUBY =
        new Lang(
            HASH, false, false, false, false,
            Set.of(
                "and", "begin", "break", "case", "class", "def", "do", "else", "elsif", "end",
                "ensure", "false", "for", "if", "in", "module", "next", "nil", "not", "or", "raise",
                "require", "rescue", "return", "self", "then", "true", "unless", "until", "when",
                "while", "yield"));

    private static final Lang GO =
        new Lang(
            SLASH, true, false, true, false,
            Set.of(
                "break", "case", "chan", "const", "continue", "default", "defer", "else",
                "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map",
                "package", "range", "return", "select", "struct", "switch", "type", "var", "nil",
                "true", "false", "iota", "string", "int", "bool", "error"));

    private static final Lang RUST =
        new Lang(
            SLASH, true, false, false, false,
            Set.of(
                "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
                "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match",
                "mod", "move", "mut", "pub", "ref", "return", "self", "static", "struct", "super",
                "trait", "true", "type", "unsafe", "use", "where", "while"));

    private static final Lang CSHARP =
        new Lang(
            SLASH, true, false, false, false,
            Set.of(
                "abstract", "as", "async", "await", "base", "bool", "break", "case", "catch",
                "char", "class", "const", "continue", "default", "delegate", "do", "double", "else",
                "enum", "event", "false", "finally", "float", "for", "foreach", "if", "in", "int",
                "interface", "internal", "is", "lock", "long", "namespace", "new", "null", "object",
                "out", "override", "params", "private", "protected", "public", "readonly", "ref",
                "return", "sealed", "short", "static", "string", "struct", "switch", "this",
                "throw", "true", "try", "typeof", "using", "var", "virtual", "void", "while"));

    private static final Lang SQL =
        new Lang(
            DASH, true, false, false, true,
            Set.of(
                "add", "all", "alter", "and", "as", "asc", "avg", "between", "by", "case", "column",
                "count", "create", "default", "delete", "desc", "distinct", "drop", "else", "end",
                "exists", "foreign", "from", "group", "having", "in", "index", "inner", "insert",
                "into", "is", "join", "key", "left", "like", "limit", "max", "min", "not", "null",
                "offset", "on", "or", "order", "outer", "primary", "references", "returning",
                "right", "select", "set", "sum", "table", "then", "true", "false", "union",
                "update", "values", "view", "when", "where", "with"));

    private static final Lang BASH =
        new Lang(
            HASH, false, false, true, false,
            Set.of(
                "case", "cd", "declare", "do", "done", "echo", "elif", "else", "esac", "export",
                "fi", "for", "function", "if", "in", "local", "readonly", "return", "source",
                "then", "until", "while", "true", "false"));

    private static final Lang YAML =
        new Lang(HASH, false, false, false, false, Set.of("true", "false", "null", "yes", "no"));

    private static final Lang JSON =
        new Lang(NONE, false, false, false, false, Set.of("true", "false", "null"));

    private static final Lang CSS = new Lang(NONE, true, false, false, false, Set.of());

    static Lang of(@Nullable String name) {
      if (name == null) {
        return PLAIN;
      }
      String key = name.trim().toLowerCase(Locale.ROOT);
      if (key.startsWith("language-")) {
        key = key.substring("language-".length());
      }
      return switch (key) {
        case "java" -> JAVA;
        case "kotlin", "kt" -> KOTLIN;
        case "javascript", "js", "jsx", "typescript", "ts", "tsx" -> JS;
        case "python", "py" -> PYTHON;
        case "ruby", "rb" -> RUBY;
        case "go", "golang" -> GO;
        case "rust", "rs" -> RUST;
        case "csharp", "cs", "c#" -> CSHARP;
        case "c" -> C_FAMILY;
        case "cpp", "c++", "cxx", "hpp" -> CPP;
        case "sql" -> SQL;
        case "bash", "sh", "shell", "zsh" -> BASH;
        case "yaml", "yml" -> YAML;
        case "json" -> JSON;
        case "css", "scss" -> CSS;
        default -> PLAIN;
      };
    }
  }
}
