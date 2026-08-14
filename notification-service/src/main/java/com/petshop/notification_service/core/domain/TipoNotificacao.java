package com.petshop.notification_service.core.domain;

/**
 * Cada constante corresponde a 1 template transacional no painel do Brevo;
 * o mapeamento {@code tipo → templateId} fica em {@code BrevoProperties}.
 */
public enum TipoNotificacao {
    CADASTRO_CONFIRMADO,
    AGENDAMENTO_CONFIRMADO,
    AGENDAMENTO_CONCLUIDO,
    AGENDAMENTO_CANCELADO,
    PEDIDO_AGUARDANDO_PAGAMENTO,
    PAGAMENTO_CONFIRMADO,
    NOTA_FISCAL_EMITIDA,
    PEDIDO_EM_PREPARACAO,
    PEDIDO_PRONTO_RETIRADA,
    CARRINHO_ABANDONADO,
    RESET_SENHA
}
