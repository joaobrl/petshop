package com.petshop.notification_service.adapter.out.brevo;

import com.petshop.notification_service.adapter.out.brevo.dto.SendSmtpEmailRequest;
import com.petshop.notification_service.core.domain.TipoNotificacao;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BrevoEmailAdapterOutTest {

    @Mock
    private BrevoFeignClient brevoFeignClient;

    private BrevoEmailAdapterOut adapter;

    @BeforeEach
    void setUp() {
        var properties = new BrevoProperties(
                "test-api-key",
                "https://api.brevo.com/v3",
                Map.of(TipoNotificacao.AGENDAMENTO_CONFIRMADO, 1L)
        );
        adapter = new BrevoEmailAdapterOut(brevoFeignClient, properties);
    }

    @Test
    void sendsTheResolvedTemplateIdAndParamsToBrevo() {
        var params = Map.<String, Object>of("ownerName", "Ciclana");

        adapter.enviar(TipoNotificacao.AGENDAMENTO_CONFIRMADO, "cliente@petshop.com", params);

        ArgumentCaptor<SendSmtpEmailRequest> captor = ArgumentCaptor.forClass(SendSmtpEmailRequest.class);
        verify(brevoFeignClient).enviarEmail(anyString(), captor.capture());
        var request = captor.getValue();
        assertThat(request.templateId()).isEqualTo(1L);
        assertThat(request.params()).isEqualTo(params);
        assertThat(request.to()).containsExactly(new SendSmtpEmailRequest.Recipient("cliente@petshop.com"));
    }

    @Test
    void sendsTheApiKeyFromPropertiesAsHeader() {
        adapter.enviar(TipoNotificacao.AGENDAMENTO_CONFIRMADO, "cliente@petshop.com", Map.of());

        verify(brevoFeignClient).enviarEmail(org.mockito.ArgumentMatchers.eq("test-api-key"), any());
    }

    @Test
    void propagatesFeignExceptionOnNonSuccessResponse() {
        var request = Request.create(Request.HttpMethod.POST, "/smtp/email", Map.of(), null, new RequestTemplate());
        var feignException = new FeignException.BadRequest("template inativo", request, null, null);
        doThrow(feignException).when(brevoFeignClient).enviarEmail(anyString(), any());

        assertThatThrownBy(() -> adapter.enviar(TipoNotificacao.AGENDAMENTO_CONFIRMADO, "cliente@petshop.com", Map.of()))
                .isInstanceOf(FeignException.class);
    }

    @Test
    void propagatesConnectivityExceptions() {
        doThrow(new RuntimeException("connection reset")).when(brevoFeignClient).enviarEmail(anyString(), any());

        assertThatThrownBy(() -> adapter.enviar(TipoNotificacao.AGENDAMENTO_CONFIRMADO, "cliente@petshop.com", Map.of()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("connection reset");
    }

    private static SendSmtpEmailRequest any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
