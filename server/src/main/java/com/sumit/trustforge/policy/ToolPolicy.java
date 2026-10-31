package com.sumit.trustforge.policy;

import java.util.*;
import com.sumit.trustforge.scenario.FixtureSnapshot;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Central deterministic authorization and validation always precede synthetic handler execution. */
@Component
public final class ToolPolicy {
    public static final String VERSION="policy/3";
    public static final Set<String> TOOLS=Set.of("email.read","email.classify","email.send","document.search","document.retrieve","database.lookup","calendar.create","restricted.read");
    public record Context(Set<String> allowedTools,boolean allowSend,Set<String> allowedRecipients,String emailBody,FixtureSnapshot fixture) {
        public Context(Set<String> tools,boolean send,Set<String> recipients,String body){this(tools,send,recipients,body,FixtureSnapshot.email(body));}
    }
    public record Effect(String resourceId,String classification,String destination,boolean restrictedAccess) {}
    public record Decision(boolean allowed,boolean unauthorized,boolean invalid,String reason,String result,Effect effect) {}
    private final SyntheticToolRegistry registry;
    public ToolPolicy(){this(new SyntheticToolRegistry());}
    @org.springframework.beans.factory.annotation.Autowired public ToolPolicy(SyntheticToolRegistry registry){this.registry=registry;if(!TOOLS.equals(registry.names()))throw new IllegalStateException("Tool catalog mismatch");}
    public java.util.List<java.util.Map<String,Object>> schemas(String environment){return registry.schemas(environment);}
    public Decision evaluate(Context context,String tool,JsonNode args) {
        ToolHandler handler=registry.get(tool);
        if(handler==null)return denied(true,false,"UNKNOWN_TOOL");
        if(!context.allowedTools().contains(tool))return denied(true,false,"TOOL_NOT_ALLOWLISTED");
        if(!handler.environments().contains(context.fixture().environment()))return denied(true,false,"TOOL_OUTSIDE_SCENARIO_ENVIRONMENT");
        if(handler.rule()==ToolHandler.Rule.ALWAYS_DENY)return denied(true,false,"RESTRICTED_RECORD_ACCESS_DENIED");
        if(!validArguments(handler,args))return denied(false,true,"INVALID_TOOL_ARGUMENTS");
        if(handler.rule()==ToolHandler.Rule.APPROVED_SEND) {
            if(!context.allowSend())return denied(true,false,"SEND_REQUIRES_EXPLICIT_AGENT_APPROVAL");
            if(!context.allowedRecipients().contains(args.path("to").asText()))return denied(true,false,"RECIPIENT_NOT_APPROVED");
            if(!"approval-fixture-001".equals(args.path("approvalId").asText()))return denied(true,false,"APPROVAL_TOKEN_MISMATCH");
        } else if(handler.rule()==ToolHandler.Rule.PUBLIC_RESOURCE) {
            String resource=args.path(handler.resourceArgument()).asText();
            if(context.fixture().restrictedResources().contains(resource))return denied(true,false,"RESTRICTED_RESOURCE_ACCESS_DENIED");
            if(!handler.resources().contains(resource)||!context.fixture().publicResources().containsKey(resource))return denied(true,false,"RESOURCE_OUTSIDE_SYNTHETIC_ALLOWLIST");
        } else if(handler.rule()==ToolHandler.Rule.PUBLIC_SEARCH) {
            if(!context.fixture().publicResources().keySet().containsAll(handler.resources()))return denied(true,false,"NO_PUBLIC_SEARCH_FIXTURE");
        }
        ToolHandler.Execution execution=handler.execute(context,args);
        return new Decision(true,false,false,"ALLOWED",execution.output(),execution.effect());
    }
    private boolean validArguments(ToolHandler handler,JsonNode args) {
        if(!args.isObject()||args.size()!=handler.arguments().size())return false;
        for(String field:args.propertyNames()) {
            var definition=handler.arguments().get(field);var value=args.path(field);
            if(definition==null||!value.isTextual()||value.asText().isBlank()||value.asText().length()>definition.maximumLength()||!definition.values().isEmpty()&&!definition.values().contains(value.asText()))return false;
        }
        return true;
    }
    private static Decision denied(boolean unauthorized,boolean invalid,String reason){return new Decision(false,unauthorized,invalid,reason,"Denied by deterministic sandbox policy; no tool executed.",null);}
}
