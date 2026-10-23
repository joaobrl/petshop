package com.petshop.booking_service.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * {@code Clock} como bean só pra permitir injetar um relógio fixo nos testes de {@code BookingService}
 * (regra de antecedência mínima depende de "agora") sem tocar no relógio do sistema.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
