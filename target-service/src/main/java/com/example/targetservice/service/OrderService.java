package com.example.targetservice.service;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    /** Calculates unit price, validating quantity to prevent division by zero. */
    public int calculateUnitPrice(int totalCents, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        return totalCents / quantity;
    }
}
