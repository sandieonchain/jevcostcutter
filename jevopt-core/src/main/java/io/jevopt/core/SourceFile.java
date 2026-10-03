package io.jevopt.core;
/** Ephemeral local source; never serialize this record. */
public record SourceFile(String relativePath, String language, String content) {}

