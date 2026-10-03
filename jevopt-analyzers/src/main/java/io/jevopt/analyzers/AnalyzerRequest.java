package io.jevopt.analyzers;
import java.util.*;
/** A small sanitized bundle; source trees, absolute paths, secrets and prompts are never accepted. */
public record AnalyzerRequest(String candidateId, String staticFingerprint, String language, String primitive, List<String> allowedOptions, String bundle) {
 public AnalyzerRequest { if(!candidateId.matches("candidate-[a-f0-9]{12}")||!staticFingerprint.matches("[a-f0-9]{64}")||bundle==null||bundle.length()>24_000||allowedOptions==null||allowedOptions.size()<2||allowedOptions.size()>32||looksSecret(bundle)||looksAbsolutePath(bundle)) throw new IllegalArgumentException("Invalid sanitized analyzer request"); allowedOptions=List.copyOf(allowedOptions); }
 private static boolean looksAbsolutePath(String v) { return v.matches("(?s).*(?:^|[\\\"\\s])/(?:Users|home|private|var|tmp)/.*") || v.matches("(?s).*[A-Za-z]:[\\\\/][^\\s]*.*"); }
 private static boolean looksSecret(String v) { return v.matches("(?is).*(?:api[_-]?key|authorization|bearer|password|secret|token)\\s*[:=].*") || v.matches("(?s).*sk-[A-Za-z0-9_-]{12,}.*") || v.matches("(?s).*-----BEGIN .*PRIVATE KEY-----.*"); }
}
