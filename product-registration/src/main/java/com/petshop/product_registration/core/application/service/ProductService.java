package com.petshop.product_registration.core.application.service;

import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.core.port.in.ProductPortIn;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import com.petshop.product_registration.core.port.out.ProductPortOut;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

// Sem @Transactional: MongoDB standalone (sem replica set, como sobe neste
// projeto) não suporta transação multi-documento, e cada método aqui faz
// só uma escrita atômica (um único documento de Product).
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService implements ProductPortIn {

    private final ProductPortOut productPortOut;

    @Value("${product.batch.max-ids:100}")
    private int batchMaxIds = 100;

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
    public List<Product> getProductsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um id de produto.");
        }
        var distinctIds = new LinkedHashSet<>(ids);
        if (distinctIds.size() > batchMaxIds) {
            throw new IllegalArgumentException("Máximo de %d ids por consulta.".formatted(batchMaxIds));
        }
        log.debug("Searching for {} products by id", distinctIds.size());
        return productPortOut.findAllByIds(distinctIds);
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
        var updatedProduct = productPortOut.updateDetails(product)
                .orElseThrow(() -> new NotFoundException("Produto", id));

        log.info("Product with ID: {} successfully updated", id);
        return updatedProduct;
    }

    @Override
    public Product deleteProduct(Long id) {
        log.info("Soft-deleting product with ID: {}", id);
        var product = getProductById(id);

        product.setEnabled(false);
        var deletedProduct = productPortOut.updateDetails(product)
                .orElseThrow(() -> new NotFoundException("Produto", id));

        log.info("Product with ID: {} has been disabled", id);
        return deletedProduct;
    }

    @Override
    public Product reserveStock(Long id, int quantity) {
        var reserved = productPortOut.tryReserve(id, quantity).orElseThrow(() -> {
            // O filtro atômico não casou: descobre o motivo só pra devolver o erro certo.
            var product = getProductById(id);
            if (!Boolean.TRUE.equals(product.getEnabled())) {
                log.warn("Reserva recusada para produto {}: produto desativado", id);
                return new BusinessRuleException("Produto indisponível: '%s'".formatted(product.getName()));
            }
            log.warn("Reserva recusada para produto {}: pedido {}, disponível {}", id, quantity, product.availableStock());
            return new BusinessRuleException("Estoque insuficiente para o produto '%s'".formatted(product.getName()));
        });
        log.info("Reservados {} unidades do produto {}. Reservado total: {}", quantity, id, reserved.getReservedStock());
        return reserved;
    }

    @Override
    public Product releaseStock(Long id, int quantity) {
        var released = productPortOut.tryRelease(id, quantity).orElseThrow(() -> {
            var product = getProductById(id);
            log.warn("Liberação recusada para produto {}: pedido {}, reservado {}", id, quantity, product.getReservedStock());
            return new BusinessRuleException("Reserva insuficiente para liberar no produto '%s'".formatted(product.getName()));
        });
        log.info("Liberadas {} unidades reservadas do produto {}. Reservado total: {}", quantity, id, released.getReservedStock());
        return released;
    }

    @Override
    public Product confirmStock(Long id, int quantity) {
        var confirmed = productPortOut.tryConfirm(id, quantity).orElseThrow(() -> {
            var product = getProductById(id);
            log.warn("Baixa recusada para produto {}: pedido {}, reservado {}", id, quantity, product.getReservedStock());
            return new BusinessRuleException("Reserva insuficiente para dar baixa no produto '%s'".formatted(product.getName()));
        });
        log.info("Baixa definitiva de {} unidades do produto {}. Estoque restante: {}", quantity, id, confirmed.getStock());
        return confirmed;
    }
}
