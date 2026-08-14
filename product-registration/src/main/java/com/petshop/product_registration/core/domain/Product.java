package com.petshop.product_registration.core.domain;

import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(of = "id")
public class Product {

    private Long id;
    private String name;
    private String description;
    private Double price;
    private Integer stock;
    /**
     * Quantidade reservada por carrinhos ainda não pagos (order-service).
     * Estoque "disponível pra venda" = stock - reservedStock.
     */
    private Integer reservedStock = 0;
    private Dimensions dimensions;
    private Boolean enabled;

    public Product(ProductRequestDto productRequest) {
        this.name = productRequest.getName();
        this.description = productRequest.getDescription();
        this.price = productRequest.getPrice();
        this.stock = productRequest.getStock();
        this.reservedStock = 0;
        this.enabled = true;
        if (productRequest.getDimensions() != null) {
            this.dimensions = new Dimensions();
            var d = productRequest.getDimensions();
            this.dimensions.setWeight(d.getWeight());
            this.dimensions.setHeight(d.getHeight());
            this.dimensions.setWidth(d.getWidth());
            this.dimensions.setLength(d.getLength());
        }
    }

    public int availableStock() {
        return this.stock - this.reservedStock;
    }

    public double reservedPercentage() {
        return this.stock == 0 ? 0.0 : (this.reservedStock * 100.0) / this.stock;
    }

    public void reserve(int quantity) {
        this.reservedStock += quantity;
    }

    public void release(int quantity) {
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
    }

    /**
     * Baixa definitiva: o pagamento foi confirmado, então a quantidade sai
     * tanto do estoque real quanto da reserva.
     */
    public void confirmSale(int quantity) {
        this.stock = Math.max(0, this.stock - quantity);
        this.reservedStock = Math.max(0, this.reservedStock - quantity);
    }

    public void update(ProductRequestDto productRequest) {
        if (productRequest.getName() != null) this.name = productRequest.getName();
        if (productRequest.getDescription() != null) this.description = productRequest.getDescription();
        if (productRequest.getPrice() != null) this.price = productRequest.getPrice();
        if (productRequest.getStock() != null) this.stock = productRequest.getStock();

        if (productRequest.getDimensions() != null) {
            if (this.dimensions == null) {
                this.dimensions = new Dimensions();
            }
            var d = productRequest.getDimensions();
            if (d.getWeight() != null) this.dimensions.setWeight(d.getWeight());
            if (d.getHeight() != null) this.dimensions.setHeight(d.getHeight());
            if (d.getWidth() != null) this.dimensions.setWidth(d.getWidth());
            if (d.getLength() != null) this.dimensions.setLength(d.getLength());
        }
    }
}
