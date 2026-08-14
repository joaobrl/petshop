package com.petshop.product_registration.core.port.out;

import com.petshop.product_registration.core.domain.Product;

import java.util.List;
import java.util.Optional;

public interface ProductPortOut {

    Product save(Product product);

    List<Product> findByEnabledTrue();

    Optional<Product> findById(Long id);
}
