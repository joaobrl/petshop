package com.petshop.customermanagement.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerUpdateDto;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integração ponta a ponta: MockMvc real (com SecurityFilterChain/JwtAuthenticationFilter
 * de verdade) + CustomerService/CustomerIdentityResolver reais; só a persistência
 * (CustomerPortOut/BookingHistoryPortOut/PurchaseHistoryPortOut) é mockada — não
 * depende de Postgres, Mongo ou Kafka. Tokens são gerados de verdade (mesmo segredo
 * do application.yaml de teste) e enviados via header Authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CustomerControllerIntegrationTest {

    // Mesmo segredo do src/test/resources/application.yaml.
    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Autowired
    private MockMvc mockMvc;

    // Boot 4 autoconfigura um JsonMapper (Jackson 3), não um ObjectMapper
    // (Jackson 2) — @Autowired não acha bean candidato, então instanciamos
    // direto (só serve pra montar o JSON das requisições de teste).
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CustomerPortOut customerPortOut;

    @MockitoBean
    private BookingHistoryPortOut bookingHistoryPortOut;

    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    @MockitoBean
    private NotificationPortOut notificationPortOut;

    private Customer customerWithId(UUID id) {
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");
        customer.setId(id);
        customer.setPet(new ArrayList<>());
        return customer;
    }

    private static RequestPostProcessor customerAuth(UUID customerId) {
        return bearer(TEST_JWT_SERVICE.generateToken(customerId, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER));
    }

    private static RequestPostProcessor adminAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "admin@petshop.local", "Admin", "00000000000", "11000000000", Role.ADMIN, AccountType.STAFF));
    }

    private static RequestPostProcessor receptionistAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "recepcao@petshop.local", "Recepcao", "00000000001", "11000000001", Role.RECEPTIONIST, AccountType.STAFF));
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    @Nested
    class PublicRegistration {

        @Test
        void registerCustomerIsPubliclyAccessibleAndReturnsCreated() throws Exception {
            var request = new CustomerRequestDto("Maria", "12345678900", "maria@mail.com", "11999999999", "Senha123", "Senha123");
            when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.empty());
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(post("/api/v1/customers/register/customer")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.cpf", is("12345678900")));
        }

        @Test
        void registerCustomerReturnsBadRequestForInvalidBody() throws Exception {
            var invalid = new CustomerRequestDto("", "abc", "not-an-email", "", "", "");

            mockMvc.perform(post("/api/v1/customers/register/customer")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray());
        }

        @Test
        void registerCustomerReturnsConflictForDuplicateCpf() throws Exception {
            var request = new CustomerRequestDto("Maria", "12345678900", "maria@mail.com", "11999999999", "Senha123", "Senha123");
            when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.of(customerWithId(UUID.randomUUID())));

            mockMvc.perform(post("/api/v1/customers/register/customer")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title", is("Resource conflict")));
        }
    }

    @Nested
    class StoreWideListings {

        @Test
        void listCustomersRequiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/v1/customers/list/customers"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void listCustomersForbiddenForCustomerRole() throws Exception {
            mockMvc.perform(get("/api/v1/customers/list/customers")
                            .with(customerAuth(UUID.randomUUID())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void listCustomersAllowedForAdminRole() throws Exception {
            when(customerPortOut.findAllPage(any())).thenReturn(new PageImpl<>(java.util.List.of(customerWithId(UUID.randomUUID()))));

            mockMvc.perform(get("/api/v1/customers/list/customers")
                            .with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()", is(1)));
        }

        @Test
        void listCustomersAllowedForReceptionistRole() throws Exception {
            when(customerPortOut.findAllPage(any())).thenReturn(new PageImpl<>(java.util.List.of()));

            mockMvc.perform(get("/api/v1/customers/list/customers")
                            .with(receptionistAuth()))
                    .andExpect(status().isOk());
        }

        @Test
        void listPetsIsRestrictedToStoreRolesEvenForOwnPets() throws Exception {
            mockMvc.perform(get("/api/v1/customers/list/pets")
                            .with(customerAuth(UUID.randomUUID())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void listPetsWithTypeHeaderIsAlsoRestrictedToStoreRoles() throws Exception {
            mockMvc.perform(get("/api/v1/customers/list/pets")
                            .header("typePets", "dog")
                            .with(customerAuth(UUID.randomUUID())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void listPetsAllowedForAdminWithoutHeaderListsEverything() throws Exception {
            var customer = customerWithId(UUID.randomUUID());
            customer.getPet().add(new Pet(new PetRequestDto()));
            when(customerPortOut.findAll()).thenReturn(java.util.List.of(customer));

            mockMvc.perform(get("/api/v1/customers/list/pets")
                            .with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)));
        }

        @Test
        void listPetsAllowedForAdminFiltersByTypeHeader() throws Exception {
            var dogRequest = new PetRequestDto();
            dogRequest.setPetType(PetType.DOG);
            var catRequest = new PetRequestDto();
            catRequest.setPetType(PetType.CAT);
            var customer = customerWithId(UUID.randomUUID());
            customer.getPet().add(new Pet(dogRequest));
            customer.getPet().add(new Pet(catRequest));
            when(customerPortOut.findAll()).thenReturn(java.util.List.of(customer));

            mockMvc.perform(get("/api/v1/customers/list/pets")
                            .header("typePets", "dog")
                            .with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)))
                    .andExpect(jsonPath("$[0].petType", is("DOG")));
        }

        @Test
        void listPetsReturnsBadRequestWhenTypeHeaderIsInvalid() throws Exception {
            mockMvc.perform(get("/api/v1/customers/list/pets")
                            .header("typePets", "bird")
                            .with(adminAuth()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class OwnCustomerAccess {

        @Test
        void findCustomerByIdRequiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/v1/customers/find/customer/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void customerCanFindOwnRecord() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));

            mockMvc.perform(get("/api/v1/customers/find/customer/" + id)
                            .with(customerAuth(id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())));
        }

        @Test
        void customerCanFindOwnRecordByCpf() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.of(customer));

            mockMvc.perform(get("/api/v1/customers/find/customer/12345678900")
                            .with(customerAuth(id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())));
        }

        @Test
        void customerCannotFindSomeoneElsesRecord() throws Exception {
            var ownId = UUID.randomUUID();
            var otherId = UUID.randomUUID();
            // Precisa resolver o registro (id ou cpf) antes de checar ownership.
            when(customerPortOut.findById(otherId)).thenReturn(Optional.of(customerWithId(otherId)));

            mockMvc.perform(get("/api/v1/customers/find/customer/" + otherId)
                            .with(customerAuth(ownId)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void adminCanFindAnyCustomersRecordWithoutOwnershipCheck() throws Exception {
            var id = UUID.randomUUID();
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customerWithId(id)));

            mockMvc.perform(get("/api/v1/customers/find/customer/" + id)
                            .with(adminAuth()))
                    .andExpect(status().isOk());
        }

        @Test
        void adminCanFindCustomerByCpf() throws Exception {
            var id = UUID.randomUUID();
            when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.of(customerWithId(id)));

            mockMvc.perform(get("/api/v1/customers/find/customer/12345678900")
                            .with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())));
        }

        @Test
        void findCustomerByIdReturnsNotFoundProblemDetailWhenMissing() throws Exception {
            var id = UUID.randomUUID();
            when(customerPortOut.findById(id)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/customers/find/customer/" + id)
                            .with(adminAuth()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource not found")));
        }

        @Test
        void customerCanUpdateOwnRecord() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var update = new CustomerUpdateDto("Novo Nome", null, null);

            mockMvc.perform(patch("/api/v1/customers/update/customer/" + id)
                            .with(customerAuth(id))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Novo Nome")));
        }

        @Test
        void customerCanDeactivateOwnRegistration() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(delete("/api/v1/customers/delete/customer/" + id)
                            .with(customerAuth(id)))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    class OwnPetsAccess {

        @Test
        void customerCanAddPetToOwnRecord() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var petRequest = new PetRequestDto();
            petRequest.setPetName("Rex");
            petRequest.setPetType(PetType.DOG);

            mockMvc.perform(patch("/api/v1/customers/register/" + id + "/pet")
                            .with(customerAuth(id))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(java.util.List.of(petRequest))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pets.length()", is(1)));
        }

        @Test
        void customerCanAddMultiplePetsToOwnRecordInOneCall() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var rex = new PetRequestDto();
            rex.setPetName("Rex");
            rex.setPetType(PetType.DOG);
            var mimi = new PetRequestDto();
            mimi.setPetName("Mimi");
            mimi.setPetType(PetType.CAT);

            mockMvc.perform(patch("/api/v1/customers/register/" + id + "/pet")
                            .with(customerAuth(id))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(java.util.List.of(rex, mimi))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pets.length()", is(2)));
        }

        @Test
        void addPetToCustomerReturnsBadRequestForEmptyList() throws Exception {
            var id = UUID.randomUUID();

            mockMvc.perform(patch("/api/v1/customers/register/" + id + "/pet")
                            .with(customerAuth(id))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(java.util.List.of())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void customerCannotAddPetToSomeoneElsesRecord() throws Exception {
            var ownId = UUID.randomUUID();
            var otherId = UUID.randomUUID();

            mockMvc.perform(patch("/api/v1/customers/register/" + otherId + "/pet")
                            .with(customerAuth(ownId))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(java.util.List.of(new PetRequestDto()))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void customerCanListOwnPets() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            customer.getPet().add(new Pet(new PetRequestDto()));
            when(customerPortOut.findAll()).thenReturn(java.util.List.of(customer));

            mockMvc.perform(get("/api/v1/customers/" + id + "/pets")
                            .with(customerAuth(id)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)));
        }

        @Test
        void customerCanUpdateOwnPet() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            var pet = new Pet(new PetRequestDto());
            customer.getPet().add(pet);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            var petUpdate = new PetRequestDto();
            petUpdate.setPetName("Novo Nome");

            mockMvc.perform(patch("/api/v1/customers/" + id + "/pets/" + pet.getId())
                            .with(customerAuth(id))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(petUpdate)))
                    .andExpect(status().isOk());
        }

        @Test
        void customerCanRemoveOwnPet() throws Exception {
            var id = UUID.randomUUID();
            var customer = customerWithId(id);
            var pet = new Pet(new PetRequestDto());
            customer.getPet().add(pet);
            when(customerPortOut.findById(id)).thenReturn(Optional.of(customer));
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(delete("/api/v1/customers/" + id + "/pets/" + pet.getId())
                            .with(customerAuth(id)))
                    .andExpect(status().isOk());
        }
    }
}
