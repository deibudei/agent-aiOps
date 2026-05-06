package org.example.agentaiops.demo;

/** Supported source-level faults for local repair demos. */
public enum DemoFaultType {
    QUANTITY_DIVISION_BY_ZERO(
            "quantity-division-by-zero",
            "Remove quantity validation so quantity=0 triggers ArithmeticException."),
    WRONG_QUOTE_ROUTE(
            "wrong-quote-route",
            "Change the quote endpoint path so controller tests fail with 404."),
    WRONG_ERROR_STATUS(
            "wrong-error-status",
            "Return HTTP 500 for validation errors instead of HTTP 400."),
    PRECISION_LOSS(
            "precision-loss",
            "Use double for discount calculation, causing floating-point precision loss."),
    RACE_CONDITION(
            "race-condition",
            "Missing synchronization in inventory deduction causes over-selling under concurrent load."),
    PATH_TRAVERSAL(
            "path-traversal",
            "Missing path validation in file download allows reading arbitrary files via '../'.");

    private final String wireName;
    private final String description;

    DemoFaultType(String wireName, String description) {
        this.wireName = wireName;
        this.description = description;
    }

    /** Returns the API-facing fault name. */
    public String wireName() {
        return wireName;
    }

    /** Returns a short human-readable fault description. */
    public String description() {
        return description;
    }

    /** Parses an API-facing fault name into an enum value. */
    public static DemoFaultType fromWireName(String wireName) {
        for (DemoFaultType type : values()) {
            if (type.wireName.equalsIgnoreCase(wireName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported demo fault type: " + wireName);
    }
}
