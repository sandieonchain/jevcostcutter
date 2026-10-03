package io.jevopt.shadow;
import io.jevopt.core.*;
import io.jevopt.traces.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
/** Synthetic tests/demos ONLY. This is not Jev inference or real runtime validation. */
public final class FixtureEvaluator implements ShadowEvaluator {
 public static final String VERSION="fixture-v1";
 private final Map<String,JsonNode> results;
 public FixtureEvaluator(Path input,List<RuntimeSample> active) throws Exception {
  JsonNode root=StrictJson.file(input);
  StrictJson.fields(root,"version","evaluatorVersion","synthetic","results");StrictJson.version(root);
  if(!StrictJson.text(root,"evaluatorVersion").equals(VERSION) || !StrictJson.bool(root,"synthetic")) throw new IllegalArgumentException("Only pinned synthetic fixture evaluator is supported");
  JsonNode entries=root.get("results");
  if(!entries.isArray() || entries.isEmpty() || entries.size()>10000) throw new IllegalArgumentException("Invalid fixture count");
  Map<String,RuntimeSample> samples=new HashMap<>();for(var s:active)samples.put(s.fingerprint(),s);
  Map<String,JsonNode> values=new TreeMap<>();
  for(JsonNode n:entries) {
   StrictJson.fields(n,"candidateId","eventId","selectedValue","probabilities","confidence","inputTokens","latencyMs");
   String candidate=StrictJson.text(n,"candidateId"),event=StrictJson.label(StrictJson.text(n,"eventId"),64);
   String fingerprint=TraceImporter.fingerprint(candidate,event);RuntimeSample s=samples.get(fingerprint);
   if(s==null || !s.synthetic()) throw new IllegalArgumentException("Fixture must reference current synthetic sample");
   if(values.putIfAbsent(fingerprint,n)!=null) throw new IllegalArgumentException("Duplicate fixture");
  }
  results=Map.copyOf(values);
  // Validate ALL entries before any replay can be persisted.
  for(var s:active) if(results.containsKey(s.fingerprint())) evaluate(s,BigDecimal.ZERO);
 }
 @Override public String version(){return VERSION;}
 @Override public Optional<ShadowResult> evaluate(RuntimeSample sample,BigDecimal threshold) {
  JsonNode n=results.get(sample.fingerprint());if(n==null)return Optional.empty();
  if(!sample.synthetic() || threshold.signum()<0 || threshold.compareTo(BigDecimal.ONE)>0) throw new IllegalArgumentException("Synthetic replay policy rejected");
  String selected=StrictJson.label(StrictJson.text(n,"selectedValue"),24);
  if(!sample.options().contains(selected))throw new IllegalArgumentException("Unknown selected option");
  JsonNode distribution=n.get("probabilities");
  StrictJson.fields(distribution,sample.options().toArray(String[]::new));
  Map<String,BigDecimal> probabilities=new TreeMap<>();BigDecimal sum=BigDecimal.ZERO;
  for(String option:sample.options()) {BigDecimal p=StrictJson.decimal(distribution,option,BigDecimal.ONE);sum=sum.add(p);probabilities.put(option,p);}
  if(sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.000001"))>0)throw new IllegalArgumentException("Invalid probability distribution");
  BigDecimal confidence=null;
  if(sample.primitive().equals("NOUL")) {
   if(!n.get("confidence").isNull())throw new IllegalArgumentException("Noul does not have separate confidence");
  } else confidence=StrictJson.decimal(n,"confidence",BigDecimal.ONE);
  BigDecimal acceptance=confidence==null?probabilities.get(selected):confidence;
  return Optional.of(new ShadowResult(sample.fingerprint(),VERSION,sample.primitive(),selected,
   Map.copyOf(probabilities),confidence,StrictJson.integer(n,"inputTokens",10_000_000),
   StrictJson.integer(n,"latencyMs",86_400_000),selected.equals(sample.observedDecision()),acceptance.compareTo(threshold)<0));
 }
}

