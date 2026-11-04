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
import com.petshop.commons.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
        @DisplayName("Deve checar o dono e devolver a página de pets do cliente")
        void shouldCheckOwnershipAndReturnPetPageWhenListingOwnPets() {
            var customerId = UUID.randomUUID();
            var pet = new Pet(new PetRequestDto());
            var user = user();
            var pageable = PageRequest.of(1, 20);
            when(portIn.listPetsOfCustomer(customerId, pageable))
                    .thenReturn(new PageImpl<>(List.of(pet), pageable, 21));

            var response = controller.listPetsOfCustomer(customerId, pageable, user);

            verify(customerIdentityResolver).requireOwnershipIfCustomer(customerId, user);
            var body = response.getBody();
            assertThat(body.content()).hasSize(1);
            assertThat(body.content().get(0).getId()).isEqualTo(pet.getId());
            assertThat(body.page()).isEqualTo(1);
            assertThat(body.size()).isEqualTo(20);
            assertThat(body.totalElements()).isEqualTo(21);
            assertThat(body.totalPages()).isEqualTo(2);
            assertThat(body.last()).isTrue();
        }

        @Test
        @DisplayName("Não deve consultar os pets quando o dono não for o cliente")
        void shouldNotListPetsWhenOwnershipIsDenied() {
            var customerId = UUID.randomUUID();
            var user = user();
            doThrow(new AccessDeniedException("negado"))
                    .when(customerIdentityResolver).requireOwnershipIfCustomer(customerId, user);

            assertThatThrownBy(() -> controller.listPetsOfCustomer(customerId, PageRequest.of(0, 20), user))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(portIn);
        }

        @Test
        @DisplayName("Deve limitar o size a 100 preservando página e ordenação nos pets do cliente")
        void shouldClampPageSizeTo100WhenListingOwnPets() {
            var customerId = UUID.randomUUID();
            var sort = Sort.by("name");
            when(portIn.listPetsOfCustomer(eq(customerId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            controller.listPetsOfCustomer(customerId, PageRequest.of(3, 500, sort), user());

            var captor = ArgumentCaptor.forClass(Pageable.class);
            verify(portIn).listPetsOfCustomer(eq(customerId), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(100);
            assertThat(captor.getValue().getPageNumber()).isEqualTo(3);
            assertThat(captor.getValue().getSort()).isEqualTo(sort);
        }

        @Test
        @DisplayName("Deve propagar NotFoundException quando o serviço não achar o cliente")
        void shouldPropagateNotFoundWhenCustomerDoesNotExist() {
            var customerId = UUID.randomUUID();
            var pageable = PageRequest.of(0, 20);
            var user = user();
            when(portIn.listPetsOfCustomer(customerId, pageable)).thenThrow(new NotFoundException("Cliente", customerId));

            assertThatThrownBy(() -> controller.listPetsOfCustomer(customerId, pageable, user))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class StoreWideEndpoints {

        @Test
        @DisplayName("Deve listar todos os pets sem filtro e sem checar dono quando o header não vier")
        void shouldListAllPetsWithoutOwnershipCheckWhenHeaderIsAbsent() {
            var pageable = PageRequest.of(0, 20);
            when(portIn.listPets(null, pageable))
                    .thenReturn(new PageImpl<>(List.of(new Pet(new PetRequestDto()), new Pet(new PetRequestDto())), pageable, 45));

            var response = controller.listPets(null, pageable);

            var body = response.getBody();
            assertThat(body.content()).hasSize(2);
            assertThat(body.totalElements()).isEqualTo(45);
            assertThat(body.totalPages()).isEqualTo(3);
            assertThat(body.last()).isFalse();
            verifyNoInteractions(customerIdentityResolver);
        }

        @ParameterizedTest
        @ValueSource(strings = {"dog", "DOG", "Dog"})
        @DisplayName("Deve converter o header typePets para o enum ignorando maiúsculas")
        void shouldConvertTypeHeaderToEnumIgnoringCase(String header) {
            var pageable = PageRequest.of(0, 20);
            when(portIn.listPets(PetType.DOG, pageable)).thenReturn(new PageImpl<>(List.of()));

            controller.listPets(header, pageable);

            verify(portIn).listPets(PetType.DOG, pageable);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        @DisplayName("Deve tratar header em branco como ausência de filtro")
        void shouldTreatBlankHeaderAsNoFilter(String header) {
            var pageable = PageRequest.of(0, 20);
            when(portIn.listPets(null, pageable)).thenReturn(new PageImpl<>(List.of()));

            controller.listPets(header, pageable);

            verify(portIn).listPets(null, pageable);
        }

        @Test
        @DisplayName("Deve lançar IllegalArgumentException quando o tipo do header for inválido")
        void shouldThrowIllegalArgumentWhenTypeHeaderIsInvalid() {
            var pageable = PageRequest.of(0, 20);

            assertThatThrownBy(() -> controller.listPets("bird", pageable))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(portIn);
        }

        @Test
        @DisplayName("Deve devolver página vazia quando não houver pets do tipo")
        void shouldReturnEmptyPageWhenNoPetsMatch() {
            var pageable = PageRequest.of(0, 20);
            when(portIn.listPets(PetType.CAT, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

            var response = controller.listPets("cat", pageable);

            assertThat(response.getBody().content()).isEmpty();
            assertThat(response.getBody().totalElements()).isZero();
        }

        @Test
        @DisplayName("Deve limitar o size a 100 preservando página e ordenação em listPets")
        void shouldClampPageSizeTo100WhenListingAllPets() {
            var sort = Sort.by(Sort.Order.desc("name"));
            when(portIn.listPets(isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            controller.listPets(null, PageRequest.of(2, 101, sort));

            var captor = ArgumentCaptor.forClass(Pageable.class);
            verify(portIn).listPets(isNull(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(100);
            assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(captor.getValue().getSort()).isEqualTo(sort);
        }

        @Test
        @DisplayName("Deve manter o mesmo Pageable quando o size for exatamente 100")
        void shouldKeepPageableWhenSizeIsExactlyTheLimit() {
            var pageable = PageRequest.of(0, 100);
            when(portIn.listPets(null, pageable)).thenReturn(new PageImpl<>(List.of()));

            controller.listPets(null, pageable);

            var captor = ArgumentCaptor.forClass(Pageable.class);
            verify(portIn).listPets(isNull(), captor.capture());
            assertThat(captor.getValue()).isSameAs(pageable);
        }
    }
}
