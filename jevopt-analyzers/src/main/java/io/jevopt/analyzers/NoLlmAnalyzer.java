package io.jevopt.analyzers;
import java.util.List;
public final class NoLlmAnalyzer implements AnalyzerProvider { public String id(){return "none";} public AnalyzerCapabilities capabilities(){return new AnalyzerCapabilities(true,true,false,"No external analyzer selected");} public AnalyzerResult analyze(AnalyzerRequest request){return new AnalyzerResult("none","none","unknown",0,List.of("No analyzer evidence collected"),new AnalyzerUsage(0,0,0,"none"));} }
