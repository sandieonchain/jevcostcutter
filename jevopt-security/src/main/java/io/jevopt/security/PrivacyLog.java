package io.jevopt.security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class PrivacyLog {
 private static final Logger LOG = LoggerFactory.getLogger(PrivacyLog.class);
 private static final Redactor REDACTOR = new Redactor();
 private PrivacyLog() {}
 public static void event(String message) { LOG.info("{}", REDACTOR.redact(message)); }
}

