package com.petshop.gateway_service.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Liga {@code gateway.rate-limit.*} (SDD-022) ao {@link RateLimitProperties}. */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
}
