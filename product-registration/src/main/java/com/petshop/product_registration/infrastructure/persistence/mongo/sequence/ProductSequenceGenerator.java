package com.petshop.product_registration.infrastructure.persistence.mongo.sequence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Gera ids sequenciais via {@code findAndModify} atômico sobre um contador
 * em {@link DatabaseSequence} — o Mongo não tem IDENTITY/SERIAL nativo.
 * O {@code $inc} + {@code findAndModify} roda como uma única operação no
 * servidor, então é seguro sob concorrência sem lock explícito.
 */
@Component
@RequiredArgsConstructor
public class ProductSequenceGenerator {

    public static final String PRODUCT_SEQUENCE = "products_sequence";

    private final MongoOperations mongoOperations;

    public long generateSequence(String sequenceName) {
        var counter = mongoOperations.findAndModify(
                Query.query(where("_id").is(sequenceName)),
                new Update().inc("seq", 1),
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                DatabaseSequence.class);
        return counter != null ? counter.getSeq() : 1L;
    }
}
