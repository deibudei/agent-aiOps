package com.example.targetservice.model;

public record InventoryItem(String sku, String name, int stock, int reserved) {
}
