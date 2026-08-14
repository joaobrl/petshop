package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private UUID id;

    private UUID customerId;

    private List<OrderItem> items = new ArrayList<>();

    private Double totalAmount;

    private OrderStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    /**
     * Quando o prazo de preparo (sorteado no pagamento, ver OrderService)
     * vence; usado pelo PickupReadyScheduler pra disparar o e-mail de
     * "pronto pra retirada".
     */
    private LocalDateTime pickupReadyAt;

    public static Order fromCart(Cart cart) {
        var order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = cart.getCustomerId();
        order.items = cart.getItems().stream().map(OrderItem::new).toList();
        order.totalAmount = cart.total();
        order.status = OrderStatus.AWAITING_PAYMENT;
        order.createdAt = LocalDateTime.now();
        return order;
    }

    public void markAsPaid() {
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void schedulePickup(int delayMinutes) {
        this.pickupReadyAt = LocalDateTime.now().plusMinutes(delayMinutes);
    }

    public void markReadyForPickup() {
        this.status = OrderStatus.READY_FOR_PICKUP;
    }
}
