package com.petshop.product_registration.infrastructure.persistence.mongo.sequence;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Coleção auxiliar de contadores (um documento por sequência) — padrão do
 * Spring Data MongoDB pra emular auto-increment, já que o Mongo não tem
 * isso nativamente. Ver {@link ProductSequenceGenerator}.
 */
@Data
@Document(collection = "database_sequences")
public class DatabaseSequence {

    @Id
    private String id;

    private long seq;
}
