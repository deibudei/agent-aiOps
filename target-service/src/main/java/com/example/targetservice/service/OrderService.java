package com.example.targetservice.service;

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
}
