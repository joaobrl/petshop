package com.petshop.booking_service.core.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PetTypeTest {

    @Test
    void hasExactlyTwoValues() {
        assertThat(PetType.values()).containsExactly(PetType.DOG, PetType.CAT);
    }

    @Test
    void valueOfResolvesByName() {
        assertThat(PetType.valueOf("DOG")).isEqualTo(PetType.DOG);
    }
}
