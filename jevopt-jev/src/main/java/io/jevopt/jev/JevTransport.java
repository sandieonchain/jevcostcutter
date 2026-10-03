package io.jevopt.jev;
import java.net.URI;
import java.time.Duration;
/** Injectable transport enables contract testing without sockets or credentials. */
@FunctionalInterface public interface JevTransport {
 Response post(URI uri, String authorization, byte[] body, Duration timeout) throws Exception;
 record Response(int status, String retryAfter, byte[] body) {}
}
