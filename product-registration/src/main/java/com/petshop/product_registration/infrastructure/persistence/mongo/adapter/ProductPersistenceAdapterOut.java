package com.petshop.product_registration.infrastructure.persistence.mongo.adapter;

import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.core.port.out.ProductPortOut;
import com.petshop.product_registration.infrastructure.persistence.mongo.entity.ProductDocument;
import com.petshop.product_registration.infrastructure.persistence.mongo.mapper.ProductMapper;
import com.petshop.product_registration.infrastructure.persistence.mongo.repository.ProductMongoRepository;
import com.petshop.product_registration.infrastructure.persistence.mongo.sequence.ProductSequenceGenerator;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Reserva/liberação/baixa usam {@code findAndModify} com filtro condicional
 * + {@code $inc} — o Mongo aplica filtro e update num único documento de
 * forma atômica, então duas reservas simultâneas do último item não passam
 * as duas (a segunda já não casa o filtro). Mesmo padrão do
 * {@link ProductSequenceGenerator}.
 */
@Component
@RequiredArgsConstructor
public class ProductPersistenceAdapterOut implements ProductPortOut {

    private final ProductMongoRepository productRepository;
    private final ProductMapper mapper;
    private final ProductSequenceGenerator sequenceGenerator;
    private final MongoOperations mongoOperations;

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
    public Optional<Product> updateDetails(Product product) {
        var entity = mapper.toEntity(product);
        var update = new Update()
                .set("name", entity.getName())
                .set("description", entity.getDescription())
                .set("price", entity.getPrice())
                .set("stock", entity.getStock())
                .set("dimensions", entity.getDimensions())
                .set("enabled", entity.getEnabled());
        return findAndModify(where("_id").is(product.getId()), update);
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

    @Override
    public List<Product> findAllByIds(Collection<Long> ids) {
        return mapper.toDomainList(productRepository.findAllById(ids));
    }

    @Override
    public Optional<Product> tryReserve(Long id, int quantity) {
        var criteria = where("_id").is(id)
                .and("enabled").is(true)
                .andOperator(Criteria.expr(() -> new Document("$gte", List.of(
                        new Document("$subtract", List.of("$stock", "$reservedStock")), quantity))));
        return findAndModify(criteria, new Update().inc("reservedStock", quantity));
    }

    @Override
    public Optional<Product> tryRelease(Long id, int quantity) {
        var criteria = where("_id").is(id).and("reservedStock").gte(quantity);
        return findAndModify(criteria, new Update().inc("reservedStock", -quantity));
    }

    @Override
    public Optional<Product> tryConfirm(Long id, int quantity) {
        var criteria = where("_id").is(id).and("reservedStock").gte(quantity);
        var update = new Update().inc("stock", -quantity).inc("reservedStock", -quantity);
        return findAndModify(criteria, update);
    }

    private Optional<Product> findAndModify(Criteria criteria, Update update) {
        var updated = mongoOperations.findAndModify(
                Query.query(criteria), update,
                FindAndModifyOptions.options().returnNew(true),
                ProductDocument.class);
        return Optional.ofNullable(updated).map(mapper::toDomain);
    }
}
