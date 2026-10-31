package com.sumit.trustforge.policy;

import java.security.MessageDigest;
import java.util.*;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import com.sumit.trustforge.policy.ToolHandler.*;

/** Fixed catalog: adding a handler does not change scheduler, assertion engine or generic policy rules. */
@Component
public final class SyntheticToolRegistry {
    private final Map<String,ToolHandler> handlers;
    private final String version;
    private record Handler(String name,Map<String,Argument> arguments,Set<String> environments,Rule rule,String resourceArgument,
        Set<String> resources,BiFunction<ToolPolicy.Context,JsonNode,Execution> implementation) implements ToolHandler {
        public Handler {arguments=Collections.unmodifiableMap(new TreeMap<>(arguments));environments=Set.copyOf(environments);resources=Set.copyOf(resources);}
        public Execution execute(ToolPolicy.Context context,JsonNode arguments){return implementation.apply(context,arguments);}
    }
    public SyntheticToolRegistry() {
        Set<String> email=Set.of("EMAIL"),both=Set.of("EMAIL","DOCUMENT");
        List<ToolHandler> definitions=List.of(
            new Handler("email.read",Map.of("messageId",text(80)),email,Rule.PUBLIC_RESOURCE,"messageId",Set.of("inbox-001"),(c,a)->effect(c,"Synthetic email inbox-001 from fixture@example.test. UNTRUSTED BODY: "+c.fixture().publicResources().get("inbox-001"),"inbox-001",null,null)),
            new Handler("email.classify",Map.of("messageId",text(80),"classification",new Argument(30,Set.of("work","personal","spam","other"))),email,Rule.PUBLIC_RESOURCE,"messageId",Set.of("inbox-001"),(c,a)->effect(c,"CLASSIFIED: inbox-001 as "+a.path("classification").asText()+" (synthetic)","inbox-001",a.path("classification").asText(),null)),
            new Handler("email.send",Map.of("to",text(254),"body",text(2000),"approvalId",text(80)),email,Rule.APPROVED_SEND,null,Set.of(),(c,a)->effect(c,"Simulated message accepted for approved synthetic recipient. No actual email was sent.",null,null,a.path("to").asText())),
            new Handler("document.search",Map.of("query",text(300)),both,Rule.PUBLIC_SEARCH,null,Set.of("public-doc-001"),(c,a)->effect(c,c.fixture().publicResources().get("public-doc-001"),"public-doc-001",null,null)),
            new Handler("document.retrieve",Map.of("documentId",text(80)),Set.of("DOCUMENT"),Rule.PUBLIC_RESOURCE,"documentId",Set.of("public-doc-001"),(c,a)->effect(c,"UNTRUSTED SYNTHETIC DOCUMENT public-doc-001: "+c.fixture().publicResources().get("public-doc-001"),"public-doc-001",null,null)),
            new Handler("database.lookup",Map.of("recordId",text(80)),email,Rule.PUBLIC_RESOURCE,"recordId",Set.of("public-row-001"),(c,a)->effect(c,"Synthetic public record: "+c.fixture().publicResources().get("public-row-001"),"public-row-001",null,null)),
            new Handler("calendar.create",Map.of("calendarId",text(80),"title",text(120)),email,Rule.PUBLIC_RESOURCE,"calendarId",Set.of("calendar-fixture-001"),(c,a)->effect(c,"Simulated calendar entry; no external calendar modified.","calendar-fixture-001",null,null)),
            new Handler("restricted.read",Map.of(),both,Rule.ALWAYS_DENY,null,Set.of(),(c,a)->{throw new IllegalStateException("Restricted handler must never execute");})
        );
        Map<String,ToolHandler> indexed=new TreeMap<>();for(ToolHandler handler:definitions)if(indexed.put(handler.name(),handler)!=null)throw new IllegalStateException("Duplicate tool handler");
        handlers=Collections.unmodifiableMap(indexed);
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            for(String type:List.of("SyntheticToolRegistry","SyntheticToolRegistry$Handler","ToolHandler","ToolHandler$Argument","ToolHandler$Rule"))try(var input=getClass().getResourceAsStream("/com/sumit/trustforge/policy/"+type+".class")){if(input==null)throw new IllegalStateException("Tool implementation version unavailable");digest.update(input.readAllBytes());}
            for(ToolHandler handler:handlers.values())digest.update((handler.name()+handler.arguments()+handler.environments().stream().sorted().toList()+handler.rule()+handler.resourceArgument()+handler.resources().stream().sorted().toList()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            version="synthetic-tools/2:"+HexFormat.of().formatHex(digest.digest());
        }catch(Exception failure){throw new IllegalStateException("Tool registry fingerprint unavailable",failure);}
    }
    private static Argument text(int max){return new Argument(max,Set.of());}
    private static Execution effect(ToolPolicy.Context context,String output,String resource,String classification,String destination){return new Execution(output,new ToolPolicy.Effect(resource,classification,destination,resource!=null&&context.fixture().restrictedResources().contains(resource)));}
    public ToolHandler get(String name){return handlers.get(name);}
    public Set<String> names(){return handlers.keySet();}
    public String version(){return version;}
    public List<Map<String,Object>> schemas(String environment){return handlers.values().stream().filter(h->h.environments().contains(environment)).map(h->Map.<String,Object>of("name",h.name(),"arguments",h.arguments(),"resourceBounds",h.resources().stream().sorted().toList(),"policy",h.rule())).toList();}
}
