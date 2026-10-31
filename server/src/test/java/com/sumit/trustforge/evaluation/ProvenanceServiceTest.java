package com.sumit.trustforge.evaluation;

import java.time.Instant;
import java.util.*;
import com.sumit.trustforge.agent.AgentService.AgentVersionDto;
import com.sumit.trustforge.ai.AIModelGateway;
import com.sumit.trustforge.scenario.*;
import com.sumit.trustforge.scenario.ScenarioService.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

class ProvenanceServiceTest {
    final JsonMapper json=JsonMapper.builder().build();
    final AIModelGateway fixture=new AIModelGateway(){public boolean configured(){return true;}public Response complete(Request r){throw new UnsupportedOperationException("No inference in fingerprint tests");}public Descriptor descriptor(String model){return new Descriptor("CONTROLLED_TEST_FIXTURE",model,Map.of("temperature",0,"fixtureVersion","1"));}};
    final ProvenanceService service=new ProvenanceService(json,fixture);
    AgentVersionDto agent(String name,String model){return new AgentVersionDto(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,name,"Synthetic instruction",List.of("email.read","email.classify"),List.of(),false,5,30,model);}
    Snapshot scenario(){return new Snapshot(UUID.randomUUID(),"Mutable title",Category.NORMAL,"Display description","Classify inbox-001","Synthetic body","CLASSIFIED","TF_CANARY_FINGERPRINT_1234",ExpectedOutcome.email("work"),FixtureSnapshot.email("Synthetic body"));}
    ProvenanceService.Provenance changed(ProvenanceService.Provenance original,String field,String value){var node=(ObjectNode)json.readTree(json.writeValueAsString(original));node.put(field,value);return json.readValue(node.toString(),ProvenanceService.Provenance.class);}
    @Test void storedSnapshotRoundTripPreservesExactFixtureSerialization(){var s=scenario();String serialized=json.writeValueAsString(s);assertEquals(serialized,json.writeValueAsString(json.readValue(serialized,Snapshot.class)));}
    @Test void canonicalMapOrderingIsStable(){assertEquals(service.fingerprint(new LinkedHashMap<>(Map.of("a",1,"b",2))),service.fingerprint(new TreeMap<>(Map.of("b",2,"a",1))));}
    @Test void mutableNamesAndAgentVersionIdentityAreNotControlledConditions(){var s=scenario();var renamed=new Snapshot(s.id(),"Renamed",s.category(),"New display description",s.legitimateTask(),s.adversarialInput(),s.expectedOutputContains(),s.canary(),s.expectations(),s.fixture());var a=service.capture(agent("First","model-a"),List.of(s));var b=service.capture(agent("Second","model-a"),List.of(renamed));assertEquals(a.deterministicFingerprint(),b.deterministicFingerprint());assertEquals(a.agentConfigurationHash(),b.agentConfigurationHash());assertTrue(service.differences(a,b).isEmpty());}
    @Test void modelChangesAreExplicitlyIncompatible(){var s=scenario();var a=service.capture(agent("First","model-a"),List.of(s));var b=service.capture(agent("Second","model-b"),List.of(s));assertEquals(a.deterministicFingerprint(),b.deterministicFingerprint());assertTrue(service.differences(a,b).stream().anyMatch(r->r.code().equals("MODEL_CONDITIONS_CHANGED")));}
    @Test void fixtureMutationInvalidatesControlledDelta(){var s=scenario();var other=new Snapshot(s.id(),s.title(),s.category(),s.description(),s.legitimateTask(),s.adversarialInput(),s.expectedOutputContains(),s.canary(),s.expectations(),FixtureSnapshot.email("Different fixture content"));assertTrue(service.differences(service.capture(agent("a","model"),List.of(s)),service.capture(agent("b","model"),List.of(other))).stream().anyMatch(r->r.code().equals("FIXTURES_CHANGED")));}
    @Test void assertionAndPolicyVersionChangesAreIncompatible(){var p=service.capture(agent("a","model"),List.of(scenario()));assertTrue(service.differences(p,changed(p,"assertionVersion","future-engine")).stream().anyMatch(r->r.code().equals("ASSERTIONS_CHANGED")));assertFalse(service.executable(changed(p,"policyVersion","future-policy")));}
    @Test void legacyProvenanceIsUnknownNotInvented(){assertEquals("LEGACY_PROVENANCE_MISSING",service.differences(null,null).getFirst().code());assertFalse(service.executable(null));}
    @Test void missingExpectationsCannotProduceCompleteProvenance(){var s=scenario();var legacy=new Snapshot(s.id(),s.title(),s.category(),s.description(),s.legitimateTask(),s.adversarialInput(),s.expectedOutputContains(),s.canary());var p=service.capture(agent("a","model"),List.of(legacy));assertFalse(p.complete());assertFalse(service.executable(p));}
    @Test void fingerprintsExposeNoInstructionsOrSyntheticCanary(){var p=service.capture(agent("a","model"),List.of(scenario()));String serialized=json.writeValueAsString(p);assertFalse(serialized.contains("Synthetic instruction"));assertFalse(serialized.contains("TF_CANARY_"));assertTrue(p.deterministicFingerprint().matches("[a-f0-9]{64}"));assertTrue(service.executable(p));}
}
