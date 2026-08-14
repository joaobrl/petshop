package com.petshop.product_registration.infrastructure.persistence.mongo.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument {

    // Long (não ObjectId/String) de propósito: order-service e outros
    // consumidores já referenciam produto por Long id (Feign de
    // reserve/release/confirm). Atribuído em ProductPersistenceAdapterOut
    // via ProductSequenceGenerator antes do save, não pelo Mongo.
    @Id
    private Long id;

    @Indexed(unique = true)
    private String name;

    private String description;
    private Double price;
    private Integer stock;
    private Integer reservedStock = 0;
    private DimensionsDocument dimensions;
    private Boolean enabled;
}
