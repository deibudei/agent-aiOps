package org.example.agentaiops.repair.tool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.example.agentaiops.repair.model.CommandResult;
import org.springframework.stereotype.Component;

@Component
public class CommandRunner {

    /** Runs an external process in a fixed directory with a hard timeout. */
    public CommandResult run(Path workingDirectory, List<String> command, Duration timeout) {
        Instant startedAt = Instant.now();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(workingDirectory.toFile());
            builder.redirectErrorStream(true);
            process = builder.start();
            CompletableFuture<byte[]> outputFuture = readOutputAsync(process);

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                String output = readOutput(outputFuture);
                long duration = Duration.between(startedAt, Instant.now()).toMillis();
                return new CommandResult(
                        -1,
                        "Process timed out: " + String.join(" ", command) + System.lineSeparator() + output,
                        duration,
                        true);
            }

            String output = readOutput(outputFuture);
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

    /** Reads process output while the process is running to avoid pipe back-pressure deadlocks. */
    private CompletableFuture<byte[]> readOutputAsync(Process process) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return process.getInputStream().readAllBytes();
            } catch (IOException e) {
                return ("Failed to read process output: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            }
        });
    }

    /** Converts process output bytes into text with a short wait after process exit. */
    private String readOutput(CompletableFuture<byte[]> outputFuture) {
        try {
            return new String(outputFuture.get(2, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Interrupted while reading process output";
        } catch (ExecutionException | TimeoutException e) {
            return "Unable to read process output: " + e.getMessage();
        }
    }
}
