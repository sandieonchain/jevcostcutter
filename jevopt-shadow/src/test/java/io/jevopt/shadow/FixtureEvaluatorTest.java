package io.jevopt.shadow;
import io.jevopt.core.*;
import io.jevopt.traces.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class FixtureEvaluatorTest {
 @TempDir Path temp;
 RuntimeSample sample(){return new RuntimeSample(TraceImporter.fingerprint("candidate-aaaaaaaaaaaa","event"),"candidate-aaaaaaaaaaaa","a".repeat(64),"NOUL","2026-01-01","true","synthetic-llm",100,1,800,List.of("true","false"),true);}
 Path fixture(String confidence)throws Exception{
  Path p=temp.toRealPath().resolve("fixture.json");Files.writeString(p,"{\"version\":1,\"evaluatorVersion\":\"fixture-v1\",\"synthetic\":true,\"results\":[{\"candidateId\":\"candidate-aaaaaaaaaaaa\",\"eventId\":\"event\",\"selectedValue\":\"true\",\"probabilities\":{\"true\":\"0.85\",\"false\":\"0.15\"},\"confidence\":"+confidence+",\"inputTokens\":80,\"latencyMs\":80}]}");return p;
 }
 @Test void noulHasProbabilityNotSeparateConfidenceAndAbstains()throws Exception{
  var s=sample();var evaluator=new FixtureEvaluator(fixture("null"),List.of(s));
  var r=evaluator.evaluate(s,new BigDecimal("0.90")).orElseThrow();assertNull(r.confidence());assertTrue(r.agrees());assertTrue(r.abstained());
  var metrics=ShadowMetrics.report(List.of(s),List.of(s),new StateStore.Replay(1,"fixture-v1",new BigDecimal("0.90"),1,List.of(r)));
  assertEquals(new BigDecimal("1.000000"),metrics.get("rawAgreement"));assertNull(metrics.get("acceptedAgreement"));
 }
 @Test void noulRejectsInventedConfidence()throws Exception{assertThrows(Exception.class,()->new FixtureEvaluator(fixture("\"0.85\""),List.of(sample())));}
 @Test void thresholdBoundaryAcceptsEquality()throws Exception{
  var s=sample();var r=new FixtureEvaluator(fixture("null"),List.of(s)).evaluate(s,new BigDecimal("0.85")).orElseThrow();assertFalse(r.abstained());
 }
}

