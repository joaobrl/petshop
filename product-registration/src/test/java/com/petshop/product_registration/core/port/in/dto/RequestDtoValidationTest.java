package com.petshop.product_registration.core.port.in.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoValidationTest {

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

    @Nested
    class ProductRequestDtoValidation {

        private ProductRequestDto validDto() {
            var dto = new ProductRequestDto();
            dto.setName("Racao");
            dto.setPrice(10.0);
            dto.setStock(5);
            return dto;
        }

        @Test
        void hasNoViolationsWhenValid() {
            assertThat(validator.validate(validDto())).isEmpty();
        }

        @Test
        void hasNoViolationsWhenStockAndDescriptionAreNull() {
            var dto = validDto();
            dto.setStock(null);
            dto.setDescription(null);

            assertThat(validator.validate(dto)).isEmpty();
        }

        @Test
        void hasViolationWhenNameIsBlank() {
            var dto = validDto();
            dto.setName(" ");

            Set<ConstraintViolation<ProductRequestDto>> violations = validator.validate(dto);
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        void hasViolationWhenPriceIsNull() {
            var dto = validDto();
            dto.setPrice(null);

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
        }

        @Test
        void hasViolationWhenPriceIsZeroOrNegative() {
            var dto = validDto();
            dto.setPrice(0.0);

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("price"));

            dto.setPrice(-5.0);
            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
        }

        @Test
        void cascadesValidationIntoNestedDimensions() {
            var dto = validDto();
            var dimensions = new DimensionsDto();
            dimensions.setWeight(null);
            dto.setDimensions(dimensions);

            var violations = validator.validate(dto);

            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("dimensions.weight"));
        }

        @Test
        void hasNoViolationsWhenDimensionsAreCompletelyFilled() {
            var dto = validDto();
            var dimensions = new DimensionsDto();
            dimensions.setWeight(1.0);
            dimensions.setHeight(2.0);
            dimensions.setWidth(3.0);
            dimensions.setLength(4.0);
            dto.setDimensions(dimensions);

            assertThat(validator.validate(dto)).isEmpty();
        }
    }

    @Nested
    class DimensionsDtoValidation {

        @Test
        void hasViolationForEachMissingField() {
            var dto = new DimensionsDto();

            var violations = validator.validate(dto);

            assertThat(violations).hasSize(4);
        }

        @Test
        void hasNoViolationsWhenAllFieldsPresent() {
            var dto = new DimensionsDto();
            dto.setWeight(1.0);
            dto.setHeight(2.0);
            dto.setWidth(3.0);
            dto.setLength(4.0);

            assertThat(validator.validate(dto)).isEmpty();
        }
    }

    @Nested
    class StockAdjustmentRequestDtoValidation {

        @Test
        void hasViolationWhenQuantityIsNull() {
            var dto = new StockAdjustmentRequestDto();

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
        }

        @Test
        void hasViolationWhenQuantityIsZeroOrNegative() {
            var dto = new StockAdjustmentRequestDto();
            dto.setQuantity(0);

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));

            dto.setQuantity(-1);
            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));
        }

        @Test
        void hasNoViolationsWhenQuantityIsPositive() {
            var dto = new StockAdjustmentRequestDto();
            dto.setQuantity(1);

            assertThat(validator.validate(dto)).isEmpty();
        }
    }
}
