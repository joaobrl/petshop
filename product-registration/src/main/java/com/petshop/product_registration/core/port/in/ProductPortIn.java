package com.petshop.product_registration.core.port.in;

import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;

import java.util.List;

public interface ProductPortIn {

    Product registerProduct(ProductRequestDto productRequest);

    List<Product> listProducts();

    Product getProductById(Long id);

    Product updateProduct(Long id, ProductRequestDto productRequest);

    Product deleteProduct(Long id);

    Product reserveStock(Long id, int quantity);

    Product releaseStock(Long id, int quantity);

    Product confirmStock(Long id, int quantity);
}
