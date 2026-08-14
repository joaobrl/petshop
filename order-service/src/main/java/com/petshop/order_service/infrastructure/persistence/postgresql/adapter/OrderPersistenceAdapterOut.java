package com.petshop.order_service.infrastructure.persistence.postgresql.adapter;

import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.domain.enums.OrderStatus;
import com.petshop.order_service.core.port.out.OrderPortOut;
import com.petshop.order_service.infrastructure.persistence.postgresql.mapper.OrderMapper;
import com.petshop.order_service.infrastructure.persistence.postgresql.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderPersistenceAdapterOut implements OrderPortOut {

    private final OrderRepository repository;
    private final OrderMapper mapper;

    @Override
    public Order save(Order order) {
        var entity = mapper.toEntity(order);
        // mapper.toEntity() sempre cria um OrderEntity novo, com o campo
        // transient "isNew" resetado pra true — mesmo quando o order já
        // existe no banco. Sem esse exists() extra, o Spring Data tentaria
        // persist() num id já existente e estouraria PK duplicada.
        if (repository.existsById(entity.getId())) {
            entity.setNew(false);
        }
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Order> findAwaitingPaymentCreatedBefore(LocalDateTime threshold) {
        return mapper.toDomainList(repository.findByStatusAndCreatedAtBefore(OrderStatus.AWAITING_PAYMENT, threshold));
    }

    @Override
    public List<Order> findPaidAndPickupDue(LocalDateTime now) {
        return mapper.toDomainList(repository.findByStatusAndPickupReadyAtLessThanEqual(OrderStatus.PAID, now));
    }
}
