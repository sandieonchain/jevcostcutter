package io.jevopt.analyzers;
public record AnalyzerUsage(long requestBytes, long responseBytes, int redactions, String providerVersion) {}
