package com.petshop.product_registration.core.port.out;

import com.petshop.product_registration.core.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProductPortOut {

    Product save(Product product);

    Page<Product> findByEnabledTrue(Pageable pageable);

    Optional<Product> findById(Long id);
}
