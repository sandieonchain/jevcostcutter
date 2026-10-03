package io.jevopt.cli;
import io.jevopt.core.*;
import io.jevopt.detectors.ConservativeDetector;
import io.jevopt.report.ReportRenderer;
import io.jevopt.scanner.RepositoryScanner;
import io.jevopt.security.*;
import io.jevopt.traces.*;
import io.jevopt.shadow.*;
import io.jevopt.economics.*;
import io.jevopt.patch.*;
import io.jevopt.jev.*;
import io.jevopt.analyzers.*;
import picocli.CommandLine;
import picocli.CommandLine.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.Callable;
import java.math.BigDecimal;
@Command(name="jevopt", mixinStandardHelpOptions=true, subcommands={JevOpt.Traces.class,JevOpt.Shadow.class}, description="Read-only bounded-decision profiler with explicit remote opt-in.")
public final class JevOpt implements Runnable {
 enum Format { text, json }
 @Option(names="--repo", scope=ScopeType.INHERIT, description="Repository directory") Path repo=Path.of(".");
 @Option(names="--format", scope=ScopeType.INHERIT, description="text or json") Format format=Format.text;
 @Option(names="--offline", scope=ScopeType.INHERIT) boolean offline;
 @Option(names="--no-llm", scope=ScopeType.INHERIT) boolean noLlm;
 @Option(names="--provider", scope=ScopeType.INHERIT, description="none, codex, claude, or ollama") String provider="none";
 @Option(names="--allow-provider", scope=ScopeType.INHERIT, description="Explicit consent for one advisory provider invocation") boolean allowProvider;
 @Option(names="--provider-executable", scope=ScopeType.INHERIT) Path providerExecutable;
 @Option(names="--provider-model", scope=ScopeType.INHERIT) String providerModel;
 @Option(names="--provider-api-key-env", scope=ScopeType.INHERIT) String providerApiKeyEnv;
 @Option(names="--provider-codex-home-env", scope=ScopeType.INHERIT) String providerCodexHomeEnv;
 @Option(names="--provider-endpoint", scope=ScopeType.INHERIT) String providerEndpoint;
 @Option(names="--redact-term", scope=ScopeType.INHERIT, description="Additional private identifier to remove from analyzer snippets") List<String> redactTerms=new ArrayList<>();
 @Option(names="--state-dir", scope=ScopeType.INHERIT, description="Explicit external JevOpt state directory") Path stateDirectory;
 @Option(names="--pricing", scope=ScopeType.INHERIT, description="Versioned user-supplied JSON pricing snapshot") Path pricingFile;
 @Spec CommandLine.Model.CommandSpec spec;
 private final ReportRenderer renderer=new ReportRenderer();
 @Override public void run() { spec.commandLine().usage(spec.commandLine().getOut()); }
 private void requireOfflineProvider() { if(!Set.of("none","codex","claude","ollama").contains(provider)) throw new IllegalArgumentException("Unsupported provider"); if(!provider.equals("none")&&(offline||noLlm)) throw new IllegalArgumentException("Offline/no-LLM mode prohibits provider process or network invocation"); }
 private AnalyzerProvider analyzer() {
  requireOfflineProvider(); if(provider.equals("none"))return new NoLlmAnalyzer();
  if(stateDirectory==null)throw new IllegalArgumentException("External --state-dir is required for provider accounting");
  if(!allowProvider)throw new IllegalArgumentException("Explicit --allow-provider consent required before provider invocation");
  if(providerModel==null||!providerModel.matches("[A-Za-z0-9._:-]{1,120}"))throw new IllegalArgumentException("Exact --provider-model required");
  if(provider.equals("ollama")){if(providerEndpoint==null)throw new IllegalArgumentException("Exact loopback --provider-endpoint required");return new OllamaAnalyzer(java.net.URI.create(providerEndpoint),providerModel);}
  if(providerExecutable==null)throw new IllegalArgumentException("Exact --provider-executable required");
  if(provider.equals("codex")){if(!"CODEX_HOME".equals(providerCodexHomeEnv))throw new IllegalArgumentException("Explicit --provider-codex-home-env CODEX_HOME required");return new CodexCliAnalyzer(providerExecutable,providerModel,providerCodexHomeEnv);}
  if(!"ANTHROPIC_API_KEY".equals(providerApiKeyEnv))throw new IllegalArgumentException("Explicit ANTHROPIC_API_KEY reference required");return new ClaudeCliAnalyzer(providerExecutable,providerModel,providerApiKeyEnv);
 }
 private String advisory="none";
 private AnalysisReport scan() throws Exception {
  requireOfflineProvider();
  var candidates=new ArrayList<DecisionCandidate>();
  var detector=new ConservativeDetector();
  var coverage=new RepositoryScanner().scan(new RepositorySandbox(repo), source->candidates.addAll(detector.detect(source)));
  // V1 intentionally uses repository-wide eligible-source freshness: every prompt/tool/consumer edit stales evidence.
  for(int i=0;i<candidates.size();i++){var c=candidates.get(i);candidates.set(i,new DecisionCandidate(c.candidateId(),c.relativePath(),c.startLine(),c.framework(),c.category(),c.suggestedPrimitive(),c.status(),c.detectorConfidence(),c.positiveSignals(),c.negativeSignals(),c.requiredEvidence(),coverage.eligibleSourceFingerprint()));}
  candidates.sort(Comparator.comparing(DecisionCandidate::relativePath).thenComparingInt(DecisionCandidate::startLine));
  if(!provider.equals("none")) {
   var a=analyzer();var redactor=new Redactor();int analyzed=0;
   try(var state=store()) {for(var c:candidates.stream().filter(x->x.status().equals("STATIC_CANDIDATE")).toList()) {
    String raw="";try{String src=new RepositorySandbox(repo).read(c.relativePath());String[] lines=src.split("\\n");int start=Math.max(0,c.startLine()-2),end=Math.min(lines.length,c.startLine()+2);raw=String.join("\\n",Arrays.copyOfRange(lines,start,end));}catch(Exception ignored){}
    String snippet=sanitizeSnippet(redactor.redact(raw));int redactions=raw.equals(snippet)?0:1;
    var object=StrictJson.MAPPER.createObjectNode();object.put("candidateId",c.candidateId());object.put("path","<repo>/source");object.put("primitive",c.suggestedPrimitive());object.put("snippet",snippet);String bundle=StrictJson.MAPPER.writeValueAsString(object);
    AnalyzerRequest request=new AnalyzerRequest(c.candidateId(),c.staticFingerprint(),"Java",c.suggestedPrimitive(),List.of("a","b"),bundle);
    try{AnalyzerResult r=a.analyze(request);AnalyzerUsage u=r.usage();state.recordProviderUsage(provider,safeVersion(r.version()),1,1,0,u.requestBytes(),u.responseBytes(),0,redactions+u.redactions());advisory=provider+":"+r.assessment();analyzed++;}
    catch(AnalyzerException e){state.recordProviderUsage(provider,"unknown",1,0,1,bundle.getBytes(StandardCharsets.UTF_8).length,0,0,redactions);advisory=provider+":error";throw e;}
   }}
   advisory=provider+":completed="+analyzed;
  }
  return new AnalysisReport(coverage.scanned(),coverage.skipped(),coverage.truncated(),List.copyOf(candidates),0,0,0,
   List.of("Conservative pattern matching, not AST/dataflow proof", "Static candidates require runtime validation", "Advisory analyzer="+advisory+"; it never authorizes recommendation or patch", "Hidden directories and unsupported languages excluded"));
 }
 private String sanitizeSnippet(String value){String result=value;for(String term:redactTerms){if(term==null||term.length()<3||term.length()>120||term.matches(".*[\\r\\n].*"))throw new IllegalArgumentException("Invalid redact term");result=result.replace(term,"[REDACTED]");}return result;}
 private static String safeVersion(String value){return value!=null&&value.matches("[A-Za-z0-9._:-]{1,80}")?value:"unknown";}
 private void emit(Object value) throws Exception { spec.commandLine().getOut().println(renderer.json(value)); }
 private void reportOutput(AnalysisReport report) throws Exception {
  if(format==Format.json) emit(report); else spec.commandLine().getOut().print(renderer.text(report));
 }
 @Command(name="analyze", description="Read-only offline call-site inventory")
 int analyze() throws Exception { reportOutput(scan()); return 0; }
 @Command(name="report", description="Recompute current report without persisted raw source")
 int report() throws Exception {
  if(stateDirectory==null)return analyze();
  var analysis=scan();var runtime=runtimeReport(analysis);
  if(format==Format.json)emit(Map.of("analysis",analysis,"runtime",runtime));
  else{spec.commandLine().getOut().print(renderer.text(analysis));runtimeOutput(runtime);}
  return 0;
 }
 private StateStore store() throws Exception {requireOfflineProvider();return new StateStore(stateDirectory,new RepositorySandbox(repo).root());}
 private Map<String,Object> runtimeReport(AnalysisReport analysis) throws Exception {
  try(var state=store()) {
   var samples=state.samples();var active=ShadowMetrics.active(samples,analysis.candidates());var replay=state.latest();
   var metrics=ShadowMetrics.report(samples,active,replay);
   metrics.put("economics",CostEngine.report(active,replay==null?List.of():replay.results(),pricingFile==null?null:PricingSnapshot.read(pricingFile)));
   metrics.put("targetWrites",0);metrics.put("targetExecutions",0);metrics.put("networkRequests",0);
   return metrics;
  }
 }
 private static Object known(Object value){return value==null?"unknown":value;}
 private void runtimeOutput(Map<String,Object> data) throws Exception {
  if(format==Format.json){emit(data);return;}
  var out=spec.commandLine().getOut();out.println("REAL SANITIZED V3 SHADOW REPORT".equals("REAL SANITIZED V3 SHADOW REPORT")&&"REAL_SANITIZED_V3_REPLAY".equals(data.get("mode"))?"REAL SANITIZED V3 SHADOW REPORT (production unchanged)":"SYNTHETIC OFFLINE SHADOW REPORT (not Jev validation)");
  for(String key:List.of("importedSamples","activeSamples","staleSamples","replayedSamples","coverage","rawAgreement","acceptedAgreement","abstentionRate","evaluatorVersion","threshold"))out.println(key+": "+known(data.get(key)));
  out.println("Per-candidate coverage, confusion and component-latency metrics:");out.println(renderer.json(data.get("candidates")));
  out.println("Estimated economics (unknown values are null; not validated savings):");out.println(renderer.json(data.get("economics")));
  out.println("Production unchanged; target writes/execution/network requests: 0. Fixtures cannot support migration recommendations.");
 }
 @Command(name="candidates", description="List static bounded-decision candidates")
 int candidates() throws Exception {
  var r=scan(); var selected=r.candidates().stream().filter(c->c.status().equals("STATIC_CANDIDATE")).toList();
  if(format==Format.json) emit(selected); else for(var c:selected) spec.commandLine().getOut().println(c.candidateId()+" "+c.relativePath()+":"+c.startLine()+" "+c.category());
  return 0;
 }
 @Command(name="explain", description="Explain a stable candidate ID against current source")
 int explain(@Parameters(index="0") String id) throws Exception {
  var found=scan().candidates().stream().filter(c->c.candidateId().equals(id)).findFirst();
  if(found.isEmpty()) { spec.commandLine().getErr().println("Candidate not found in current scan; source may have changed."); return 3; }
  if(format==Format.json) emit(found.get()); else {
   var c=found.get(); spec.commandLine().getOut().println(c.candidateId()+"\n"+c.relativePath()+":"+c.startLine()+"\n"+c.status()+" -> "+c.suggestedPrimitive()+"\nPositive: "+String.join("; ",c.positiveSignals())+"\nNegative: "+String.join("; ",c.negativeSignals())+"\nRequired: "+String.join("; ",c.requiredEvidence()));
  }
  return 0;
 }
 @Command(name="doctor", description="Check local boundary and runtime; no credential-store inspection")
 int doctor() throws Exception {
  requireOfflineProvider(); new RepositorySandbox(repo);
  AnalyzerCapabilities cap=provider.equals("none")?new NoLlmAnalyzer().capabilities():provider.equals("ollama")?(providerEndpoint==null?new AnalyzerCapabilities(false,true,false,"endpoint not configured"):new OllamaAnalyzer(java.net.URI.create(providerEndpoint),providerModel==null?"unset":providerModel).capabilities()):(providerExecutable==null?new AnalyzerCapabilities(false,false,provider.equals("claude"),"executable not configured"):new AnalyzerCapabilities(Files.isRegularFile(providerExecutable,LinkOption.NOFOLLOW_LINKS)&&Files.isExecutable(providerExecutable)&&!Files.isSymbolicLink(providerExecutable),false,provider.equals("claude"),"configured executable; version not invoked"));
  var result=Map.of("java",Runtime.version().feature(),"repositoryReadable",true,"analyzer",provider,"capability",cap,"networkEnabled",false,"telemetry",false,"credentialStoresInspected",false);
  emit(result); return 0;
 }
 @Command(name="init", description="Create configuration and SQLite schema OUTSIDE the target")
 int init() throws Exception {
  Path supplied=Objects.requireNonNull(stateDirectory,"External state directory required");
  requireOfflineProvider(); Path root=new RepositorySandbox(repo).root();
  Path state=supplied.toAbsolutePath().normalize();
  if(Files.exists(state,LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("State directory must be new");
  Path parent=state.getParent().toRealPath();
  state=parent.resolve(state.getFileName());
  if(state.startsWith(root)) throw new IllegalArgumentException("State must be outside target");
  Files.createDirectory(state);
  try { Files.setPosixFilePermissions(state,PosixFilePermissions.fromString("rwx------")); } catch(UnsupportedOperationException ignored) { }
  try {
   Files.writeString(state.resolve("config.json"),"{\"version\":1,\"analyzer\":\"none\",\"telemetry\":false,\"storeRawTraces\":false,\"followSymlinks\":false,\"autoApply\":false}\n",StandardOpenOption.CREATE_NEW);
   try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+state.resolve("state.db")); Statement statement=connection.createStatement()) {
    statement.executeUpdate("CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT NOT NULL)");
    statement.executeUpdate("INSERT INTO metadata VALUES ('schema_version','1')");
   }
   try { Files.setPosixFilePermissions(state.resolve("config.json"),PosixFilePermissions.fromString("rw-------")); Files.setPosixFilePermissions(state.resolve("state.db"),PosixFilePermissions.fromString("rw-------")); } catch(UnsupportedOperationException ignored) { }
  } catch(Exception failure) { throw new IllegalStateException("Initialization incomplete; inspect external state directory"); }
  PrivacyLog.event("Initialized offline configuration; telemetry disabled");
  emit(Map.of("initialized",true,"targetWrites",0,"networkRequests",0,"privacy","Local only; target code not executed; target files not modified; no credentials inspected"));
  return 0;
 }
 @Command(name="config",description="Read verified privacy defaults and schema version")
 int config() throws Exception {try(var state=store()){emit(state.config());}return 0;}
 @Command(name="purge",description="Explicitly remove runtime samples/replays from external state only")
 int purge(@Option(names="--confirm",required=true) boolean confirm) throws Exception {
  if(!confirm)throw new IllegalArgumentException("Confirmation required");
  try(var state=store()){emit(Map.of("samplesRemoved",state.purge(),"configurationPreserved",true,"targetWrites",0,"forensicErasureGuaranteed",false));}return 0;
 }
 @Command(name="privacy-report",description="Report local provider accounting; tokens, prompts and credentials are never emitted")
 int privacyReport() throws Exception { try(var state=store()) { var usage=state.providerUsage();long invocations=usage.stream().mapToLong(StateStore.ProviderUsage::invocations).sum(),requestBytes=usage.stream().mapToLong(StateStore.ProviderUsage::requestBytes).sum(),responseBytes=usage.stream().mapToLong(StateStore.ProviderUsage::responseBytes).sum(),redactions=usage.stream().mapToLong(StateStore.ProviderUsage::redactions).sum();emit(Map.of("providerInvocationAttempts",invocations,"requestBytes",requestBytes,"responseBytes",responseBytes,"redactionsApplied",redactions,"usage",usage,"providerPayloadSnippetsStored",0,"credentialStoresInspected",false,"limitations","Counts cover explicit JevOpt invocations; provider-side retries or internal CLI transport are not observable")); } return 0; }
 @Command(name="pricing",description="Validate and display the selected dated pricing snapshot")
 int pricing() throws Exception {if(pricingFile==null)emit(Map.of("known",false,"reason","No pricing snapshot selected; dollar totals remain unknown"));else emit(PricingSnapshot.read(pricingFile));return 0;}
 @Command(name="traces",description="Strict explicit runtime evidence import")
 static final class Traces {
  @ParentCommand JevOpt parent;
  @Command(name="import",description="Import categorical JSON/JSONL, never raw prompts")
  int importFile(@Parameters(index="0") Path path) throws Exception {
   var samples=new TraceImporter().read(path,parent.scan().candidates());
   try(var state=parent.store()){parent.emit(state.add(samples));}return 0;
  }
 }
 @Command(name="shadow",description="Offline synthetic or explicitly consented sanitized v3 replay; never controls production")
 static final class Shadow {
  @ParentCommand JevOpt parent;
  @Command(name="start",description="Replay a synthetic fixture or explicitly approved sanitized v3 evidence")
  int start(@Option(names="--fixtures") Path fixtures,@Option(names="--jev") boolean jev,@Option(names="--allow-remote") boolean allowRemote,@Option(names="--api-key-env") String apiKeyEnv,@Option(names="--endpoint") String endpoint,@Option(names="--threshold",defaultValue="0.90") BigDecimal threshold) throws Exception {
   if(threshold.scale()>6 || threshold.signum()<0 || threshold.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Invalid threshold");
   if(jev== (fixtures!=null))throw new IllegalArgumentException("Choose exactly one replay source");
   if(jev&&(parent.offline||!allowRemote))throw new IllegalArgumentException("Offline mode or missing explicit remote consent prohibits transport");
   var analysis=parent.scan();
   try(var state=parent.store()){
    var active=ShadowMetrics.active(state.samples(),analysis.candidates());
    ShadowEvaluator evaluator;
    if(jev){if(apiKeyEnv==null||!apiKeyEnv.matches("JEVOPT_[A-Z0-9_]{1,80}"))throw new IllegalArgumentException("Named JEVOPT API key environment required");String token=System.getenv(apiKeyEnv);if(token==null||token.isBlank())throw new IllegalArgumentException("Configured token unavailable");java.net.URI ep=endpoint==null?JevClient.DEFAULT_ENDPOINT:java.net.URI.create(endpoint);specOut(parent,"Remote replay: provider host="+ep.getHost()+", model="+JevClient.PINNED_MODEL+", sanitized user-imported state will be sent; token/state are not printed.");evaluator=new JevReplayEvaluator(new JevClient(ep,token,false,true));}
    else evaluator=new FixtureEvaluator(fixtures,active);
    List<ShadowResult> results=new ArrayList<>();for(var sample:active)evaluator.evaluate(sample,threshold).ifPresent(results::add);
    state.saveReplay(evaluator.version(),threshold,active.size(),results);
    if(evaluator instanceof JevReplayEvaluator real)state.recordProviderUsage("jev",real.version(),real.requests(),real.requests(),0,real.requestBytes(),real.responseBytes(),real.inputTokens(),0);
   }
   parent.runtimeOutput(parent.runtimeReport(analysis));return 0;
  }
  private static void specOut(JevOpt parent,String s){parent.spec.commandLine().getOut().println(s);}
  @Command(name="status") int status() throws Exception {parent.runtimeOutput(parent.runtimeReport(parent.scan()));return 0;}
  @Command(name="report") int report() throws Exception {return status();}
 }
 @Command(name="patch",description="Emit a review-only diff in external JevOpt state; never applies a patch")
 int patch(@Parameters(index="0") String candidateId) throws Exception {
  var analysis=scan(); var candidate=analysis.candidates().stream().filter(c->c.candidateId().equals(candidateId)).findFirst().orElseThrow(()->new IllegalArgumentException("Candidate not found"));
  try(var state=store()) { var active=ShadowMetrics.active(state.samples(),analysis.candidates());var replay=state.latest();
   var results=replay==null?List.<ShadowResult>of():replay.results();var verdict=new RecommendationEngine().evaluate(active,results,new RecommendationEngine.Policy(20,0.95,false),active.size()!=state.samples().size());
   Path generated=PatchGenerator.generate(stateDirectory.toAbsolutePath().normalize(),repo,candidate,verdict);
   emit(Map.of("patch",generated.getFileName().toString(),"applied",false,"compilationRun",false,"testsRun",false,"fallback","Retain existing LLM fallback on abstention or error","verdict",verdict.toString())); }
  return 0;
 }
 @Command(name="version") int version() throws Exception { if(format==Format.json) emit(Map.of("name","JevOpt","version","1.0.0-rc1")); else spec.commandLine().getOut().println("JevOpt 1.0.0-rc1"); return 0; }
 public static CommandLine commandLine() {
  CommandLine cli=new CommandLine(new JevOpt());
  cli.setExecutionExceptionHandler((ex, cmd, result)->{cmd.getErr().println("Operation rejected or failed; check repository permissions, supported provider, and external state directory. No exception payload is disclosed.");return 2;});
  cli.setParameterExceptionHandler((ex,args)->{ex.getCommandLine().getErr().println("Invalid arguments. Run jevopt --help for supported options.");return 2;});
  return cli;
 }
 public static void main(String[] args) {
  CommandLine cli=commandLine(); int status=cli.execute(args);
  cli.getOut().flush(); cli.getErr().flush(); System.exit(status);
 }
}
