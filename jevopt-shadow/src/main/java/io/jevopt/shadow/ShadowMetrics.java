package io.jevopt.shadow;
import io.jevopt.core.*;
import io.jevopt.traces.StateStore;
import java.util.*;
import java.math.*;
public final class ShadowMetrics {
 private ShadowMetrics(){}
 public static List<RuntimeSample> active(List<RuntimeSample> samples,List<DecisionCandidate> candidates) {
  Map<String,DecisionCandidate> current=new HashMap<>();for(var c:candidates)current.put(c.candidateId(),c);
  return samples.stream().filter(s->current.containsKey(s.candidateId()) && current.get(s.candidateId()).staticFingerprint().equals(s.staticFingerprint()) &&
   current.get(s.candidateId()).status().equals("STATIC_CANDIDATE")).toList();
 }
 public static Map<String,Object> report(List<RuntimeSample> all,List<RuntimeSample> active,StateStore.Replay replay) {
  Map<String,Object> out=new LinkedHashMap<>();
  out.put("mode",replay!=null&&"jev-1.13.0".equals(replay.evaluatorVersion())?"REAL_SANITIZED_V3_REPLAY":"SYNTHETIC_FIXTURE_ONLY");out.put("productionAuthority","unchanged");out.put("recommendation","NEEDS_RUNTIME_EVIDENCE");
  out.put("importedSamples",all.size());out.put("activeSamples",active.size());out.put("staleSamples",all.size()-active.size());
  out.put("evaluatorVersion",replay==null?null:replay.evaluatorVersion());
  out.put("threshold",replay==null?null:replay.threshold());out.put("importedAtReplay",replay==null?0:replay.importedAtReplay());
  Map<String,RuntimeSample> samples=new HashMap<>();for(var s:active)samples.put(s.fingerprint(),s);
  List<ShadowResult> results=replay==null?List.of():replay.results().stream().filter(r->samples.containsKey(r.sampleFingerprint())).toList();
  out.put("replayedSamples",results.size());out.put("coverage",ratio(results.size(),active.size()));
  out.put("rawAgreement",ratio(results.stream().filter(ShadowResult::agrees).count(),results.size()));
  List<ShadowResult> accepted=results.stream().filter(r->!r.abstained()).toList();
  out.put("acceptedAgreement",ratio(accepted.stream().filter(ShadowResult::agrees).count(),accepted.size()));
  out.put("abstentionRate",ratio(results.stream().filter(ShadowResult::abstained).count(),results.size()));
  Map<String,Object> perCandidate=new TreeMap<>();
  for(String id:active.stream().map(RuntimeSample::candidateId).distinct().sorted().toList()) {
   List<RuntimeSample> local=active.stream().filter(s->s.candidateId().equals(id)).toList();
   List<ShadowResult> comparisons=results.stream().filter(r->samples.get(r.sampleFingerprint()).candidateId().equals(id)).toList();
   Map<String,Object> detail=new LinkedHashMap<>();
   detail.put("importedSamples",local.size());detail.put("replayedSamples",comparisons.size());
   detail.put("coverage",ratio(comparisons.size(),local.size()));detail.put("rawAgreement",ratio(comparisons.stream().filter(ShadowResult::agrees).count(),comparisons.size()));
   Map<String,Map<String,Long>> confusion=new TreeMap<>();
   for(var r:comparisons)confusion.computeIfAbsent(samples.get(r.sampleFingerprint()).observedDecision(),k->new TreeMap<>()).merge(r.selectedValue(),1L,Long::sum);
   detail.put("confusionMatrixObservedToFixture",confusion);
   Map<String,Object> perClass=new TreeMap<>();
   for(String option:local.getFirst().options()) {
    List<ShadowResult> cls=comparisons.stream().filter(r->samples.get(r.sampleFingerprint()).observedDecision().equals(option)).toList();
    perClass.put(option,nullableMap("samples",cls.size(),"agreement",ratio(cls.stream().filter(ShadowResult::agrees).count(),cls.size())));
   }
   detail.put("perClass",perClass);
   long covered=comparisons.stream().map(r->samples.get(r.sampleFingerprint()).observedDecision()).distinct().count();
   detail.put("observedOptionCoverage",ratio(covered,local.getFirst().options().size()));
   detail.put("confidenceBuckets",buckets(comparisons,false));
   detail.put("noulSelectedProbabilityBuckets",buckets(comparisons,true));
   detail.put("observedLatencyMs",percentiles(comparisons.stream().map(r->samples.get(r.sampleFingerprint()).latencyMs()).toList()));
   detail.put("fixtureLatencyMs",percentiles(comparisons.stream().map(ShadowResult::latencyMs).toList()));
   perCandidate.put(id,detail);
  }
  out.put("candidates",perCandidate);
  out.put("limitations",List.of("Fixtures are synthetic and do not validate Jev","Agreement is not accuracy","Coverage is of imported data, not proof of representative production workload","Latency is fixture/component latency, not measured workflow acceleration"));
  return out;
 }
 private static Map<String,Object> nullableMap(String a,Object x,String b,Object y) {
  Map<String,Object> m=new LinkedHashMap<>();m.put(a,x);m.put(b,y);return m;
 }
 private static Map<String,Object> buckets(List<ShadowResult> results,boolean noul) {
  Map<String,List<ShadowResult>> groups=new TreeMap<>();
  for(var r:results) {
   if((r.confidence()==null)!=noul)continue;
   BigDecimal value=noul?r.probabilities().get(r.selectedValue()):r.confidence();
   String key=value.compareTo(new BigDecimal("0.9"))>=0?"0.90-1.00":value.compareTo(new BigDecimal("0.5"))>=0?"0.50-0.90":"0.00-0.50";
   groups.computeIfAbsent(key,k->new ArrayList<>()).add(r);
  }
  Map<String,Object> out=new TreeMap<>();
  groups.forEach((key,value)->out.put(key,nullableMap("samples",value.size(),"agreement",ratio(value.stream().filter(ShadowResult::agrees).count(),value.size()))));
  return out;
 }
 public static BigDecimal ratio(long numerator,long denominator) {return denominator==0?null:BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator),6,RoundingMode.HALF_UP);}
 private static Map<String,Object> percentiles(List<Long> values) {
  if(values.isEmpty())return nullableMap("p50",null,"p95",null);
  List<Long> sorted=values.stream().sorted().toList();
  return nullableMap("p50",sorted.get((int)Math.ceil(sorted.size()*0.5)-1),"p95",sorted.get((int)Math.ceil(sorted.size()*0.95)-1));
 }
}
