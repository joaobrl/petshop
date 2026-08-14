package com.petshop.commons.security.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTypeTest {

    @Test
    void hasExactlyThreeValuesInOrder() {
        assertThat(AccountType.values()).containsExactly(
                AccountType.CUSTOMER,
                AccountType.STAFF,
                AccountType.SERVICE
        );
    }

    @Test
    void valueOfResolvesByName() {
        assertThat(AccountType.valueOf("STAFF")).isEqualTo(AccountType.STAFF);
    }
}
