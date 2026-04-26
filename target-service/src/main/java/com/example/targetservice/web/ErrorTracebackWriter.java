package com.example.targetservice.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ErrorTracebackWriter {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private final Path logDirectory;
    private final Clock clock;

    /** Uses the target-service traceback log directory from configuration. */
    @Autowired
    public ErrorTracebackWriter(@Value("${target-service.traceback-log-dir:logs/tracebacks}") String logDirectory) {
        this(Path.of(logDirectory), Clock.systemUTC());
    }

    ErrorTracebackWriter(Path logDirectory, Clock clock) {
        this.logDirectory = logDirectory;
        this.clock = clock;
    }

    /** Writes one standalone traceback file and returns the path used for the response body. */
    public Path write(Throwable throwable, HttpServletRequest request) {
        try {
            Files.createDirectories(logDirectory);
            String traceId = UUID.randomUUID().toString().substring(0, 8);
            Instant timestamp = clock.instant();
            Path tracebackPath = logDirectory.resolve("traceback-%s-%s.log".formatted(
                    FILE_TIMESTAMP.format(timestamp), traceId));
            Files.writeString(tracebackPath, formatTraceback(timestamp, traceId, throwable, request));
            return tracebackPath;
        } catch (Exception writeFailure) {
            throw new IllegalStateException("Failed to write traceback log", writeFailure);
        }
    }

    private String formatTraceback(Instant timestamp, String traceId, Throwable throwable, HttpServletRequest request) {
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));
        return """
                timestamp=%s
                traceId=%s
                method=%s
                path=%s
                query=%s
                exception=%s: %s

                %s
                """.formatted(
                timestamp,
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                throwable.getClass().getName(),
                throwable.getMessage(),
                stackTrace);
    }
}
