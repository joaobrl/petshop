package com.petshop.order_service.core.application.scheduler;

import com.petshop.order_service.core.port.in.OrderPortIn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

    @Mock
    private OrderPortIn orderPortIn;

    @Test
    void delegatesToOrderPortIn() {
        var scheduler = new OrderExpirationScheduler(orderPortIn);

        scheduler.expireUnpaidOrders();

        verify(orderPortIn).expireUnpaidOrders();
    }
}
