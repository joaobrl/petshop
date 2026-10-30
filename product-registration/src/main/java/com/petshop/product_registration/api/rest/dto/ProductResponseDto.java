package com.petshop.product_registration.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.petshop.product_registration.core.domain.Product;
import lombok.Data;

@Data
public class ProductResponseDto {
    private Long id;
    private String name;
    private String description;
    private Double price;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer stock;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer reservedStock;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer availableStock;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double reservedPercentage;
    /**
     * Disponibilidade simples (sem expor quantidade), visível a qualquer
     * chamador — inclusive sem autenticação — para o front poder marcar o
     * item como "sem estoque".
     */
    private Boolean inStock;
    /**
     * Faixa de estoque ("50+", "20+", "LOW" ou null quando sem estoque),
     * visível a qualquer chamador — ver Product#stockRange.
     */
    private String stockRange;
    /**
     * Produto desativado (soft delete) continua acessível por id; o
     * order-service usa isso pra tirar o produto do carrinho.
     */
    private Boolean enabled;
    private DimensionsResponseDto dimensions;

    public ProductResponseDto(Product product) {
        this(product, true);
    }

    /**
     * includeStock=false omite a quantidade exata do JSON (campos ficam
     * null; @JsonInclude NON_NULL tira do corpo) — usado quando o usuário
     * não é STAFF. {@code inStock}, {@code stockRange} e {@code enabled} não
     * dependem disso: são sempre informados.
     */
    public ProductResponseDto(Product product, boolean includeStock) {
        this.id = product.getId();
        this.name = product.getName();
        this.description = product.getDescription();
        this.price = product.getPrice();
        this.inStock = product.availableStock() > 0;
        this.stockRange = product.stockRange();
        this.enabled = product.getEnabled();
        if (includeStock) {
            this.stock = product.getStock();
            this.reservedStock = product.getReservedStock();
            this.availableStock = product.availableStock();
            this.reservedPercentage = product.reservedPercentage();
        }

        if (product.getDimensions() != null) {
            this.dimensions = new DimensionsResponseDto();
            this.dimensions.setWeight(product.getDimensions().getWeight());
            this.dimensions.setHeight(product.getDimensions().getHeight());
            this.dimensions.setWidth(product.getDimensions().getWidth());
            this.dimensions.setLength(product.getDimensions().getLength());
        }
    }
}
