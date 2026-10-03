package io.jevopt.patch;
import org.junit.jupiter.api.*;import io.jevopt.core.*;import java.math.*;import java.nio.file.*;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class PatchPolicyTest {
 Path temp;
 @BeforeEach void setup() throws Exception { temp=Files.createTempDirectory(Path.of("/private/tmp"),"jevopt-patch-"); }
 RuntimeSample sample(boolean synthetic,String decision){return new RuntimeSample("a".repeat(64),"candidate-aaaaaaaaaaaa","b".repeat(64),"CHOICE","2026-01-01",decision,"model",1,1,1,List.of("a","b"),synthetic);}
 ShadowResult result(String value){return new ShadowResult("a".repeat(64),"jev-1.13.0","CHOICE",value,Map.of("a",new BigDecimal("0.9"),"b",new BigDecimal("0.1")),new BigDecimal("0.9"),1,1,value.equals("a"),false);}
 @Test void syntheticAndHighRiskNeverPromote() {var e=new RecommendationEngine();assertEquals(RecommendationEngine.Verdict.NEEDS_RUNTIME_EVIDENCE,e.evaluate(List.of(sample(true,"a")),List.of(result("a")),new RecommendationEngine.Policy(1,.5,false),false));assertEquals(RecommendationEngine.Verdict.REFUSED_HIGH_RISK,e.evaluate(List.of(sample(false,"a")),List.of(result("a")),new RecommendationEngine.Policy(1,.5,true),false));}
 @Test void patchIsApplicableAndNeverWritesTarget() throws Exception {
  Path fixture=temp.resolve("fixture");Files.createDirectory(fixture);Path source=fixture.resolve("Router.java");String original=String.join("\n","class Router {","  String route() {","    String route = client.chat.completions.create(request);","    return route;","  }","}","");Files.writeString(source,original);Path state=temp.resolve("state");Files.createDirectory(state);
  DecisionCandidate c=new DecisionCandidate("candidate-aaaaaaaaaaaa","Router.java",3,"x","TOOL_ROUTING","CHOICE","STATIC_CANDIDATE",1,List.of(),List.of(),List.of(),"b".repeat(64));assertThrows(IllegalArgumentException.class,()->PatchGenerator.generate(state,fixture,c,RecommendationEngine.Verdict.NEEDS_RUNTIME_EVIDENCE));Path p=PatchGenerator.generate(state,fixture,c,RecommendationEngine.Verdict.VALIDATED_WITH_LIMITS);String diff=Files.readString(p);assertTrue(diff.contains("jev-1.13.0"));assertTrue(diff.contains("Jev abstained"));assertEquals(original,Files.readString(source));Path copy=temp.resolve("copy");Files.createDirectory(copy);Files.writeString(copy.resolve("Router.java"),original);new ProcessBuilder("git","init","-q").directory(copy.toFile()).start().waitFor();assertEquals(0,new ProcessBuilder("git","apply","--check",p.toString()).directory(copy.toFile()).start().waitFor());
 }
}
