package com.example.targetservice.service;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    public int calculateUnitPrice(int totalCents, int quantity) {
        return totalCents / quantity;
    }
}
