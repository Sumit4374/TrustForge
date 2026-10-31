package com.sumit.trustforge.policy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ToolPolicyTest {
    private final ToolPolicy policy=new ToolPolicy();
    private final ObjectMapper json=JsonMapper.builder().build();
    private final ToolPolicy.Context context=new ToolPolicy.Context(Set.of("email.read","email.classify","email.send","restricted.read","database.lookup"),false,Set.of(),"Synthetic inbox body");
    @Test void normalClassifyExecutesSyntheticTool() {
        var result=policy.evaluate(context,"email.classify",json.readTree("{\"messageId\":\"inbox-001\",\"classification\":\"work\"}"));
        assertTrue(result.allowed());assertTrue(result.result().contains("CLASSIFIED"));
    }
    @Test void forbiddenSendNeverExecutes() {
        var decision=policy.evaluate(context,"email.send",json.readTree("{\"to\":\"outside@example.test\",\"body\":\"synthetic\",\"approvalId\":\"forged\"}"));
        assertFalse(decision.allowed());assertTrue(decision.unauthorized());assertFalse(decision.result().contains("accepted"));
    }
    @Test void modelCannotForgePolicyApproval() {
        var permitted=new ToolPolicy.Context(Set.of("email.send"),true,Set.of("approved@example.test"),"");
        assertFalse(policy.evaluate(permitted,"email.send",json.readTree("{\"to\":\"outside@example.test\",\"body\":\"hello\",\"approvalId\":\"approval-fixture-001\"}")).allowed());
        assertFalse(policy.evaluate(permitted,"email.send",json.readTree("{\"to\":\"approved@example.test\",\"body\":\"hello\",\"approvalId\":\"forged\"}")).allowed());
        assertTrue(policy.evaluate(permitted,"email.send",json.readTree("{\"to\":\"approved@example.test\",\"body\":\"hello\",\"approvalId\":\"approval-fixture-001\"}")).allowed());
    }
    @Test void unknownToolAndRestrictedResourcesAreDenied() {
        assertFalse(policy.evaluate(context,"shell.exec",json.readTree("{} ")).allowed());
        assertFalse(policy.evaluate(context,"restricted.read",json.readTree("{} ")).allowed());
        assertFalse(policy.evaluate(context,"database.lookup",json.readTree("{\"recordId\":\"secret-row\"}")).allowed());
    }
    @Test void argumentSchemasRejectExtrasMissingFieldsAndWrongTypes() {
        assertTrue(policy.evaluate(context,"email.read",json.readTree("{\"messageId\":\"inbox-001\",\"url\":\"http://169.254.169.254\"}")).invalid());
        assertTrue(policy.evaluate(context,"email.read",json.readTree("{\"messageId\":21}")).invalid());
        assertTrue(policy.evaluate(context,"email.classify",json.readTree("{\"messageId\":\"inbox-001\",\"classification\":\"override\"}")).invalid());
    }
    @Test void documentRetrievalUsesStoredPublicFixtureAndDeniesRestrictedId() {
        var fixture=com.sumit.trustforge.scenario.FixtureSnapshot.document("Untrusted synthetic document content");
        var context=new ToolPolicy.Context(Set.of("document.retrieve","email.classify","restricted.read"),false,Set.of(),"",fixture);
        var publicRead=policy.evaluate(context,"document.retrieve",json.readTree("{\"documentId\":\"public-doc-001\"}"));
        assertTrue(publicRead.allowed());assertEquals("public-doc-001",publicRead.effect().resourceId());assertFalse(publicRead.effect().restrictedAccess());assertTrue(publicRead.result().contains("Untrusted synthetic"));
        var restricted=policy.evaluate(context,"document.retrieve",json.readTree("{\"documentId\":\"restricted-doc-001\"}"));
        assertFalse(restricted.allowed());assertTrue(restricted.unauthorized());assertNull(restricted.effect());
        assertFalse(policy.evaluate(context,"email.classify",json.readTree("{\"messageId\":\"inbox-001\",\"classification\":\"work\"}")).allowed());
    }
    @Test void registryDeclaresBoundedSchemasAndStableImplementationVersion() {
        var registry=new SyntheticToolRegistry();assertEquals(ToolPolicy.TOOLS,registry.names());assertEquals(registry.version(),new SyntheticToolRegistry().version());
        assertTrue(registry.version().startsWith("synthetic-tools/2:"));assertEquals(Set.of("documentId"),registry.get("document.retrieve").arguments().keySet());
        assertFalse(policy.evaluate(new ToolPolicy.Context(Set.of("document.retrieve"),false,Set.of(),"",com.sumit.trustforge.scenario.FixtureSnapshot.document("test")),"document.retrieve",json.readTree("{\"documentId\":\"public-doc-001\",\"url\":\"http://169.254.169.254\"}")).allowed());
    }

}
