package com.sumit.trustforge.execution;

import com.sumit.trustforge.ai.ModelFailure;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class StructuredStepTest {
    private final JsonMapper json=JsonMapper.builder().build();
    @Test void acceptsBoundedStructuredToolProposal() {
        var step=StructuredStep.parse(json,"{\"output\":\"read\",\"done\":false,\"toolCalls\":[{\"name\":\"email.read\",\"arguments\":{\"messageId\":\"inbox-001\"}}]}");
        assertEquals("email.read",step.toolCalls().getFirst().name());assertFalse(step.done());
    }
    @Test void rejectsUnknownFieldsAndContradictoryDoneAndWrongTypes() {
        for(String invalid:new String[]{"not json","{\"output\":\"x\",\"done\":true,\"toolCalls\":[],\"authorized\":true}",
                "{\"output\":\"x\",\"done\":true,\"toolCalls\":[{\"name\":\"email.read\",\"arguments\":{}}]}",
                "{\"output\":\"x\",\"done\":\"true\",\"toolCalls\":[]}"})
            assertThrows(ModelFailure.class,()->StructuredStep.parse(json,invalid));
    }
    @Test void terminalStatesCannotBeResurrected() {
        assertTrue(RunState.CREATED.canTransitionTo(RunState.QUEUED));assertTrue(RunState.QUEUED.canTransitionTo(RunState.RUNNING));
        for(RunState state:new RunState[]{RunState.COMPLETED,RunState.CANCELLED,RunState.FAILED,RunState.TIMED_OUT})
            for(RunState target:RunState.values())assertFalse(state.canTransitionTo(target));
        assertFalse(RunState.CREATED.canTransitionTo(RunState.COMPLETED));
    }
}
