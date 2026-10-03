package io.jevopt.core;
import java.math.BigDecimal;
import java.util.Map;
public record ShadowResult(String sampleFingerprint, String evaluatorVersion, String primitive,
 String selectedValue, Map<String,BigDecimal> probabilities, BigDecimal confidence,
 long inputTokens, long latencyMs, boolean agrees, boolean abstained) {}
