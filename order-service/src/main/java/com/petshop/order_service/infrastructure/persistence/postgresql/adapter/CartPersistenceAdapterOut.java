package com.petshop.order_service.infrastructure.persistence.postgresql.adapter;

import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.infrastructure.persistence.postgresql.mapper.CartMapper;
import com.petshop.order_service.infrastructure.persistence.postgresql.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CartPersistenceAdapterOut implements CartPortOut {

    private final CartRepository repository;
    private final CartMapper mapper;

    @Override
    public Cart save(Cart cart) {
        var entity = mapper.toEntity(cart);
        // mapper.toEntity() sempre cria um CartEntity novo, com o campo
        // transient "isNew" resetado pra true — mesmo quando o carrinho já
        // existe no banco. Sem esse exists() extra, o Spring Data tentaria
        // persist() num id já existente e estouraria PK duplicada.
        if (repository.existsById(entity.getId())) {
            entity.setNew(false);
        }
        return mapper.toDomain(repository.save(entity));
    }

    @Override
    public Optional<Cart> findOpenCartByCustomerId(UUID customerId) {
        return repository.findByCustomerIdAndStatus(customerId, CartStatus.OPEN)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Cart> findById(UUID id) {
        return repository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public List<Cart> findExpiredOpenCarts() {
        return mapper.toDomainList(repository.findByStatusAndExpiresAtBefore(CartStatus.OPEN, LocalDateTime.now()));
    }
}
