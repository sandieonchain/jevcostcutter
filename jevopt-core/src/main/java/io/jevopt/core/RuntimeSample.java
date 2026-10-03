package io.jevopt.core;
import java.util.List;
import java.util.Map;
public record RuntimeSample(String fingerprint, String candidateId, String staticFingerprint,
 String primitive, String timestampBucket, String observedDecision, String observedModel,
 long inputTokens, long outputTokens, long latencyMs, List<String> options, boolean synthetic,
 int traceVersion, String sanitizedState, String questionInstructions, Map<String,String> criteriaDescriptions,
 String jevModel, String sourceEvidenceFingerprint) {
 public RuntimeSample(String fingerprint,String candidateId,String staticFingerprint,String primitive,String timestampBucket,String observedDecision,String observedModel,long inputTokens,long outputTokens,long latencyMs,List<String> options,boolean synthetic) {
  this(fingerprint,candidateId,staticFingerprint,primitive,timestampBucket,observedDecision,observedModel,inputTokens,outputTokens,latencyMs,options,synthetic,1,null,null,Map.of(),null,null);
 }
}
