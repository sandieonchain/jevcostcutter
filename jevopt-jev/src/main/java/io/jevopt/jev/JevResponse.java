package io.jevopt.jev;
import java.util.List;
public record JevResponse(String model, List<JevAnswer> answers, long inputTokens, long outputTokens, long requestBytes, long responseBytes) {}
