package io.jevopt.scanner;
import io.jevopt.security.RepositorySandbox;
import io.jevopt.core.SourceFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ScannerTest {
 @TempDir Path temp;
 @Test void skipsCredentialsLinksBinariesAndGeneratedDirectories() throws Exception {
  Path root=temp.toRealPath();
  Files.writeString(root.resolve("router.py"),"result=client.responses.create()");
  Files.writeString(root.resolve(".env"),"synthetic");
  Files.createDirectories(root.resolve(".codex"));
  Files.writeString(root.resolve(".codex/auth.json"),"synthetic");
  Files.createDirectories(root.resolve("node_modules"));
  Files.writeString(root.resolve("node_modules/router.py"),"synthetic");
  Files.write(root.resolve("binary.py"),new byte[]{0});
  Files.createSymbolicLink(root.resolve("link.py"),root.resolve("router.py"));
  List<SourceFile> files=new ArrayList<>();
  var coverage=new RepositoryScanner().scan(new RepositorySandbox(root),files::add);
  assertEquals(1,coverage.scanned()); assertEquals(5,coverage.skipped());
  assertEquals("router.py",files.getFirst().relativePath()); assertFalse(coverage.truncated());
 }
 @Test void credentialStoreCannotBeUsedAsRoot() throws Exception {
  Path root=Files.createDirectory(temp.toRealPath().resolve(".codex"));
  assertThrows(Exception.class,()->new RepositorySandbox(root));
 }
}
