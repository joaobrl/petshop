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

    /**
     * Até quando o estoque fica reservado esperando pagamento; passou disso
     * sem pagar, o pedido é cancelado e a reserva liberada.
     */
    private LocalDateTime expiresAt;

    /**
     * Chave enviada pelo cliente no header Idempotency-Key do checkout:
     * repetir o checkout com a mesma chave devolve este pedido em vez de
     * criar outro. Null para pedidos sem chave.
     */
    private String idempotencyKey;

    /**
     * Pedido só com os itens selecionados no checkout, já com estoque
     * reservado; a reserva vale até {@code expiresAt} (ver
     * OrderExpirationScheduler).
     */
    public static Order fromCartItems(UUID customerId, List<CartItem> cartItems, int reservationTtlMinutes) {
        var order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.items = cartItems.stream().map(OrderItem::new).toList();
        order.totalAmount = cartItems.stream().mapToDouble(CartItem::subtotal).sum();
        order.status = OrderStatus.AWAITING_PAYMENT;
        order.createdAt = LocalDateTime.now();
        order.expiresAt = order.createdAt.plusMinutes(reservationTtlMinutes);
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
