package com.petshop.product_registration.core.port.out;

import com.petshop.product_registration.core.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductPortOut {

    Product save(Product product);

    /**
     * Grava só os campos editáveis (nome, descrição, preço, estoque,
     * dimensões, enabled) — nunca {@code reservedStock}, que é mexido
     * só pelas operações atômicas abaixo. Um {@code save} do documento
     * inteiro sobrescreveria uma reserva feita entre a leitura e a escrita.
     */
    Optional<Product> updateDetails(Product product);

    Page<Product> findByEnabledTrue(Pageable pageable);

    Optional<Product> findById(Long id);

    /** Uma consulta só ({@code $in}); ids inexistentes simplesmente não vêm. */
    List<Product> findAllByIds(Collection<Long> ids);

    /**
     * Reserva atômica: só incrementa {@code reservedStock} se o produto
     * estiver ativo e com {@code stock - reservedStock >= quantity}.
     * Vazio quando o filtro não casou (inexistente, desativado ou sem estoque).
     */
    Optional<Product> tryReserve(Long id, int quantity);

    /** Decrementa {@code reservedStock} só se {@code reservedStock >= quantity}. */
    Optional<Product> tryRelease(Long id, int quantity);

    /**
     * Baixa definitiva: decrementa {@code stock} e {@code reservedStock}
     * juntos, só se {@code reservedStock >= quantity}.
     */
    Optional<Product> tryConfirm(Long id, int quantity);
}
