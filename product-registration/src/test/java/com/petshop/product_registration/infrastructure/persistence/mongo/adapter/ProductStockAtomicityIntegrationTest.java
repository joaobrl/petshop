package com.petshop.product_registration.infrastructure.persistence.mongo.adapter;

import com.petshop.product_registration.infrastructure.persistence.mongo.entity.ProductDocument;
import com.petshop.product_registration.infrastructure.persistence.mongo.mapper.ProductMapperImpl;
import com.petshop.product_registration.infrastructure.persistence.mongo.sequence.ProductSequenceGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reserva/liberação/baixa contra um Mongo de verdade, com threads
 * disparando juntas (latch) — repetido para não passar por sorte.
 */
@DataMongoTest
@Testcontainers
@Import({ProductPersistenceAdapterOut.class, ProductMapperImpl.class, ProductSequenceGenerator.class})
class ProductStockAtomicityIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @Autowired
    private ProductPersistenceAdapterOut adapter;

    @Autowired
    private MongoOperations mongoOperations;

    @BeforeEach
    void clean() {
        mongoOperations.remove(new Query(), ProductDocument.class);
    }

    private ProductDocument insert(long id, int stock, int reservedStock, boolean enabled) {
        var product = new ProductDocument();
        product.setId(id);
        product.setName("Produto " + id);
        product.setPrice(10.0);
        product.setStock(stock);
        product.setReservedStock(reservedStock);
        product.setEnabled(enabled);
        return mongoOperations.save(product);
    }

    private int reservedStockOf(long id) {
        return mongoOperations.findById(id, ProductDocument.class).getReservedStock();
    }

    private long concurrentReserves(long id, int threads, int quantity) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(threads)) {
            List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        start.await();
                        return adapter.tryReserve(id, quantity).isPresent();
                    })
                    .toList();
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            long successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) successes++;
            }
            return successes;
        }
    }

    @RepeatedTest(20)
    void tenConcurrentReservesOfOneWithStockFiveReserveExactlyFive() throws Exception {
        insert(1L, 5, 0, true);

        assertThat(concurrentReserves(1L, 10, 1)).isEqualTo(5);
        assertThat(reservedStockOf(1L)).isEqualTo(5);
    }

    @RepeatedTest(20)
    void twoCustomersRacingForTheLastItemOnlyOneWins() throws Exception {
        insert(1L, 1, 0, true);

        assertThat(concurrentReserves(1L, 2, 1)).isEqualTo(1);
        assertThat(reservedStockOf(1L)).isEqualTo(1);
    }

    @Test
    void reserveAboveAvailableReservesNothing() {
        insert(1L, 5, 0, true);

        assertThat(adapter.tryReserve(1L, 10)).isEmpty();
        assertThat(reservedStockOf(1L)).isZero();
    }

    @Test
    void reserveOfDisabledProductReservesNothing() {
        insert(1L, 5, 0, false);

        assertThat(adapter.tryReserve(1L, 1)).isEmpty();
        assertThat(reservedStockOf(1L)).isZero();
    }

    @Test
    void reserveOfMissingProductIsEmpty() {
        assertThat(adapter.tryReserve(999L, 1)).isEmpty();
    }

    @Test
    void releaseNeverDropsReservedStockBelowZero() {
        insert(1L, 5, 0, true);

        assertThat(adapter.tryRelease(1L, 1)).isEmpty();
        assertThat(reservedStockOf(1L)).isZero();
    }

    @Test
    void releaseDecrementsReservedStock() {
        insert(1L, 5, 3, true);

        assertThat(adapter.tryRelease(1L, 2)).get()
                .satisfies(product -> assertThat(product.getReservedStock()).isEqualTo(1));
    }

    @Test
    void confirmDecrementsStockAndReservedStockTogether() {
        insert(1L, 10, 4, true);

        var confirmed = adapter.tryConfirm(1L, 3).orElseThrow();

        assertThat(confirmed.getStock()).isEqualTo(7);
        assertThat(confirmed.getReservedStock()).isEqualTo(1);
    }

    @Test
    void confirmAboveReservedChangesNothing() {
        insert(1L, 10, 1, true);

        assertThat(adapter.tryConfirm(1L, 2)).isEmpty();
        var product = mongoOperations.findById(1L, ProductDocument.class);
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(product.getReservedStock()).isEqualTo(1);
    }

    @Test
    void updateDetailsDoesNotOverwriteReservedStock() {
        insert(1L, 10, 0, true);
        var staleRead = adapter.findById(1L).orElseThrow();
        adapter.tryReserve(1L, 4);

        staleRead.setName("Nome novo");
        var updated = adapter.updateDetails(staleRead).orElseThrow();

        assertThat(updated.getName()).isEqualTo("Nome novo");
        assertThat(updated.getReservedStock()).isEqualTo(4);
    }

    @Test
    void findAllByIdsReturnsExistingIncludingDisabled() {
        insert(1L, 5, 0, true);
        insert(2L, 5, 0, false);

        assertThat(adapter.findAllByIds(List.of(1L, 2L, 999L)))
                .extracting(product -> product.getId())
                .containsExactlyInAnyOrder(1L, 2L);
    }
}
