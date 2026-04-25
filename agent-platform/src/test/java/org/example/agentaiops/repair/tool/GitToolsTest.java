package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitToolsTest {

    @Test
    void parseChangedFilesIgnoresGitWarnings() {
        String output = """
                warning: in the working copy of 'target-service/src/main/java/com/example/targetservice/service/OrderService.java', LF will be replaced by CRLF the next time Git touches it
                target-service/src/main/java/com/example/targetservice/service/OrderService.java
                """;

        assertThat(GitTools.parseChangedFiles(output))
                .containsExactly("target-service/src/main/java/com/example/targetservice/service/OrderService.java");
    }
}
