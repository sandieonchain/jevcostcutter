package io.jevopt.detectors;
import io.jevopt.core.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
class DetectorTest {
 @ParameterizedTest
 @CsvSource(delimiter='|',value={
  "Choose one of search or browser tool|TOOL_ROUTING|CHOICE|STATIC_CANDIDATE",
  "Return retry or stop|RETRY_STOP_DECISION|CHOICE|STATIC_CANDIDATE",
  "Relevant? Return true or false|RELEVANCE_CHECK|NOUL|STATIC_CANDIDATE",
  "Boolean classifier|BINARY_SEMANTIC_GATE|NOUL|STATIC_CANDIDATE",
  "Score severity from 1 to 5|ORDERED_SCORING|SCORE|STATIC_CANDIDATE",
  "Return an enum classification|FIXED_CHOICE_ROUTING|CHOICE|STATIC_CANDIDATE",
  "Write a helpful response|TEXT_GENERATION|NONE|KEEP_LLM",
  "Generate code for this task|CODE_GENERATION|NONE|KEEP_LLM",
  "Choose one of options and reason step by step|TEXT_GENERATION|NONE|KEEP_LLM",
  "Choose from available_tools dynamically|UNKNOWN_REASONING_HEAVY|NONE|NEEDS_RUNTIME_EVIDENCE"
 })
 void goldenCases(String prompt,String category,String primitive,String status) {
  var values=new ConservativeDetector().detect(new SourceFile("src/router.py","Python","result = client.chat.completions.create(prompt=\""+prompt+"\")"));
  assertEquals(1,values.size()); var c=values.getFirst();
  assertEquals(category,c.category()); assertEquals(primitive,c.suggestedPrimitive()); assertEquals(status,c.status());
  assertFalse(c.requiredEvidence().isEmpty()); assertEquals("<repo>/src/router.py",c.relativePath());
 }
 @Test void separatesAdjacentCalls() {
  var values=new ConservativeDetector().detect(new SourceFile("src/a.py","Python","a=client.messages.create(prompt='Return true or false')\nb=client.responses.create(prompt='Write an answer')"));
  assertEquals(2,values.size()); assertEquals("STATIC_CANDIDATE",values.get(0).status()); assertEquals("KEEP_LLM",values.get(1).status());
  assertEquals(2,values.get(1).startLine());
 }
 @Test void ignoresCommentCallAndKeepsStableFingerprint() {
  var source=new SourceFile("a.py","Python","# client.responses.create(prompt='enum')\na=client.responses.create(prompt='enum')");
  var detector=new ConservativeDetector(); var first=detector.detect(source);
  assertEquals(1,first.size()); assertEquals(2,first.getFirst().startLine());
  assertEquals(first,detector.detect(source));
 }
 @Test void preservesBlankLinesBeforeComments() {
  var source=new SourceFile("a.py","Python","\n\n# synthetic\na=client.responses.create(prompt='enum')");
  assertEquals(4,new ConservativeDetector().detect(source).getFirst().startLine());
 }
 @Test void recognizesToolRoutingAndStructuredEnum() {
  var tool=new ConservativeDetector().detect(new SourceFile("graph.py","Python","route = client.chat.completions.create(prompt='Choose one of search or browser tool')"));
  var structured=new ConservativeDetector().detect(new SourceFile("router.py","Python","route = generateObject(schema='enum', prompt='Return an enum classification')"));
  assertEquals("TOOL_ROUTING",tool.getFirst().category());assertEquals("CHOICE",structured.getFirst().suggestedPrimitive());
 }
}
