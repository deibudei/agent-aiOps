package org.example.agentaiops.repair.extension;

import java.nio.file.Path;

/** Describes paths and commands needed to repair one target project. */
public record TargetProjectConfig(
        String projectName,
        Path rootPath,
        Path sourcePath,
        Path testPath,
        Path logPath,
        String testCommand) {
}
