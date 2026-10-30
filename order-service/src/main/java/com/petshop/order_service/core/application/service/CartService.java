package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.BusinessException;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.CartView;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.domain.enums.StockAvailability;
import com.petshop.order_service.core.port.in.CartPortIn;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService implements CartPortIn {

    private final CartPortOut cartPortOut;
    private final ProductStockPortOut productStockPortOut;
    private final CustomerPortOut customerPortOut;
    private final NotificationPortOut notificationPortOut;

    @Value("${order.cart.abandoned-after-hours:24}")
    private int abandonedAfterHours;

    @Override
    @Transactional
    public CartView addItem(UUID customerId, Long productId, int quantity, String token) {
        var productInfo = productStockPortOut.getInfo(productId, token);
        if (!productInfo.isEnabled()) {
            throw new NotFoundException("Produto", productId);
        }
        if (productInfo.availability() == StockAvailability.UNAVAILABLE) {
            throw new BusinessRuleException("Produto sem estoque");
        }

        var cart = cartPortOut.findOpenCartByCustomerId(customerId)
                .orElseGet(() -> Cart.openFor(customerId, abandonedAfterHours));
        cart.addOrIncrementItem(productId, productInfo.name(), productInfo.price(), quantity);
        cart.renewExpiration(abandonedAfterHours);

        var saved = cartPortOut.save(cart);
        log.info("Item {} (x{}) adicionado/atualizado no carrinho do cliente {}", productId, quantity, customerId);
        return toView(saved, token);
    }

    @Override
    @Transactional
    public CartView removeItem(UUID customerId, Long productId, String token) {
        var cart = cartPortOut.findOpenCartByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException("Carrinho do cliente", customerId));

        cart.findItem(productId)
                .orElseThrow(() -> new NotFoundException("Item do carrinho para o produto", productId));
        cart.removeItem(productId);

        var saved = cartPortOut.save(cart);
        log.info("Item {} removido do carrinho do cliente {}", productId, customerId);
        return toView(saved, token);
    }

    @Override
    @Transactional
    public CartView getCart(UUID customerId, String token) {
        var cart = cartPortOut.findOpenCartByCustomerId(customerId)
                .orElseGet(() -> Cart.openFor(customerId, abandonedAfterHours));
        return toView(cart, token);
    }

    // Disponibilidade não é persistida: é consultada a cada leitura, numa
    // chamada só ao product-registration. Produto desativado ou inexistente
    // sai do carrinho; sem estoque continua (pode voltar a ter). Se o
    // product-registration não responder, devolve o carrinho como está —
    // não remove nada por falha de rede.
    private CartView toView(Cart cart, String token) {
        if (cart.isEmpty()) {
            return CartView.withoutAvailability(cart);
        }

        Map<Long, ProductStockInfo> infos;
        try {
            infos = productStockPortOut.getInfos(cart.productIds(), token);
        } catch (BusinessException e) {
            log.warn("Não foi possível consultar a disponibilidade dos itens do carrinho {}: {}", cart.getId(), e.getMessage());
            return CartView.withoutAvailability(cart);
        }

        List<String> removedItems = new ArrayList<>();
        Map<Long, StockAvailability> availability = new HashMap<>();
        for (var item : List.copyOf(cart.getItems())) {
            var info = infos.get(item.getProductId());
            if (info == null || !info.isEnabled()) {
                removedItems.add(item.getProductName());
                cart.removeItem(item.getProductId());
            } else {
                availability.put(item.getProductId(), info.availability());
            }
        }

        if (!removedItems.isEmpty()) {
            cart = cartPortOut.save(cart);
            log.info("Produtos fora de venda removidos do carrinho {}: {}", cart.getId(), removedItems);
        }
        return new CartView(cart, availability, removedItems);
    }

    @Override
    @Transactional
    public void expireOldCarts() {
        var expiredCarts = cartPortOut.findExpiredOpenCarts();

        for (var cart : expiredCarts) {
            boolean hadItems = !cart.isEmpty();

            cart.setStatus(CartStatus.EXPIRED);
            cartPortOut.save(cart);
            log.info("Carrinho {} do cliente {} expirou (abandonado).", cart.getId(), cart.getCustomerId());

            if (hadItems) {
                sendAbandonedCartReminder(cart);
            }
        }
    }

    // Só dispara lembrete se o carrinho expirou com itens; carrinho
    // anônimo (sem cliente cadastrado) não tem e-mail pra mandar, só loga.
    private void sendAbandonedCartReminder(Cart cart) {
        customerPortOut.findCustomerById(cart.getCustomerId(), null).ifPresentOrElse(
                customer -> notificationPortOut.sendCartReminder(cart, customer.email(), customer.name()),
                () -> log.warn("Cliente {} não encontrado; lembrete de carrinho abandonado não enviado.", cart.getCustomerId())
        );
    }
}
