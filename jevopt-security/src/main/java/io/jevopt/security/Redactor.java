package io.jevopt.security;
import java.util.regex.Pattern;
/** Defense in depth; not a guarantee of perfect secret detection. */
public final class Redactor {
 private static final Pattern[] PATTERNS = {
  Pattern.compile("(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----"),
  Pattern.compile("(?i)(?:authorization\\s*[:=]\\s*|bearer\\s+)[^\\r\\n,;]+"),
  Pattern.compile("(?i)(?:api[_-]?key|password|secret|token)\\s*[:=]\\s*[^\\s,;]+"),
  Pattern.compile("\\b(?:sk-[A-Za-z0-9_-]{12,}|AKIA[A-Z0-9]{16}|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)\\b"),
  Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s\\\"'<>]+"),
  Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
  Pattern.compile("(?i)(?:[a-z]:[\\\\/]|/)[^\\s\\\"'<>]*"),
  Pattern.compile("\\b(?:[A-Za-z0-9-]+\\.)+(?:local|internal|lan)\\b"),
  Pattern.compile("\\b[A-Za-z0-9_+/=-]{32,}\\b")
 };
 public String redact(String value) { String s = value; for (var p : PATTERNS) s = p.matcher(s).replaceAll("[REDACTED]"); return s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "?"); }
 public String relativeLabel(String path) {
  StringBuilder b = new StringBuilder("<repo>");
  for (String part : path.replace('\\', '/').split("/")) {
   String safe = redact(part);
   if (!safe.matches("[A-Za-z0-9_.-]{1,100}")) safe = "[redacted-name]";
   b.append('/').append(safe);
  }
  return b.toString();
 }
}

