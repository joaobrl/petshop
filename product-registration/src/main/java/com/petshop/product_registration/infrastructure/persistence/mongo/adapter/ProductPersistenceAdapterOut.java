package com.petshop.product_registration.infrastructure.persistence.mongo.adapter;

import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.core.port.out.ProductPortOut;
import com.petshop.product_registration.infrastructure.persistence.mongo.mapper.ProductMapper;
import com.petshop.product_registration.infrastructure.persistence.mongo.repository.ProductMongoRepository;
import com.petshop.product_registration.infrastructure.persistence.mongo.sequence.ProductSequenceGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapterOut implements ProductPortOut {

    private final ProductMongoRepository productRepository;
    private final ProductMapper mapper;
    private final ProductSequenceGenerator sequenceGenerator;

    @Override
    public Product save(Product product) {
        var entity = mapper.toEntity(product);
        // Mongo não tem IDENTITY/SERIAL: sem isso salvaria com id nulo. Só
        // gera id novo se ainda não tiver um — update reaproveita o id
        // existente ("id == null" distingue insert de update).
        if (entity.getId() == null) {
            entity.setId(sequenceGenerator.generateSequence(ProductSequenceGenerator.PRODUCT_SEQUENCE));
        }
        return mapper.toDomain(productRepository.save(entity));
    }

    @Override
    public Page<Product> findByEnabledTrue(Pageable pageable) {
        return productRepository.findByEnabledTrue(pageable).map(mapper::toDomain);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id)
                .map(mapper::toDomain);
    }
}
