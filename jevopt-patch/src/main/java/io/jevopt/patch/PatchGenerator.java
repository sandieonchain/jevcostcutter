package io.jevopt.patch;
import io.jevopt.core.DecisionCandidate;
import io.jevopt.security.RepositorySandbox;
import java.nio.file.*;
import java.util.*;
/** Emits review-only diffs into JevOpt-owned state. It never touches the target. */
public final class PatchGenerator {
 private PatchGenerator() {}
 /** Compatibility overload intentionally refuses: source must be read through RepositorySandbox. */
 public static Path generate(Path state,DecisionCandidate candidate,RecommendationEngine.Verdict verdict) throws Exception {
  throw new IllegalArgumentException("RepositorySandbox source is required for patch generation");
 }
 public static Path generate(Path state,Path repository,DecisionCandidate candidate,RecommendationEngine.Verdict verdict) throws Exception {
  if(verdict!=RecommendationEngine.Verdict.VALIDATED_WITH_LIMITS)throw new IllegalArgumentException("Evidence is insufficient, stale, synthetic, or high-risk");
  if(!candidate.category().equals("FIXED_CHOICE_ROUTING")&&!candidate.category().equals("TOOL_ROUTING")||!candidate.relativePath().endsWith(".java"))throw new IllegalArgumentException("Unsupported patch pattern");
  String source=new RepositorySandbox(repository).read(candidate.relativePath());
  String[] lines=source.split("\\n",-1);int index=candidate.startLine()-1;if(index<0||index>=lines.length)throw new IllegalArgumentException("Candidate source line unavailable");
  var assignment=java.util.regex.Pattern.compile("^(\\s*)([A-Za-z_$][\\w$<>?, ]*)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*((?:(?:[\\w$]+\\.)*(?:chat\\s*\\.\\s*completions\\s*\\.\\s*create|responses\\s*\\.\\s*create|messages\\s*\\.\\s*create)|generateText|generateObject)\\s*\\(.*\\))\\s*;\\s*$").matcher(lines[index]);
  if(!assignment.matches())throw new IllegalArgumentException("Only a single Java direct-call assignment is supported");
  String indent=assignment.group(1),type=assignment.group(2).trim(),name=assignment.group(3),original=assignment.group(4);
  List<String> before=Arrays.asList(lines).subList(Math.max(0,index-2),index);List<String> after=Arrays.asList(lines).subList(index+1,Math.min(lines.length,index+3));
  Path out=state.resolve("patches");Files.createDirectories(out);Path file=out.resolve(candidate.candidateId()+".diff");
  StringBuilder diff=new StringBuilder("--- a/").append(candidate.relativePath()).append("\n+++ b/").append(candidate.relativePath()).append("\n");
  int oldCount=before.size()+1+after.size(),newCount=before.size()+9+after.size();diff.append("@@ -").append(index-before.size()+1).append(",").append(oldCount).append(" +").append(index-before.size()+1).append(",").append(newCount).append(" @@\n");
  before.forEach(l->diff.append(' ').append(l).append('\n'));diff.append('-').append(lines[index]).append('\n');
  diff.append('+').append(indent).append(type).append(' ').append(name).append(";\n");
  diff.append('+').append(indent).append("try {\n").append('+').append(indent).append("  // JevOpt: pinned model jev-1.13.0; review question/criteria before applying.\n").append('+').append(indent).append("  ").append(name).append(" = jevClient.answer(new JevQuestion(\"jev-1.13.0\", \"route\", \"Choose one bounded route using the existing consumer criteria\"));\n").append('+').append(indent).append("  if (").append(name).append(" == null) throw new IllegalStateException(\"Jev abstained\");\n").append('+').append(indent).append("} catch (Exception jevOptFailure) {\n").append('+').append(indent).append("  ").append(name).append(" = ").append(original).append(";\n").append('+').append(indent).append("}\n").append('+').append(indent).append("// TODO(jevopt): add an integration test covering Jev abstention/error fallback.\n");
  after.forEach(l->diff.append(' ').append(l).append('\n'));
  Files.writeString(file,diff.toString(),StandardOpenOption.CREATE_NEW);return file;
 }
}
