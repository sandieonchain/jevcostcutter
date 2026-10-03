package io.jevopt.economics;
import io.jevopt.core.*;
import java.math.BigDecimal;
import java.util.*;
public final class CostEngine {
 private static final BigDecimal MILLION=new BigDecimal("1000000");
 private CostEngine(){}
 private static BigDecimal cost(long tokens,BigDecimal perMillion){return BigDecimal.valueOf(tokens).multiply(perMillion).divide(MILLION);}
 public static Map<String,Object> report(List<RuntimeSample> samples,List<ShadowResult> results,PricingSnapshot pricing){
  Map<String,ShadowResult> indexed=new HashMap<>();for(var r:results)indexed.put(r.sampleFingerprint(),r);
  Set<String> unknown=new TreeSet<>();BigDecimal current=BigDecimal.ZERO,projected=BigDecimal.ZERO,replay=BigDecimal.ZERO;
  boolean currentKnown=pricing!=null,projectedKnown=pricing!=null,replayKnown=pricing!=null;
  for(var sample:samples){
   PricingSnapshot.Rate rate=pricing==null?null:pricing.models().get(sample.observedModel());
   BigDecimal original=null;
   if(rate==null){currentKnown=false;unknown.add("model:"+sample.observedModel());}
   else{original=cost(sample.inputTokens(),rate.inputPerMillion()).add(cost(sample.outputTokens(),rate.outputPerMillion()));current=current.add(original);}
   ShadowResult r=indexed.get(sample.fingerprint());
   if(r==null){if(original==null)projectedKnown=false;else projected=projected.add(original);continue;}
   BigDecimal price=pricing==null?null:pricing.evaluatorInputPerMillion().get(r.evaluatorVersion());
   if(price==null){projectedKnown=false;replayKnown=false;unknown.add("evaluator:"+r.evaluatorVersion());}
   else{BigDecimal amount=cost(r.inputTokens(),price);projected=projected.add(amount);replay=replay.add(amount);}
   if(r.abstained()){if(original==null)projectedKnown=false;else projected=projected.add(original);}
  }
  Map<String,Object> out=new LinkedHashMap<>();
  out.put("basis","USER_SUPPLIED_ESTIMATE_SYNTHETIC_REPLAY_NOT_VALIDATED_SAVINGS");
  out.put("snapshot",pricing);out.put("sampleCount",samples.size());
  out.put("currentEstimatedUsd",currentKnown?current:null);
  out.put("projectedComponentUsd",projectedKnown?projected:null);
  out.put("fixtureEvaluationEstimatedUsd",replayKnown?replay:null);
  BigDecimal analyzer=pricing==null?null:pricing.analyzerOverhead(),shadow=pricing==null?null:pricing.shadowOverhead();
  out.put("analyzerOverheadUsd",analyzer);out.put("shadowOverheadUsd",shadow);
  out.put("netEstimatedDifferenceUsd",currentKnown && projectedKnown && analyzer!=null && shadow!=null?current.subtract(projected).subtract(analyzer).subtract(shadow):null);
  if(analyzer==null)unknown.add("analyzerOverhead");if(shadow==null)unknown.add("shadowOverhead");
  out.put("unknown",unknown);
  out.put("assumptions",List.of("Imported sample window only; no monthly extrapolation","Fixture latency/tokens are synthetic, not measured Jev usage","Projected component uses evaluator on replayed samples and original LLM for abstentions/unreplayed samples","Tool charges, cache discounts and instrumentation overhead not included","Shadow overhead is separately supplied; fixture estimate is not automatically billed or double-counted"));
  return out;
 }
}

