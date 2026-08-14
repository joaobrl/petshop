package com.petshop.order_service.core.application.scheduler;

import com.petshop.order_service.core.port.in.CartPortIn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartExpirationSchedulerTest {

    @Mock
    private CartPortIn cartPortIn;

    @Test
    void delegatesToCartPortIn() {
        var scheduler = new CartExpirationScheduler(cartPortIn);

        scheduler.expireOldCarts();

        verify(cartPortIn).expireOldCarts();
    }
}
