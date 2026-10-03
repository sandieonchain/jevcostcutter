package io.jevopt.core;
import java.util.List;
public record DecisionCandidate(String candidateId, String relativePath, int startLine,
 String framework, String category, String suggestedPrimitive, String status,
 double detectorConfidence, List<String> positiveSignals, List<String> negativeSignals,
 List<String> requiredEvidence, String staticFingerprint) {}

