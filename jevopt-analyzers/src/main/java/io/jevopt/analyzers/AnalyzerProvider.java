package io.jevopt.analyzers;
/** Advisory only: an analyzer result can add evidence but can never authorize a patch. */
public interface AnalyzerProvider {
 String id();
 AnalyzerCapabilities capabilities();
 AnalyzerResult analyze(AnalyzerRequest request) throws AnalyzerException;
}
