package com.example.targetservice.service;

import com.example.targetservice.model.InventoryItem;
import com.example.targetservice.repository.InventoryRepository;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final InventoryRepository repository;
    private final ReentrantLock lock = new ReentrantLock();

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    /** Deducts stock with a process-local lock to prevent concurrent over-selling. */
    public InventoryItem deduct(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
        }
        lock.lock();
        try {
            InventoryItem item = repository.findBySku(sku);
            if (item.stock() < quantity) {
                throw new IllegalArgumentException(
                        "insufficient stock for " + sku + ": have " + item.stock() + ", need " + quantity);
            }
            InventoryItem updated = new InventoryItem(item.sku(), item.name(),
                    item.stock() - quantity, item.reserved() + quantity);
            return repository.save(updated);
        } finally {
            lock.unlock();
        }
    }

    public InventoryItem getStock(String sku) {
        return repository.findBySku(sku);
    }
}
