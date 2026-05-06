package com.example.targetservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.targetservice.repository.InventoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class InventoryServiceConcurrencyTest {

    private final InventoryRepository repository = new InventoryRepository();
    private final InventoryService inventoryService = new InventoryService(repository);

    @Test
    void concurrentDeductionShouldNotOverSell() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                inventoryService.deduct("SKU-003", 1);
                return null;
            }));
        }

        latch.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        int finalStock = repository.stock("SKU-003");
        assertThat(finalStock).as("stock should never go negative").isGreaterThanOrEqualTo(0);
        assertThat(finalStock).as("total deductions should not exceed initial stock").isEqualTo(0);
    }
}
