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
}
