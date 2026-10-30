package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.CheckoutResult;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.port.in.OrderPortIn;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import com.petshop.order_service.core.port.out.PurchaseHistoryPortOut;
import com.petshop.order_service.infrastructure.persistence.postgresql.repository.CartRepository;
import com.petshop.order_service.infrastructure.persistence.postgresql.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Double-checkout contra Postgres de verdade (o SELECT ... FOR UPDATE do
 * carrinho é o que está sendo testado — H2 não serve). OrderService real;
 * só os ports externos são mockados, com a reserva "lenta" para alargar a
 * janela de corrida.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.show-sql=false"
})
class CheckoutConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    private static final String TOKEN = "Bearer test-token";
    private static final long SLOW_RESERVE_MS = 300;

    @Autowired
    private OrderPortIn orderPortIn;
    @Autowired
    private CartPortOut cartPortOut;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ProductStockPortOut productStockPortOut;
    @MockitoBean
    private CustomerPortOut customerPortOut;
    @MockitoBean
    private NotificationPortOut notificationPortOut;
    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    @BeforeEach
    void setUp() {
        when(productStockPortOut.getInfos(any(), any())).thenAnswer(inv -> {
            Map<Long, ProductStockInfo> infos = new HashMap<>();
            for (Long id : inv.<Collection<Long>>getArgument(0)) {
                infos.put(id, new ProductStockInfo(id, "Produto " + id, 10.0, true, "50+"));
            }
            return infos;
        });
        when(productStockPortOut.reserve(any(), anyInt(), any())).thenAnswer(inv -> {
            Thread.sleep(SLOW_RESERVE_MS);
            return new ProductStockInfo(inv.getArgument(0), "Produto", 10.0, true, "50+");
        });
        when(customerPortOut.findCustomerById(any(), any()))
                .thenAnswer(inv -> Optional.of(new CustomerInfo(inv.getArgument(0), "Maria", "maria@mail.com")));
    }

    private UUID customerWithCart(Long... productIds) {
        var customerId = UUID.randomUUID();
        var cart = Cart.openFor(customerId, 24);
        for (Long id : productIds) {
            cart.addOrIncrementItem(id, "Produto " + id, 10.0, 1);
        }
        cartPortOut.save(cart);
        return customerId;
    }

    private long ordersOf(UUID customerId) {
        return orderRepository.findAll().stream().filter(o -> o.getCustomerId().equals(customerId)).count();
    }

    private List<Object> runTogether(List<Callable<CheckoutResult>> calls) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            var futures = calls.stream().map(call -> executor.submit(() -> {
                start.await();
                return call.call();
            })).toList();
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (var future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        }
    }

    @RepeatedTest(5)
    void twoSimultaneousCheckoutsOfTheSameCartCreateExactlyOneOrder() throws Exception {
        var customerId = customerWithCart(1L, 2L);
        Callable<CheckoutResult> checkout = () -> orderPortIn.checkout(customerId, List.of(1L, 2L), null, TOKEN);

        var outcomes = runTogether(List.of(checkout, checkout));

        assertThat(outcomes).filteredOn(CheckoutResult.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(BusinessRuleException.class::isInstance).singleElement()
                .satisfies(e -> assertThat(((Exception) e).getMessage()).contains("Não há carrinho aberto"));
        assertThat(ordersOf(customerId)).isEqualTo(1);
        assertThat(cartRepository.findByCustomerIdAndStatusOrderByCreatedAtDesc(customerId, CartStatus.CHECKED_OUT)).hasSize(1);
        verify(productStockPortOut, times(1)).reserve(eq(1L), eq(1), any());
        verify(productStockPortOut, times(1)).reserve(eq(2L), eq(1), any());
    }

    @RepeatedTest(5)
    void twoSimultaneousCheckoutsWithTheSameKeyReturnTheSameOrder() throws Exception {
        var customerId = customerWithCart(1L, 2L);
        var key = UUID.randomUUID().toString();
        Callable<CheckoutResult> checkout = () -> orderPortIn.checkout(customerId, List.of(1L, 2L), key, TOKEN);

        var outcomes = runTogether(List.of(checkout, checkout));

        assertThat(outcomes).allMatch(CheckoutResult.class::isInstance);
        var results = outcomes.stream().map(CheckoutResult.class::cast).toList();
        assertThat(results).extracting(r -> r.order().getId()).containsOnly(results.get(0).order().getId());
        assertThat(results).extracting(CheckoutResult::created).containsExactlyInAnyOrder(true, false);
        assertThat(ordersOf(customerId)).isEqualTo(1);
    }

    @Test
    void checkoutsOfDifferentCustomersDoNotBlockEachOther() throws Exception {
        var slowCustomer = customerWithCart(1L);
        var fastCustomer = customerWithCart(3L);
        var slowReserveStarted = new CountDownLatch(1);
        var releaseSlowReserve = new CountDownLatch(1);
        doAnswer(inv -> {
            if (inv.<Long>getArgument(0) == 1L) {
                slowReserveStarted.countDown();
                releaseSlowReserve.await(10, TimeUnit.SECONDS);
            }
            return new ProductStockInfo(inv.getArgument(0), "Produto", 10.0, true, "50+");
        }).when(productStockPortOut).reserve(any(), anyInt(), any());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var slow = executor.submit(() -> orderPortIn.checkout(slowCustomer, List.of(1L), null, TOKEN));
            assertThat(slowReserveStarted.await(10, TimeUnit.SECONDS)).isTrue();

            // O cliente lento está com o carrinho travado; o outro não pode esperar por ele.
            long startedAt = System.nanoTime();
            var fast = orderPortIn.checkout(fastCustomer, List.of(3L), null, TOKEN);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            releaseSlowReserve.countDown();
            assertThat(fast.created()).isTrue();
            assertThat(elapsedMs).isLessThan(5_000);
            assertThat(slow.get(30, TimeUnit.SECONDS).created()).isTrue();
        }
    }
}
