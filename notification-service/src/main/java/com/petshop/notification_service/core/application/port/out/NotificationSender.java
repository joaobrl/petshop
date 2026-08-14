package com.petshop.notification_service.core.application.port.out;

import com.petshop.notification_service.core.domain.TipoNotificacao;

import java.util.Map;

public interface NotificationSender {
    void enviar(TipoNotificacao tipo, String destinatarioEmail, Map<String, Object> variaveis);
}
