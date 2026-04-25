package org.example.agentaiops.repair.tool;

import org.example.agentaiops.repair.extension.TestRunner;
import org.example.agentaiops.repair.model.TestExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class RunTestTools {

    private final TestRunner testRunner;

    public RunTestTools(TestRunner testRunner) {
        this.testRunner = testRunner;
    }

    public TestExecutionResult runTargetServiceTests() {
        return testRunner.runTests();
    }
}
