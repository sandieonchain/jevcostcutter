package io.jevopt.cli;
import io.jevopt.core.*;
import io.jevopt.detectors.ConservativeDetector;
import io.jevopt.traces.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.io.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeCliTest {
 @TempDir Path temp;
 Path repo,state,trace,fixture,pricing;String id;String source;
 record Result(int code,String out,String err){}
 Result run(String... commands){
  var cli=JevOpt.commandLine();var out=new StringWriter();var err=new StringWriter();
  cli.setOut(new PrintWriter(out,true));cli.setErr(new PrintWriter(err,true));
  List<String> args=new ArrayList<>(List.of("--repo",repo.toString(),"--state-dir",state.toString(),"--format","json"));args.addAll(List.of(commands));
  return new Result(cli.execute(args.toArray(String[]::new)),out.toString(),err.toString());
 }
 JsonNode output(Result r)throws Exception{assertEquals(0,r.code());return StrictJson.parse(r.out());}
 @BeforeEach void setup()throws Exception{
  Path base=temp.toRealPath();repo=Files.createDirectory(base.resolve("agent"));state=base.resolve("state");
  source="from pathlib import Path\nPath('EXECUTED').write_text('bad')\nresult=client.responses.create(prompt='Choose one of search or browser tool')\n";
  Files.writeString(repo.resolve("router.py"),source);
  id=new ConservativeDetector().detect(new SourceFile("router.py","Python",source)).getFirst().candidateId();
  trace=base.resolve("observations.jsonl");fixture=base.resolve("fixture.json");pricing=base.resolve("pricing.json");
  Files.writeString(trace,observation("event-a","search").toString()+"\n"+observation("event-b","browser").toString()+"\n");
  writeFixture(false);
  Files.writeString(pricing,"{\"version\":1,\"id\":\"synthetic-pricing\",\"date\":\"2026-01-01\",\"currency\":\"USD\",\"synthetic\":true,\"models\":{\"synthetic-llm\":{\"inputPerMillion\":\"2\",\"outputPerMillion\":\"6\"}},\"evaluatorInputPerMillion\":{\"fixture-v1\":\"0.05\"},\"analyzerOverhead\":\"0\",\"shadowOverhead\":\"0\"}");
  assertEquals(0,run("init").code());
 }
 ObjectNode observation(String event,String decision){
  ObjectNode n=StrictJson.MAPPER.createObjectNode();n.put("version",1);n.put("eventId",event);n.put("candidateId",id);n.put("timestampBucket","2026-01-01");
  n.put("observedDecision",decision);n.put("observedModel","synthetic-llm");n.put("inputTokens",100);n.put("outputTokens",1);n.put("latencyMs",800);n.putArray("options").add("search").add("browser");n.put("synthetic",true);return n;
 }
 void writeFixture(boolean fullAgreement)throws Exception{
  ObjectNode root=StrictJson.MAPPER.createObjectNode();root.put("version",1);root.put("evaluatorVersion","fixture-v1");root.put("synthetic",true);
  ArrayNode results=root.putArray("results");
  for(int i=0;i<2;i++){
   ObjectNode r=results.addObject();r.put("candidateId",id);r.put("eventId",i==0?"event-a":"event-b");
   r.put("selectedValue",i==1&&fullAgreement?"browser":"search");
   r.putObject("probabilities").put("search","0.6").put("browser","0.4");
   r.put("confidence",i==0?"0.95":"0.60");r.put("inputTokens",80);r.put("latencyMs",80);
  }
  Files.writeString(fixture,root.toString());
 }
 void importTrace()throws Exception{assertEquals(2,output(run("traces","import",trace.toString())).get("inserted").intValue());}
 JsonNode replay()throws Exception{return output(run("--pricing",pricing.toString(),"shadow","start","--fixtures",fixture.toString()));}
 @Test void endToEndDeterministicMetricsEconomicsAndNoTargetMutation()throws Exception{
  importTrace();JsonNode first=replay();JsonNode second=replay();assertEquals(first,second);
  assertEquals(0,first.get("rawAgreement").decimalValue().compareTo(new BigDecimal("0.5")));
  assertEquals(0,first.get("acceptedAgreement").decimalValue().compareTo(BigDecimal.ONE));
  assertEquals(0,first.get("abstentionRate").decimalValue().compareTo(new BigDecimal("0.5")));
  assertEquals(0,first.get("coverage").decimalValue().compareTo(BigDecimal.ONE));
  JsonNode costs=first.get("economics");
  assertEquals(0,costs.get("currentEstimatedUsd").decimalValue().compareTo(new BigDecimal("0.000412")));
  assertEquals(0,costs.get("projectedComponentUsd").decimalValue().compareTo(new BigDecimal("0.000214")));
  assertEquals(0,costs.get("netEstimatedDifferenceUsd").decimalValue().compareTo(new BigDecimal("0.000198")));
  assertEquals(1,first.get("candidates").get(id).get("confusionMatrixObservedToFixture").get("browser").get("search").intValue());
  assertEquals("NEEDS_RUNTIME_EVIDENCE",first.get("recommendation").textValue());
  assertEquals(source,Files.readString(repo.resolve("router.py")));assertFalse(Files.exists(repo.resolve("EXECUTED")));
  assertEquals(1,Files.list(repo).count());assertEquals(0,first.get("targetWrites").intValue());assertEquals(0,first.get("targetExecutions").intValue());assertEquals(0,first.get("networkRequests").intValue());
  assertFalse(first.toString().contains(repo.toString()));assertFalse(first.toString().contains("Path("));assertFalse(first.toString().contains("event-a"));
  assertEquals(2,output(run("report")).get("runtime").get("replayedSamples").intValue());
 }
 @Test void jsonImportDeduplicatesAndNeverStoresEventIdsOrRawPayload()throws Exception{
  importTrace();var duplicate=output(run("traces","import",trace.toString()));assertEquals(0,duplicate.get("inserted").intValue());assertEquals(2,duplicate.get("duplicates").intValue());
  Files.writeString(trace,"["+observation("event-a","search")+","+observation("event-b","browser")+"]");
  assertEquals(2,output(run("traces","import",trace.toString())).get("duplicates").intValue());
  String db=new String(Files.readAllBytes(state.resolve("state.db")),java.nio.charset.StandardCharsets.ISO_8859_1);
  assertFalse(db.contains("event-a"));assertFalse(db.contains("event-b"));assertFalse(db.contains("Path("));
  assertEquals(4,output(run("config")).get("schemaVersion").intValue());
 }
 @ParameterizedTest @ValueSource(strings={"prompt","sanitizedState","authorization","password","payload"})
 void rejectsRawFieldsAtomicallyWithoutPersistence(String field)throws Exception{
  var bad=observation("event-b","browser");bad.put(field,"synthetic-sensitive-value");
  Files.writeString(trace,observation("event-a","search")+"\n"+bad+"\n");
  Result result=run("traces","import",trace.toString());assertEquals(2,result.code());assertFalse(result.err().contains("synthetic-sensitive-value"));assertFalse(result.err().contains(temp.toString()));
  assertEquals(0,output(run("shadow","status")).get("importedSamples").intValue());
  assertFalse(new String(Files.readAllBytes(state.resolve("state.db")),java.nio.charset.StandardCharsets.ISO_8859_1).contains("synthetic-sensitive-value"));
 }
 @ParameterizedTest @ValueSource(strings={"negative","fractional","unknownCandidate","invalidDay","unsafeLabel","duplicateKey","unknownVersion","outsideOptions","giant","symlink"})
 void rejectsMalformedAndUnsafeTraces(String variant)throws Exception{
  var bad=observation("event-a","search");
  switch(variant){
   case "negative"->bad.put("inputTokens",-1);
   case "fractional"->bad.put("inputTokens",1.5);
   case "unknownCandidate"->bad.put("candidateId","candidate-000000000000");
   case "invalidDay"->bad.put("timestampBucket","2026-02-30");
   case "unsafeLabel"->bad.put("observedModel","Bearer pretend-value");
   case "unknownVersion"->bad.put("version",2);
   case "outsideOptions"->bad.put("observedDecision","other");
  }
  Files.writeString(trace,bad.toString());
  if(variant.equals("duplicateKey"))Files.writeString(trace,bad.toString().replace("\"version\":1","\"version\":1,\"version\":1"));
  if(variant.equals("giant"))Files.writeString(trace," ".repeat(2_000_001));
  if(variant.equals("symlink")){Path link=temp.toRealPath().resolve("linked.jsonl");Files.createSymbolicLink(link,trace);trace=link;}
  assertEquals(2,run("traces","import",trace.toString()).code());assertEquals(0,output(run("shadow","status")).get("importedSamples").intValue());
 }
 @Test void conflictingDuplicatesAndOptionDriftRollback()throws Exception{
  importTrace();
  var changed=observation("event-a","browser");Files.writeString(trace,observation("new-event","search")+"\n"+changed);
  assertEquals(2,run("traces","import",trace.toString()).code());
  var drift=observation("new-event","search");drift.putArray("options").add("search").add("other");Files.writeString(trace,drift.toString());
  assertEquals(2,run("traces","import",trace.toString()).code());assertEquals(2,output(run("shadow","status")).get("importedSamples").intValue());
 }
 @Test void staleSourceExcludesEvidenceAndPreventsReplay()throws Exception{
  importTrace();replay();Files.writeString(repo.resolve("router.py"),source.replace("search or browser","search or browser or archive"));
  JsonNode status=output(run("shadow","status"));assertEquals(2,status.get("staleSamples").intValue());assertEquals(0,status.get("activeSamples").intValue());assertEquals(0,status.get("replayedSamples").intValue());assertTrue(status.get("rawAgreement").isNull());
  assertEquals(2,run("shadow","start","--fixtures",fixture.toString()).code());
 }
 @Test void nonSyntheticTracesCannotBecomeFixtureEvidence()throws Exception{
  var one=observation("event-a","search");one.put("synthetic",false);
  Files.writeString(trace,one+"\n"+observation("event-b","browser"));importTrace();
  assertEquals(2,run("shadow","start","--fixtures",fixture.toString()).code());
  assertEquals(0,output(run("shadow","status")).get("replayedSamples").intValue());
 }
 @ParameterizedTest @ValueSource(strings={"movingVersion","badDistribution","unknownOption","duplicate","invalidConfidence","secretField"})
 void rejectsInvalidFixturesAndPreservesPriorReplay(String mode)throws Exception{
  importTrace();JsonNode before=replay();ObjectNode root=(ObjectNode)StrictJson.file(fixture);ObjectNode first=(ObjectNode)root.get("results").get(0);
  switch(mode){
   case "movingVersion"->root.put("evaluatorVersion","fixture-latest");
   case "badDistribution"->((ObjectNode)first.get("probabilities")).put("search","0.99");
   case "unknownOption"->first.put("selectedValue","other");
   case "duplicate"->((ArrayNode)root.get("results")).add(first.deepCopy());
   case "invalidConfidence"->first.put("confidence","1.1");
   case "secretField"->first.put("prompt","pretend-private-prompt");
  }
  Files.writeString(fixture,root.toString());assertEquals(2,run("shadow","start","--fixtures",fixture.toString()).code());
  assertEquals(before,output(run("--pricing",pricing.toString(),"shadow","report")));
 }
 @Test void unknownPricesAndOverheadsStayUnknown()throws Exception{
  importTrace();replay();JsonNode report=output(run("shadow","report"));
  assertTrue(report.get("economics").get("currentEstimatedUsd").isNull());assertTrue(report.get("economics").get("netEstimatedDifferenceUsd").isNull());
  ObjectNode prices=(ObjectNode)StrictJson.file(pricing);prices.putNull("analyzerOverhead");Files.writeString(pricing,prices.toString());
  report=output(run("--pricing",pricing.toString(),"shadow","report"));
  assertTrue(report.get("economics").get("netEstimatedDifferenceUsd").isNull());assertFalse(report.get("economics").get("currentEstimatedUsd").isNull());
 }
 @Test void purgeRequiresConfirmationPreservesConfigAndRemovesRows()throws Exception{
  importTrace();replay();assertEquals(2,run("purge").code());
  assertEquals(2,output(run("purge","--confirm")).get("samplesRemoved").intValue());
  JsonNode status=output(run("shadow","status"));assertEquals(0,status.get("importedSamples").intValue());assertEquals(0,status.get("replayedSamples").intValue());
  assertTrue(Files.exists(state.resolve("config.json")));assertEquals(source,Files.readString(repo.resolve("router.py")));
 }
 @Test void rejectsStateSymlinksAndTamperedUnsafeConfig()throws Exception{
  Path link=temp.toRealPath().resolve("state-link");Files.createSymbolicLink(link,state);Path actual=state;state=link;
  assertEquals(2,run("config").code());state=actual;
  Files.writeString(state.resolve("config.json"),Files.readString(state.resolve("config.json")).replace("\"telemetry\":false","\"telemetry\":true"));
  assertEquals(2,run("config").code());
 }
 @Test void persistedSampleTamperingFailsClosed()throws Exception{
  importTrace();
  try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+state.resolve("state.db"));Statement s=connection.createStatement()){
   s.executeUpdate("UPDATE samples SET data=replace(data,'synthetic-llm','Bearer pretend-sensitive')");
  }
  var result=run("shadow","report");assertEquals(2,result.code());assertFalse(result.err().contains("pretend-sensitive"));
 }
 @Test void partialReplayAndNewImportsExposeCoverage()throws Exception{
  importTrace();ObjectNode f=(ObjectNode)StrictJson.file(fixture);((ArrayNode)f.get("results")).remove(1);Files.writeString(fixture,f.toString());
  assertEquals(0,replay().get("coverage").decimalValue().compareTo(new BigDecimal("0.5")));
  Files.writeString(trace,observation("event-c","search").toString());assertEquals(1,output(run("traces","import",trace.toString())).get("inserted").intValue());
  assertEquals(0,output(run("shadow","report")).get("coverage").decimalValue().compareTo(new BigDecimal("0.333333")));
 }
 @ParameterizedTest @ValueSource(strings={"negative","huge","unknownField","wrongCurrency","invalidDate"})
 void rejectsInvalidPricing(String mode)throws Exception{
  importTrace();replay();ObjectNode prices=(ObjectNode)StrictJson.file(pricing);
  switch(mode){
   case "negative"->prices.put("analyzerOverhead","-1");
   case "huge"->prices.put("shadowOverhead","999999999999999999999999999999999999");
   case "unknownField"->prices.put("apiKey","pretend-sensitive");
   case "wrongCurrency"->prices.put("currency","OTHER");
   case "invalidDate"->prices.put("date","2026-02-30");
  }
  Files.writeString(pricing,prices.toString());var result=run("--pricing",pricing.toString(),"shadow","report");assertEquals(2,result.code());assertFalse(result.err().contains("pretend-sensitive"));
 }
 @Test void stateDatabaseSymlinkIsRejected()throws Exception{
  Path actual=temp.toRealPath().resolve("actual.db");Files.move(state.resolve("state.db"),actual);Files.createSymbolicLink(state.resolve("state.db"),actual);
  assertEquals(2,run("config").code());
 }
}
