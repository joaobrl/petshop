package com.petshop.notification_service.adapter.out.brevo;

import com.petshop.notification_service.adapter.out.brevo.dto.SendSmtpEmailRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Endpoint de e-mail transacional do Brevo, não o de campanhas em massa.
 * Autenticação via header passado explicitamente em cada chamada (sem
 * RequestInterceptor — decisão deliberada).
 */
@FeignClient(name = "brevoClient", url = "${brevo.base-url}")
public interface BrevoFeignClient {

    @PostMapping("/smtp/email")
    void enviarEmail(@RequestHeader("api-key") String apiKey, @RequestBody SendSmtpEmailRequest request);
}
