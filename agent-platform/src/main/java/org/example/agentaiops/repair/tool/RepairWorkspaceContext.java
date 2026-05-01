package org.example.agentaiops.repair.tool;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Holds the active repair workspace for one repair executor thread. */
@Component
public class RepairWorkspaceContext {

    private final ThreadLocal<Path> activeWorkspaceRoot = new ThreadLocal<>();

    /** Returns the active workspace root for the current thread, if one is set. */
    public Optional<Path> activeWorkspaceRoot() {
        Path value = activeWorkspaceRoot.get();
        return value == null ? Optional.empty() : Optional.of(value);
    }

    /** Runs an action with a temporary active workspace root. */
    public void runWithWorkspace(Path workspaceRoot, Runnable action) {
        callWithWorkspace(workspaceRoot, () -> {
            action.run();
            return null;
        });
    }

    /** Calls an action with a temporary active workspace root. */
    public <T> T callWithWorkspace(Path workspaceRoot, Supplier<T> action) {
        Path previous = activeWorkspaceRoot.get();
        activeWorkspaceRoot.set(workspaceRoot.toAbsolutePath().normalize());
        try {
            return action.get();
        } finally {
            if (previous == null) {
                activeWorkspaceRoot.remove();
            } else {
                activeWorkspaceRoot.set(previous);
            }
        }
    }
}
