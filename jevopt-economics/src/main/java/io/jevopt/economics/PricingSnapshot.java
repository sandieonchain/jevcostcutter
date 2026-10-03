package io.jevopt.economics;
import io.jevopt.traces.StrictJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
public record PricingSnapshot(int version,String id,String date,String currency,boolean synthetic,
 Map<String,Rate> models,Map<String,BigDecimal> evaluatorInputPerMillion,BigDecimal analyzerOverhead,BigDecimal shadowOverhead) {
 public record Rate(BigDecimal inputPerMillion,BigDecimal outputPerMillion) {}
 public static PricingSnapshot read(Path path) throws Exception {
  JsonNode root=StrictJson.file(path);
  StrictJson.fields(root,"version","id","date","currency","synthetic","models","evaluatorInputPerMillion","analyzerOverhead","shadowOverhead");StrictJson.version(root);
  if(!StrictJson.text(root,"currency").equals("USD"))throw new IllegalArgumentException("Only explicit USD snapshots supported");
  Map<String,Rate> models=new TreeMap<>();JsonNode rates=root.get("models");
  if(!rates.isObject() || rates.size()>100)throw new IllegalArgumentException("Invalid price models");
  var fields=rates.fields();
  while(fields.hasNext()){
   var entry=fields.next();String model=StrictJson.label(entry.getKey(),64);JsonNode rate=entry.getValue();
   StrictJson.fields(rate,"inputPerMillion","outputPerMillion");
   models.put(model,new Rate(amount(rate,"inputPerMillion"),amount(rate,"outputPerMillion")));
  }
  Map<String,BigDecimal> evaluators=new TreeMap<>();JsonNode prices=root.get("evaluatorInputPerMillion");
  if(!prices.isObject() || prices.size()>10)throw new IllegalArgumentException("Invalid evaluator prices");
  var names=prices.fieldNames();while(names.hasNext()){String name=StrictJson.label(names.next(),64);evaluators.put(name,amount(prices,name));}
  return new PricingSnapshot(1,StrictJson.label(StrictJson.text(root,"id"),64),StrictJson.day(root,"date"),"USD",StrictJson.bool(root,"synthetic"),
   Map.copyOf(models),Map.copyOf(evaluators),optional(root,"analyzerOverhead"),optional(root,"shadowOverhead"));
 }
 private static BigDecimal amount(JsonNode n,String key){return StrictJson.decimal(n,key,new BigDecimal("1000000000"));}
 private static BigDecimal optional(JsonNode n,String key){return n.get(key).isNull()?null:amount(n,key);}
}

