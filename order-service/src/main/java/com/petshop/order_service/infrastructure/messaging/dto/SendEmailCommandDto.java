package com.petshop.order_service.infrastructure.messaging.dto;

import java.util.Map;

/**
 * Contrato novo (pós-migração Brevo): sem HTML/assunto prontos — só um
 * identificador do tipo de notificação (tem que bater com o nome de uma
 * constante de {@code TipoNotificacao} no notification-service, que é
 * quem resolve tipo → templateId do Brevo) + os parâmetros dinâmicos do
 * template.
 */
public record SendEmailCommandDto(
        String to,
        String tipo,
        Map<String, Object> params
) {}
