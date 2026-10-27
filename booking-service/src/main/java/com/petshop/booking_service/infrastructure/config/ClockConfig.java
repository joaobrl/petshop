package com.petshop.booking_service.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * {@code Clock} como bean pra fixar o fuso horário de Brasília (America/Sao_Paulo) em todo "agora"
 * usado nas regras de agendamento, independente do fuso configurado no sistema/container, e pra
 * permitir injetar um relógio fixo nos testes de {@code BookingService} sem tocar no relógio do sistema.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId BRASILIA_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock clock() {
        return Clock.system(BRASILIA_ZONE);
    }
}
