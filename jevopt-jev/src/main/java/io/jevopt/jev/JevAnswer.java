package io.jevopt.jev;
import java.math.BigDecimal;
import java.util.Map;
public record JevAnswer(String questionId, String value, Map<String,BigDecimal> probabilities, BigDecimal confidence) {}
