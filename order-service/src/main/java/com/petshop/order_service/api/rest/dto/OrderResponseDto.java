package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.domain.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class OrderResponseDto {
    private UUID id;
    private UUID customerId;
    private List<OrderItemResponseDto> items;
    private Double totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime pickupReadyAt;

    public OrderResponseDto(Order order) {
        this.id = order.getId();
        this.customerId = order.getCustomerId();
        this.items = order.getItems().stream().map(OrderItemResponseDto::new).toList();
        this.totalAmount = order.getTotalAmount();
        this.status = order.getStatus();
        this.createdAt = order.getCreatedAt();
        this.paidAt = order.getPaidAt();
        this.pickupReadyAt = order.getPickupReadyAt();
    }
}
