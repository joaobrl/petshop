package com.petshop.customermanagement.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Usado tanto no domínio quanto no documento Mongo (não precisa de duas
 * classes separadas, já que é só um retrato do item comprado).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseHistoryItem {
    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
}
