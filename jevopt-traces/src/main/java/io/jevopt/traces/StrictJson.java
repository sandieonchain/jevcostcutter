package io.jevopt.traces;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import io.jevopt.security.*;
import java.io.IOException;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public final class StrictJson {
 public static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
  .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(12).maxStringLength(24000).maxNumberLength(32).build()).build())
  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
  .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
 private StrictJson() {}
 public static String read(Path supplied) throws IOException {
  Path path=supplied.toAbsolutePath().normalize();
  if(path.getParent()==null) throw new IOException("Invalid input path");
  return new RepositorySandbox(path.getParent()).read(path.getFileName().toString());
 }
 public static JsonNode parse(String text) throws IOException { return MAPPER.readTree(text); }
 public static JsonNode file(Path path) throws IOException { return parse(read(path)); }
 public static void fields(JsonNode n,String... names) {
  if(n==null || !n.isObject()) throw new IllegalArgumentException("Object required");
  Set<String> expected=Set.of(names), actual=new HashSet<>(); n.fieldNames().forEachRemaining(actual::add);
  if(!actual.equals(expected)) throw new IllegalArgumentException("Schema fields mismatch");
 }
 public static void version(JsonNode n) { if(integer(n,"version",1)!=1) throw new IllegalArgumentException("Unsupported schema"); }
 public static String text(JsonNode n,String key) {
  JsonNode value=n.get(key); if(value==null || !value.isTextual()) throw new IllegalArgumentException("Text required");
  return value.textValue();
 }
 public static String label(String value,int max) {
  if(!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,"+(max-1)+"}") ||
    !new Redactor().redact(value).equals(value) ||
    value.toLowerCase(Locale.ROOT).matches(".*(secret|password|bearer|authorization|api.key).*")) throw new IllegalArgumentException("Unsafe categorical label");
  return value;
 }
 public static long integer(JsonNode n,String key,long max) {
  JsonNode v=n.get(key);
  if(v==null || !v.isIntegralNumber() || !v.canConvertToLong() || v.longValue()<0 || v.longValue()>max) throw new IllegalArgumentException("Invalid count");
  return v.longValue();
 }
 public static boolean bool(JsonNode n,String key) {
  JsonNode v=n.get(key); if(v==null || !v.isBoolean()) throw new IllegalArgumentException("Boolean required");
  return v.booleanValue();
 }
 public static BigDecimal decimal(JsonNode n,String key,BigDecimal max) {
  JsonNode v=n.get(key);
  if(v==null || !(v.isNumber() || v.isTextual())) throw new IllegalArgumentException("Decimal required");
  String raw=v.asText(); if(!raw.matches("[0-9]{1,12}(\\.[0-9]{1,12})?")) throw new IllegalArgumentException("Invalid decimal");
  BigDecimal b=new BigDecimal(raw); if(b.compareTo(max)>0) throw new IllegalArgumentException("Decimal exceeds bound"); return b;
 }
 public static String day(JsonNode n,String key) {
  String day=text(n,key); if(!day.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Day bucket required");
  LocalDate.parse(day); return day;
 }
 public static List<String> options(JsonNode n) {
  if(n==null || !n.isArray() || n.size()<2 || n.size()>32) throw new IllegalArgumentException("Finite option set required");
  List<String> values=new ArrayList<>();
  for(JsonNode v:n) { if(!v.isTextual()) throw new IllegalArgumentException("Categorical option required"); values.add(label(v.textValue(),24)); }
  if(new HashSet<>(values).size()!=values.size()) throw new IllegalArgumentException("Duplicate options");
  return List.copyOf(values);
 }
}
