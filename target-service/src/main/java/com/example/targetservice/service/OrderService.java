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
