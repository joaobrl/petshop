package com.petshop.product_registration.infrastructure.persistence.mongo.repository;

import com.petshop.product_registration.infrastructure.persistence.mongo.entity.DimensionsDocument;
import com.petshop.product_registration.infrastructure.persistence.mongo.entity.ProductDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// auto-index-creation só é ligado aqui (não globalmente em
// src/test/resources/application.yml) porque é a única classe que
// realmente testa o índice único — ligá-lo globalmente forçava toda classe
// de teste com @SpringBootTest "puro" (sem Testcontainers) a tentar criar
// índice contra um Mongo real na subida do contexto, mesmo em testes que
// mockam o repositório e nunca tocam Mongo de verdade.
@DataMongoTest
@Testcontainers
@TestPropertySource(properties = "spring.data.mongodb.auto-index-creation=true")
class ProductRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @Autowired
    private ProductMongoRepository repository;

    private static long nextId = 1;

    private ProductDocument newProduct(String name, boolean enabled) {
        var product = new ProductDocument();
        product.setId(nextId++);
        product.setName(name);
        product.setDescription("Descricao");
        product.setPrice(10.0);
        product.setStock(5);
        product.setReservedStock(0);
        product.setEnabled(enabled);
        return product;
    }

    @Test
    void savesAndFindsProductById() {
        var saved = repository.save(newProduct("Racao " + nextId, true));

        var found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(saved.getName());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(999999L)).isEmpty();
    }

    @Test
    void findByEnabledTrueReturnsOnlyEnabledProducts() {
        repository.save(newProduct("Ativo " + nextId, true));
        repository.save(newProduct("Inativo " + nextId, false));

        var result = repository.findByEnabledTrue(PageRequest.of(0, 100)).getContent();

        assertThat(result).extracting(ProductDocument::getName)
                .anyMatch(name -> name.startsWith("Ativo"));
        assertThat(result).extracting(ProductDocument::getName)
                .noneMatch(name -> name.startsWith("Inativo"));
    }

    @Test
    void findByEnabledTrueReturnsEmptyWhenNoneEnabled() {
        var inativo1 = repository.save(newProduct("Inativo1 " + nextId, false));
        var inativo2 = repository.save(newProduct("Inativo2 " + nextId, false));

        assertThat(repository.findByEnabledTrue(PageRequest.of(0, 100)).getContent())
                .extracting(ProductDocument::getId)
                .doesNotContain(inativo1.getId(), inativo2.getId());
    }

    @Test
    void rejectsDuplicateName() {
        var name = "Racao Unica " + nextId;
        repository.save(newProduct(name, true));

        assertThatThrownBy(() -> repository.save(newProduct(name, true)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void persistsAndReloadsEmbeddedDimensions() {
        var product = newProduct("Com Dimensoes " + nextId, true);
        product.setDimensions(new DimensionsDocument(1.5, 20.0, 15.0, 30.0));

        var saved = repository.save(product);
        var reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getDimensions()).isNotNull();
        assertThat(reloaded.getDimensions().getWeight()).isEqualTo(1.5);
        assertThat(reloaded.getDimensions().getHeight()).isEqualTo(20.0);
        assertThat(reloaded.getDimensions().getWidth()).isEqualTo(15.0);
        assertThat(reloaded.getDimensions().getLength()).isEqualTo(30.0);
    }

    @Test
    void persistsNullDimensionsAsNull() {
        var saved = repository.save(newProduct("Sem Dimensoes " + nextId, true));

        var reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getDimensions()).isNull();
    }

    @Test
    void updatingReservedStockPersists() {
        var saved = repository.save(newProduct("Reservavel " + nextId, true));
        saved.setReservedStock(saved.getReservedStock() + 3);
        repository.save(saved);

        var reloaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getReservedStock()).isEqualTo(3);
    }

    @Test
    void findAllReturnsEveryPersistedProduct() {
        repository.save(newProduct("P1 " + nextId, true));
        repository.save(newProduct("P2 " + nextId, false));

        assertThat(repository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }
}