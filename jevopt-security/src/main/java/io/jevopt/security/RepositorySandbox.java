package io.jevopt.security;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
public final class RepositorySandbox {
 public static final int MAX_FILE_BYTES = 2_000_000;
 private final Path root;
 private static final Set<String> DENIED_DIRS = Set.of("node_modules", "vendor", "build", "dist", "target", "venv", "__pycache__", "credentials", "browser", "wallet", "keychain");
 public RepositorySandbox(Path supplied) throws IOException {
  Path absolute = supplied.toAbsolutePath().normalize();
  rejectSymlinkComponents(absolute);
  for (Path part : absolute) if (deniedName(part.toString())) throw new IOException("Sensitive repository root denied");
  root = absolute.toRealPath(LinkOption.NOFOLLOW_LINKS);
  if (root.getParent() == null || root.equals(Path.of(System.getProperty("user.home")).toRealPath())) throw new IOException("Broad repository root denied");
  if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid repository root");
 }
 public Path root() { return root; }
 public static boolean deniedName(String name) {
  String n = name.toLowerCase(Locale.ROOT);
  return n.startsWith(".") || DENIED_DIRS.contains(n) || n.startsWith("id_rsa") || n.startsWith("id_ed25519") ||
   n.matches(".*\\.(pem|key|p12|pfx|db|sqlite|sqlite3|keystore)$") || n.matches(".*(credential|secret|token|session|auth).*\\.(json|ya?ml|toml|ini|conf)$");
 }
 private static void rejectSymlinkComponents(Path p) throws IOException {
  Path current = p.getRoot();
  for (Path part : p) { current = current.resolve(part); if (Files.isSymbolicLink(current)) throw new IOException("Symbolic links are not permitted"); }
 }
 public Path resolve(String relative) throws IOException {
  if (relative.isBlank() || relative.contains("\\") || relative.startsWith("/") || relative.matches("^[A-Za-z]:.*")) throw new IOException("Invalid relative path");
  Path rel = Path.of(relative);
  if (rel.isAbsolute()) throw new IOException("Invalid relative path");
  for (Path part : rel) if (part.toString().equals("..") || deniedName(part.toString())) throw new IOException("Path denied");
  Path resolved = root.resolve(rel).normalize();
  if (!resolved.startsWith(root)) throw new IOException("Path denied");
  rejectSymlinkComponents(resolved);
  if (!resolved.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(root)) throw new IOException("Path denied");
  return resolved;
 }
 public String read(String relative) throws IOException {
  Path p = resolve(relative);
  BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
  if (!a.isRegularFile() || a.size() > MAX_FILE_BYTES) throw new IOException("Unsupported file");
  try (SeekableByteChannel channel = Files.newByteChannel(p, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
   BasicFileAttributes after = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
   if (!after.isRegularFile() || !Objects.equals(a.fileKey(), after.fileKey())) throw new IOException("File changed during scan");
   ByteBuffer buffer = ByteBuffer.allocate(MAX_FILE_BYTES + 1);
   while (buffer.hasRemaining() && channel.read(buffer) != -1) {}
   if (buffer.position() > MAX_FILE_BYTES) throw new IOException("File exceeds limit");
   buffer.flip();
   String source = StandardCharsets.UTF_8.newDecoder().decode(buffer).toString();
   if (source.indexOf(0) >= 0) throw new IOException("Binary file denied");
   return source;
  }
 }
}
