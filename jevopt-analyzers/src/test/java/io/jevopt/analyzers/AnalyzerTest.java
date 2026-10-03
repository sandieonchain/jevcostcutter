package io.jevopt.analyzers;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.io.TempDir; import java.nio.file.*; import java.util.*; import java.net.*; import java.nio.charset.StandardCharsets; import com.sun.net.httpserver.*; import static org.junit.jupiter.api.Assertions.*;
class AnalyzerTest {
 @TempDir Path temp;
 AnalyzerRequest request(){return new AnalyzerRequest("candidate-aaaaaaaaaaaa","b".repeat(64),"Java","CHOICE",List.of("a","b"),"{}");}
 @Test void strictSchemaRejectsUnknownAndNoLlmIsZero() throws Exception {assertThrows(AnalyzerException.class,()->StrictAnalyzerJson.parse("{\"version\":\"x\",\"assessment\":\"bounded\",\"confidence\":1,\"reasons\":[],\"extra\":1}","fake"));var r=new NoLlmAnalyzer().analyze(request());assertEquals(0,r.usage().requestBytes());}
 @Test void fakeExecutableUsesArgvAndStrictJson() throws Exception {Path fake=temp.resolve("fake");Files.writeString(fake,"#!/bin/sh\ncat >/dev/null\nprintf '%s' '{\"version\":\"fake-1\",\"assessment\":\"bounded\",\"confidence\":0.8,\"reasons\":[\"fixture\"]}'\n");fake.toFile().setExecutable(true);Path schema=temp.resolve("schema.json");Files.writeString(schema,"{}");var result=new CodexCliAnalyzer(fake,schema).analyze(request());assertEquals("bounded",result.assessment());assertEquals("fake-1",result.version());}
 @Test void ollamaRejectsRemoteAndDoesNotPull() {assertThrows(IllegalArgumentException.class,()->new OllamaAnalyzer(URI.create("http://example.com:11434"),"x"));assertFalse(new OllamaAnalyzer(URI.create("http://127.0.0.1:9"),"pinned").capabilities().available());}
 @Test void ollamaLoopbackContractUsesPinnedInstalledModel() throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);List<String> paths=new ArrayList<>();
  server.createContext("/api/tags",exchange->{paths.add(exchange.getRequestURI().getPath());byte[] body="{\"models\":[{\"name\":\"pinned\"}]}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
  server.createContext("/api/generate",exchange->{paths.add(exchange.getRequestURI().getPath());byte[] body="{\"model\":\"pinned\",\"response\":\"{\\\"version\\\":\\\"local-1\\\",\\\"assessment\\\":\\\"bounded\\\",\\\"confidence\\\":0.9,\\\"reasons\\\":[\\\"fixture\\\"]}\",\"done\":true}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
  server.start();try{var analyzer=new OllamaAnalyzer(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"pinned");var result=analyzer.analyze(request());assertEquals("bounded",result.assessment());assertEquals(List.of("/api/tags","/api/generate"),paths);assertTrue(result.usage().requestBytes()>0);assertTrue(result.usage().responseBytes()>0);}finally{server.stop(0);}
 }
}
