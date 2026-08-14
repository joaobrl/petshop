package com.petshop.booking_service.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheConfigTest {

    @Test
    void canBeInstantiated() {
        assertThat(new CacheConfig()).isNotNull();
    }
}
