package com.sumit.trustforge.execution;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RunStateTest {
    @Test void ordinaryLifecycleUsesExplicitTransitions() {
        assertTrue(RunState.CREATED.canTransitionTo(RunState.QUEUED));
        assertTrue(RunState.QUEUED.canTransitionTo(RunState.RUNNING));
        assertTrue(RunState.RUNNING.canTransitionTo(RunState.COMPLETED));
        assertFalse(RunState.CREATED.canTransitionTo(RunState.COMPLETED));
        assertFalse(RunState.QUEUED.canTransitionTo(RunState.TIMED_OUT));
    }
    @Test void cancellationAndFailuresAreValidOnlyFromDefinedStates() {
        assertTrue(RunState.QUEUED.canTransitionTo(RunState.CANCELLED));
        assertTrue(RunState.RUNNING.canTransitionTo(RunState.CANCELLED));
        assertTrue(RunState.RUNNING.canTransitionTo(RunState.TIMED_OUT));
        assertTrue(RunState.RUNNING.canTransitionTo(RunState.FAILED));
        assertFalse(RunState.CREATED.canTransitionTo(RunState.RUNNING));
    }
    @Test void terminalRunsCannotResumeOrChangeTheirResult() {
        for(RunState terminal:Set.of(RunState.COMPLETED,RunState.FAILED,RunState.CANCELLED,RunState.TIMED_OUT)) {
            assertTrue(terminal.terminal());
            for(RunState next:RunState.values())assertFalse(terminal.canTransitionTo(next));
        }
    }
}
