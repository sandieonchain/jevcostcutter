package io.jevopt.analyzers;
public record AnalyzerCapabilities(boolean available, boolean localOnly, boolean requiresExplicitCredential, String detail) {}
