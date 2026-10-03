package io.jevopt.core;
import java.util.List;
public interface CallSiteDetector { List<DecisionCandidate> detect(SourceFile source); }

