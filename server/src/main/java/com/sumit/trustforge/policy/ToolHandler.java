package com.sumit.trustforge.policy;

import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Fixed in-process synthetic handler; centralized policy must authorize before execute. */
public interface ToolHandler {
    enum Rule { PUBLIC_RESOURCE, PUBLIC_SEARCH, APPROVED_SEND, ALWAYS_DENY }
    record Argument(int maximumLength,Set<String> values) {public Argument {values=java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(values));}}
    record Execution(String output,ToolPolicy.Effect effect) {}
    String name();
    Map<String,Argument> arguments();
    Set<String> environments();
    Rule rule();
    String resourceArgument();
    Set<String> resources();
    Execution execute(ToolPolicy.Context context,JsonNode arguments);
}
