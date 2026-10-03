package io.jevopt.jev;
import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import java.io.*;import java.net.*;import java.nio.charset.*;import java.util.*;import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Wire-level regression test: no provider or real token is used. */
class JevClientHttpServerTest {
 HttpServer server; URI endpoint; AtomicInteger requests=new AtomicInteger(); List<byte[]> bodies=Collections.synchronizedList(new ArrayList<>()); AtomicInteger status=new AtomicInteger(200);
 @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/v1/systemone",this::handle);server.start();endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/systemone");}
 @AfterEach void stop(){server.stop(0);}
 void handle(HttpExchange x)throws IOException {requests.incrementAndGet();bodies.add(x.getRequestBody().readAllBytes());int s=status.get();String body=s==200?"{\"model\":\"jev-1.13.0\",\"answers\":{\"choice\":{\"type\":\"choice\",\"choice\":\"a\",\"probabilities\":{\"a\":0.8,\"b\":0.2},\"confidence\":0.8},\"score\":{\"type\":\"score\",\"score\":0.3,\"legend\":{\"0\":\"low\",\"1\":\"high\"},\"probabilities\":{\"0\":0.7,\"1\":0.3},\"confidence\":0.7},\"noul\":{\"type\":\"noul\",\"noul\":0.2}},\"usage\":{\"input_tokens\":7,\"output_tokens\":0}}":"{}";x.sendResponseHeaders(s,body.getBytes(StandardCharsets.UTF_8).length);x.getResponseBody().write(body.getBytes(StandardCharsets.UTF_8));x.close();}
 List<JevQuestion> qs(){return List.of(new JevQuestion("choice",JevQuestion.Type.CHOICE,"pick",Map.of("a","alpha","b","beta"),List.of(),Map.of()),new JevQuestion("score",JevQuestion.Type.SCORE,"rate",Map.of(),List.of("low","high"),Map.of()),new JevQuestion("noul",JevQuestion.Type.NOUL,"assert",Map.of(),List.of(),Map.of("true","yes","false","no")));}
 @Test void nativeNamedMapGoldenEnvelopeAndAllPrimitives() throws Exception {var r=new JevClient(endpoint,"fake-token",false,true).ask("approved sanitized state","jev-1.13.0",qs());assertEquals(7,r.inputTokens());assertEquals(3,r.answers().size());assertEquals(1,requests.get());JsonNode root=new ObjectMapper().readTree(bodies.getFirst());assertTrue(root.get("questions").isObject());assertFalse(root.get("questions").isArray());assertEquals("pick",root.at("/questions/choice/instructions").asText());assertEquals("alpha",root.at("/questions/choice/criteria/a").asText());assertEquals("low",root.at("/questions/score/criteria/0").asText());assertEquals("yes",root.at("/questions/noul/criteria/true").asText());assertFalse(new String(bodies.getFirst(),StandardCharsets.UTF_8).contains("fake-token"));}
 @Test void offlineAndConsentDoNotReachServer() {assertThrows(IllegalStateException.class,()->new JevClient(endpoint,"fake-token",true,true).ask("safe","jev-1.13.0",qs()));assertThrows(IllegalStateException.class,()->new JevClient(endpoint,"fake-token",false,false).ask("safe","jev-1.13.0",qs()));assertEquals(0,requests.get());}
 @Test void authAndValidationAreNotRetried() {for(int s:List.of(401,422)){status.set(s);assertThrows(IllegalArgumentException.class,()->new JevClient(endpoint,"fake-token",false,true).ask("safe","jev-1.13.0",qs()));assertEquals(1,requests.get());requests.set(0);}}
}
