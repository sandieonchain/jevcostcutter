package io.jevopt.traces;
import io.jevopt.core.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
public final class TraceImporter {
 public static String fingerprint(String candidate,String event) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((candidate+"\n"+event).getBytes(StandardCharsets.UTF_8))); }
  catch(NoSuchAlgorithmException e) { throw new IllegalStateException("Digest unavailable"); }
 }
 public List<RuntimeSample> read(Path path,List<DecisionCandidate> current) throws Exception {
  Map<String,DecisionCandidate> candidates=new HashMap<>();
  for(var c:current) if(c.status().equals("STATIC_CANDIDATE")) candidates.put(c.candidateId(),c);
  String input=StrictJson.read(path).strip(); List<JsonNode> records=new ArrayList<>();
  if(input.startsWith("[")) {
   JsonNode array=StrictJson.parse(input);
   if(!array.isArray() || array.size()>10000) throw new IllegalArgumentException("Trace count exceeded");
   array.forEach(records::add);
  } else {
   for(String line:input.split("\\R")) {
    if(!line.isBlank()) records.add(StrictJson.parse(line));
    if(records.size()>10000) throw new IllegalArgumentException("Trace count exceeded");
   }
  }
  if(records.isEmpty()) throw new IllegalArgumentException("Empty trace");
  Map<String,RuntimeSample> samples=new LinkedHashMap<>();
  for(JsonNode n:records) {
   long traceVersion=StrictJson.integer(n,"version",3);
   if(traceVersion==1) StrictJson.fields(n,"version","eventId","candidateId","timestampBucket","observedDecision","observedModel","inputTokens","outputTokens","latencyMs","options","synthetic");
   else if(traceVersion==2) StrictJson.fields(n,"version","eventId","candidateId","timestampBucket","observedDecision","observedModel","inputTokens","outputTokens","latencyMs","options","synthetic","decisionState","question");
   else if(traceVersion==3) StrictJson.fields(n,"version","eventId","candidateId","timestampBucket","observedDecision","observedModel","inputTokens","outputTokens","latencyMs","options","synthetic","decisionState","question","jevModel","sourceEvidenceFingerprint");
   else throw new IllegalArgumentException("Unsupported trace schema");
   String id=StrictJson.text(n,"candidateId");
   var c=candidates.get(id); if(c==null) throw new IllegalArgumentException("Unknown or ineligible candidate");
   String event=StrictJson.label(StrictJson.text(n,"eventId"),64);
   List<String> options=StrictJson.options(n.get("options"));
   String contract="";
   String state=null,instructions=null,jevModel=null,evidence=null;Map<String,String> descriptions=Map.of();
   if(traceVersion==2) contract=validateV2(n,options,c.suggestedPrimitive());
   if(traceVersion==3) { V3 v=validateV3(n,options,c.suggestedPrimitive());contract=v.contract;state=v.state;instructions=v.instructions;descriptions=v.criteria;jevModel=v.model;evidence=v.evidence; }
   String observed=StrictJson.label(StrictJson.text(n,"observedDecision"),24);
   if(!options.contains(observed)) throw new IllegalArgumentException("Observed decision outside options");
   if(c.suggestedPrimitive().equals("NOUL") && !new HashSet<>(options).equals(Set.of("true","false"))) throw new IllegalArgumentException("Noul requires boolean options");
   if(c.suggestedPrimitive().equals("SCORE")) {
    int previous=-1;
    for(String option:options) { int level=Integer.parseInt(option); if(level<0 || level>100 || level<=previous) throw new IllegalArgumentException("Score levels must be ordered"); previous=level; }
   }
   var sample=new RuntimeSample(fingerprint(id,traceVersion==1?event:event+"\n"+contract),id,c.staticFingerprint(),c.suggestedPrimitive(),
    StrictJson.day(n,"timestampBucket"),observed,StrictJson.label(StrictJson.text(n,"observedModel"),64),
    StrictJson.integer(n,"inputTokens",10_000_000),StrictJson.integer(n,"outputTokens",10_000_000),StrictJson.integer(n,"latencyMs",86_400_000),
    options,StrictJson.bool(n,"synthetic"),(int)traceVersion,state,instructions,descriptions,jevModel,evidence);
   RuntimeSample prior=samples.putIfAbsent(sample.fingerprint(),sample);
   if(prior!=null && !prior.equals(sample)) throw new IllegalArgumentException("Conflicting duplicate event");
  }
  return List.copyOf(samples.values());
 }
 private record V3(String contract,String state,String instructions,Map<String,String> criteria,String model,String evidence){}
 private static V3 validateV3(JsonNode n,List<String> options,String primitive) {
  JsonNode ds=n.get("decisionState"),q=n.get("question");StrictJson.fields(ds,"sanitizedState");
  String state=StrictJson.text(ds,"sanitizedState");if(state.length()>24000||state.toLowerCase(Locale.ROOT).matches(".*(authorization:|bearer |api[_-]?key|password|secret).*"))throw new IllegalArgumentException("Unsafe sanitized state");
  StrictJson.fields(q,"id","type","instructions","criteria");String id=StrictJson.label(StrictJson.text(q,"id"),64),type=StrictJson.text(q,"type"),instructions=StrictJson.text(q,"instructions");if(!primitive.equals(type)||instructions.length()>4096||instructions.toLowerCase(Locale.ROOT).matches(".*(authorization:|bearer |api[_-]?key|password|secret).*"))throw new IllegalArgumentException("Invalid v3 question");
  String model=StrictJson.text(n,"jevModel"), evidence=StrictJson.text(n,"sourceEvidenceFingerprint");if(!model.equals("jev-1.13.0")||!evidence.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Pinned model/evidence required");
  Map<String,String> criteria=new LinkedHashMap<>();JsonNode c=q.get("criteria");
  if(primitive.equals("SCORE")){if(!c.isArray()||c.size()<2||c.size()>10)throw new IllegalArgumentException("Invalid score criteria");for(int i=0;i<c.size();i++){if(!c.get(i).isTextual()||c.get(i).textValue().isBlank()||c.get(i).textValue().length()>4096)throw new IllegalArgumentException("Invalid score criteria");criteria.put(String.valueOf(i),c.get(i).textValue());}if(!options.equals(new ArrayList<>(criteria.keySet())))throw new IllegalArgumentException("Score options mismatch");}
  else if(primitive.equals("CHOICE")){if(!c.isObject()||c.size()<1||c.size()>255)throw new IllegalArgumentException("Invalid choice criteria");for(String k:options){JsonNode v=c.get(k);if(v==null||!v.isTextual()||v.textValue().isBlank()||v.textValue().length()>4096)throw new IllegalArgumentException("Choice criteria mismatch");criteria.put(k,v.textValue());}if(c.size()!=criteria.size())throw new IllegalArgumentException("Choice criteria mismatch");}
  else {if(c!=null&&!c.isNull()){if(!c.isObject()||!c.fieldNames().hasNext())throw new IllegalArgumentException("Invalid noul criteria");for(String k:List.of("true","false")){JsonNode v=c.get(k);if(v==null||!v.isTextual()||v.textValue().isBlank()||v.textValue().length()>4096)throw new IllegalArgumentException("Invalid noul criteria");criteria.put(k,v.textValue());}if(c.size()!=2)throw new IllegalArgumentException("Invalid noul criteria");}}
  return new V3(state+"\n"+id+"\n"+type+"\n"+instructions+"\n"+criteria+"\n"+model+"\n"+evidence,state,instructions,Map.copyOf(criteria),model,evidence);
 }
 private static String validateV2(JsonNode n,List<String> options,String primitive) {
  JsonNode state=n.get("decisionState"), question=n.get("question");
  StrictJson.fields(state,"stateFingerprint","sourceEvidenceFingerprint");
  String sf=StrictJson.text(state,"stateFingerprint"), ef=StrictJson.text(state,"sourceEvidenceFingerprint");
  if(!sf.matches("[a-f0-9]{64}")||!ef.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Sanitized fingerprints required");
  StrictJson.fields(question,"id","type","options","model");
  String qid=StrictJson.label(StrictJson.text(question,"id"),64), type=StrictJson.text(question,"type"), model=StrictJson.label(StrictJson.text(question,"model"),64);
  if(!primitive.equals(type)||!StrictJson.options(question.get("options")).equals(options))throw new IllegalArgumentException("Question contract mismatch");
  // No free text exists in v2: raw prompt/state/payload/auth fields are rejected by exact schemas.
  return sf+"\n"+ef+"\n"+qid+"\n"+type+"\n"+options+"\n"+model;
 }
}
