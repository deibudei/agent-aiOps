package com.example.targetservice.model;

import java.math.BigDecimal;

public record DiscountRequest(BigDecimal total, double discountRate, int quantity) {
}
