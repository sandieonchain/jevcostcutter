package io.jevopt.detectors;
import io.jevopt.core.*;
import io.jevopt.security.Redactor;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.*;
public final class ConservativeDetector implements CallSiteDetector {
 private static final Pattern CALL = Pattern.compile("\\b(?:[\\w]+\\.)*(chat\\s*\\.\\s*completions\\s*\\.\\s*create|responses\\s*\\.\\s*create|messages\\s*\\.\\s*create|generateText|generateObject)\\s*\\(");
 private static final Pattern NEGATIVE = Pattern.compile("(?i)generate.{0,20}code|write.{0,20}code|write.{0,20}(answer|response|story)|free.form|step.by.step|multi.step|debug|create.{0,20}plan");
 private final Redactor redactor = new Redactor();
 @Override public List<DecisionCandidate> detect(SourceFile source) {
  List<DecisionCandidate> found = new ArrayList<>();
  // Full-line comments cannot manufacture call sites. This is deliberately not an AST parser.
  String clean = source.content().replaceAll("(?m)^[\\t ]*(#|//).*$", "");
  Matcher m = CALL.matcher(clean);
  while (m.find()) {
   int line = 1 + (int)clean.substring(0,m.start()).chars().filter(c -> c == '\n').count();
   int end = callEnd(clean, m.end());
   String context = clean.substring(m.start(), end).toLowerCase(Locale.ROOT);
   String category = "UNKNOWN_REASONING_HEAVY", primitive = "NONE", status = "NEEDS_RUNTIME_EVIDENCE";
   List<String> positive = new ArrayList<>(), negative = new ArrayList<>();
   if (NEGATIVE.matcher(context).find()) {
    category = context.matches("(?s).*(generate|write).{0,20}code.*") ? "CODE_GENERATION" : "TEXT_GENERATION";
    status = "KEEP_LLM"; negative.add("Generation or multi-step reasoning language in call arguments");
   } else if (context.contains("dynamic") || context.contains("available_tools")) {
    negative.add("Option set is dynamic or cannot be proven bounded");
   } else if (context.contains("retry") && context.contains("stop")) {
    category = "RETRY_STOP_DECISION"; primitive = "CHOICE"; status = "STATIC_CANDIDATE"; positive.add("Explicit retry/stop alternatives");
   } else if (context.matches("(?s).*(true.{0,15}false|boolean|yes.{0,15}no).*")) {
    category = context.contains("relevan") ? "RELEVANCE_CHECK" : "BINARY_SEMANTIC_GATE";
    primitive = "NOUL"; status = "STATIC_CANDIDATE"; positive.add("Boolean output language");
   } else if (context.matches("(?s).*score.*(?:0.{0,5}5|1.{0,5}5|1.{0,5}10).*")) {
    category = "ORDERED_SCORING"; primitive = "SCORE"; status = "STATIC_CANDIDATE"; positive.add("Explicit bounded scoring range");
   } else if (context.contains("enum") || context.matches("(?s).*choose.{0,20}(one of|from).*")) {
    category = context.contains("tool") ? "TOOL_ROUTING" : "FIXED_CHOICE_ROUTING";
    primitive = "CHOICE"; status = "STATIC_CANDIDATE"; positive.add("Finite-choice output language");
   } else negative.add("Bounded output contract not established");
   // The ID is call-site stable, while freshness deliberately covers every eligible source byte.
   // This conservative V1 policy invalidates evidence when prompts, consumers or tools change.
   String callFingerprint = hash(source.relativePath() + "\n" + line + "\n" + clean.substring(m.start(),end));
   String fingerprint = hash("eligible-source-v1\n" + source.relativePath() + "\n" + clean);
   found.add(new DecisionCandidate("candidate-" + callFingerprint.substring(0,12),
    redactor.relativeLabel(source.relativePath()), line,
    m.group(1).startsWith("messages") ? "Anthropic-compatible" : m.group(1).startsWith("generate") ? "Vercel-style" : "OpenAI-compatible",
    category, primitive, status, 0.65, List.copyOf(positive), List.copyOf(negative),
    status.equals("KEEP_LLM") ? List.of("No migration recommended by static analysis") :
     List.of("Confirm bounded downstream consumer", "Representative labeled or shadow runtime samples", "Per-class coverage and high-risk review"),
    fingerprint));
  }
  return found;
 }
 private static int callEnd(String text, int start) {
  int depth = 1; char quote = 0; boolean escaped = false;
  int limit = Math.min(text.length(), start + 8000);
  for(int i=start;i<limit;i++) {
   char c=text.charAt(i);
   if (quote != 0) { if (escaped) escaped=false; else if(c=='\\') escaped=true; else if(c==quote) quote=0; continue; }
   if(c=='\'' || c=='"') quote=c;
   else if(c=='(') depth++;
   else if(c==')' && --depth==0) return i+1;
  }
  return limit;
 }
 private static String hash(String s) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
  catch (NoSuchAlgorithmException e) { throw new IllegalStateException("Digest unavailable"); }
 }
}
