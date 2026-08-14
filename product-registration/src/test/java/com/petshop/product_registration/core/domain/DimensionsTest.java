package com.petshop.product_registration.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DimensionsTest {

    @Test
    void gettersAndSettersRoundTrip() {
        var dimensions = new Dimensions();
        dimensions.setWeight(1.5);
        dimensions.setHeight(20.0);
        dimensions.setWidth(15.0);
        dimensions.setLength(30.0);

        assertThat(dimensions.getWeight()).isEqualTo(1.5);
        assertThat(dimensions.getHeight()).isEqualTo(20.0);
        assertThat(dimensions.getWidth()).isEqualTo(15.0);
        assertThat(dimensions.getLength()).isEqualTo(30.0);
    }

    @Test
    void allArgsConstructorSetsEveryField() {
        var dimensions = new Dimensions(1.5, 20.0, 15.0, 30.0);

        assertThat(dimensions.getWeight()).isEqualTo(1.5);
        assertThat(dimensions.getHeight()).isEqualTo(20.0);
        assertThat(dimensions.getWidth()).isEqualTo(15.0);
        assertThat(dimensions.getLength()).isEqualTo(30.0);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var dimensions1 = new Dimensions(1.5, 20.0, 15.0, 30.0);
        var dimensions2 = new Dimensions(1.5, 20.0, 15.0, 30.0);
        var dimensions3 = new Dimensions(9.9, 20.0, 15.0, 30.0);

        assertThat(dimensions1).isEqualTo(dimensions2);
        assertThat(dimensions1.hashCode()).isEqualTo(dimensions2.hashCode());
        assertThat(dimensions1).isNotEqualTo(dimensions3);
        assertThat(dimensions1).isNotEqualTo(null);
        assertThat(dimensions1).isNotEqualTo("not dimensions");
        assertThat(dimensions1.toString()).contains("1.5");
    }
}
