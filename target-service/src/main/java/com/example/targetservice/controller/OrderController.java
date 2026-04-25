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
