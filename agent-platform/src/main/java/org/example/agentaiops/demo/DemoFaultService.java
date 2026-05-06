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
    private static final String INVENTORY_SERVICE =
            "target-service/src/main/java/com/example/targetservice/service/InventoryService.java";
    private static final String FILE_STORAGE_SERVICE =
            "target-service/src/main/java/com/example/targetservice/service/FileStorageService.java";
    private static final String FILE_DOWNLOAD_CONTROLLER =
            "target-service/src/main/java/com/example/targetservice/controller/FileDownloadController.java";

    private final ToolPolicy toolPolicy;

    public DemoFaultService(ToolPolicy toolPolicy) {
        this.toolPolicy = toolPolicy;
    }

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
                case PRECISION_LOSS -> {
                    write(ORDER_SERVICE, orderServiceWithPrecisionBug());
                    yield List.of(ORDER_SERVICE);
                }
                case RACE_CONDITION -> {
                    write(INVENTORY_SERVICE, inventoryServiceWithoutLock());
                    yield List.of(INVENTORY_SERVICE);
                }
                case PATH_TRAVERSAL -> {
                    write(FILE_STORAGE_SERVICE, fileStorageServiceWithoutValidation());
                    write(FILE_DOWNLOAD_CONTROLLER, fileDownloadControllerBuggy());
                    yield List.of(FILE_STORAGE_SERVICE, FILE_DOWNLOAD_CONTROLLER);
                }
            };
            return new DemoFaultResult(
                    type.wireName(), true,
                    "Demo fault injected: " + type.description(),
                    changedFiles, nextSteps());
        } catch (IOException | IllegalArgumentException e) {
            return new DemoFaultResult(type.wireName(), false, e.getMessage(), List.of(), List.of());
        }
    }

    public DemoFaultResult reset() {
        try {
            write(ORDER_SERVICE, fixedOrderService());
            write(ORDER_CONTROLLER, fixedOrderController());
            write(GLOBAL_EXCEPTION_HANDLER, fixedGlobalExceptionHandler());
            write(INVENTORY_SERVICE, fixedInventoryService());
            write(FILE_STORAGE_SERVICE, fixedFileStorageService());
            write(FILE_DOWNLOAD_CONTROLLER, fixedFileDownloadController());
            return new DemoFaultResult(
                    "reset", true, "Demo faults reset to fixed baseline.",
                    List.of(ORDER_SERVICE, ORDER_CONTROLLER, GLOBAL_EXCEPTION_HANDLER,
                            INVENTORY_SERVICE, FILE_STORAGE_SERVICE, FILE_DOWNLOAD_CONTROLLER),
                    List.of("Run mvn -pl target-service test to verify the baseline."));
        } catch (IOException | IllegalArgumentException e) {
            return new DemoFaultResult("reset", false, e.getMessage(), List.of(), List.of());
        }
    }

    private void write(String displayPath, String content) throws IOException {
        Path resolved = toolPolicy.resolveForWrite(displayPath);
        Files.writeString(resolved, content);
    }

    private List<String> nextSteps() {
        return List.of(
                "Run mvn -pl target-service test to confirm the fault.",
                "Restart target-service before triggering an HTTP runtime fault.",
                "Call POST /api/repair/run to let the Agent repair it.");
    }

    // ── Fixed baselines ─────────────────────────────────────────────

    private String fixedOrderService() {
        return """
                package com.example.targetservice.service;

                import java.math.BigDecimal;
                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {

                    /** Calculates unit price, rejecting non-positive quantities. */
                    public int calculateUnitPrice(int totalCents, int quantity) {
                        if (quantity <= 0) {
                            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
                        }
                        return totalCents / quantity;
                    }

                    /**
                     * Calculates the discounted price for a bulk order.
                     * Uses BigDecimal throughout to avoid floating-point precision loss.
                     */
                    public BigDecimal calculateDiscountPrice(BigDecimal total, double discountRate, int quantity) {
                        if (quantity <= 0) {
                            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
                        }
                        if (discountRate <= 0 || discountRate >= 1) {
                            throw new IllegalArgumentException("discountRate must be between 0 and 1, but got: " + discountRate);
                        }
                        BigDecimal rate = BigDecimal.valueOf(discountRate);
                        BigDecimal qty = BigDecimal.valueOf(quantity);
                        return total.multiply(rate).multiply(qty);
                    }
                }
                """;
    }

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

                    public OrderController(OrderService orderService) {
                        this.orderService = orderService;
                    }

                    @GetMapping("/api/orders/quote")
                    public OrderQuoteResponse quote(@RequestParam int totalCents, @RequestParam int quantity) {
                        int unitPriceCents = orderService.calculateUnitPrice(totalCents, quantity);
                        return new OrderQuoteResponse(totalCents, quantity, unitPriceCents);
                    }
                }
                """;
    }

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

                    public GlobalExceptionHandler(ErrorTracebackWriter tracebackWriter) {
                        this.tracebackWriter = tracebackWriter;
                    }

                    @ExceptionHandler(IllegalArgumentException.class)
                    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of(
                                        "timestamp", Instant.now().toString(),
                                        "status", HttpStatus.BAD_REQUEST.value(),
                                        "error", "Bad Request",
                                        "message", message(exception)));
                    }

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

    private String fixedInventoryService() {
        return """
                package com.example.targetservice.service;

                import com.example.targetservice.model.InventoryItem;
                import com.example.targetservice.repository.InventoryRepository;
                import java.util.concurrent.locks.ReentrantLock;
                import org.springframework.stereotype.Service;

                @Service
                public class InventoryService {

                    private final InventoryRepository repository;
                    private final ReentrantLock lock = new ReentrantLock();

                    public InventoryService(InventoryRepository repository) {
                        this.repository = repository;
                    }

                    /** Deducts stock with a lock to prevent concurrent over-selling. */
                    public InventoryItem deduct(String sku, int quantity) {
                        if (quantity <= 0) {
                            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
                        }
                        lock.lock();
                        try {
                            InventoryItem item = repository.findBySku(sku);
                            if (item.stock() < quantity) {
                                throw new IllegalArgumentException(
                                        "insufficient stock for " + sku + ": have " + item.stock() + ", need " + quantity);
                            }
                            InventoryItem updated = new InventoryItem(item.sku(), item.name(),
                                    item.stock() - quantity, item.reserved() + quantity);
                            return repository.save(updated);
                        } finally {
                            lock.unlock();
                        }
                    }

                    public InventoryItem getStock(String sku) {
                        return repository.findBySku(sku);
                    }
                }
                """;
    }

    private String fixedFileStorageService() {
        return """
                package com.example.targetservice.service;

                import java.io.IOException;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.nio.file.Paths;
                import org.springframework.stereotype.Service;

                @Service
                public class FileStorageService {

                    private static final Path BASE_DIR = Paths.get("files").toAbsolutePath().normalize();

                    public FileStorageService() throws IOException {
                        Files.createDirectories(BASE_DIR);
                    }

                    /** Reads a file, rejecting path traversal attempts. */
                    public String readFile(String fileName) throws IOException {
                        Path filePath = BASE_DIR.resolve(fileName).toAbsolutePath().normalize();
                        if (!filePath.startsWith(BASE_DIR)) {
                            throw new IllegalArgumentException("path traversal denied: " + fileName);
                        }
                        return Files.readString(filePath);
                    }

                    public Path baseDir() {
                        return BASE_DIR;
                    }
                }
                """;
    }

    private String fixedFileDownloadController() {
        return """
                package com.example.targetservice.controller;

                import com.example.targetservice.service.FileStorageService;
                import java.io.IOException;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class FileDownloadController {

                    private final FileStorageService fileStorageService;

                    public FileDownloadController(FileStorageService fileStorageService) {
                        this.fileStorageService = fileStorageService;
                    }

                    @GetMapping("/api/files/download")
                    public String download(@RequestParam String fileName) throws IOException {
                        return fileStorageService.readFile(fileName);
                    }
                }
                """;
    }

    // ── Buggy variants (existing) ───────────────────────────────────

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

                    public OrderController(OrderService orderService) {
                        this.orderService = orderService;
                    }

                    @GetMapping("/api/orders/price")
                    public OrderQuoteResponse quote(@RequestParam int totalCents, @RequestParam int quantity) {
                        int unitPriceCents = orderService.calculateUnitPrice(totalCents, quantity);
                        return new OrderQuoteResponse(totalCents, quantity, unitPriceCents);
                    }
                }
                """;
    }

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

                    public GlobalExceptionHandler(ErrorTracebackWriter tracebackWriter) {
                        this.tracebackWriter = tracebackWriter;
                    }

                    @ExceptionHandler(IllegalArgumentException.class)
                    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of(
                                        "timestamp", Instant.now().toString(),
                                        "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                        "error", "Internal Server Error",
                                        "message", message(exception)));
                    }

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

    // ── Buggy variants (new) ────────────────────────────────────────

    /** OrderService with double-based discount calculation (precision loss). */
    private String orderServiceWithPrecisionBug() {
        return """
                package com.example.targetservice.service;

                import java.math.BigDecimal;
                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {

                    /** Calculates unit price, rejecting non-positive quantities. */
                    public int calculateUnitPrice(int totalCents, int quantity) {
                        if (quantity <= 0) {
                            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
                        }
                        return totalCents / quantity;
                    }

                    /**
                     * Calculates the discounted price for a bulk order.
                     * BUG: Uses double for intermediate calculation, causing floating-point precision loss.
                     */
                    public BigDecimal calculateDiscountPrice(BigDecimal total, double discountRate, int quantity) {
                        if (quantity <= 0) {
                            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
                        }
                        if (discountRate <= 0 || discountRate >= 1) {
                            throw new IllegalArgumentException("discountRate must be between 0 and 1, but got: " + discountRate);
                        }
                        double perItem = total.doubleValue() * discountRate;
                        return BigDecimal.valueOf(perItem * quantity);
                    }
                }
                """;
    }

    /** InventoryService without synchronization (race condition). */
    private String inventoryServiceWithoutLock() {
        return """
                package com.example.targetservice.service;

                import com.example.targetservice.model.InventoryItem;
                import com.example.targetservice.repository.InventoryRepository;
                import org.springframework.stereotype.Service;

                @Service
                public class InventoryService {

                    private final InventoryRepository repository;

                    public InventoryService(InventoryRepository repository) {
                        this.repository = repository;
                    }

                    /**
                     * Deducts stock for a given SKU.
                     * BUG: No synchronization \\u2014 concurrent calls can over-sell inventory.
                     */
                    public InventoryItem deduct(String sku, int quantity) {
                        if (quantity <= 0) {
                            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
                        }
                        InventoryItem item = repository.findBySku(sku);
                        if (item.stock() < quantity) {
                            throw new IllegalArgumentException(
                                    "insufficient stock for " + sku + ": have " + item.stock() + ", need " + quantity);
                        }
                        InventoryItem updated = new InventoryItem(item.sku(), item.name(),
                                item.stock() - quantity, item.reserved() + quantity);
                        return repository.save(updated);
                    }

                    public InventoryItem getStock(String sku) {
                        return repository.findBySku(sku);
                    }
                }
                """;
    }

    /** FileStorageService without path traversal protection. */
    private String fileStorageServiceWithoutValidation() {
        return """
                package com.example.targetservice.service;

                import java.io.IOException;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.nio.file.Paths;
                import org.springframework.stereotype.Service;

                @Service
                public class FileStorageService {

                    private static final Path BASE_DIR = Paths.get("files").toAbsolutePath().normalize();

                    public FileStorageService() throws IOException {
                        Files.createDirectories(BASE_DIR);
                    }

                    /**
                     * Reads a file from the storage directory.
                     * BUG: No path traversal protection \\u2014 "../" sequences allow reading arbitrary files.
                     */
                    public String readFile(String fileName) throws IOException {
                        Path filePath = BASE_DIR.resolve(fileName);
                        return Files.readString(filePath);
                    }

                    public Path baseDir() {
                        return BASE_DIR;
                    }
                }
                """;
    }

    /** FileDownloadController — same as fixed, the bug is in FileStorageService. */
    private String fileDownloadControllerBuggy() {
        return """
                package com.example.targetservice.controller;

                import com.example.targetservice.service.FileStorageService;
                import java.io.IOException;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class FileDownloadController {

                    private final FileStorageService fileStorageService;

                    public FileDownloadController(FileStorageService fileStorageService) {
                        this.fileStorageService = fileStorageService;
                    }

                    @GetMapping("/api/files/download")
                    public String download(@RequestParam String fileName) throws IOException {
                        return fileStorageService.readFile(fileName);
                    }
                }
                """;
    }
}
