package com.sumit.trustforge.evaluation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import com.sumit.trustforge.agent.AgentService.AgentVersionDto;
import com.sumit.trustforge.ai.AIModelGateway;
import com.sumit.trustforge.execution.AssertionEvaluator;
import com.sumit.trustforge.policy.ToolPolicy;
import com.sumit.trustforge.scenario.ScenarioService.Snapshot;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Canonical, credential-free conditions. Agent instructions/permissions may differ in remediation. */
@Service
public class ProvenanceService {
    public static final String SERIALIZATION="canonical-json/1",SCHEMA="evaluation/3",REGISTRY=new com.sumit.trustforge.policy.SyntheticToolRegistry().version();
    public record Provenance(String serializationVersion,UUID agentVersionId,int agentRevision,String agentConfigurationHash,
        String scenarioHash,String fixtureHash,AIModelGateway.Descriptor provider,String toolRegistryVersion,String policyVersion,
        String assertionVersion,String evaluationSchemaVersion,String softwareVersion,String runtimeVersion,
        Map<String,Object> executionConfiguration,String deterministicFingerprint,String modelFingerprint,boolean complete) {}
    public record Incompatibility(String code,String dimension,String baseline,String candidate,String message) {}
    private final ObjectMapper json;
    private final AIModelGateway model;
    private final String software;
    public ProvenanceService(ObjectMapper json,AIModelGateway model){this.json=json;this.model=model;this.software=softwareFingerprint();}
    public Provenance capture(AgentVersionDto agent,List<Snapshot> scenarios) {
        var descriptor=model.descriptor(agent.model());
        if(descriptor==null)descriptor=new AIModelGateway.Descriptor("UNSPECIFIED_PROVIDER",agent.model(),Map.of());
        List<Object> definitions=new ArrayList<>(),fixtures=new ArrayList<>();
        for(Snapshot s:scenarios.stream().sorted(Comparator.comparing(Snapshot::id).thenComparingInt(Snapshot::trialIndex)).toList()) {
            Map<String,Object> definition=new TreeMap<>();definition.put("id",s.id().toString());definition.put("trialIndex",s.trialIndex());definition.put("category",s.category());
            definition.put("legitimateTask",s.legitimateTask());definition.put("untrustedInput",s.adversarialInput());
            definition.put("outputRequirement",s.expectedOutputContains());definition.put("canaryDigest",hash(s.canary()));definition.put("expectations",s.expectations());
            definitions.add(definition);fixtures.add(s.fixture());
        }
        String scenarioHash=fingerprint(definitions),fixtureHash=fingerprint(fixtures);
        Map<String,Object> execution=Map.of("maxSteps",agent.maxSteps(),"timeoutSeconds",agent.timeoutSeconds(),"maxCallsPerStep",4,"maxScenarios",20,"trialsPerScenario",scenarios.stream().mapToInt(Snapshot::trialIndex).max().orElse(1),"uniqueScenarios",scenarios.stream().map(Snapshot::id).distinct().count());
        var conditions=Map.of("scenarioHash",scenarioHash,"fixtureHash",fixtureHash,"registry",REGISTRY,"policy",ToolPolicy.VERSION,
            "assertions",AssertionEvaluator.VERSION,"schema",SCHEMA,"software",software,"execution",execution,"serialization",SERIALIZATION,"runtime",System.getProperty("java.runtime.version"));
        var agentConfig=Map.of("instructions",agent.instructions(),"tools",agent.allowedTools().stream().sorted().toList(),"recipients",agent.allowedRecipients().stream().sorted().toList(),"allowSend",agent.allowSend(),"execution",execution);
        boolean complete=scenarios.stream().allMatch(s->s.expectations()!=null&&s.fixture()!=null)&&!descriptor.provider().equals("UNSPECIFIED_PROVIDER");
        return new Provenance(SERIALIZATION,agent.id(),agent.version(),fingerprint(agentConfig),scenarioHash,fixtureHash,descriptor,REGISTRY,
            ToolPolicy.VERSION,AssertionEvaluator.VERSION,SCHEMA,software,System.getProperty("java.runtime.version"),execution,fingerprint(conditions),fingerprint(descriptor),complete);
    }
    public boolean executable(Provenance provenance) {
        return provenance!=null&&provenance.complete()&&SERIALIZATION.equals(provenance.serializationVersion())&&REGISTRY.equals(provenance.toolRegistryVersion())
            &&ToolPolicy.VERSION.equals(provenance.policyVersion())&&AssertionEvaluator.VERSION.equals(provenance.assertionVersion())&&SCHEMA.equals(provenance.evaluationSchemaVersion())&&software.equals(provenance.softwareVersion());
    }
    public List<Incompatibility> differences(Provenance a,Provenance b) {
        if(a==null||b==null)return List.of(new Incompatibility("LEGACY_PROVENANCE_MISSING","provenance",a==null?"unknown":"recorded",b==null?"unknown":"recorded","Historical provenance cannot be reconstructed; create a reviewed baseline"));
        List<Incompatibility> reasons=new ArrayList<>();
        if(!a.complete()||!b.complete())reasons.add(new Incompatibility("INCOMPLETE_PROVENANCE","provenance",Boolean.toString(a.complete()),Boolean.toString(b.complete()),"Unreviewed expectations, unknown fixtures or provider conditions prevent a controlled comparison"));
        compare(reasons,"SERIALIZATION_CHANGED","serialization",a.serializationVersion(),b.serializationVersion());
        compare(reasons,"SCENARIOS_CHANGED","scenarios",a.scenarioHash(),b.scenarioHash());compare(reasons,"FIXTURES_CHANGED","fixtures",a.fixtureHash(),b.fixtureHash());
        compare(reasons,"TOOL_REGISTRY_CHANGED","tools",a.toolRegistryVersion(),b.toolRegistryVersion());compare(reasons,"POLICY_CHANGED","policy",a.policyVersion(),b.policyVersion());
        compare(reasons,"ASSERTIONS_CHANGED","assertions",a.assertionVersion(),b.assertionVersion());compare(reasons,"SCHEMA_CHANGED","schema",a.evaluationSchemaVersion(),b.evaluationSchemaVersion());
        compare(reasons,"SOFTWARE_CHANGED","software",a.softwareVersion(),b.softwareVersion());
        compare(reasons,"MODEL_CONDITIONS_CHANGED","model",a.modelFingerprint(),b.modelFingerprint());
        compare(reasons,"EXECUTION_CONDITIONS_CHANGED","execution",a.deterministicFingerprint(),b.deterministicFingerprint());
        return List.copyOf(reasons);
    }
    private static void compare(List<Incompatibility> reasons,String code,String dimension,String a,String b){if(!Objects.equals(a,b))reasons.add(new Incompatibility(code,dimension,a,b,"Recorded "+dimension+" conditions differ; no controlled security delta"));}
    public String fingerprint(Object value){return hash(json.writeValueAsString(canonical(json.readTree(json.writeValueAsString(value)))));}
    private Object canonical(JsonNode node) {
        if(node.isObject()){Map<String,Object> sorted=new TreeMap<>();for(String key:node.propertyNames())sorted.put(key,canonical(node.path(key)));return sorted;}
        if(node.isArray()){List<Object> items=new ArrayList<>();for(JsonNode child:node)items.add(canonical(child));return items;}
        if(node.isNull())return null;if(node.isBoolean())return node.asBoolean();if(node.isIntegralNumber())return node.asLong();if(node.isNumber())return node.decimalValue();return node.asText();
    }
    private String softwareFingerprint() {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            String[] classes={"evaluation/ProvenanceService","execution/ExecutionEvidence","execution/ExecutionEvidence$ToolCall","execution/ExecutionEvidence$ModelCall","policy/SyntheticToolRegistry","policy/ToolHandler","execution/ScenarioExecutor","execution/ExecutionOrchestrator","execution/AssertionEvaluator","execution/CategoryAssertions$Normal","execution/CategoryAssertions$Injection","execution/CategoryAssertions$Agency","execution/CategoryAssertions$Disclosure","execution/CategoryAssertions$InvalidArguments","execution/StructuredStep","policy/ToolPolicy","scenario/FixtureSnapshot","scenario/ExpectedOutcome","ai/BharatCodeGateway"};
            for(String name:classes)try(var input=getClass().getResourceAsStream("/com/sumit/trustforge/"+name+".class")){if(input==null)throw new IllegalStateException("Execution version cannot be established");digest.update(name.getBytes(StandardCharsets.UTF_8));digest.update(input.readAllBytes());}
            return "engine/2:"+HexFormat.of().formatHex(digest.digest());
        }catch(Exception failure){throw new IllegalStateException("Execution software fingerprint unavailable",failure);}
    }
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception failure){throw new IllegalStateException(failure);}}
}
