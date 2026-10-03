package io.jevopt.report;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jevopt.core.*;
public final class ReportRenderer {
 private final ObjectMapper mapper = new ObjectMapper();
 public String json(Object value) throws java.io.IOException { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
 public String text(AnalysisReport report) {
  long bounded=report.candidates().stream().filter(c->c.status().equals("STATIC_CANDIDATE")).count();
  long keep=report.candidates().stream().filter(c->c.status().equals("KEEP_LLM")).count();
  StringBuilder b=new StringBuilder("JEVOPT ANALYSIS\n");
  b.append("Source files scanned: ").append(report.filesScanned()).append("\nSkipped entries: ").append(report.filesSkipped());
  b.append("\nCoverage truncated: ").append(report.truncated());
  b.append("\nSuspected LLM call sites: ").append(report.candidates().size());
  b.append("\nBounded-decision candidates: ").append(bounded).append("\nKeep as LLM: ").append(keep);
  b.append("\nShadow validated: 0\nCost/savings: unknown (no runtime evidence or pricing)\n");
  for(var c:report.candidates()) b.append(c.candidateId()).append(" ").append(c.relativePath()).append(":").append(c.startLine()).append(" ").append(c.status()).append(" ").append(c.category()).append(" -> ").append(c.suggestedPrimitive()).append("\n");
  b.append("Target writes: ").append(report.targetWrites()).append("\nTarget commands executed: ").append(report.targetExecutions()).append("\nNetwork requests: ").append(report.networkRequests()).append("\n");
  b.append("Limitations: ").append(String.join("; ",report.limitations())).append("\n");
  return b.toString();
 }
}

