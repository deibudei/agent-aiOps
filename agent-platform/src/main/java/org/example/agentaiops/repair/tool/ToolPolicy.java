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

    public ToolPolicy(RepairProperties properties) {
        this.workspaceRoot = discoverWorkspaceRoot(properties.getWorkspaceRoot());
        this.targetRoot = resolveTargetRoot(properties.getTargetProject().getRootPath());
        this.targetSourceRoot = targetRoot.resolve("src").normalize();
        this.targetMainRoot = targetRoot.resolve("src/main").normalize();
        this.targetTestRoot = targetRoot.resolve("src/test").normalize();
        this.targetLogsRoot = targetRoot.resolve("logs").normalize();
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public Path targetRoot() {
        return targetRoot;
    }

    public Path targetLogsRoot() {
        return targetLogsRoot;
    }

    public Path resolveForRead(String path) {
        Path resolved = resolveTargetAware(path);
        ensureUnderAny(resolved, List.of(targetSourceRoot, targetLogsRoot, targetRoot.resolve("pom.xml")));
        return resolved;
    }

    public Path resolveForWrite(String path) {
        Path resolved = resolveTargetAware(path);
        ensureUnderAny(resolved, List.of(targetMainRoot, targetTestRoot));
        return resolved;
    }

    public boolean isAllowedChangedFile(String fileName) {
        Path resolved = resolveTargetAware(fileName);
        return startsWith(resolved, targetMainRoot) || startsWith(resolved, targetTestRoot);
    }

    public String display(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (startsWith(normalized, workspaceRoot)) {
            return workspaceRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString();
    }

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

    private boolean isWorkspaceRoot(Path path) {
        return Files.exists(path.resolve("pom.xml"))
                && Files.exists(path.resolve("agent-platform/pom.xml"))
                && Files.exists(path.resolve("target-service/pom.xml"));
    }

    private Path resolveWorkspacePath(String path) {
        Path raw = Paths.get(path);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        return workspaceRoot.resolve(raw).toAbsolutePath().normalize();
    }

    private void ensureUnderAny(Path path, List<Path> allowedRoots) {
        Path normalized = path.toAbsolutePath().normalize();
        for (Path allowedRoot : allowedRoots) {
            if (normalized.equals(allowedRoot) || startsWith(normalized, allowedRoot)) {
                return;
            }
        }
        throw new IllegalArgumentException("Path is outside repair whitelist: " + normalized);
    }

    private boolean startsWith(Path path, Path root) {
        return path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize());
    }
}
