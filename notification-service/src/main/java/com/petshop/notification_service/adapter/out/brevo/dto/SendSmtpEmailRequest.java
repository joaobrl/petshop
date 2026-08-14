package com.petshop.notification_service.adapter.out.brevo.dto;

import java.util.List;
import java.util.Map;

/**
 * Sem {@code sender}/{@code subject}: ambos fixados dentro de cada
 * template no painel do Brevo (não fazem parte do payload da API).
 */
public record SendSmtpEmailRequest(List<Recipient> to, Long templateId, Map<String, Object> params) {

    public record Recipient(String email) {
    }
}
