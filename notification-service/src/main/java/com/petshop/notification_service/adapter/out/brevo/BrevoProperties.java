package com.petshop.notification_service.adapter.out.brevo;

import com.petshop.notification_service.core.domain.TipoNotificacao;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Binding relaxado do Spring casa as chaves do YAML direto com os nomes
 * das constantes de {@link TipoNotificacao}. IDs de template ficam em
 * config pra recriar um template no Brevo não exigir rebuild.
 */
@ConfigurationProperties(prefix = "brevo")
public record BrevoProperties(String apiKey, String baseUrl, Map<TipoNotificacao, Long> templates) {
}
