package com.example.targetservice.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ErrorTracebackWriter tracebackWriter;

    /** Injects the traceback writer used for runtime 500 errors. */
    public GlobalExceptionHandler(ErrorTracebackWriter tracebackWriter) {
        this.tracebackWriter = tracebackWriter;
    }

    /** Converts validation failures into a 400 response body. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", HttpStatus.BAD_REQUEST.value(),
                        "error", "Bad Request",
                        "message", message(exception)));
    }

    /** Writes unexpected runtime failures into one traceback file per request. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception, HttpServletRequest request) {
        String logFile = tracebackWriter.write(exception, request).toString().replace('\\', '/');
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "timestamp", Instant.now().toString(),
                        "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "error", "Internal Server Error",
                        "message", message(exception),
                        "tracebackLog", logFile));
    }

    private String message(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage();
    }
}
