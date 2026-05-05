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
     * Deducts stock for a given SKU.
     * BUG: No synchronization — concurrent calls can over-sell inventory.
     */
    public InventoryItem deduct(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
        }
        InventoryItem item = repository.findBySku(sku);
        if (item.stock() < quantity) {
            throw new IllegalArgumentException(
                    "insufficient stock for " + sku + ": have " + item.stock() + ", need " + quantity);
        }
        InventoryItem updated = new InventoryItem(item.sku(), item.name(),
                item.stock() - quantity, item.reserved() + quantity);
        return repository.save(updated);
    }

    public InventoryItem getStock(String sku) {
        return repository.findBySku(sku);
    }
}
