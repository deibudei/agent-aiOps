package org.example.agentaiops.repair.tool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.springframework.stereotype.Component;

@Component
public class ToolPolicy {

    private final Path workspaceRoot;
    private final Path targetRoot;
    private final Path targetSourceRoot;
    private final Path targetMainRoot;
    private final Path targetTestRoot;
    private final Path targetLogsRoot;

    /** Computes all read/write boundaries from repair configuration. */
    public ToolPolicy(RepairProperties properties) {
        this.workspaceRoot = discoverWorkspaceRoot(properties.getWorkspaceRoot());
        this.targetRoot = resolveTargetRoot(properties.getTargetProject().getRootPath());
        this.targetSourceRoot = targetRoot.resolve("src").normalize();
        this.targetMainRoot = targetRoot.resolve("src/main").normalize();
        this.targetTestRoot = targetRoot.resolve("src/test").normalize();
        this.targetLogsRoot = targetRoot.resolve("logs").normalize();
    }

    /** Returns the detected repository root. */
    public Path workspaceRoot() {
        return workspaceRoot;
    }

    /** Returns the target service module root. */
    public Path targetRoot() {
        return targetRoot;
    }

    /** Returns the target service log directory. */
    public Path targetLogsRoot() {
        return targetLogsRoot;
    }

    /** Resolves a path for safe reads from source, logs, or target pom.xml. */
    public Path resolveForRead(String path) {
        Path resolved = resolveTargetAware(path);
        ensureUnderAny(resolved, List.of(targetSourceRoot, targetLogsRoot, targetRoot.resolve("pom.xml")));
        return resolved;
    }

    /** Resolves a path for safe writes under target-service src/main or src/test. */
    public Path resolveForWrite(String path) {
        Path resolved = resolveTargetAware(path);
        ensureUnderAny(resolved, List.of(targetMainRoot, targetTestRoot));
        return resolved;
    }

    /** Checks whether a changed file is allowed to appear in a repair diff. */
    public boolean isAllowedChangedFile(String fileName) {
        Path resolved = resolveTargetAware(fileName);
        return startsWith(resolved, targetMainRoot) || startsWith(resolved, targetTestRoot);
    }

    /** Converts an absolute path to a repo-relative display path when possible. */
    public String display(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (startsWith(normalized, workspaceRoot)) {
            return workspaceRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString();
    }

    /** Resolves the configured target root, including sibling fallback for module launches. */
    private Path resolveTargetRoot(String configuredRoot) {
        Path direct = resolveWorkspacePath(configuredRoot);
        if (direct.toFile().exists()) {
            return direct;
        }
        Path sibling = workspaceRoot.resolve("..").resolve(configuredRoot).toAbsolutePath().normalize();
        if (sibling.toFile().exists()) {
            return sibling;
        }
        return direct;
    }

    /** Resolves relative paths from either repo root or target module root. */
    private Path resolveTargetAware(String path) {
        Path raw = Paths.get(path);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }

        Path fromWorkspace = resolveWorkspacePath(path);
        if (startsWith(fromWorkspace, targetRoot)) {
            return fromWorkspace;
        }

        Path targetRootName = targetRoot.getFileName();
        if (targetRootName != null && raw.getNameCount() > 0 && raw.getName(0).equals(targetRootName)) {
            if (raw.getNameCount() == 1) {
                return targetRoot;
            }
            return targetRoot.resolve(raw.subpath(1, raw.getNameCount())).toAbsolutePath().normalize();
        }

        return targetRoot.resolve(path).toAbsolutePath().normalize();
    }

    /** Detects the repo root even when agent-platform is launched from its module directory. */
    private Path discoverWorkspaceRoot(String configuredRoot) {
        Path configured = Paths.get(configuredRoot).toAbsolutePath().normalize();
        if (isWorkspaceRoot(configured)) {
            return configured;
        }

        Path parent = configured.getParent();
        if (parent != null && isWorkspaceRoot(parent)) {
            return parent;
        }

        return configured;
    }

    /** Checks for the expected multi-module Maven project layout. */
    private boolean isWorkspaceRoot(Path path) {
        return Files.exists(path.resolve("pom.xml"))
                && Files.exists(path.resolve("agent-platform/pom.xml"))
                && Files.exists(path.resolve("target-service/pom.xml"));
    }

    /** Resolves a path relative to the repository root unless it is already absolute. */
    private Path resolveWorkspacePath(String path) {
        Path raw = Paths.get(path);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        return workspaceRoot.resolve(raw).toAbsolutePath().normalize();
    }

    /** Throws when a resolved path falls outside every allowed root. */
    private void ensureUnderAny(Path path, List<Path> allowedRoots) {
        Path normalized = path.toAbsolutePath().normalize();
        for (Path allowedRoot : allowedRoots) {
            if (normalized.equals(allowedRoot) || startsWith(normalized, allowedRoot)) {
                return;
            }
        }
        throw new IllegalArgumentException("Path is outside repair whitelist: " + normalized);
    }

    /** Normalizes both sides before testing path containment. */
    private boolean startsWith(Path path, Path root) {
        return path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());
    }
}
