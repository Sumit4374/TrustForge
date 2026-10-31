package com.sumit.trustforge.execution;

import java.util.List;
import com.sumit.trustforge.evaluation.EvaluationStore.*;

public record ScenarioExecution(TestResult result,List<Event> events,List<Violation> violations,boolean providerFailed,boolean timedOut) {
    public ScenarioExecution {events=List.copyOf(events);violations=List.copyOf(violations);}
}
