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
     * Deducts stock for a given SKU using atomic operation.
     */
    public InventoryItem deduct(String sku, int quantity) {
        return repository.deductStock(sku, quantity);
    }

    public InventoryItem getStock(String sku) {
        return repository.findBySku(sku);
    }
}
