package com.petshop.notification_service.adapter.out.brevo;

import com.petshop.notification_service.adapter.out.brevo.dto.SendSmtpEmailRequest;
import com.petshop.notification_service.core.application.port.out.NotificationSender;
import com.petshop.notification_service.core.domain.TipoNotificacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BrevoEmailAdapterOut implements NotificationSender {

    private final BrevoFeignClient brevoFeignClient;
    private final BrevoProperties brevoProperties;

    @Override
    public void enviar(TipoNotificacao tipo, String destinatarioEmail, Map<String, Object> variaveis) {
        var templateId = brevoProperties.templates().get(tipo);
        var request = new SendSmtpEmailRequest(
                List.of(new SendSmtpEmailRequest.Recipient(destinatarioEmail)),
                templateId,
                variaveis
        );

        try {
            brevoFeignClient.enviarEmail(brevoProperties.apiKey(), request);
            log.info("E-mail '{}' enviado via Brevo (template {}) para [{}]", tipo, templateId, destinatarioEmail);
        } catch (Exception ex) {
            // FeignException é unchecked e já cobre qualquer resposta não-2xx
            // (diferente do SDK do SendGrid, que exigia checagem manual);
            // só logamos com o contexto do destinatário e repropagamos.
            log.error("Falha ao enviar e-mail '{}' via Brevo (template {}) para [{}]", tipo, templateId, destinatarioEmail, ex);
            throw ex;
        }
    }
}
