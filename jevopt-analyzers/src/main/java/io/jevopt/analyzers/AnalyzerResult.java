package io.jevopt.analyzers;
import java.util.*;
public record AnalyzerResult(String provider, String version, String assessment, double confidence, List<String> reasons, AnalyzerUsage usage) {
 public AnalyzerResult { if(!assessment.matches("(bounded|unbounded|unknown)")||confidence<0||confidence>1||reasons.size()>8) throw new IllegalArgumentException("Invalid analyzer result"); reasons=List.copyOf(reasons); }
}
