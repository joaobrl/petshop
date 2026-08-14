package com.petshop.customermanagement.api.rest.dto;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistoryItem;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseDtoTest {

    @Test
    void petResponseDtoCopiesEveryFieldFromTheGivenPet() {
        var petRequest = new PetRequestDto();
        petRequest.setPetName("Rex");
        petRequest.setPetType(PetType.DOG);
        petRequest.setPetBreed("Labrador");
        petRequest.setPetSize(SizeCategory.LARGE);
        petRequest.setWeightInKg(30.5);
        petRequest.setPetHealthIssues("Nenhum");
        var pet = new Pet(petRequest);

        var dto = new PetResponseDto(pet);

        // Regressão: o construtor já fez self-assignment (this.id = id) em vez
        // de copiar do Pet recebido, e todo pet devolvido pela API vinha nulo.
        assertThat(dto.getId()).isEqualTo(pet.getId());
        assertThat(dto.getPetName()).isEqualTo("Rex");
        assertThat(dto.getPetType()).isEqualTo(PetType.DOG);
        assertThat(dto.getPetBreed()).isEqualTo("Labrador");
        assertThat(dto.getPetSize()).isEqualTo(SizeCategory.LARGE);
        assertThat(dto.getWeightInKg()).isEqualTo(30.5);
        assertThat(dto.getPetHealthIssues()).isEqualTo("Nenhum");
    }

    @Test
    void petResponseDtoHandlesNullOptionalFields() {
        var pet = new Pet(new PetRequestDto());

        var dto = new PetResponseDto(pet);

        assertThat(dto.getId()).isEqualTo(pet.getId());
        assertThat(dto.getPetName()).isNull();
        assertThat(dto.getPetType()).isNull();
    }

    @Test
    void customerResponseDtoCopiesFieldsAndMapsPetsList() {
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");
        customer.setPet(new ArrayList<>(List.of(new Pet(new PetRequestDto()))));

        var dto = new CustomerResponseDto(customer);

        assertThat(dto.id()).isEqualTo(customer.getId());
        assertThat(dto.name()).isEqualTo("Maria");
        assertThat(dto.cpf()).isEqualTo("12345678900");
        assertThat(dto.email()).isEqualTo("maria@mail.com");
        assertThat(dto.phone()).isEqualTo("11999999999");
        assertThat(dto.enabled()).isTrue();
        assertThat(dto.pets()).hasSize(1);
        assertThat(dto.pets().get(0).getId()).isEqualTo(customer.getPet().get(0).getId());
    }

    @Test
    void customerResponseDtoHandlesEmptyPetList() {
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");

        var dto = new CustomerResponseDto(customer);

        assertThat(dto.pets()).isEmpty();
    }

    @Test
    void customerResponseDtoRecordEqualityIsFieldBased() {
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");
        var dto1 = new CustomerResponseDto(customer);
        var dto2 = new CustomerResponseDto(customer);

        assertThat(dto1).isEqualTo(dto2);
        assertThat(dto1.hashCode()).isEqualTo(dto2.hashCode());
        assertThat(dto1.toString()).contains("Maria");
    }

    @Test
    void bookingHistoryResponseDtoCopiesEveryField() {
        var history = new BookingHistory();
        history.setBookingId(UUID.randomUUID());
        history.setCustomerId(UUID.randomUUID());
        history.setServiceType("Banho");
        history.setBookingDate("10/08/2026");
        history.setBookingTime("14:00");
        history.setStatus("CONFIRMED");

        var dto = new BookingHistoryResponseDto(history);

        assertThat(dto.bookingId()).isEqualTo(history.getBookingId());
        assertThat(dto.customerId()).isEqualTo(history.getCustomerId());
        assertThat(dto.serviceType()).isEqualTo("Banho");
        assertThat(dto.bookingDate()).isEqualTo("10/08/2026");
        assertThat(dto.bookingTime()).isEqualTo("14:00");
        assertThat(dto.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void purchaseHistoryResponseDtoCopiesEveryFieldIncludingItems() {
        var history = new PurchaseHistory();
        history.setOrderId(UUID.randomUUID());
        history.setCustomerId(UUID.randomUUID());
        var items = List.of(new PurchaseHistoryItem(1L, "Racao", 2, 50.0));
        history.setItems(items);
        history.setTotalAmount(100.0);
        var paidAt = LocalDateTime.now();
        history.setPaidAt(paidAt);

        var dto = new PurchaseHistoryResponseDto(history);

        assertThat(dto.orderId()).isEqualTo(history.getOrderId());
        assertThat(dto.customerId()).isEqualTo(history.getCustomerId());
        assertThat(dto.items()).isEqualTo(items);
        assertThat(dto.totalAmount()).isEqualTo(100.0);
        assertThat(dto.paidAt()).isEqualTo(paidAt);
    }

    @Test
    void staffResponseDtoCopiesEveryFieldFromTheGivenStaff() {
        var staff = new Staff(UUID.randomUUID(), "João", "12345678900", "joao@petshop.com",
                "11999999999", true, Role.RECEPTIONIST);

        var dto = new StaffResponseDto(staff);

        assertThat(dto.getId()).isEqualTo(staff.getId());
        assertThat(dto.getName()).isEqualTo("João");
        assertThat(dto.getCpf()).isEqualTo("12345678900");
        assertThat(dto.getEmail()).isEqualTo("joao@petshop.com");
        assertThat(dto.getPhone()).isEqualTo("11999999999");
        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getRole()).isEqualTo(Role.RECEPTIONIST);
    }

    @Test
    void loginResponseDtoSecondaryConstructorDefaultsTokenTypeToBearer() {
        var dto = new LoginResponseDto("token-value", 3600L, Role.CUSTOMER, AccountType.CUSTOMER, true);

        assertThat(dto.accessToken()).isEqualTo("token-value");
        assertThat(dto.tokenType()).isEqualTo("Bearer");
        assertThat(dto.expiresIn()).isEqualTo(3600L);
        assertThat(dto.role()).isEqualTo(Role.CUSTOMER);
        assertThat(dto.type()).isEqualTo(AccountType.CUSTOMER);
        assertThat(dto.mustChangePassword()).isTrue();
    }

    @Test
    void loginResponseDtoCanonicalConstructorAllowsCustomTokenType() {
        var dto = new LoginResponseDto("token-value", "Custom", 60L, Role.ADMIN, AccountType.STAFF, false);

        assertThat(dto.tokenType()).isEqualTo("Custom");
        assertThat(dto.mustChangePassword()).isFalse();
    }
}
