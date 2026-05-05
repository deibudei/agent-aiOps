package com.example.targetservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderServicePrecisionTest {

    private final OrderService orderService = new OrderService();

    @ParameterizedTest
    @CsvSource({
            "100.00, 0.85, 3, 255.00",
            "49.99, 0.80, 5, 199.96",
            "299.00, 0.90, 2, 538.20",
            "1.00,  0.10, 10, 1.00",
    })
    void discountPriceShouldBeExact(BigDecimal total, double discountRate, int quantity,
                                    BigDecimal expected) {
        BigDecimal result = orderService.calculateDiscountPrice(total, discountRate, quantity);
        assertThat(result).isEqualByComparingTo(expected);
    }

    @Test
    void rejectsNegativeQuantity() {
        assertThatThrownBy(() -> orderService.calculateDiscountPrice(
                BigDecimal.valueOf(100), 0.85, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsInvalidDiscountRate() {
        assertThatThrownBy(() -> orderService.calculateDiscountPrice(
                BigDecimal.valueOf(100), 1.5, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discountRate");
    }
}
