package io.jevopt.jev;
import java.util.*;
/** A deliberately small, categorical System One question. Text must be approved by the caller. */
public record JevQuestion(String id, Type type, String instructions, Map<String,String> choiceCriteria, List<String> scoreCriteria, Map<String,String> noulCriteria) {
 public enum Type { CHOICE, SCORE, NOUL }
 public JevQuestion {
  if(id==null || !id.matches("[A-Za-z0-9_.-]{1,64}") || instructions==null || instructions.isBlank() || instructions.length()>4096 || type==null) throw new IllegalArgumentException("Invalid question");
  choiceCriteria=choiceCriteria==null?Map.of():Map.copyOf(choiceCriteria); scoreCriteria=scoreCriteria==null?List.of():List.copyOf(scoreCriteria); noulCriteria=noulCriteria==null?Map.of():Map.copyOf(noulCriteria);
  if((type==Type.CHOICE && (choiceCriteria.isEmpty() || choiceCriteria.size()>255)) || (type==Type.SCORE && (scoreCriteria.size()<2 || scoreCriteria.size()>10)) || (type==Type.NOUL && !noulCriteria.isEmpty()&&!noulCriteria.keySet().equals(Set.of("true","false")))) throw new IllegalArgumentException("Invalid question criteria");
  for(var e:choiceCriteria.entrySet()) if(e.getKey()==null||e.getKey().isBlank()||e.getKey().length()>128||e.getValue()==null||e.getValue().isBlank()||e.getValue().length()>4096)throw new IllegalArgumentException("Invalid choice criterion");
  for(String c:scoreCriteria) if(c==null||c.isBlank()||c.length()>4096)throw new IllegalArgumentException("Invalid score criterion");
  for(var e:noulCriteria.entrySet()) if(!Set.of("true","false").contains(e.getKey())||e.getValue()==null||e.getValue().isBlank()||e.getValue().length()>4096)throw new IllegalArgumentException("Invalid noul criterion");
 }
 public JevQuestion(String id,Type type,String instructions,List<String> criteria) { this(id,type,instructions,type==Type.CHOICE?labels(criteria):Map.of(),type==Type.SCORE?criteria:List.of(),type==Type.NOUL?bools(criteria):Map.of()); }
 private static Map<String,String> labels(List<String> x){if(x==null)return Map.of();Map<String,String> r=new LinkedHashMap<>();for(String s:x){if(r.put(s,s)!=null)throw new IllegalArgumentException("Duplicate criterion");}return r;}
 private static Map<String,String> bools(List<String> x){if(x==null||x.isEmpty())return Map.of();if(x.size()!=2)throw new IllegalArgumentException("Invalid noul criteria");return Map.of("true",x.get(0),"false",x.get(1));}
 public List<String> options(){return type==Type.CHOICE?List.copyOf(choiceCriteria.keySet()):type==Type.SCORE?java.util.stream.IntStream.range(0,scoreCriteria.size()).mapToObj(String::valueOf).toList():List.of("true","false");}
}
