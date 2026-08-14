package com.petshop.customermanagement.core.port.in.dto;

import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
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
    class CustomerRequestDtoValidation {

        private CustomerRequestDto validDto() {
            return new CustomerRequestDto("Maria", "12345678900", "maria@mail.com", "11999999999", "Senha123", "Senha123");
        }

        @Test
        void hasNoViolationsWhenAllFieldsAreValid() {
            Set<ConstraintViolation<CustomerRequestDto>> violations = validator.validate(validDto());

            assertThat(violations).isEmpty();
        }

        @Test
        void hasViolationWhenNameIsBlank() {
            var dto = validDto();
            dto.setName(" ");

            var violations = validator.validate(dto);

            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        void hasViolationWhenNameIsNull() {
            var dto = validDto();
            dto.setName(null);

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
        }

        @Test
        void hasViolationWhenCpfHasLettersOrWrongLength() {
            var dto = validDto();
            dto.setCpf("abc");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("cpf"));
        }

        @Test
        void hasViolationWhenCpfIsBlank() {
            var dto = validDto();
            dto.setCpf("");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("cpf"));
        }

        @Test
        void hasViolationWhenEmailIsInvalid() {
            var dto = validDto();
            dto.setEmail("not-an-email");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @Test
        void hasViolationWhenEmailIsBlank() {
            var dto = validDto();
            dto.setEmail("");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @Test
        void hasViolationWhenPhoneIsBlank() {
            var dto = validDto();
            dto.setPhone("");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
        }

        @Test
        void reportsAllViolationsWhenEveryFieldIsInvalid() {
            var dto = new CustomerRequestDto("", "abc", "invalid", "", "", "");

            var violations = validator.validate(dto);

            assertThat(violations).hasSizeGreaterThanOrEqualTo(4);
        }

        @Test
        void hasViolationWhenPasswordIsBlank() {
            var dto = validDto();
            dto.setPassword("");
            dto.setConfirmPassword("");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }

        @Test
        void hasViolationWhenPasswordIsTooShort() {
            var dto = validDto();
            dto.setPassword("123");
            dto.setConfirmPassword("123");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
        }

        @Test
        void hasViolationWhenConfirmPasswordIsBlank() {
            var dto = validDto();
            dto.setConfirmPassword("");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("confirmPassword"));
        }

        @Test
        void hasViolationWhenPasswordAndConfirmPasswordDoNotMatch() {
            var dto = validDto();
            dto.setConfirmPassword("SenhaDiferente123");

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("confirmPasswordMatching"));
        }

        @Test
        void hasNoViolationsWhenPasswordAndConfirmPasswordMatch() {
            var dto = validDto();

            assertThat(validator.validate(dto)).isEmpty();
        }
    }

    @Nested
    class CustomerUpdateDtoValidation {

        @Test
        void hasNoViolationsWhenAllFieldsAreNull() {
            var dto = new CustomerUpdateDto(null, null, null);

            assertThat(validator.validate(dto)).isEmpty();
        }

        @Test
        void hasViolationWhenEmailIsProvidedButInvalid() {
            var dto = new CustomerUpdateDto("Nome", "invalid-email", null);

            assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
        }

        @Test
        void hasNoViolationsWhenEmailIsValid() {
            var dto = new CustomerUpdateDto("Nome", "valid@mail.com", "11999999999");

            assertThat(validator.validate(dto)).isEmpty();
        }
    }

    @Nested
    class PetRequestDtoUsage {

        @Test
        void hasNoBeanValidationConstraintsAndAllFieldsAreSettable() {
            var dto = new PetRequestDto();
            dto.setPetName("Rex");
            dto.setPetType(PetType.DOG);
            dto.setPetBreed("Labrador");
            dto.setPetSize(SizeCategory.LARGE);
            dto.setWeightInKg(30.5);
            dto.setPetHealthIssues("Nenhum");

            assertThat(validator.validate(dto)).isEmpty();
            assertThat(dto.getPetName()).isEqualTo("Rex");
            assertThat(dto.getPetType()).isEqualTo(PetType.DOG);
            assertThat(dto.getPetBreed()).isEqualTo("Labrador");
            assertThat(dto.getPetSize()).isEqualTo(SizeCategory.LARGE);
            assertThat(dto.getWeightInKg()).isEqualTo(30.5);
            assertThat(dto.getPetHealthIssues()).isEqualTo("Nenhum");
        }

        @Test
        void emptyPetRequestDtoHasNoViolations() {
            assertThat(validator.validate(new PetRequestDto())).isEmpty();
        }
    }
}
