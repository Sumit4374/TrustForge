package com.sumit.trustforge.execution;

import com.sumit.trustforge.ai.ModelFailure;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;

public record StructuredStep(String output,boolean done,List<Call> toolCalls) {
    public record Call(String name,JsonNode arguments) {}
    public static StructuredStep parse(ObjectMapper json,String content) {
        try {
            JsonNode root=json.readTree(content);
            exact(root,Set.of("output","done","toolCalls"));
            if(!root.path("output").isTextual() || root.path("output").asText().length()>8000 || !root.path("done").isBoolean() ||
                !root.path("toolCalls").isArray() || root.path("toolCalls").size()>4) throw new IllegalArgumentException();
            List<Call> calls=new ArrayList<>();
            for(JsonNode c:root.path("toolCalls")) {
                exact(c,Set.of("name","arguments"));
                if(!c.path("name").isTextual() || !c.path("name").asText().matches("[a-z][a-z0-9_.]{0,59}") || !c.path("arguments").isObject() || c.path("arguments").toString().length()>6000)
                    throw new IllegalArgumentException();
                calls.add(new Call(c.path("name").asText(),c.path("arguments")));
            }
            if(root.path("done").asBoolean() && !calls.isEmpty()) throw new IllegalArgumentException();
            return new StructuredStep(root.path("output").asText(),root.path("done").asBoolean(),List.copyOf(calls));
        } catch(Exception ex) {throw new ModelFailure("INVALID_STRUCTURE","Invalid structured model output; no proposed tool was executed");}
    }
    private static void exact(JsonNode n,Set<String> names) {
        if(!n.isObject() || n.size()!=names.size()) throw new IllegalArgumentException();
        for(String field:n.propertyNames()) if(!names.contains(field)) throw new IllegalArgumentException();
    }
}
