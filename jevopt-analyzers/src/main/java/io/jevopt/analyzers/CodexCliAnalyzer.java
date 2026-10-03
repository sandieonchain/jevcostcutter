package io.jevopt.analyzers;
import java.nio.file.Path; import java.util.*;
public final class CodexCliAnalyzer extends ProcessAnalyzer {
 public CodexCliAnalyzer(Path executable,String model,String codexHome){super("codex",executable,List.of("exec","--ignore-user-config","--ignore-rules","--ephemeral","--sandbox","read-only","--skip-git-repo-check","--model",model,"--output-schema","{temp}/schema.json","--output-last-message","{temp}/result.json","-"),checked(codexHome));}
 public CodexCliAnalyzer(Path executable,Path ignoredSchema){super("codex",executable,List.of("exec","--ignore-user-config","--ignore-rules","--ephemeral","--sandbox","read-only","--skip-git-repo-check","--model","contract-test","--output-schema","{temp}/schema.json","--output-last-message","{temp}/result.json","-"));}
 private static String checked(String name){if(!"CODEX_HOME".equals(name))throw new IllegalArgumentException("Explicit CODEX_HOME reference required");return name;}
 @Override protected AnalyzerResult parse(Path temp,String stdout)throws AnalyzerException {try{Path result=temp.resolve("result.json");return StrictAnalyzerJson.parse(java.nio.file.Files.exists(result)?java.nio.file.Files.readString(result):stdout,id());}catch(Exception e){throw new AnalyzerException("Analyzer response unavailable");}}
}
