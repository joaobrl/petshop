package com.petshop.booking_service.core.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangeTypeTest {

    @Test
    void hasExactlyThreeValues() {
        assertThat(RangeType.values()).containsExactly(RangeType.DAY, RangeType.WEEK, RangeType.MONTH);
    }

    @Test
    void valueOfResolvesByName() {
        assertThat(RangeType.valueOf("WEEK")).isEqualTo(RangeType.WEEK);
    }
}
