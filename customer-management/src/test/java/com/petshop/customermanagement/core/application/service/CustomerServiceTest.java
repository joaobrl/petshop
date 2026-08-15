package com.petshop.customermanagement.core.application.service;

import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerUpdateDto;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerPortOut customerPortOut;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private NotificationPortOut notificationPortOut;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerPortOut, passwordEncoder, notificationPortOut);
    }

    private Customer sampleCustomer() {
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");
        customer.setPet(new ArrayList<>());
        return customer;
    }

    @Nested
    class RegisterCustomer {

        @Test
        void savesNewCustomerWhenCpfDoesNotExist() {
            var request = new CustomerRequestDto("Maria", "12345678900", "maria@mail.com", "11999999999", "Senha123", "Senha123");
            when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Senha123")).thenReturn("hashed-password");
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = customerService.registerCustomer(request);

            assertThat(result.getName()).isEqualTo("Maria");
            assertThat(result.getCpf()).isEqualTo("12345678900");
            assertThat(result.getEnabled()).isTrue();
            assertThat(result.getPasswordHash()).isEqualTo("hashed-password");
            assertThat(result.getMustChangePassword()).isFalse();
            verify(customerPortOut).save(any(Customer.class));
            verify(notificationPortOut).sendRegistrationConfirmation("maria@mail.com", "Maria");
        }

        @Test
        void throwsConflictWhenCpfAlreadyExists() {
            var request = new CustomerRequestDto("Maria", "12345678900", "maria@mail.com", "11999999999", "Senha123", "Senha123");
            when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.of(sampleCustomer()));

            assertThatThrownBy(() -> customerService.registerCustomer(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("12345678900");

            verify(customerPortOut, never()).save(any());
            verify(notificationPortOut, never()).sendRegistrationConfirmation(any(), any());
        }
    }

    @Nested
    class CustomerList {

        @Test
        void returnsAllCustomersFromPortOut() {
            var customers = List.of(sampleCustomer(), sampleCustomer());
            when(customerPortOut.findAll()).thenReturn(customers);

            var result = customerService.customerList();

            assertThat(result).hasSize(2);
        }

        @Test
        void returnsEmptyListWhenNoCustomers() {
            when(customerPortOut.findAll()).thenReturn(List.of());

            assertThat(customerService.customerList()).isEmpty();
        }
    }

    @Nested
    class CustomerListPage {

        private final PageRequest pageable = PageRequest.of(0, 20);

        @Test
        void returnsPagedCustomersFromPortOut() {
            var customers = List.of(sampleCustomer(), sampleCustomer());
            when(customerPortOut.findAllPage(pageable)).thenReturn(new PageImpl<>(customers));

            var result = customerService.customerListPage(pageable);

            assertThat(result.getContent()).hasSize(2);
        }

        @Test
        void returnsEmptyPageWhenNoCustomers() {
            when(customerPortOut.findAllPage(pageable)).thenReturn(new PageImpl<>(List.of()));

            assertThat(customerService.customerListPage(pageable).getContent()).isEmpty();
        }
    }

    @Nested
    class UpdateCustomer {

        @Test
        void updatesAndSavesWhenCustomerExists() {
            var customer = sampleCustomer();
            var id = customer.getId();
            var dto = new CustomerUpdateDto("Novo Nome", "novo@mail.com", "11000000000");
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = customerService.updateCustomer(id, dto);

            assertThat(result.getName()).isEqualTo("Novo Nome");
            assertThat(result.getEmail()).isEqualTo("novo@mail.com");
            assertThat(result.getPhone()).isEqualTo("11000000000");
        }

        @Test
        void throwsNotFoundWhenCustomerDoesNotExist() {
            var id = UUID.randomUUID();
            var dto = new CustomerUpdateDto("Novo Nome", null, null);
            when(customerPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.updateCustomer(id, dto))
                    .isInstanceOf(NotFoundException.class);

            verify(customerPortOut, never()).save(any());
        }
    }

    @Nested
    class FindCustomer {

        @Test
        void returnsCustomerWhenFound() {
            var customer = sampleCustomer();
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));

            assertThat(customerService.findCustomer(customer.getId())).isEqualTo(customer);
        }

        @Test
        void throwsNotFoundWhenMissing() {
            var id = UUID.randomUUID();
            when(customerPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.findCustomer(id))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    @Nested
    class FindCustomerByIdOrCpf {

        @Test
        void findsByIdWhenValueIsAValidUuid() {
            var customer = sampleCustomer();
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));

            assertThat(customerService.findCustomerByIdOrCpf(customer.getId().toString())).isEqualTo(customer);
        }

        @Test
        void fallsBackToCpfWhenValueIsNotAUuid() {
            var customer = sampleCustomer();
            when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.of(customer));

            assertThat(customerService.findCustomerByIdOrCpf("12345678900")).isEqualTo(customer);
        }

        @Test
        void fallsBackToCpfWhenValidUuidIsNotFound() {
            var id = UUID.randomUUID();
            var customer = sampleCustomer();
            when(customerPortOut.findById(id)).thenReturn(Optional.empty());
            when(customerPortOut.findByCpf(id.toString())).thenReturn(Optional.of(customer));

            assertThat(customerService.findCustomerByIdOrCpf(id.toString())).isEqualTo(customer);
        }

        @Test
        void throwsNotFoundWhenNeitherIdNorCpfMatch() {
            when(customerPortOut.findByCpf("00000000000")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.findCustomerByIdOrCpf("00000000000"))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class DeleteCustomer {

        @Test
        void disablesCustomerWhenFound() {
            var customer = sampleCustomer();
            var id = customer.getId();
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = customerService.deleteCustomer(id);

            assertThat(result.getEnabled()).isFalse();
            verify(customerPortOut).save(customer);
        }

        @Test
        void throwsNotFoundWhenMissing() {
            var id = UUID.randomUUID();
            when(customerPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.deleteCustomer(id))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class AddPetToCustomer {

        @Test
        void addsPetAndSavesWhenCustomerExists() {
            var customer = sampleCustomer();
            var id = customer.getId();
            var petRequest = new PetRequestDto();
            petRequest.setPetName("Rex");
            petRequest.setPetType(PetType.DOG);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = customerService.addPetToCustomer(id, List.of(petRequest));

            assertThat(result.getPet()).hasSize(1);
            assertThat(result.getPet().get(0).getPetName()).isEqualTo("Rex");
        }

        @Test
        void addsMultiplePetsInOneCallAndSaves() {
            var customer = sampleCustomer();
            var id = customer.getId();
            var rex = new PetRequestDto();
            rex.setPetName("Rex");
            rex.setPetType(PetType.DOG);
            var mimi = new PetRequestDto();
            mimi.setPetName("Mimi");
            mimi.setPetType(PetType.CAT);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = customerService.addPetToCustomer(id, List.of(rex, mimi));

            assertThat(result.getPet()).hasSize(2);
            assertThat(result.getPet()).extracting(Pet::getPetName).containsExactlyInAnyOrder("Rex", "Mimi");
        }

        @Test
        void throwsNotFoundWhenCustomerMissing() {
            var id = UUID.randomUUID();
            when(customerPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.addPetToCustomer(id, List.of(new PetRequestDto())))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class UpdatePet {

        @Test
        void updatesExistingPetAndSaves() {
            var customer = sampleCustomer();
            var pet = new Pet(new PetRequestDto());
            pet.setPetName("Rex");
            customer.getPet().add(pet);
            var petUpdate = new PetRequestDto();
            petUpdate.setPetName("Rex Atualizado");
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = customerService.updatePet(customer.getId(), pet.getId(), petUpdate);

            assertThat(result.getPet().get(0).getPetName()).isEqualTo("Rex Atualizado");
        }

        @Test
        void throwsNotFoundWhenCustomerMissing() {
            var id = UUID.randomUUID();
            when(customerPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.updatePet(id, UUID.randomUUID(), new PetRequestDto()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void throwsNotFoundWhenPetMissing() {
            var customer = sampleCustomer();
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> customerService.updatePet(customer.getId(), UUID.randomUUID(), new PetRequestDto()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pet");
        }
    }

    @Nested
    class RemovePetFromCustomer {

        @Test
        void removesExistingPetAndSaves() {
            var customer = sampleCustomer();
            var pet = new Pet(new PetRequestDto());
            customer.getPet().add(pet);
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = customerService.removePetFromCustomer(customer.getId(), pet.getId());

            assertThat(result.getPet()).isEmpty();
        }

        @Test
        void throwsNotFoundWhenCustomerMissing() {
            var id = UUID.randomUUID();
            when(customerPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.removePetFromCustomer(id, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void throwsNotFoundWhenPetMissing() {
            var customer = sampleCustomer();
            when(customerPortOut.findById(customer.getId())).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> customerService.removePetFromCustomer(customer.getId(), UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("Pet");
        }
    }

    @Nested
    class FindByCpf {

        @Test
        void returnsCustomerWhenPresent() {
            var customer = sampleCustomer();
            when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.of(customer));

            assertThat(customerService.findByCpf("12345678900")).contains(customer);
        }

        @Test
        void returnsEmptyWhenAbsent() {
            when(customerPortOut.findByCpf("00000000000")).thenReturn(Optional.empty());

            assertThat(customerService.findByCpf("00000000000")).isEmpty();
        }
    }

    @Test
    void savePassesTheExactCustomerReferenceBeingMutated() {
        var customer = sampleCustomer();
        var id = customer.getId();
        when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
        when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        customerService.updateCustomer(id, new CustomerUpdateDto("X", null, null));

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerPortOut).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
    }
}
