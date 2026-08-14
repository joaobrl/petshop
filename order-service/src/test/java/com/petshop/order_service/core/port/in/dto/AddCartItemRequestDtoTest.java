package com.petshop.order_service.core.port.in.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AddCartItemRequestDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private AddCartItemRequestDto validDto() {
        var dto = new AddCartItemRequestDto();
        dto.setCustomerId(UUID.randomUUID());
        dto.setProductId(1L);
        dto.setQuantity(2);
        return dto;
    }

    @Test
    void hasNoViolationsWhenValid() {
        assertThat(validator.validate(validDto())).isEmpty();
    }

    @Test
    void hasViolationWhenCustomerIdIsNull() {
        var dto = validDto();
        dto.setCustomerId(null);

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("customerId"));
    }

    @Test
    void hasViolationWhenProductIdIsNull() {
        var dto = validDto();
        dto.setProductId(null);

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("productId"));
    }

    @Test
    void hasViolationWhenQuantityIsNull() {
        var dto = validDto();
        dto.setQuantity(null);

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    void hasViolationWhenQuantityIsZeroOrNegative() {
        var dto = validDto();
        dto.setQuantity(0);
        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));

        dto.setQuantity(-1);
        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
    }

    @Test
    void settersAndGettersRoundTrip() {
        var dto = new AddCartItemRequestDto();
        var customerId = UUID.randomUUID();

        dto.setCustomerId(customerId);
        dto.setProductId(5L);
        dto.setQuantity(3);

        assertThat(dto.getCustomerId()).isEqualTo(customerId);
        assertThat(dto.getProductId()).isEqualTo(5L);
        assertThat(dto.getQuantity()).isEqualTo(3);
    }
}
