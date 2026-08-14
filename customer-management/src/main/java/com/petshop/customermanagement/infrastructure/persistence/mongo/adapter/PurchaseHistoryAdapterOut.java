package com.petshop.customermanagement.infrastructure.persistence.mongo.adapter;

import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import com.petshop.customermanagement.infrastructure.persistence.mongo.mapper.PurchaseHistoryMapper;
import com.petshop.customermanagement.infrastructure.persistence.mongo.repository.CustomerPurchaseHistoryMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PurchaseHistoryAdapterOut implements PurchaseHistoryPortOut {

    private final CustomerPurchaseHistoryMongoRepository repository;
    private final PurchaseHistoryMapper mapper;

    @Override
    public Optional<PurchaseHistory> findByOrderId(UUID orderId) {
        return repository.findByOrderId(orderId)
                .map(mapper::toDomain);
    }

    @Override
    public List<PurchaseHistory> findAllByCustomerId(UUID customerId) {
        return repository.findAllByCustomerId(customerId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<PurchaseHistory> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public PurchaseHistory save(PurchaseHistory historyDomain) {
        var entity = mapper.toEntity(historyDomain);

        repository.findByOrderId(historyDomain.getOrderId())
                .ifPresent(existing -> entity.setId(existing.getId()));

        var savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }
}
