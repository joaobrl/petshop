package com.petshop.commons.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void hasExactlyFiveValuesInOrder() {
        assertThat(Role.values()).containsExactly(
                Role.ADMIN,
                Role.RECEPTIONIST,
                Role.GROOMER,
                Role.VETERINARIAN,
                Role.CUSTOMER
        );
    }

    @Test
    void valueOfResolvesByName() {
        assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
    }
}
