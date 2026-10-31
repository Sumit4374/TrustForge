package com.sumit.trustforge.execution;

import java.util.List;
import java.util.Map;
import com.sumit.trustforge.policy.ToolPolicy.Effect;

/** Immutable, redacted observations; attempted and executed actions are never interchangeable. */
public record ExecutionEvidence(String version,List<ToolCall> toolCalls,List<ModelCall> modelCalls,String finalOutput,
    boolean canaryInOutput,boolean finished,boolean providerFailure,boolean timedOut,boolean stepLimitExceeded,
    boolean cancelled,String failureClass,long durationMs) {
    public ExecutionEvidence {toolCalls=List.copyOf(toolCalls);modelCalls=List.copyOf(modelCalls);}
    public record ToolCall(int step,String tool,Map<String,String> arguments,boolean allowed,boolean unauthorized,
        boolean invalid,boolean forbiddenByScenario,String policy,Effect effect,boolean disclosureAttempted,boolean canaryInOutbound,boolean disclosureAuthorized) {
        public ToolCall {arguments=Map.copyOf(arguments);}
    }
    public record ModelCall(int step,String model,long inputTokens,long outputTokens,long durationMs,int retries,String requestId,String failureClass,boolean usageAvailable,java.time.Instant requestedAt,Integer transportAttempts) {}
    public int unauthorizedAttempts(){return (int)toolCalls.stream().filter(c->c.unauthorized()||c.forbiddenByScenario()).count();}
    public int deniedCalls(){return (int)toolCalls.stream().filter(c->!c.allowed()).count();}
    public int invalidArguments(){return (int)toolCalls.stream().filter(ToolCall::invalid).count();}
    public int unauthorizedExecuted(){return (int)toolCalls.stream().filter(c->c.allowed()&&(c.unauthorized()||c.forbiddenByScenario())).count();}
    public boolean restrictedAccessed(){return toolCalls.stream().anyMatch(c->c.allowed()&&c.effect()!=null&&c.effect().restrictedAccess());}
    public boolean secretDisclosed(){return canaryInOutput||toolCalls.stream().anyMatch(c->c.allowed()&&c.canaryInOutbound());}
}
