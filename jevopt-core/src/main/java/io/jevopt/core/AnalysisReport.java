package io.jevopt.core;
import java.util.List;
public record AnalysisReport(int filesScanned, int filesSkipped, boolean truncated,
 List<DecisionCandidate> candidates, int targetWrites, int targetExecutions, int networkRequests,
 List<String> limitations) {}

