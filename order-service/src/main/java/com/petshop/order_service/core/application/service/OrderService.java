package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.BusinessException;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.CartItem;
import com.petshop.order_service.core.domain.CheckoutResult;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.port.in.OrderPortIn;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.OrderPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import com.petshop.order_service.core.port.out.PurchaseHistoryPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService implements OrderPortIn {

    // Tamanho da coluna orders.idempotency_key (VARCHAR(255) padrão do JPA).
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final OrderPortOut orderPortOut;
    private final CartPortOut cartPortOut;
    private final CustomerPortOut customerPortOut;
    private final NotificationPortOut notificationPortOut;
    private final ProductStockPortOut productStockPortOut;
    private final PurchaseHistoryPortOut purchaseHistoryPortOut;

    @Value("${order.payment.mock-delay-seconds:30}")
    private int mockPaymentDelaySeconds;

    // Por quanto tempo o estoque dos itens fica reservado esperando pagamento.
    @Value("${order.payment.reservation-ttl-minutes:30}")
    private int reservationTtlMinutes = 30;

    // Prazo de preparo sorteado nessa faixa a cada pedido pago, só para
    // fins de teste (em produção seria fixo/real). O e-mail ao cliente
    // continua fixo ("até 1h").
    @Value("${order.pickup.min-delay-minutes:1}")
    private int pickupMinDelayMinutes;

    @Value("${order.pickup.max-delay-minutes:60}")
    private int pickupMaxDelayMinutes;

    @Override
    @Transactional
    public CheckoutResult checkout(UUID customerId, List<Long> productIds, String idempotencyKey, String token) {
        if (productIds == null || productIds.isEmpty()) {
            throw new BusinessRuleException("Selecione ao menos um item do carrinho para finalizar.");
        }
        var key = normalizeIdempotencyKey(idempotencyKey);

        // Lock do carrinho até o commit: um segundo checkout simultâneo (duplo
        // clique, retry de rede) espera aqui e, quando passa, já vê o carrinho
        // sem os itens finalizados — não reserva nem cria pedido de novo.
        var openCart = cartPortOut.findOpenCartByCustomerIdForUpdate(customerId);

        // Depois do lock: se a chamada que segurava o lock usou a mesma chave,
        // o pedido dela já está commitado e é devolvido como está.
        if (key != null) {
            var existing = orderPortOut.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                if (!existing.get().getCustomerId().equals(customerId)) {
                    throw new ConflictException("Idempotency-Key já usada em outro pedido.");
                }
                log.info("Checkout repetido com a mesma Idempotency-Key; devolvendo o pedido {}.", existing.get().getId());
                return new CheckoutResult(existing.get(), false);
            }
        }

        var cart = openCart
                .orElseThrow(() -> new BusinessRuleException("Não há carrinho aberto para o cliente " + customerId));

        var selectedIds = new LinkedHashSet<>(productIds);
        List<CartItem> selectedItems = new ArrayList<>();
        List<Long> notInCart = new ArrayList<>();
        for (var productId : selectedIds) {
            cart.findItem(productId).ifPresentOrElse(selectedItems::add, () -> notInCart.add(productId));
        }
        if (!notInCart.isEmpty()) {
            throw new BusinessRuleException("Itens selecionados não estão no carrinho: " + notInCart);
        }

        // Antes de reservar: cliente inexistente faria o pedido falhar depois
        // da reserva, deixando estoque preso sem pedido que o expire.
        var customer = requireCustomer(customerId, token);

        ensureAllProductsOnSale(selectedItems, token);
        reserveAllOrNothing(selectedItems, token);

        var order = Order.fromCartItems(customerId, selectedItems, reservationTtlMinutes);
        order.setIdempotencyKey(key);
        var savedOrder = orderPortOut.save(order);

        cart.removeItems(selectedIds);
        if (cart.isEmpty()) {
            cart.setStatus(CartStatus.CHECKED_OUT);
        }
        cartPortOut.save(cart);

        notificationPortOut.sendOrderAwaitingPayment(savedOrder, customer.email(), customer.name());

        log.info("Pedido {} criado para o cliente {} (total: {}), aguardando pagamento até {}.",
                savedOrder.getId(), customerId, savedOrder.getTotalAmount(), savedOrder.getExpiresAt());
        return new CheckoutResult(savedOrder, true);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        var key = idempotencyKey.strip();
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key deve ter no máximo %d caracteres.".formatted(MAX_IDEMPOTENCY_KEY_LENGTH));
        }
        return key;
    }

    // Produto desativado (ou que deixou de existir) entre o GET /cart e o
    // "Finalizar" é recusado aqui, sem gastar reserva.
    private void ensureAllProductsOnSale(List<CartItem> items, String token) {
        var infos = productStockPortOut.getInfos(items.stream().map(CartItem::getProductId).toList(), token);
        var unavailable = items.stream()
                .filter(item -> {
                    var info = infos.get(item.getProductId());
                    return info == null || !info.isEnabled();
                })
                .map(item -> "Produto indisponível: '%s'".formatted(item.getProductName()))
                .toList();
        if (!unavailable.isEmpty()) {
            throw new BusinessRuleException(String.join(" | ", unavailable));
        }
    }

    // Tenta reservar todos os itens antes de desistir, em vez de parar no
    // primeiro erro — assim o cliente fica sabendo de todos os itens com
    // problema de uma só vez, não um por tentativa. A reserva é atômica por
    // produto no product-registration; o tudo-ou-nada é feito aqui.
    private void reserveAllOrNothing(List<CartItem> items, String token) {
        List<String> failures = new ArrayList<>();
        List<CartItem> reservedInThisAttempt = new ArrayList<>();

        for (var item : items) {
            try {
                productStockPortOut.reserve(item.getProductId(), item.getQuantity(), token);
                reservedInThisAttempt.add(item);
            } catch (BusinessException e) {
                failures.add(e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            // Libera o que foi reservado nesta tentativa para não deixar
            // reserva "órfã" de um checkout que vai falhar como um todo.
            for (var item : reservedInThisAttempt) {
                productStockPortOut.release(item.getProductId(), item.getQuantity(), token);
            }
            throw new BusinessRuleException(String.join(" | ", failures));
        }
    }

    @Override
    public Order findById(UUID id) {
        return orderPortOut.findById(id)
                .orElseThrow(() -> new NotFoundException("Pedido", id));
    }

    // Chamado periodicamente pelo PaymentMockScheduler: aprova pedidos
    // aguardando pagamento há mais tempo que o atraso configurado,
    // simulando um webhook assíncrono de gateway real.
    @Override
    @Transactional
    public void processPendingPayments() {
        var threshold = LocalDateTime.now().minusSeconds(mockPaymentDelaySeconds);
        var pendingOrders = orderPortOut.findAwaitingPaymentCreatedBefore(threshold);

        for (var order : pendingOrders) {
            order.markAsPaid();

            int pickupDelayMinutes = ThreadLocalRandom.current()
                    .nextInt(pickupMinDelayMinutes, pickupMaxDelayMinutes + 1);
            order.schedulePickup(pickupDelayMinutes);

            // Transição condicional: se a expiração já cancelou o pedido (ou
            // a reserva venceu), o pagamento perde e não dá baixa em nada.
            if (!orderPortOut.markAsPaidIfAwaitingPayment(order)) {
                log.warn("Pedido {} não está mais aguardando pagamento (expirado/cancelado); pagamento ignorado.", order.getId());
                continue;
            }

            for (var item : order.getItems()) {
                // Scheduler, sem requisição HTTP em curso — TokenPropagation assina um token de serviço.
                productStockPortOut.confirm(item.getProductId(), item.getQuantity(), null);
            }

            customerPortOut.findCustomerById(order.getCustomerId(), null).ifPresentOrElse(
                    customer -> {
                        notificationPortOut.sendPaymentConfirmed(order, customer.email(), customer.name());
                        notificationPortOut.sendInvoiceIssued(order, customer.email(), customer.name());
                        notificationPortOut.sendPickupWindow(order, customer.email(), customer.name());
                    },
                    () -> log.warn("Cliente {} não encontrado; e-mails do pedido {} não enviados.",
                            order.getCustomerId(), order.getId())
            );

            purchaseHistoryPortOut.publishOrderCompleted(order);

            log.info("Pagamento simulado aprovado para o pedido {}. Retirada estimada em {} min (prazo de teste).",
                    order.getId(), pickupDelayMinutes);
        }
    }

    // Chamado periodicamente pelo OrderExpirationScheduler: pedido que não foi
    // pago dentro do prazo de reserva é cancelado e o estoque volta. Os itens
    // não voltam para o carrinho.
    @Override
    @Transactional
    public void expireUnpaidOrders() {
        var expiredOrders = orderPortOut.findAwaitingPaymentExpiredBefore(LocalDateTime.now());

        for (var order : expiredOrders) {
            // Mesma transição condicional do pagamento: só um dos dois vence.
            if (!orderPortOut.cancelIfAwaitingPayment(order.getId())) {
                log.info("Pedido {} já saiu de aguardando pagamento; expiração ignorada.", order.getId());
                continue;
            }

            for (var item : order.getItems()) {
                // Scheduler, sem requisição HTTP em curso — TokenPropagation assina um token de serviço.
                productStockPortOut.release(item.getProductId(), item.getQuantity(), null);
            }
            log.info("Pedido {} cancelado por falta de pagamento; reserva de estoque liberada.", order.getId());
        }
    }

    // Chamado periodicamente pelo PickupReadyScheduler: pedido pago cujo
    // prazo de preparo sorteado já passou vira "pronto pra retirada".
    @Override
    @Transactional
    public void processPickupReadyOrders() {
        var dueOrders = orderPortOut.findPaidAndPickupDue(LocalDateTime.now());

        for (var order : dueOrders) {
            order.markReadyForPickup();
            orderPortOut.save(order);

            customerPortOut.findCustomerById(order.getCustomerId(), null).ifPresentOrElse(
                    customer -> notificationPortOut.sendOrderReadyForPickup(order, customer.email(), customer.name()),
                    () -> log.warn("Cliente {} não encontrado; e-mail de retirada do pedido {} não enviado.",
                            order.getCustomerId(), order.getId())
            );

            log.info("Pedido {} pronto para retirada.", order.getId());
        }
    }

    private CustomerInfo requireCustomer(UUID customerId, String token) {
        return customerPortOut.findCustomerById(customerId, token)
                .orElseThrow(() -> new NotFoundException("Cliente", customerId));
    }
}
