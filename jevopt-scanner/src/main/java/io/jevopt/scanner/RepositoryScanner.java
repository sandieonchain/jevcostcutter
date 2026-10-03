package io.jevopt.scanner;
import io.jevopt.core.SourceFile;
import io.jevopt.security.RepositorySandbox;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.security.*;
import java.nio.charset.StandardCharsets;
public final class RepositoryScanner {
 public record Coverage(int scanned, int skipped, boolean truncated, String eligibleSourceFingerprint) {}
 private static final Map<String,String> LANGUAGES = Map.of("py","Python","js","JavaScript","ts","TypeScript","tsx","TypeScript","jsx","JavaScript","java","Java");
 public Coverage scan(RepositorySandbox sandbox, Consumer<SourceFile> consumer) throws IOException {
  int[] counts = {0,0,0}; long[] bytes = {0}; boolean[] truncated = {false}; MessageDigest digest;
  try { digest=MessageDigest.getInstance("SHA-256"); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  MessageDigest sourceDigest=digest;
  Files.walkFileTree(sandbox.root(), EnumSet.noneOf(FileVisitOption.class), 64, new SimpleFileVisitor<>() {
   @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes a) {
    if (!dir.equals(sandbox.root()) && RepositorySandbox.deniedName(dir.getFileName().toString())) { counts[1]++; return FileVisitResult.SKIP_SUBTREE; }
    return FileVisitResult.CONTINUE;
   }
   @Override public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
    if (++counts[2] > 10000 || bytes[0] > 50_000_000) { truncated[0] = true; return FileVisitResult.TERMINATE; }
    String name = file.getFileName().toString();
    String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    if (!a.isRegularFile() || RepositorySandbox.deniedName(name) || !LANGUAGES.containsKey(ext) || a.size() > RepositorySandbox.MAX_FILE_BYTES) {
     counts[1]++; if (a.isDirectory()) truncated[0] = true; return FileVisitResult.CONTINUE;
    }
    String relative = sandbox.root().relativize(file).toString().replace('\\','/');
    try {
     String source = sandbox.read(relative);
     bytes[0] += source.length() * 2L;
     sourceDigest.update(relative.getBytes(StandardCharsets.UTF_8));sourceDigest.update((byte)0);sourceDigest.update(source.getBytes(StandardCharsets.UTF_8));sourceDigest.update((byte)0);
     consumer.accept(new SourceFile(relative, LANGUAGES.get(ext), source)); counts[0]++;
    } catch (IOException | SecurityException ignored) { counts[1]++; }
    return FileVisitResult.CONTINUE;
   }
   @Override public FileVisitResult visitFileFailed(Path file, IOException failure) { counts[1]++; return FileVisitResult.CONTINUE; }
  });
  return new Coverage(counts[0], counts[1], truncated[0],HexFormat.of().formatHex(sourceDigest.digest()));
 }
}
