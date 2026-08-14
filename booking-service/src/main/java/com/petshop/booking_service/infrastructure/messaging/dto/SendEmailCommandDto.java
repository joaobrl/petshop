package com.petshop.booking_service.infrastructure.messaging.dto;

import java.util.Map;

/**
 * Sem HTML/assunto prontos — só um identificador do tipo de notificação (deve bater com uma constante de
 * {@code TipoNotificacao} no notification-service, que resolve tipo → templateId do Brevo) + os parâmetros do template.
 */
public record SendEmailCommandDto(
        String to,
        String tipo,
        Map<String, Object> params
) {}
