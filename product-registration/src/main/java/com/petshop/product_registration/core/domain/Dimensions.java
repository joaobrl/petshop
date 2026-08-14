package com.petshop.product_registration.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Value object de dimensões — sem anotações de persistência (isso fica em
 * DimensionsDocument) nem de validação (isso fica em DimensionsDto).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Dimensions {

    private Double weight;      // in kg (e.g., 1.5)
    private Double height;      // in cm
    private Double width;       // in cm
    private Double length;      // in cm
}
