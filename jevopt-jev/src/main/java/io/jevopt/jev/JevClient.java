package io.jevopt.jev;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.math.*;

/** Strict, opt-in TypeSafe System One adapter. It never discovers, stores, or logs credentials. */
public final class JevClient {
 public static final URI DEFAULT_ENDPOINT=URI.create("https://api.typesafe.ai/v1/systemone");
 public static final String PINNED_MODEL="jev-1.13.0";
 public static final int MAX_REQUEST_BYTES=48_000, MAX_RESPONSE_BYTES=64_000; // conservative local limits
 private static final ObjectMapper JSON=new ObjectMapper();
 private final URI endpoint; private final String token; private final boolean offline, consent; private final JevTransport transport;
 public JevClient(String token, boolean offline, boolean consent) { this(DEFAULT_ENDPOINT,token,offline,consent,new HttpTransport()); }
 public JevClient(URI endpoint,String token,boolean offline,boolean consent) { this(endpoint,token,offline,consent,new HttpTransport()); }
 public JevClient(URI endpoint,String token,boolean offline,boolean consent,JevTransport transport) {
  if(endpoint==null||transport==null||token==null||token.isBlank())throw new IllegalArgumentException("Explicit API token required");
  if(!"https".equals(endpoint.getScheme()) && !isLoopback(endpoint))throw new IllegalArgumentException("Endpoint must use HTTPS");
  if(!DEFAULT_ENDPOINT.equals(endpoint) && !isLoopback(endpoint))throw new IllegalArgumentException("Only loopback endpoint override is permitted");
  this.endpoint=endpoint;this.token=token;this.offline=offline;this.consent=consent;this.transport=transport;
 }
 public static JevClient fromNamedEnvironment(String envName,boolean offline,boolean consent) {
  if(envName==null||!envName.matches("JEVOPT_[A-Z0-9_]{1,80}"))throw new IllegalArgumentException("Named JEVOPT environment variable required");
  String token=System.getenv(envName); if(token==null||token.isBlank())throw new IllegalArgumentException("Configured token unavailable"); return new JevClient(token,offline,consent);
 }
 public JevResponse ask(String state, String model, List<JevQuestion> questions) throws Exception {
  if(offline) throw new IllegalStateException("Offline mode prohibits network before socket creation");
  if(!consent) throw new IllegalStateException("Remote provider disclosure requires explicit opt-in");
  if(state==null||state.isBlank()||state.length()>24_000||containsSecret(state)||model==null||!model.equals(PINNED_MODEL)||questions==null||questions.isEmpty()||questions.size()>32) throw new IllegalArgumentException("Unsafe or invalid Jev request");
  ObjectNode root=JSON.createObjectNode();root.put("state",state);root.put("model",model);ObjectNode qs=root.putObject("questions");Set<String> ids=new HashSet<>();
  for(JevQuestion q:questions){if(!ids.add(q.id()))throw new IllegalArgumentException("Duplicate question ID");ObjectNode n=qs.putObject(q.id());n.put("type",q.type().name().toLowerCase(Locale.ROOT));n.put("instructions",q.instructions());
   if(q.type()==JevQuestion.Type.CHOICE){ObjectNode c=n.putObject("criteria");for(var x:q.choiceCriteria().entrySet())c.put(x.getKey(),x.getValue());}
   else if(q.type()==JevQuestion.Type.SCORE){ArrayNode c=n.putArray("criteria");q.scoreCriteria().forEach(c::add);}
   else if(!q.noulCriteria().isEmpty()){ObjectNode c=n.putObject("criteria");for(var x:q.noulCriteria().entrySet())c.put(x.getKey(),x.getValue());}
  }
  byte[] body=JSON.writeValueAsBytes(root);if(body.length>MAX_REQUEST_BYTES)throw new IllegalArgumentException("Request exceeds local byte limit");
  JevTransport.Response reply=null; Exception last=null;
  for(int attempt=0;attempt<3;attempt++) try { reply=transport.post(endpoint,"Bearer "+token,body,Duration.ofSeconds(15)); if(reply.body()==null||reply.body().length>MAX_RESPONSE_BYTES)throw new IllegalArgumentException("Response exceeds local byte limit"); if(reply.status()>=200&&reply.status()<300)break; if(reply.status()==401||reply.status()==422||!(reply.status()==429||reply.status()==529||reply.status()>=500))throw new IllegalArgumentException("Provider rejected request"); pause(reply.retryAfter(),attempt); } catch(Exception e){last=e;if(e instanceof IllegalArgumentException)throw e;if(attempt==2)throw e; pause(null,attempt);}
  if(reply==null||reply.status()<200||reply.status()>=300)throw new IllegalStateException("Provider unavailable",last);
  return parse(reply.body(),questions,model,body.length);
 }
 private static void pause(String retryAfter,int attempt) throws InterruptedException { long ms=100L*(1L<<attempt);try{if(retryAfter!=null)ms=Math.min(2000,Math.max(ms,Long.parseLong(retryAfter)*1000));}catch(NumberFormatException ignored){} Thread.sleep(ms); }
 private static JevResponse parse(byte[] data,List<JevQuestion> questions,String requestedModel,long requestBytes)throws Exception {
  JsonNode n=JSON.readTree(data);if(n==null||!n.isObject()||!n.has("model")||!n.has("answers")||!n.has("usage")||n.size()!=3||!n.get("model").isTextual()||!requestedModel.equals(n.get("model").textValue())||!n.get("answers").isObject())throw new IllegalArgumentException("Malformed Jev response");
  JsonNode use=n.get("usage");if(!use.isObject()||use.size()!=2||!use.has("input_tokens")||!use.has("output_tokens")||!use.get("input_tokens").canConvertToLong()||!use.get("output_tokens").canConvertToLong())throw new IllegalArgumentException("Malformed usage");
  Map<String,JevQuestion> expected=new HashMap<>();for(var q:questions)expected.put(q.id(),q);List<JevAnswer> out=new ArrayList<>();
  if(n.get("answers").size()!=questions.size())throw new IllegalArgumentException("Missing answers");
  for(var entry:n.get("answers").properties()){String id=entry.getKey();JsonNode a=entry.getValue();JevQuestion q=expected.get(id);if(q==null||!a.isObject()||!a.has("type")||!a.get("type").isTextual()||!a.get("type").textValue().equals(q.type().name().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Mismatched answer");String key=q.type()==JevQuestion.Type.CHOICE?"choice":q.type()==JevQuestion.Type.SCORE?"score":"noul";if(!a.has(key))throw new IllegalArgumentException("Missing answer value");
   if(q.type()==JevQuestion.Type.NOUL){if(a.size()!=2)throw new IllegalArgumentException("Malformed Noul answer");BigDecimal value=decimal(a.get(key));if(value.signum()<0||value.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Invalid answer value");out.add(new JevAnswer(id,value.toPlainString(),Map.of(),null));continue;}
   if(q.type()==JevQuestion.Type.CHOICE&&a.size()!=4)throw new IllegalArgumentException("Malformed choice answer"); if(q.type()==JevQuestion.Type.SCORE&&a.size()!=5)throw new IllegalArgumentException("Malformed score answer");
   Map<String,BigDecimal> probs=distribution(a.get("probabilities"),q);BigDecimal confidence=decimal(a.get("confidence"));if(confidence.signum()<0||confidence.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Invalid confidence");
   String selected;if(q.type()==JevQuestion.Type.CHOICE){selected=a.get(key).isTextual()?a.get(key).textValue():null;if(selected==null||!q.choiceCriteria().containsKey(selected))throw new IllegalArgumentException("Invalid choice");}else{BigDecimal score=decimal(a.get(key));if(score.signum()<0||score.compareTo(BigDecimal.valueOf(q.scoreCriteria().size()-1))>0)throw new IllegalArgumentException("Invalid score");JsonNode legend=a.get("legend");if(!legend.isObject()||legend.size()!=q.scoreCriteria().size())throw new IllegalArgumentException("Invalid score legend");for(int i=0;i<q.scoreCriteria().size();i++)if(!legend.has(String.valueOf(i))||!legend.get(String.valueOf(i)).isTextual()||!q.scoreCriteria().get(i).equals(legend.get(String.valueOf(i)).textValue()))throw new IllegalArgumentException("Invalid score legend");selected=score.toPlainString();}out.add(new JevAnswer(id,selected,probs,confidence)); }
  if(out.size()!=questions.size())throw new IllegalArgumentException("Missing answers");return new JevResponse(requestedModel,List.copyOf(out),use.get("input_tokens").longValue(),use.get("output_tokens").longValue(),requestBytes,data.length);
 }
 private static Map<String,BigDecimal> distribution(JsonNode n,JevQuestion q){List<String> options=q.options();if(n==null||!n.isObject()||n.size()!=options.size())throw new IllegalArgumentException("Invalid probabilities");Map<String,BigDecimal> m=new LinkedHashMap<>();BigDecimal sum=BigDecimal.ZERO;for(String x:options){BigDecimal d=decimal(n.get(x));if(d.signum()<0||d.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Invalid probability");m.put(x,d);sum=sum.add(d);}if(sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.000001"))>0)throw new IllegalArgumentException("Invalid distribution");return Map.copyOf(m);}
 private static BigDecimal decimal(JsonNode n){try{if(n==null||!n.isNumber())throw new Exception();return n.decimalValue();}catch(Exception e){throw new IllegalArgumentException("Invalid numeric field");}}
 private static boolean isLoopback(URI u){return "http".equals(u.getScheme())&&Set.of("127.0.0.1","[::1]","::1").contains(u.getHost());}
 private static boolean containsSecret(String s){String x=s.toLowerCase(Locale.ROOT);return x.contains("authorization:")||x.contains("bearer ")||x.contains("api_key")||x.contains("password");}
 private static final class HttpTransport implements JevTransport { private final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();public Response post(URI u,String auth,byte[] body,Duration timeout)throws Exception{HttpRequest r=HttpRequest.newBuilder(u).timeout(timeout).header("Authorization",auth).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();HttpResponse<byte[]> x=client.send(r,HttpResponse.BodyHandlers.ofByteArray());return new Response(x.statusCode(),x.headers().firstValue("Retry-After").orElse(null),x.body());}}
}
