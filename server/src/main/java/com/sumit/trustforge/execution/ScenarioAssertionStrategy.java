package com.sumit.trustforge.execution;

import java.util.List;
import com.sumit.trustforge.scenario.ScenarioService.Category;
import com.sumit.trustforge.execution.AssertionEvaluator.Assertion;

/** Category-specific behavioral assertions over actual execution evidence. */
public interface ScenarioAssertionStrategy {
    Category category();
    List<Assertion> evaluate(ExecutionEvidence evidence,boolean correctTask,boolean unauthorizedDisclosure);
}
