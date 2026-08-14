package com.petshop.product_registration.infrastructure.persistence.mongo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sub-documento embutido em ProductDocument — Spring Data MongoDB
 * serializa objetos aninhados como sub-documento automaticamente, sem
 * anotação (diferente do @Embeddable/@Embedded do JPA).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DimensionsDocument {

    private Double weight;
    private Double height;
    private Double width;
    private Double length;
}
