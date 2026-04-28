package org.example.agentaiops.repair.agentic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.example.agentaiops.repair.tool.ReadCodeTools;
import org.example.agentaiops.repair.tool.ReadLogTools;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgenticReadOnlyToolsTest {

    @Test
    void readFileCachesByMtimeWithinOneSession() {
        ReadLogTools readLogTools = Mockito.mock(ReadLogTools.class);
        ReadCodeTools readCodeTools = Mockito.mock(ReadCodeTools.class);
        AgenticReadOnlyTools tools = new AgenticReadOnlyTools(readLogTools, readCodeTools, true);

        when(readCodeTools.lastModifiedMillis("target-service/src/main/java/Foo.java"))
                .thenReturn(ToolExecutionResult.success("123"));
        when(readCodeTools.readFile("target-service/src/main/java/Foo.java"))
                .thenReturn(ToolExecutionResult.success("class Foo {}"));

        String first = tools.readFile("target-service/src/main/java/Foo.java");
        String second = tools.readFile("target-service/src/main/java/Foo.java");

        assertThat(first).isEqualTo(second);
        verify(readCodeTools, times(2)).lastModifiedMillis("target-service/src/main/java/Foo.java");
        verify(readCodeTools, times(1)).readFile("target-service/src/main/java/Foo.java");
    }

    @Test
    void readFileCacheMissesWhenMtimeChanges() {
        ReadLogTools readLogTools = Mockito.mock(ReadLogTools.class);
        ReadCodeTools readCodeTools = Mockito.mock(ReadCodeTools.class);
        AgenticReadOnlyTools tools = new AgenticReadOnlyTools(readLogTools, readCodeTools, true);

        when(readCodeTools.lastModifiedMillis("target-service/src/main/java/Foo.java"))
                .thenReturn(ToolExecutionResult.success("123"), ToolExecutionResult.success("124"));
        when(readCodeTools.readFile("target-service/src/main/java/Foo.java"))
                .thenReturn(ToolExecutionResult.success("v1"), ToolExecutionResult.success("v2"));

        String first = tools.readFile("target-service/src/main/java/Foo.java");
        String second = tools.readFile("target-service/src/main/java/Foo.java");

        assertThat(first).isNotEqualTo(second);
        verify(readCodeTools, times(2)).readFile("target-service/src/main/java/Foo.java");
    }
}

