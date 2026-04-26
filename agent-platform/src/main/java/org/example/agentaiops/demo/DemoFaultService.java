package org.example.agentaiops.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.example.agentaiops.repair.tool.ToolPolicy;
import org.springframework.stereotype.Service;

@Service
public class DemoFaultService {

    private static final String ORDER_SERVICE =
            "target-service/src/main/java/com/example/targetservice/service/OrderService.java";
    private static final String ORDER_CONTROLLER =
            "target-service/src/main/java/com/example/targetservice/controller/OrderController.java";
    private static final String GLOBAL_EXCEPTION_HANDLER =
            "target-service/src/main/java/com/example/targetservice/web/GlobalExceptionHandler.java";

    private final ToolPolicy toolPolicy;

    /** Keeps demo fault writes inside the target-service source whitelist. */
    public DemoFaultService(ToolPolicy toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

    /** Lists all available demo fault types. */
    public List<DemoFaultResult> listFaults() {
        return Arrays.stream(DemoFaultType.values())
                .map(type -> new DemoFaultResult(
                        type.wireName(),
                        true,
                        type.description(),
                        List.of(),
                        List.of("POST /api/demo/faults/" + type.wireName() + "/inject")))
                .toList();
    }

    /** Injects one selected source-level fault into target-service. */
    public DemoFaultResult inject(String faultType) {
        DemoFaultType type = DemoFaultType.fromWireName(faultType);
        try {
            List<String> changedFiles = switch (type) {
                case QUANTITY_DIVISION_BY_ZERO -> {
                    write(ORDER_SERVICE, buggyOrderService());
                    yield List.of(ORDER_SERVICE);
                }
                case WRONG_QUOTE_ROUTE -> {
                    write(ORDER_CONTROLLER, buggyOrderController());
                    yield List.of(ORDER_CONTROLLER);
                }
                case WRONG_ERROR_STATUS -> {
                    write(GLOBAL_EXCEPTION_HANDLER, buggyGlobalExceptionHandler());
                    yield List.of(GLOBAL_EXCEPTION_HANDLER);
                }
            };
            return new DemoFaultResult(
                    type.wireName(),
                    true,
                    "Demo fault injected: " + type.description(),
                    changedFiles,
                    nextSteps());
        } catch (IOException | IllegalArgumentException e) {
            return new DemoFaultResult(type.wireName(), false, e.getMessage(), List.of(), List.of());
        }
    }

    /** Restores all demo-touched files to the fixed baseline state. */
    public DemoFaultResult reset() {
        try {
            write(ORDER_SERVICE, fixedOrderService());
            write(ORDER_CONTROLLER, fixedOrderController());
            write(GLOBAL_EXCEPTION_HANDLER, fixedGlobalExceptionHandler());
            return new DemoFaultResult(
                    "reset",
                    true,
                    "Demo faults reset to fixed baseline.",
                    List.of(ORDER_SERVICE, ORDER_CONTROLLER, GLOBAL_EXCEPTION_HANDLER),
                    List.of("Run mvn -pl target-service test to verify the baseline."));
        } catch (IOException | IllegalArgumentException e) {
            return new DemoFaultResult("reset", false, e.getMessage(), List.of(), List.of());
        }
    }

    /** Writes a complete source file through ToolPolicy path checks. */
    private void write(String displayPath, String content) throws IOException {
        Path resolved = toolPolicy.resolveForWrite(displayPath);
        Files.writeString(resolved, content);
    }

    /** Returns operator steps after injecting a source-level fault. */
    private List<String> nextSteps() {
        return List.of(
                "Run mvn -pl target-service test to confirm the fault.",
                "Restart target-service before triggering an HTTP runtime fault.",
                "Call POST /api/repair/run to let the Agent repair it.");
    }

    /** Fixed OrderService baseline. */
    private String fixedOrderService() {
        return """
                package com.example.targetservice.service;

                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {

                    /** Calculates unit price and rejects invalid quantities. */
                    public int calculateUnitPrice(int totalCents, int quantity) {
                        if (quantity <= 0) {
                            throw new IllegalArgumentException("quantity must be greater than 0");
                        }
                        return totalCents / quantity;
                    }
                }
                """;
    }

    /** Faulty OrderService without quantity validation. */
    private String buggyOrderService() {
        return """
                package com.example.targetservice.service;

                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {

                    /** Calculates unit price without guarding invalid quantities. */
                    public int calculateUnitPrice(int totalCents, int quantity) {
                        return totalCents / quantity;
                    }
                }
                """;
    }

    /** Fixed OrderController baseline. */
    private String fixedOrderController() {
        return """
                package com.example.targetservice.controller;

                import com.example.targetservice.model.OrderQuoteResponse;
                import com.example.targetservice.service.OrderService;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class OrderController {

                    private final OrderService orderService;

                    /** Injects the order pricing service. */
                    public OrderController(OrderService orderService) {
                        this.orderService = orderService;
                    }

                    /** Returns a quote with the calculated unit price. */
                    @GetMapping("/api/orders/quote")
                    public OrderQuoteResponse quote(@RequestParam int totalCents, @RequestParam int quantity) {
                        int unitPriceCents = orderService.calculateUnitPrice(totalCents, quantity);
                        return new OrderQuoteResponse(totalCents, quantity, unitPriceCents);
                    }
                }
                """;
    }

    /** Faulty OrderController with an incorrect endpoint path. */
    private String buggyOrderController() {
        return """
                package com.example.targetservice.controller;

                import com.example.targetservice.model.OrderQuoteResponse;
                import com.example.targetservice.service.OrderService;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class OrderController {

                    private final OrderService orderService;

                    /** Injects the order pricing service. */
                    public OrderController(OrderService orderService) {
                        this.orderService = orderService;
                    }

                    /** Returns a quote with the calculated unit price. */
                    @GetMapping("/api/orders/price")
                    public OrderQuoteResponse quote(@RequestParam int totalCents, @RequestParam int quantity) {
                        int unitPriceCents = orderService.calculateUnitPrice(totalCents, quantity);
                        return new OrderQuoteResponse(totalCents, quantity, unitPriceCents);
                    }
                }
                """;
    }

    /** Fixed exception handler baseline. */
    private String fixedGlobalExceptionHandler() {
        return """
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
                        String logFile = tracebackWriter.write(exception, request).toString().replace('\\\\', '/');
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
                """;
    }

    /** Faulty exception handler that maps validation failures to HTTP 500. */
    private String buggyGlobalExceptionHandler() {
        return """
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

                    /** Converts validation failures into the wrong response status. */
                    @ExceptionHandler(IllegalArgumentException.class)
                    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                        "timestamp", Instant.now().toString(),
                                        "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                        "error", "Internal Server Error",
                                        "message", message(exception)));
                    }

                    /** Writes unexpected runtime failures into one traceback file per request. */
                    @ExceptionHandler(Exception.class)
                    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception, HttpServletRequest request) {
                        String logFile = tracebackWriter.write(exception, request).toString().replace('\\\\', '/');
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
                """;
    }
}
