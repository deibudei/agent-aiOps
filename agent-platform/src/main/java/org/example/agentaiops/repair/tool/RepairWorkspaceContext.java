package org.example.agentaiops.repair.tool;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Holds the active repair workspace for one repair executor thread. */
@Component
public class RepairWorkspaceContext {

    private final ThreadLocal<Path> activeWorkspaceRoot = new ThreadLocal<>();
    private final ThreadLocal<String> activeBaseBranch = new ThreadLocal<>();

    /** Returns the active workspace root for the current thread, if one is set. */
    public Optional<Path> activeWorkspaceRoot() {
        Path value = activeWorkspaceRoot.get();
        return value == null ? Optional.empty() : Optional.of(value);
    }

    /** Returns the PR base branch associated with the current repair workspace, if one is set. */
    public Optional<String> activeBaseBranch() {
        String value = activeBaseBranch.get();
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    /** Runs an action with a temporary active workspace root. */
    public void runWithWorkspace(Path workspaceRoot, Runnable action) {
        runWithWorkspace(workspaceRoot, null, action);
    }

    /** Runs an action with a temporary active workspace root and PR base branch. */
    public void runWithWorkspace(Path workspaceRoot, String baseBranch, Runnable action) {
        callWithWorkspace(workspaceRoot, baseBranch, () -> {
            action.run();
            return null;
        });
    }

    /** Calls an action with a temporary active workspace root. */
    public <T> T callWithWorkspace(Path workspaceRoot, Supplier<T> action) {
        return callWithWorkspace(workspaceRoot, null, action);
    }

    /** Calls an action with a temporary active workspace root and optional PR base branch. */
    public <T> T callWithWorkspace(Path workspaceRoot, String baseBranch, Supplier<T> action) {
        Path previous = activeWorkspaceRoot.get();
        String previousBaseBranch = activeBaseBranch.get();
        activeWorkspaceRoot.set(workspaceRoot.toAbsolutePath().normalize());
        if (baseBranch != null && !baseBranch.isBlank()) {
            activeBaseBranch.set(baseBranch);
        }
        try {
            return action.get();
        } finally {
            if (previous == null) {
                activeWorkspaceRoot.remove();
            } else {
                activeWorkspaceRoot.set(previous);
            }
            if (previousBaseBranch == null) {
                activeBaseBranch.remove();
            } else {
                activeBaseBranch.set(previousBaseBranch);
            }
        }
    }
}
