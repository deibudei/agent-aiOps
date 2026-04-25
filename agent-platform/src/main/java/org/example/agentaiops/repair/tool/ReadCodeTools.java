package org.example.agentaiops.repair.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.example.agentaiops.repair.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

@Component
public class ReadCodeTools {

    private final ToolPolicy toolPolicy;

    public ReadCodeTools(ToolPolicy toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

    public ToolExecutionResult readFile(String path) {
        try {
            Path resolved = toolPolicy.resolveForRead(path);
            return ToolExecutionResult.success(Files.readString(resolved));
        } catch (IOException | IllegalArgumentException e) {
            return ToolExecutionResult.failure(e.getMessage());
        }
    }

    public ToolExecutionResult searchCode(String query) {
        List<String> matches = new ArrayList<>();
        Path sourceRoot = toolPolicy.targetRoot().resolve("src");
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> collectMatch(query, matches, path));
            return ToolExecutionResult.success(String.join(System.lineSeparator(), matches));
        } catch (IOException e) {
            return ToolExecutionResult.failure(e.getMessage());
        }
    }

    private void collectMatch(String query, List<String> matches, Path path) {
        try {
            String content = Files.readString(path);
            if (content.contains(query)) {
                matches.add(toolPolicy.display(path));
            }
        } catch (IOException ignored) {
            // Ignore unreadable files inside the allowed source tree.
        }
    }
}
