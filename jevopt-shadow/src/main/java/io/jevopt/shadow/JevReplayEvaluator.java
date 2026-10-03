package io.jevopt.shadow;
import io.jevopt.core.*;
import io.jevopt.jev.*;
import java.math.*;
import java.util.*;

/** Maps only a validated v3 import to one native System One request. */
public final class JevReplayEvaluator implements ShadowEvaluator {
 private final JevClient client;
 private long requests,requestBytes,responseBytes,inputTokens;
 public JevReplayEvaluator(JevClient client){this.client=Objects.requireNonNull(client);}
 @Override public String version(){return JevClient.PINNED_MODEL;}
 @Override public Optional<ShadowResult> evaluate(RuntimeSample s,BigDecimal threshold) {
  try {
   if(s.synthetic()||s.traceVersion()!=3||!JevClient.PINNED_MODEL.equals(s.jevModel()))throw new IllegalArgumentException("Real replay requires non-synthetic v3 evidence");
   JevQuestion.Type type=JevQuestion.Type.valueOf(s.primitive());
   JevQuestion q=new JevQuestion("q",type,s.questionInstructions(),type==JevQuestion.Type.CHOICE?s.criteriaDescriptions():Map.of(),type==JevQuestion.Type.SCORE?levels(s.criteriaDescriptions()):List.of(),type==JevQuestion.Type.NOUL?s.criteriaDescriptions():Map.of());
   long started=System.nanoTime();JevResponse response=client.ask(s.sanitizedState(),s.jevModel(),List.of(q));long latency=(System.nanoTime()-started)/1_000_000;requests++;requestBytes+=response.requestBytes();responseBytes+=response.responseBytes();inputTokens+=response.inputTokens();
   JevAnswer a=response.answers().getFirst();String selected=a.value();Map<String,BigDecimal> probs=a.probabilities();BigDecimal confidence=a.confidence();
   if(type==JevQuestion.Type.NOUL){selected=new BigDecimal(selected).compareTo(new BigDecimal("0.5"))>=0?"true":"false";probs=Map.of("true",new BigDecimal(a.value()),"false",BigDecimal.ONE.subtract(new BigDecimal(a.value())));confidence=null;}
   if(type==JevQuestion.Type.SCORE){selected=String.valueOf(Math.max(0,Math.min(s.options().size()-1,new BigDecimal(selected).setScale(0,RoundingMode.HALF_UP).intValue())));}
   BigDecimal gate=confidence==null?probs.get(selected):confidence;return Optional.of(new ShadowResult(s.fingerprint(),response.model(),s.primitive(),selected,probs,confidence,response.inputTokens(),latency,selected.equals(s.observedDecision()),gate.compareTo(threshold)<0));
  } catch(Exception e) { throw new IllegalStateException("Jev replay rejected without disclosing state or token"); }
 }
 public long requests(){return requests;} public long requestBytes(){return requestBytes;} public long responseBytes(){return responseBytes;} public long inputTokens(){return inputTokens;}
 private static List<String> levels(Map<String,String> m){List<String> x=new ArrayList<>();for(int i=0;i<m.size();i++){String v=m.get(String.valueOf(i));if(v==null)throw new IllegalArgumentException("Invalid score criteria");x.add(v);}return x;}
}
