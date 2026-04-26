package com.example.targetservice.model;

/** API response for an order quote request. */
public record OrderQuoteResponse(int totalCents, int quantity, int unitPriceCents) {
}
