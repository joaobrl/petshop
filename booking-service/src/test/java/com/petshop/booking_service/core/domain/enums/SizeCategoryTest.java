package com.petshop.booking_service.core.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SizeCategoryTest {

    @Test
    void hasExactlyThreeValues() {
        assertThat(SizeCategory.values()).containsExactly(
                SizeCategory.SMALL, SizeCategory.MEDIUM, SizeCategory.LARGE);
    }

    @Test
    void valueOfResolvesByName() {
        assertThat(SizeCategory.valueOf("MEDIUM")).isEqualTo(SizeCategory.MEDIUM);
    }
}
