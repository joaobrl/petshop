package com.petshop.product_registration.core.application.service;

import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.core.port.in.ProductPortIn;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import com.petshop.product_registration.core.port.out.ProductPortOut;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

// Sem @Transactional: MongoDB standalone (sem replica set, como sobe neste
// projeto) não suporta transação multi-documento, e cada método aqui faz
// só uma escrita atômica (save() de um único Product).
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements ProductPortIn {

    private final ProductPortOut productPortOut;

    @Override
    public Product registerProduct(ProductRequestDto productRequest) {
        log.info("Starting product registration process for: {}", productRequest.getName());

        if (productRequest.getStock() == null) {
            log.debug("Stock not provided for product {}, defaulting to 0", productRequest.getName());
            productRequest.setStock(0);
        }

        var product = new Product(productRequest);
        var savedProduct = productPortOut.save(product);

        log.info("Product successfully registered with ID: {}", savedProduct.getId());
        return savedProduct;
    }

    @Override
    public Page<Product> listProducts(Pageable pageable) {
        log.debug("Fetching enabled products page {} (size {})", pageable.getPageNumber(), pageable.getPageSize());
        return productPortOut.findByEnabledTrue(pageable);
    }

    @Override
    public Product getProductById(Long id) {
        log.debug("Searching for product with ID: {}", id);
        return productPortOut.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product search failed: ID {} not found", id);
                    return new NotFoundException("Produto", id);
                });
    }

    @Override
    public Product updateProduct(Long id, ProductRequestDto productRequest) {
        if (productRequest == null) {
            log.error("Update failed: Request body is null for product ID {}", id);
            throw new IllegalArgumentException("Os dados de atualização não podem ser nulos.");
        }

        log.info("Updating product ID: {}", id);
        var product = getProductById(id);

        product.update(productRequest);
        var updatedProduct = productPortOut.save(product);

        log.info("Product with ID: {} successfully updated", id);
        return updatedProduct;
    }

    @Override
    public Product deleteProduct(Long id) {
        log.info("Soft-deleting product with ID: {}", id);
        var product = getProductById(id);

        product.setEnabled(false);
        var deletedProduct = productPortOut.save(product);

        log.info("Product with ID: {} has been disabled", id);
        return deletedProduct;
    }

    @Override
    public Product reserveStock(Long id, int quantity) {
        var product = getProductById(id);

        if (product.availableStock() < quantity) {
            log.warn("Reserva recusada para produto {}: pedido {}, disponível {}", id, quantity, product.availableStock());
            throw new BusinessRuleException(
                    "Estoque insuficiente para o produto '%s': disponível %d, solicitado %d"
                            .formatted(product.getName(), product.availableStock(), quantity));
        }

        product.reserve(quantity);
        var saved = productPortOut.save(product);
        log.info("Reservados {} unidades do produto {}. Reservado total: {}", quantity, id, saved.getReservedStock());
        return saved;
    }

    @Override
    public Product releaseStock(Long id, int quantity) {
        var product = getProductById(id);
        product.release(quantity);
        var saved = productPortOut.save(product);
        log.info("Liberadas {} unidades reservadas do produto {}. Reservado total: {}", quantity, id, saved.getReservedStock());
        return saved;
    }

    @Override
    public Product confirmStock(Long id, int quantity) {
        var product = getProductById(id);
        product.confirmSale(quantity);
        var saved = productPortOut.save(product);
        log.info("Baixa definitiva de {} unidades do produto {}. Estoque restante: {}", quantity, id, saved.getStock());
        return saved;
    }
}
