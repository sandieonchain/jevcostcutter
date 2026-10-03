package io.jevopt.traces;
import io.jevopt.core.*;
import io.jevopt.security.RepositorySandbox;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;
public final class StateStore implements AutoCloseable {
 private final Connection db;
 public record ImportSummary(int inserted,int duplicates,int total) {}
 public record Replay(long id,String evaluatorVersion,BigDecimal threshold,int importedAtReplay,List<ShadowResult> results) {}
 public record ProviderUsage(String provider,String providerVersion,long invocations,long successes,long failures,long requestBytes,long responseBytes,long inputTokens,long redactions) {}
 public StateStore(Path supplied,Path repository) throws Exception {
  if(supplied==null) throw new IllegalArgumentException("External state directory required");
  var boundary=new RepositorySandbox(supplied);
  Path state=boundary.root();
  if(state.startsWith(repository)) throw new IllegalArgumentException("State cannot be inside target");
  JsonNode config=StrictJson.parse(boundary.read("config.json"));
  StrictJson.fields(config,"version","analyzer","telemetry","storeRawTraces","followSymlinks","autoApply");
  StrictJson.version(config);
  if(!StrictJson.text(config,"analyzer").equals("none") || StrictJson.bool(config,"telemetry") || StrictJson.bool(config,"storeRawTraces") ||
    StrictJson.bool(config,"followSymlinks") || StrictJson.bool(config,"autoApply")) throw new IllegalArgumentException("Unsafe or unsupported configuration");
  // Database is owned state, not repository input; the repository read denylist deliberately rejects .db.
  Path file=state.resolve("state.db");
  if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) || Files.size(file)>100_000_000) throw new IllegalArgumentException("Invalid state database");
  rejectLinkedFile(file);
  for(String suffix:List.of("-journal","-wal","-shm")) if(Files.exists(state.resolve("state.db"+suffix),LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("State is busy or needs recovery");
  db=DriverManager.getConnection("jdbc:sqlite:"+file);
  try {
   try(Statement s=db.createStatement()) { s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=1000"); s.execute("PRAGMA secure_delete=ON"); }
   migrate(); validateSchema();
  } catch(Exception e) { db.close(); throw e; }
 }
 private void migrate() throws Exception {
  int version;
  try(Statement s=db.createStatement(); ResultSet r=s.executeQuery("SELECT value FROM metadata WHERE name='schema_version'")) {
   if(!r.next()) throw new IllegalArgumentException("Missing schema version"); version=Integer.parseInt(r.getString(1));
  }
  if(version<1 || version>4) throw new IllegalArgumentException("Unsupported database schema");
  while(version<4) {
   db.setAutoCommit(false);
   try(Statement s=db.createStatement()) {
   String migration=version==1?"/db/migration/V2__runtime_evidence.sql":version==2?"/db/migration/V3__state_hardening.sql":"/db/migration/V4__provider_usage.sql";
   try(var resource=StateStore.class.getResourceAsStream(migration)) {
    if(resource==null)throw new IllegalStateException("Migration missing");
    String sql=new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    for(String statement:sql.split(";"))if(!statement.isBlank())s.executeUpdate(statement);
   }
   db.commit(); version++;
  } catch(Exception e) { db.rollback(); throw e; } finally { db.setAutoCommit(true); }
  }
 }
 private void validateSchema() throws Exception {
  Set<String> tables=new HashSet<>();
  try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT type,name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'")) {
   while(r.next()) {String type=r.getString(1),name=r.getString(2);if(!"table".equals(type))throw new IllegalArgumentException("Unexpected database object");tables.add(name);}
  }
  if(!tables.equals(Set.of("metadata","samples","replay_runs","shadow_results","provider_usage")))throw new IllegalArgumentException("Unexpected database schema");
 }
 private static void rejectLinkedFile(Path file) throws Exception {
  try { Object n=Files.getAttribute(file,"unix:nlink",LinkOption.NOFOLLOW_LINKS); if(n instanceof Number x&&x.longValue()!=1)throw new IllegalArgumentException("Hard-linked state file denied"); }
  catch(UnsupportedOperationException ignored) { /* platform does not expose hard-link count */ }
 }
 public List<RuntimeSample> samples() throws Exception {
  List<RuntimeSample> values=new ArrayList<>();
  try(Statement s=db.createStatement(); ResultSet r=s.executeQuery("SELECT data FROM samples ORDER BY fingerprint")) {
   while(r.next()) { if(values.size()>=100000) throw new IllegalStateException("State count limit"); var sample=StrictJson.MAPPER.readValue(r.getString(1),RuntimeSample.class); validate(sample); values.add(sample); }
  }
  return List.copyOf(values);
 }
 private static void validate(RuntimeSample s) {
  if(!s.fingerprint().matches("[a-f0-9]{64}") || !s.staticFingerprint().matches("[a-f0-9]{64}") || !s.candidateId().matches("candidate-[a-f0-9]{12}") ||
    !Set.of("CHOICE","SCORE","NOUL").contains(s.primitive()) || !s.timestampBucket().matches("\\d{4}-\\d{2}-\\d{2}") ||
    s.inputTokens()<0 || s.inputTokens()>10000000 || s.outputTokens()<0 || s.outputTokens()>10000000 || s.latencyMs()<0 || s.latencyMs()>86400000 ||
    s.options().size()<2 || s.options().size()>32 || new HashSet<>(s.options()).size()!=s.options().size() || !s.options().contains(s.observedDecision())) throw new IllegalArgumentException("Invalid persisted sample");
  java.time.LocalDate.parse(s.timestampBucket());StrictJson.label(s.observedModel(),64);StrictJson.label(s.observedDecision(),24);
  for(String option:s.options())StrictJson.label(option,24);
  if(s.traceVersion()==3){if(s.synthetic()||s.sanitizedState()==null||s.questionInstructions()==null||s.criteriaDescriptions()==null||!"jev-1.13.0".equals(s.jevModel())||s.sourceEvidenceFingerprint()==null||!s.sourceEvidenceFingerprint().matches("[a-f0-9]{64}")||s.sanitizedState().length()>24000||s.questionInstructions().length()>4096)throw new IllegalArgumentException("Invalid persisted v3 sample");}
  else if(s.traceVersion()<1||s.traceVersion()>3)throw new IllegalArgumentException("Invalid trace version");
 }
 public ImportSummary add(List<RuntimeSample> samples) throws Exception {
  List<RuntimeSample> current=samples();
  Map<String,RuntimeSample> existing=new HashMap<>(); Map<String,List<String>> options=new HashMap<>();
  for(var sample:current) { existing.put(sample.fingerprint(),sample); options.put(sample.candidateId(),sample.options()); }
  int inserted=0,duplicates=0;
  db.setAutoCommit(false);
  try(PreparedStatement put=db.prepareStatement("INSERT INTO samples(fingerprint,candidate_id,data) VALUES(?,?,?)")) {
   for(var sample:samples) {
    validate(sample);
    if(options.containsKey(sample.candidateId()) && !options.get(sample.candidateId()).equals(sample.options())) throw new IllegalArgumentException("Option set drift; use separate state");
    var prior=existing.get(sample.fingerprint());
    if(prior!=null) { if(!prior.equals(sample)) throw new IllegalArgumentException("Conflicting duplicate event"); duplicates++; continue; }
    if(current.size()+inserted>=100000) throw new IllegalArgumentException("State count limit");
    put.setString(1,sample.fingerprint());put.setString(2,sample.candidateId());put.setString(3,StrictJson.MAPPER.writeValueAsString(sample));put.executeUpdate();
    existing.put(sample.fingerprint(),sample);options.put(sample.candidateId(),sample.options());inserted++;
   }
   db.commit(); return new ImportSummary(inserted,duplicates,current.size()+inserted);
  } catch(Exception e) { db.rollback(); throw e; } finally { db.setAutoCommit(true); }
 }
 public long saveReplay(String version,BigDecimal threshold,int importedCount,List<ShadowResult> results) throws Exception {
  if(results.isEmpty()) throw new IllegalArgumentException("No synthetic matching results");
  if((!version.equals("fixture-v1")&&!version.equals("jev-1.13.0")) || threshold.signum()<0 || threshold.compareTo(BigDecimal.ONE)>0 || importedCount<results.size()) throw new IllegalArgumentException("Invalid replay metadata");
  Map<String,RuntimeSample> samples=new HashMap<>();for(var s:samples())samples.put(s.fingerprint(),s);
  for(var r:results)validateResult(r,samples.get(r.sampleFingerprint()),threshold);
  try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT COUNT(*) FROM shadow_results")) {r.next();if(r.getLong(1)+results.size()>100000)throw new IllegalArgumentException("Replay retention limit reached; purge explicitly");}
  db.setAutoCommit(false);
  try(PreparedStatement run=db.prepareStatement("INSERT INTO replay_runs(evaluator_version,threshold,imported_count) VALUES(?,?,?)");
      PreparedStatement put=db.prepareStatement("INSERT INTO shadow_results(run_id,fingerprint,data) VALUES(?,?,?)")) {
   run.setString(1,version);run.setString(2,threshold.toPlainString());run.setInt(3,importedCount);run.executeUpdate();
   long id; try(Statement s=db.createStatement(); ResultSet r=s.executeQuery("SELECT last_insert_rowid()")) { r.next();id=r.getLong(1); }
   for(var result:results) {put.setLong(1,id);put.setString(2,result.sampleFingerprint());put.setString(3,StrictJson.MAPPER.writeValueAsString(result));put.executeUpdate();}
   db.commit();return id;
  } catch(Exception e) {db.rollback();throw e;} finally {db.setAutoCommit(true);}
 }
 public Replay latest() throws Exception {
  try(Statement s=db.createStatement(); ResultSet r=s.executeQuery("SELECT id,evaluator_version,threshold,imported_count FROM replay_runs ORDER BY id DESC LIMIT 1")) {
   if(!r.next()) return null;
   long id=r.getLong(1);String version=r.getString(2);BigDecimal threshold=new BigDecimal(r.getString(3));int count=r.getInt(4);
   if((!version.equals("fixture-v1")&&!version.equals("jev-1.13.0")) || threshold.signum()<0 || threshold.compareTo(BigDecimal.ONE)>0 || count<0 || count>100000)throw new IllegalArgumentException("Invalid persisted replay metadata");
   List<ShadowResult> values=new ArrayList<>();
   Map<String,RuntimeSample> samples=new HashMap<>();for(var sample:samples())samples.put(sample.fingerprint(),sample);
   try(PreparedStatement q=db.prepareStatement("SELECT data FROM shadow_results WHERE run_id=? ORDER BY fingerprint")) {
    q.setLong(1,id);try(ResultSet rows=q.executeQuery()) {while(rows.next()){var result=StrictJson.MAPPER.readValue(rows.getString(1),ShadowResult.class);validateResult(result,samples.get(result.sampleFingerprint()),threshold);values.add(result);}}
   }
   return new Replay(id,version,threshold,count,List.copyOf(values));
  }
 }
 private static void validateResult(ShadowResult r,RuntimeSample sample,BigDecimal threshold) {
  boolean fixture=r.evaluatorVersion().equals("fixture-v1"), real=r.evaluatorVersion().equals("jev-1.13.0");
  if(sample==null || (!fixture&&!real) || (fixture&&!sample.synthetic()) || (real&&(sample.synthetic()||sample.traceVersion()!=3||!"jev-1.13.0".equals(sample.jevModel()))) || !sample.primitive().equals(r.primitive()) || !sample.options().contains(r.selectedValue()) ||
   !r.probabilities().keySet().equals(new HashSet<>(sample.options())) || r.inputTokens()<0 || r.inputTokens()>10000000 || r.latencyMs()<0 || r.latencyMs()>86400000)throw new IllegalArgumentException("Invalid persisted replay");
  BigDecimal sum=BigDecimal.ZERO;for(BigDecimal p:r.probabilities().values()){if(p.signum()<0 || p.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Invalid probability");sum=sum.add(p);}
  if(sum.subtract(BigDecimal.ONE).abs().compareTo(new BigDecimal("0.000001"))>0)throw new IllegalArgumentException("Invalid distribution");
  if(sample.primitive().equals("NOUL") ? r.confidence()!=null : r.confidence()==null || r.confidence().signum()<0 || r.confidence().compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Invalid confidence");
  BigDecimal acceptance=r.confidence()==null?r.probabilities().get(r.selectedValue()):r.confidence();
  if(r.agrees()!=r.selectedValue().equals(sample.observedDecision()) || r.abstained()!=(acceptance.compareTo(threshold)<0))throw new IllegalArgumentException("Inconsistent replay policy");
 }
 public void recordProviderUsage(String provider,String version,long invocations,long successes,long failures,long requestBytes,long responseBytes,long inputTokens,long redactions) throws Exception {
  if(!Set.of("codex","claude","ollama","jev").contains(provider) || version==null || !version.matches("[A-Za-z0-9._:-]{1,80}") || invocations<1 || successes<0 || failures<0 || successes+failures!=invocations || requestBytes<0 || responseBytes<0 || inputTokens<0 || redactions<0 || requestBytes>1_000_000_000L || responseBytes>1_000_000_000L || inputTokens>1_000_000_000L || redactions>1_000_000L) throw new IllegalArgumentException("Invalid provider accounting");
  try(PreparedStatement p=db.prepareStatement("INSERT INTO provider_usage(provider,provider_version,invocations,successes,failures,request_bytes,response_bytes,input_tokens,redactions) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(provider,provider_version) DO UPDATE SET invocations=invocations+excluded.invocations,successes=successes+excluded.successes,failures=failures+excluded.failures,request_bytes=request_bytes+excluded.request_bytes,response_bytes=response_bytes+excluded.response_bytes,input_tokens=input_tokens+excluded.input_tokens,redactions=redactions+excluded.redactions")) {
   p.setString(1,provider);p.setString(2,version);p.setLong(3,invocations);p.setLong(4,successes);p.setLong(5,failures);p.setLong(6,requestBytes);p.setLong(7,responseBytes);p.setLong(8,inputTokens);p.setLong(9,redactions);p.executeUpdate();
  }
 }
 public List<ProviderUsage> providerUsage() throws Exception {
  List<ProviderUsage> result=new ArrayList<>();
  try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT provider,provider_version,invocations,successes,failures,request_bytes,response_bytes,input_tokens,redactions FROM provider_usage ORDER BY provider,provider_version")) {
   while(r.next()) result.add(new ProviderUsage(r.getString(1),r.getString(2),r.getLong(3),r.getLong(4),r.getLong(5),r.getLong(6),r.getLong(7),r.getLong(8),r.getLong(9)));
  }
  return List.copyOf(result);
 }
 public Map<String,Object> config() { return Map.of("schemaVersion",4,"stateSchemaVersion",4,"analyzer","none","telemetry",false,"storeRawTraces",false,"followSymlinks",false,"autoApply",false,"statePermissions","owner-only where supported"); }
 public int purge() throws Exception {
  int count=samples().size(); db.setAutoCommit(false);
  try(Statement s=db.createStatement()) {s.executeUpdate("DELETE FROM shadow_results");s.executeUpdate("DELETE FROM replay_runs");s.executeUpdate("DELETE FROM samples");s.executeUpdate("DELETE FROM provider_usage");db.commit();}
  catch(Exception e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
  return count;
 }
 @Override public void close() throws SQLException {db.close();}
}
