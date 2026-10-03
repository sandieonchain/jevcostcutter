package io.jevopt.security;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.nio.channels.*;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;
class SecurityTest {
 @TempDir Path temp;
 RepositorySandbox sandbox() throws Exception { return new RepositorySandbox(temp.toRealPath()); }
 @ParameterizedTest @ValueSource(strings={"../../escape.py","..\\escape.py","C:\\example\\secret.py","/etc/passwd",".env",".aws/credentials",".codex/auth.json",".claude/auth.json","id_rsa","private.pem","session.json"})
 void rejectsTraversalAndSensitivePaths(String path) throws Exception { var s=sandbox(); assertThrows(Exception.class,()->s.read(path)); }
 @Test void readsOnlyRegularBoundedFiles() throws Exception {
  Files.writeString(temp.resolve("safe.py"),"print('synthetic')");
  assertEquals("print('synthetic')",sandbox().read("safe.py"));
  Files.write(temp.resolve("large.py"),new byte[RepositorySandbox.MAX_FILE_BYTES+1]);
  assertThrows(Exception.class,()->sandbox().read("large.py"));
  Files.createDirectory(temp.resolve("directory.py"));
  assertThrows(Exception.class,()->sandbox().read("directory.py"));
 }
 @Test void rejectsSymlinkFilesAndDirectories() throws Exception {
  Path safe=Files.writeString(temp.resolve("safe.py"),"synthetic");
  Files.createSymbolicLink(temp.resolve("link.py"),safe);
  Files.createSymbolicLink(temp.resolve("outside"),Path.of(System.getProperty("java.io.tmpdir")).toRealPath());
  assertThrows(Exception.class,()->sandbox().read("link.py"));
  assertThrows(Exception.class,()->sandbox().read("outside/file.py"));
 }
 @Test void rejectsUnixSocket() throws Exception {
  Path socket=temp.toRealPath().resolve("socket.py");
  try(ServerSocketChannel channel=ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
   channel.bind(UnixDomainSocketAddress.of(socket));
   assertThrows(Exception.class,()->sandbox().read("socket.py"));
  }
 }
 @Test void rejectsFifo() throws Exception {
  Path fifo=temp.toRealPath().resolve("pipe.py");
  // Test harness only: this is NOT target code and is never used by production.
  Process process=new ProcessBuilder("mkfifo",fifo.toString()).start();
  assertEquals(0,process.waitFor());
  assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->assertThrows(Exception.class,()->sandbox().read("pipe.py")));
 }
 @Test void redactsSecretsAndIdentifiers() {
  String synthetic="Bearer pretend-value\npassword=pretend-password\n" +
   "-----BEGIN PRIVATE KEY-----\nsynthetic\n-----END PRIVATE KEY-----\n" +
   "AKIA"+"A".repeat(16)+" eyJabc.defghi.jklmno\npostgres://name:pass@host/db\n" +
   "user@example.invalid workstation.local C:\\example\\private\\file /workspace/private/file";
  String result=new Redactor().redact(synthetic);
  for(String forbidden:new String[]{"pretend-value","pretend-password","synthetic","AKIA","eyJabc","postgres","example.invalid","workstation.local","C:\\","/workspace"}) assertFalse(result.contains(forbidden),forbidden);
 }
 @Test void redactsUnsafeRelativeNames() {
  assertEquals("<repo>/src/router.py",new Redactor().relativeLabel("src/router.py"));
  assertFalse(new Redactor().relativeLabel("user@example.invalid/router.py").contains("@"));
 }
}

