package com.example.targetservice.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

class ErrorTracebackWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesOneTracebackFilePerUnexpectedFailure() throws Exception {
        Path tracebackDir = tempDir.resolve("logs/tracebacks");
        ErrorTracebackWriter writer = new ErrorTracebackWriter(
                tracebackDir,
                Clock.fixed(Instant.parse("2026-04-26T08:30:15Z"), ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders/quote");
        request.setQueryString("totalCents=100&quantity=0");

        Path first = writer.write(new ArithmeticException("/ by zero"), request);
        Path second = writer.write(new IllegalStateException("boom"), request);

        assertThat(first).isNotEqualTo(second);
        try (var stream = Files.list(tracebackDir)) {
            List<Path> tracebacks = stream.toList();
            assertThat(tracebacks).hasSize(2);
        }
        String firstContent = Files.readString(first);
        assertThat(firstContent).contains("traceId=");
        assertThat(firstContent).contains("path=/api/orders/quote");
        assertThat(firstContent).contains("java.lang.ArithmeticException: / by zero");
    }
}
