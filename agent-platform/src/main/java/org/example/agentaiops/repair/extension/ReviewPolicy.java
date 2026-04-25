package org.example.agentaiops.repair.extension;

import java.util.List;
import org.example.agentaiops.repair.model.TestExecutionResult;

public interface ReviewPolicy {

    boolean allows(TestExecutionResult testResult, List<String> changedFiles);
}
