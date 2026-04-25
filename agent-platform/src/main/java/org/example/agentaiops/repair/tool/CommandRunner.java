package org.example.agentaiops.repair.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.example.agentaiops.repair.model.CommandResult;
import org.springframework.stereotype.Component;

@Component
public class CommandRunner {

    public CommandResult run(Path workingDirectory, List<String> command, Duration timeout) {
        Instant startedAt = Instant.now();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            process = builder.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                long duration = Duration.between(startedAt, Instant.now()).toMillis();
                return new CommandResult(-1, "Process timed out: " + String.join(" ", command), duration, true);
            }

            byte[] outputBytes = process.getInputStream().readAllBytes();
            String output = new String(outputBytes, StandardCharsets.UTF_8);
            long duration = Duration.between(startedAt, Instant.now()).toMillis();
            return new CommandResult(process.exitValue(), output, duration, false);
        } catch (IOException e) {
            long duration = Duration.between(startedAt, Instant.now()).toMillis();
            return new CommandResult(-1, e.getMessage(), duration, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            long duration = Duration.between(startedAt, Instant.now()).toMillis();
            return new CommandResult(-1, "Interrupted: " + e.getMessage(), duration, false);
        }
    }
}
