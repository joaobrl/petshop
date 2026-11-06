package com.petshop.customermanagement.support;

import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Admin API do Keycloak nos testes (SDD-024 F3): mock do {@link IdentityProviderPortOut} no
 * lugar do adapter REST, para nenhum {@code @SpringBootTest} (nem o BootstrapAdminRunner)
 * chamar um Keycloak real. Mesmo esquema do {@link TestJwtDecoderConfig}: achada pelo
 * component scan e {@code @Primary}. Testes que verificam chamadas declaram o próprio
 * {@code @MockitoBean IdentityProviderPortOut}.
 */
@Configuration
public class TestIdentityProviderConfig {

    @Bean
    @Primary
    public IdentityProviderPortOut testIdentityProvider() {
        return Mockito.mock(IdentityProviderPortOut.class);
    }
}
