package org.example.agentaiops.repair.extension;

import java.nio.file.Path;

public record TargetProjectConfig(
        String projectName,
        Path rootPath,
        Path sourcePath,
        Path testPath,
        Path logPath,
        String testCommand) {
}
