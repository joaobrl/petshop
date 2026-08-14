package com.petshop.product_registration.api.rest.dto;

import lombok.Data;

@Data
public class DimensionsResponseDto {
    private Double weight;
    private Double height;
    private Double width;
    private Double length;
}
