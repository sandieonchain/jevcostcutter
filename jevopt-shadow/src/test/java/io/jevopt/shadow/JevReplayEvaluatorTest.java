package io.jevopt.shadow;

import io.jevopt.core.RuntimeSample;
import io.jevopt.jev.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JevReplayEvaluatorTest {
 @Test void exposesOnlyAggregateTransportAccounting() {
  byte[] response="{\"model\":\"jev-1.13.0\",\"answers\":{\"q\":{\"type\":\"choice\",\"choice\":\"a\",\"probabilities\":{\"a\":0.9,\"b\":0.1},\"confidence\":0.9}},\"usage\":{\"input_tokens\":7,\"output_tokens\":0}}".getBytes(StandardCharsets.UTF_8);
  JevTransport transport=(uri,authorization,body,timeout)->new JevTransport.Response(200,null,response);
  JevClient client=new JevClient(URI.create("http://127.0.0.1:9/v1/systemone"),"fake-token",false,true,transport);
  JevReplayEvaluator evaluator=new JevReplayEvaluator(client);
  RuntimeSample sample=new RuntimeSample("a".repeat(64),"candidate-aaaaaaaaaaaa","b".repeat(64),"CHOICE","2026-01-01","a","synthetic-model",1,1,1,List.of("a","b"),false,3,"approved state","pick",Map.of("a","alpha","b","beta"),"jev-1.13.0","c".repeat(64));
  assertTrue(evaluator.evaluate(sample,new BigDecimal("0.8")).isPresent());assertEquals(1,evaluator.requests());assertTrue(evaluator.requestBytes()>0);assertEquals(response.length,evaluator.responseBytes());assertEquals(7,evaluator.inputTokens());
 }
}
