package com.example.targetservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OrderServiceTest {

    private final OrderService orderService = new OrderService();

    @Test
    void calculatesUnitPrice() {
        assertThat(orderService.calculateUnitPrice(1200, 3)).isEqualTo(400);
    }

    @Test
    void rejectsZeroQuantity() {
        assertThatThrownBy(() -> orderService.calculateUnitPrice(1200, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }
}
