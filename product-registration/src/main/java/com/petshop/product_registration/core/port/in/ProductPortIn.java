package com.petshop.product_registration.core.port.in;

import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface ProductPortIn {

    Product registerProduct(ProductRequestDto productRequest);

    Page<Product> listProducts(Pageable pageable);

    Product getProductById(Long id);

    /**
     * Busca em lote numa consulta só. Devolve também produtos desativados;
     * id inexistente simplesmente não vem. Vazio ou acima do limite
     * ({@code product.batch.max-ids}) → IllegalArgumentException (400).
     */
    List<Product> getProductsByIds(Collection<Long> ids);

    Product updateProduct(Long id, ProductRequestDto productRequest);

    Product deleteProduct(Long id);

    Product reserveStock(Long id, int quantity);

    Product releaseStock(Long id, int quantity);

    Product confirmStock(Long id, int quantity);
}
