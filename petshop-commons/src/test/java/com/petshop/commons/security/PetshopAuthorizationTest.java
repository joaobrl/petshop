package com.petshop.commons.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PetshopAuthorizationTest {

    private static Authentication authenticatedWith(String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated("subject", "credentials",
                AuthorityUtils.createAuthorityList(authorities));
    }

    private static boolean granted(AuthorizationManager<Object> manager, Authentication authentication) {
        var decision = manager.authorize(() -> authentication, new Object());
        return decision != null && decision.isGranted();
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Deve aceitar em anyUser cada papel de usuário do projeto")
    void shouldGrantAnyUserWhenAuthenticationHasProjectRole(Role role) {
        var manager = PetshopAuthorization.<Object>anyUser();

        assertThat(granted(manager, authenticatedWith("ROLE_" + role.name()))).isTrue();
    }

    @Test
    @DisplayName("Deve negar em anyUser quando só houver permissão svc")
    void shouldDenyAnyUserWhenOnlyServicePermission() {
        var manager = PetshopAuthorization.<Object>anyUser();

        assertThat(granted(manager, authenticatedWith("svc:customers:customer-read"))).isFalse();
    }

    @Test
    @DisplayName("Deve aceitar em anyUserOr o usuário ou a permissão informada")
    void shouldGrantAnyUserOrWhenUserOrGivenPermission() {
        var manager = PetshopAuthorization.<Object>anyUserOr("svc:customers:customer-read");

        assertThat(granted(manager, authenticatedWith("ROLE_CUSTOMER"))).isTrue();
        assertThat(granted(manager, authenticatedWith("svc:customers:customer-read"))).isTrue();
    }

    @Test
    @DisplayName("Deve negar em anyUserOr quando a permissão for de outra rota")
    void shouldDenyAnyUserOrWhenOtherServicePermission() {
        var manager = PetshopAuthorization.<Object>anyUserOr("svc:customers:customer-read");

        assertThat(granted(manager, authenticatedWith("svc:customers:staff-read"))).isFalse();
    }

    @Test
    @DisplayName("Deve aceitar em rolesOr o papel listado ou a permissão informada")
    void shouldGrantRolesOrWhenListedRoleOrGivenPermission() {
        var manager = PetshopAuthorization.<Object>rolesOr("svc:customers:staff-read", "ADMIN");

        assertThat(granted(manager, authenticatedWith("ROLE_ADMIN"))).isTrue();
        assertThat(granted(manager, authenticatedWith("svc:customers:staff-read"))).isTrue();
    }

    @Test
    @DisplayName("Deve negar em rolesOr quando o papel não estiver listado")
    void shouldDenyRolesOrWhenRoleIsNotListed() {
        var manager = PetshopAuthorization.<Object>rolesOr("svc:customers:staff-read", "ADMIN");

        assertThat(granted(manager, authenticatedWith("ROLE_CUSTOMER"))).isFalse();
        assertThat(granted(manager, authenticatedWith("svc:products:stock-write"))).isFalse();
    }
}
