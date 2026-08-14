package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.CartStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    private UUID id;

    private UUID customerId;

    private List<CartItem> items = new ArrayList<>();

    private CartStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    public static Cart openFor(UUID customerId, int reservationTtlHours) {
        var cart = new Cart();
        cart.id = UUID.randomUUID();
        cart.customerId = customerId;
        cart.items = new ArrayList<>();
        cart.status = CartStatus.OPEN;
        cart.createdAt = LocalDateTime.now();
        cart.renewExpiration(reservationTtlHours);
        return cart;
    }

    public void renewExpiration(int reservationTtlHours) {
        this.expiresAt = LocalDateTime.now().plusHours(reservationTtlHours);
    }

    public Optional<CartItem> findItem(Long productId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    public void addOrIncrementItem(Long productId, String productName, Double unitPrice, int quantity, boolean reserved) {
        findItem(productId).ifPresentOrElse(
                item -> {
                    item.setQuantity(item.getQuantity() + quantity);
                    if (reserved) {
                        item.setReserved(true);
                    }
                },
                () -> items.add(new CartItem(productId, productName, quantity, unitPrice, reserved))
        );
    }

    public void removeItem(Long productId) {
        items.removeIf(item -> item.getProductId().equals(productId));
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public double total() {
        return items.stream().mapToDouble(CartItem::subtotal).sum();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
