package com.example.targetservice.service;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    /** Calculates unit price without guarding invalid quantities. */
    public int calculateUnitPrice(int totalCents, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        return totalCents / quantity;
    }
}
