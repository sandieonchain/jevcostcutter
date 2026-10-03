package io.jevopt.patch;
import io.jevopt.core.*;
import java.util.*;
/** Deterministic policy: fixture/synthetic evidence can never validate a replacement. */
public final class RecommendationEngine {
 public enum Verdict { NEEDS_RUNTIME_EVIDENCE, VALIDATED_WITH_LIMITS, STRONG_REPLACEMENT_CANDIDATE, REFUSED_HIGH_RISK }
 public record Policy(int minimumSamples, double minimumAgreement, boolean highRisk) { public Policy {if(minimumSamples<1||minimumAgreement<0||minimumAgreement>1)throw new IllegalArgumentException("Invalid policy");} }
 public Verdict evaluate(List<RuntimeSample> samples,List<ShadowResult> results,Policy policy,boolean evidenceStale) {
  if(policy.highRisk())return Verdict.REFUSED_HIGH_RISK;
  if(evidenceStale||samples.size()<policy.minimumSamples()||samples.stream().anyMatch(s->s.synthetic()||s.traceVersion()!=3||!"jev-1.13.0".equals(s.jevModel()))||results.size()!=samples.size()||results.stream().anyMatch(r->!"jev-1.13.0".equals(r.evaluatorVersion())))return Verdict.NEEDS_RUNTIME_EVIDENCE;
  Set<String> classes=new HashSet<>();for(var s:samples)classes.add(s.observedDecision());if(classes.size()<2)return Verdict.NEEDS_RUNTIME_EVIDENCE;
  long accepted=results.stream().filter(r->!r.abstained()).count();if(accepted<policy.minimumSamples())return Verdict.NEEDS_RUNTIME_EVIDENCE;
  double agreement=(double)results.stream().filter(r->!r.abstained()&&r.agrees()).count()/accepted;
  return agreement>=policy.minimumAgreement()?Verdict.VALIDATED_WITH_LIMITS:Verdict.NEEDS_RUNTIME_EVIDENCE;
 }
}
