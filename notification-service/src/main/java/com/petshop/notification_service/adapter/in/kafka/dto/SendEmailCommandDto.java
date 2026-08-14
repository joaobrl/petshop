package com.petshop.notification_service.adapter.in.kafka.dto;

import com.petshop.notification_service.core.domain.TipoNotificacao;

import java.util.Map;

/**
 * Sem {@code subject}/{@code bodyHtml}: o conteúdo é montado inteiramente
 * pelo template do Brevo, identificado por {@code tipo}.
 */
public record SendEmailCommandDto(String to, TipoNotificacao tipo, Map<String, Object> params) {
}
