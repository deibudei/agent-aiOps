package com.example.targetservice.controller;

import com.example.targetservice.model.InventoryItem;
import com.example.targetservice.service.InventoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/api/inventory/stock")
    public InventoryItem getStock(@RequestParam String sku) {
        return inventoryService.getStock(sku);
    }

    @PostMapping("/api/inventory/deduct")
    public InventoryItem deduct(@RequestParam String sku, @RequestParam int quantity) {
        return inventoryService.deduct(sku, quantity);
    }
}
