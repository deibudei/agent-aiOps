package org.example.agentaiops.repair.tool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.List;
import org.example.agentaiops.config.RepairProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ToolPolicy {

    private final RepairProperties properties;
    private final RepairWorkspaceContext workspaceContext;
    private final Path homeWorkspaceRoot;

    /** Computes all read/write boundaries from repair configuration. */
    @Autowired
    public ToolPolicy(RepairProperties properties, RepairWorkspaceContext workspaceContext) {
        this.properties = properties;
        this.workspaceContext = workspaceContext;
        this.homeWorkspaceRoot = discoverWorkspaceRoot(Paths.get(properties.getWorkspaceRoot()));
    }

    /** Test-friendly constructor without a Spring context. */
    public ToolPolicy(RepairProperties properties) {
        this(properties, new RepairWorkspaceContext());
    }

    /** Returns the active repository root, or the launch repository root when no repair worktree is active. */
    public Path workspaceRoot() {
        return workspaceContext.activeWorkspaceRoot()
                .map(this::discoverWorkspaceRoot)
                .orElse(homeWorkspaceRoot);
    }

    /** Returns the launch repository root used for shared records and worktree management. */
    public Path homeWorkspaceRoot() {
        return homeWorkspaceRoot;
    }

    /** Returns the target service module root. */
    public Path targetRoot() {
        return resolveTargetRoot(properties.getTargetProject().getRootPath(), workspaceRoot());
    }

    /** Returns the target service log directory. */
    public Path targetLogsRoot() {
        return targetRoot().resolve("logs").normalize();
    }

    /** Resolves a path for safe reads from source, logs, or target pom.xml. */
    public Path resolveForRead(String path) {
        Path resolved = resolveTargetAware(path);
        Path targetRoot = targetRoot();
        ensureUnderAny(resolved, List.of(
                targetRoot.resolve("src").normalize(),
                targetRoot.resolve("logs").normalize(),
                targetRoot.resolve("pom.xml").normalize()));
        return resolved;
    }

    /** Resolves a path for safe writes under target-service src/main or src/test. */
    public Path resolveForWrite(String path) {
        Path resolved = resolveTargetAware(path);
        Path targetRoot = targetRoot();
        ensureUnderAny(resolved, List.of(
                targetRoot.resolve("src/main").normalize(),
                targetRoot.resolve("src/test").normalize()));
        return resolved;
    }

    /** Checks whether a changed file is allowed to appear in a repair diff. */
    public boolean isAllowedChangedFile(String fileName) {
        Path resolved = resolveTargetAware(fileName);
        Path targetRoot = targetRoot();
        return startsWith(resolved, targetRoot.resolve("src/main"))
                || startsWith(resolved, targetRoot.resolve("src/test"));
    }

    /** Converts an absolute path to a repo-relative display path when possible. */
    public String display(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path activeRoot = workspaceRoot();
        if (startsWith(normalized, activeRoot)) {
            return activeRoot.relativize(normalized).toString().replace('\\', '/');
        }
        if (startsWith(normalized, homeWorkspaceRoot)) {
            return homeWorkspaceRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString();
    }

    /** Resolves the configured target root, including sibling fallback for module launches. */
    private Path resolveTargetRoot(String configuredRoot, Path root) {
        Path direct = resolveWorkspacePath(configuredRoot, root);
        if (direct.toFile().exists()) {
            return direct;
        }
        Path sibling = root.resolve("..").resolve(configuredRoot).toAbsolutePath().normalize();
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

        Path activeWorkspaceRoot = workspaceRoot();
        Path targetRoot = targetRoot();
        Path fromWorkspace = resolveWorkspacePath(path, activeWorkspaceRoot);
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
    private Path discoverWorkspaceRoot(Path configuredRoot) {
        Path configured = configuredRoot.toAbsolutePath().normalize();
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
    private Path resolveWorkspacePath(String path, Path root) {
        Path raw = Paths.get(path);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        return root.resolve(raw).toAbsolutePath().normalize();
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
