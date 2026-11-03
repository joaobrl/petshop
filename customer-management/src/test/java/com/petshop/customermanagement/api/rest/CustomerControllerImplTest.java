package com.petshop.customermanagement.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.port.in.CustomerPortIn;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerUpdateDto;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import com.petshop.customermanagement.infrastructure.security.CustomerIdentityResolver;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerControllerImplTest {

    @Mock
    private CustomerPortIn portIn;

    @Mock
    private CustomerIdentityResolver customerIdentityResolver;

    private CustomerControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new CustomerControllerImpl(portIn, customerIdentityResolver);
    }

    private Customer sampleCustomer() {
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");
        customer.setPet(new ArrayList<>());
        return customer;
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(UUID.randomUUID(), "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    @Test
    void registerCustomerReturnsCreatedWithLocationAndBody() {
        var customer = sampleCustomer();
        var request = new CustomerRequestDto("Maria", "12345678900", "maria@mail.com", "11999999999", "Senha123", "Senha123");
        when(portIn.registerCustomer(request)).thenReturn(customer);

        var response = controller.registerCustomer(request, UriComponentsBuilder.fromUriString("http://localhost:8080/api/v1/customers/register/customer"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).contains(customer.getId().toString());
        assertThat(response.getBody().id()).isEqualTo(customer.getId());
    }

    @Test
    void listCustomersMapsAllCustomersToDto() {
        var pageable = PageRequest.of(0, 20);
        when(portIn.customerListPage(pageable)).thenReturn(new PageImpl<>(List.of(sampleCustomer(), sampleCustomer())));

        var response = controller.listCustomers(pageable);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(2);
    }

    @Test
    void listCustomersReturnsEmptyListWhenNoneExist() {
        var pageable = PageRequest.of(0, 20);
        when(portIn.customerListPage(pageable)).thenReturn(new PageImpl<>(List.of()));

        var response = controller.listCustomers(pageable);

        assertThat(response.getBody().content()).isEmpty();
    }

    @Nested
    class OwnershipEnforcedEndpoints {

        @Test
        void getCustomerByIdResolvesThenChecksOwnership() {
            var customer = sampleCustomer();
            var user = user();
            when(portIn.findCustomerByIdOrCpf(customer.getId().toString())).thenReturn(customer);

            var response = controller.getCustomerById(customer.getId().toString(), user);

            var inOrder = inOrder(portIn, customerIdentityResolver);
            inOrder.verify(portIn).findCustomerByIdOrCpf(customer.getId().toString());
            inOrder.verify(customerIdentityResolver).requireOwnershipIfCustomer(customer.getId(), user);
            assertThat(response.getBody().id()).isEqualTo(customer.getId());
        }

        @Test
        void getCustomerByIdPropagatesAccessDeniedWhenNotOwner() {
            var customer = sampleCustomer();
            var user = user();
            when(portIn.findCustomerByIdOrCpf(customer.getId().toString())).thenReturn(customer);
            doThrow(new AccessDeniedException("nope")).when(customerIdentityResolver).requireOwnershipIfCustomer(customer.getId(), user);

            assertThatThrownBy(() -> controller.getCustomerById(customer.getId().toString(), user)).isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void updateCustomerChecksOwnershipAndDelegates() {
            var customer = sampleCustomer();
            var dto = new CustomerUpdateDto("Novo Nome", null, null);
            var user = user();
            when(portIn.updateCustomer(customer.getId(), dto)).thenReturn(customer);

            var response = controller.updateCustomer(customer.getId(), dto, user);

            verify(customerIdentityResolver).requireOwnershipIfCustomer(customer.getId(), user);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void deleteCustomerChecksOwnershipAndReturnsNoContent() {
            var id = UUID.randomUUID();
            var user = user();

            var response = controller.deleteCustomer(id, user);

            verify(customerIdentityResolver).requireOwnershipIfCustomer(id, user);
            verify(portIn).deleteCustomer(id);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        void addPetToCustomerChecksOwnershipAndDelegates() {
            var customer = sampleCustomer();
            var petRequests = List.of(new PetRequestDto());
            var user = user();
            when(portIn.addPetToCustomer(customer.getId(), petRequests)).thenReturn(customer);

            var response = controller.addPetToCustomer(customer.getId(), petRequests, user);

            verify(customerIdentityResolver).requireOwnershipIfCustomer(customer.getId(), user);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void addPetToCustomerThrowsForEmptyList() {
            var customer = sampleCustomer();
            var user = user();

            assertThatThrownBy(() -> controller.addPetToCustomer(customer.getId(), List.of(), user))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(portIn, never()).addPetToCustomer(any(), any());
        }

        @Test
        void addPetToCustomerAcceptsMultiplePetsInOneCall() {
            var customer = sampleCustomer();
            var rex = new PetRequestDto();
            rex.setPetName("Rex");
            var mimi = new PetRequestDto();
            mimi.setPetName("Mimi");
            var petRequests = List.of(rex, mimi);
            var user = user();
            when(portIn.addPetToCustomer(customer.getId(), petRequests)).thenReturn(customer);

            var response = controller.addPetToCustomer(customer.getId(), petRequests, user);

            verify(portIn).addPetToCustomer(customer.getId(), petRequests);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void updatePetChecksOwnershipAndDelegates() {
            var customer = sampleCustomer();
            var petId = UUID.randomUUID();
            var petRequest = new PetRequestDto();
            var user = user();
            when(portIn.updatePet(customer.getId(), petId, petRequest)).thenReturn(customer);

            var response = controller.updatePet(customer.getId(), petId, petRequest, user);

            verify(customerIdentityResolver).requireOwnershipIfCustomer(customer.getId(), user);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void removePetFromCustomerChecksOwnershipAndDelegates() {
            var customer = sampleCustomer();
            var petId = UUID.randomUUID();
            var user = user();
            when(portIn.removePetFromCustomer(customer.getId(), petId)).thenReturn(customer);

            var response = controller.removePetFromCustomer(customer.getId(), petId, user);

            verify(customerIdentityResolver).requireOwnershipIfCustomer(customer.getId(), user);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void listPetsOfCustomerChecksOwnershipAndReturnsOnlyThatCustomersPets() {
            var customer = sampleCustomer();
            var pet = new Pet(new PetRequestDto());
            customer.getPet().add(pet);
            var other = sampleCustomer();
            var user = user();
            when(portIn.customerList()).thenReturn(List.of(customer, other));

            var response = controller.listPetsOfCustomer(customer.getId(), user);

            verify(customerIdentityResolver).requireOwnershipIfCustomer(customer.getId(), user);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void listPetsOfCustomerThrowsWhenCustomerNotFoundAmongList() {
            var user = user();
            var id = UUID.randomUUID();
            when(portIn.customerList()).thenReturn(List.of(sampleCustomer()));

            assertThatThrownBy(() -> controller.listPetsOfCustomer(id, user))
                    .isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    class StoreWideEndpoints {

        @Test
        void listPetsAggregatesPetsFromAllCustomersWithoutOwnershipCheckWhenHeaderAbsent() {
            var customer1 = sampleCustomer();
            customer1.getPet().add(new Pet(new PetRequestDto()));
            var customer2 = sampleCustomer();
            customer2.getPet().add(new Pet(new PetRequestDto()));
            when(portIn.customerList()).thenReturn(List.of(customer1, customer2));

            var response = controller.listPets(null);

            assertThat(response.getBody()).hasSize(2);
            verifyNoInteractions(customerIdentityResolver);
        }

        @Test
        void listPetsFiltersByTypeWhenHeaderPresent() {
            var dogRequest = new PetRequestDto();
            dogRequest.setPetType(PetType.DOG);
            var catRequest = new PetRequestDto();
            catRequest.setPetType(PetType.CAT);

            var customer = sampleCustomer();
            customer.getPet().add(new Pet(dogRequest));
            customer.getPet().add(new Pet(catRequest));
            when(portIn.customerList()).thenReturn(List.of(customer));

            var response = controller.listPets("dog");

            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getPetType()).isEqualTo(PetType.DOG);
        }

        @Test
        void listPetsHeaderFilterIsCaseInsensitive() {
            var dogRequest = new PetRequestDto();
            dogRequest.setPetType(PetType.DOG);
            var customer = sampleCustomer();
            customer.getPet().add(new Pet(dogRequest));
            when(portIn.customerList()).thenReturn(List.of(customer));

            var response = controller.listPets("DOG");

            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void listPetsThrowsForInvalidTypeHeader() {
            when(portIn.customerList()).thenReturn(List.of(sampleCustomer()));

            assertThatThrownBy(() -> controller.listPets("bird"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void listPetsReturnsEmptyWhenNoPetsOfThatType() {
            var customer = sampleCustomer();
            when(portIn.customerList()).thenReturn(List.of(customer));

            var response = controller.listPets("cat");

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        void listPetsIgnoresBlankHeaderAndListsAll() {
            var customer = sampleCustomer();
            customer.getPet().add(new Pet(new PetRequestDto()));
            when(portIn.customerList()).thenReturn(List.of(customer));

            var response = controller.listPets("  ");

            assertThat(response.getBody()).hasSize(1);
        }
    }
}
