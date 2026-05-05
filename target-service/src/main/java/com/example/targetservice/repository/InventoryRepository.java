package com.example.targetservice.repository;

import com.example.targetservice.model.InventoryItem;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {

    private final Map<String, InventoryItem> store = new ConcurrentHashMap<>();

    public InventoryRepository() {
        store.put("SKU-001", new InventoryItem("SKU-001", "Wireless Mouse", 100, 0));
        store.put("SKU-002", new InventoryItem("SKU-002", "Mechanical Keyboard", 50, 0));
        store.put("SKU-003", new InventoryItem("SKU-003", "USB-C Hub", 10, 0));
    }

    public InventoryItem findBySku(String sku) {
        InventoryItem item = store.get(sku);
        if (item == null) {
            throw new IllegalArgumentException("SKU not found: " + sku);
        }
        return item;
    }

    public InventoryItem save(InventoryItem item) {
        store.put(item.sku(), item);
        return item;
    }

    public int stock(String sku) {
        return findBySku(sku).stock();
    }

    /**
     * Atomically deducts stock for a given SKU.
     * Uses ConcurrentHashMap.compute to ensure read-check-write atomicity.
     * @throws IllegalArgumentException if SKU not found or insufficient stock
     */
    public InventoryItem deductStock(String sku, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, but got: " + quantity);
        }
        return store.compute(sku, (key, item) -> {
            if (item == null) {
                throw new IllegalArgumentException("SKU not found: " + sku);
            }
            if (item.stock() < quantity) {
                throw new IllegalArgumentException(
                        "insufficient stock for " + sku + ": have " + item.stock() + ", need " + quantity);
            }
            return new InventoryItem(item.sku(), item.name(),
                    item.stock() - quantity, item.reserved() + quantity);
        });
    }
}
