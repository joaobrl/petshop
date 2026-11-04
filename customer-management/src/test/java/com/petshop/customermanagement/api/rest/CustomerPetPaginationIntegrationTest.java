package com.petshop.customermanagement.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CustomerPetPaginationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    private static final int CUSTOMERS = 10;
    private static final int PETS = 45;
    private static final int DOGS = 12;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private BookingHistoryPortOut bookingHistoryPortOut;

    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    @MockitoBean
    private NotificationPortOut notificationPortOut;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final List<UUID> customerIds = new ArrayList<>();

    @BeforeEach
    void seed() {
        jdbc.update("delete from customer_pets");
        jdbc.update("delete from customers");
        customerIds.clear();

        for (int i = 0; i < CUSTOMERS; i++) {
            var id = UUID.randomUUID();
            customerIds.add(id);
            jdbc.update("insert into customers (id, name, cpf, email, phone, enabled, must_change_password, version) "
                    + "values (?, ?, ?, ?, '11999999999', true, false, 0)",
                    id, "Cliente " + i, String.format("%011d", i), "cliente" + i + "@mail.com");
        }

        for (int i = 0; i < PETS; i++) {
            var type = i < DOGS ? "DOG" : "CAT";
            jdbc.update("insert into customer_pets (customer_id, id, pet_name, pet_type, pet_breed, pet_size, weight_in_kg) "
                    + "values (?, ?, ?, ?, 'SRD', 'MEDIUM', ?)",
                    customerIds.get(i % CUSTOMERS), UUID.randomUUID(), String.format("pet-%02d", i), type, 5.0 + i);
        }
    }

    private static String bearer(UUID id, Role role, AccountType accountType) {
        return "Bearer " + TEST_JWT_SERVICE.generateToken(id, "user@mail.com", "User", "12345678900", "11999999999", role, accountType);
    }

    private static String admin() {
        return bearer(UUID.randomUUID(), Role.ADMIN, AccountType.STAFF);
    }

    private static String customer(UUID id) {
        return bearer(id, Role.CUSTOMER, AccountType.CUSTOMER);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult listAll(String auth, String... params) throws Exception {
        var request = get("/api/v1/customers/list/pets").header("Authorization", auth);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request).andReturn();
    }

    @Test
    @DisplayName("CA1: 45 pets em 10 clientes com size=20 devem formar 3 páginas sem repetir pets")
    void shouldPaginate45PetsInThreePagesWhenSizeIs20() throws Exception {
        var ids = new HashSet<String>();
        var sizes = new ArrayList<Integer>();
        var lasts = new ArrayList<Boolean>();

        for (int page = 0; page < 3; page++) {
            var result = listAll(admin(), "page", String.valueOf(page), "size", "20");
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            var json = body(result);
            assertThat(json.get("totalElements").asLong()).isEqualTo(PETS);
            assertThat(json.get("totalPages").asInt()).isEqualTo(3);
            assertThat(json.get("page").asInt()).isEqualTo(page);
            sizes.add(json.get("content").size());
            lasts.add(json.get("last").asBoolean());
            json.get("content").forEach(pet -> ids.add(pet.get("id").asText()));
        }

        assertThat(sizes).containsExactly(20, 20, 5);
        assertThat(lasts).containsExactly(false, false, true);
        assertThat(ids).hasSize(PETS);
    }

    @Test
    @DisplayName("CA2: typePets=DOG deve devolver só os 12 cães com totalElements=12")
    void shouldFilterByTypeAndCountOnlyDogs() throws Exception {
        var filtered = mockMvc.perform(get("/api/v1/customers/list/pets")
                .header("Authorization", admin())
                .header("typePets", "dog")
                .param("size", "20")).andReturn();

        assertThat(filtered.getResponse().getStatus()).isEqualTo(200);
        var json = body(filtered);
        assertThat(json.get("totalElements").asLong()).isEqualTo(DOGS);
        assertThat(json.get("totalPages").asInt()).isEqualTo(1);
        assertThat(json.get("content")).hasSize(DOGS);
        json.get("content").forEach(pet -> assertThat(pet.get("petType").asText()).isEqualTo("DOG"));
    }

    @Test
    @DisplayName("CA3: cada chamada deve executar no máximo 2 statements (conteúdo e contagem)")
    void shouldExecuteAtMostTwoStatementsPerCall() throws Exception {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.isStatisticsEnabled()).isTrue();

        var authAll = admin();
        statistics.clear();
        var all = listAll(authAll, "size", "20");
        var statementsForAll = statistics.getPrepareStatementCount();

        var authOwn = customer(customerIds.get(0));
        statistics.clear();
        var own = mockMvc.perform(get("/api/v1/customers/" + customerIds.get(0) + "/pets")
                .header("Authorization", authOwn)).andReturn();
        var statementsForOwn = statistics.getPrepareStatementCount();

        assertThat(all.getResponse().getStatus()).isEqualTo(200);
        assertThat(own.getResponse().getStatus()).isEqualTo(200);
        assertThat(statementsForAll).isBetween(1L, 2L);
        assertThat(statementsForOwn).isBetween(1L, 2L);
    }

    @Test
    @DisplayName("Deve ordenar por name ascendente por padrão e descendente com sort=name,desc")
    void shouldSortByNameAscendingByDefaultAndDescendingWhenRequested() throws Exception {
        var asc = body(listAll(admin(), "size", "100"));
        var ascExplicit = body(listAll(admin(), "size", "100", "sort", "name"));
        var desc = body(listAll(admin(), "size", "100", "sort", "name,desc"));

        assertThat(asc.get("content").get(0).get("petName").asText()).isEqualTo("pet-00");
        assertThat(asc.get("content").get(PETS - 1).get("petName").asText()).isEqualTo("pet-44");
        assertThat(ascExplicit.get("content").get(0).get("petName").asText()).isEqualTo("pet-00");
        assertThat(desc.get("content").get(0).get("petName").asText()).isEqualTo("pet-44");
        assertThat(desc.get("content").get(PETS - 1).get("petName").asText()).isEqualTo("pet-00");
    }

    @Test
    @DisplayName("Deve ordenar por campo de outro tipo e manter ordem estável entre páginas")
    void shouldSortByOtherAllowedFieldAcrossPages() throws Exception {
        var first = body(listAll(admin(), "size", "20", "sort", "weightInKg,desc"));
        var second = body(listAll(admin(), "page", "1", "size", "20", "sort", "weightInKg,desc"));

        assertThat(first.get("content").get(0).get("weightInKg").asDouble()).isEqualTo(49.0);
        assertThat(second.get("content").get(0).get("weightInKg").asDouble()).isEqualTo(29.0);
    }

    @Test
    @DisplayName("Deve responder 400 quando o campo de sort não estiver na lista permitida")
    void shouldReturnBadRequestWhenSortFieldIsInvalid() throws Exception {
        var all = listAll(admin(), "sort", "cpf");
        var own = mockMvc.perform(get("/api/v1/customers/" + customerIds.get(0) + "/pets")
                .header("Authorization", admin())
                .param("sort", "passwordHash")).andReturn();

        assertThat(all.getResponse().getStatus()).isEqualTo(400);
        assertThat(own.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Deve limitar size=500 a 100")
    void shouldClampSizeTo100WhenSizeIs500() throws Exception {
        var result = listAll(admin(), "size", "500");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var json = body(result);
        assertThat(json.get("size").asInt()).isEqualTo(100);
        assertThat(json.get("content")).hasSize(PETS);
        assertThat(json.get("last").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Deve devolver página vazia com totais corretos quando a página passar do fim")
    void shouldReturnEmptyContentWhenPageIsBeyondTheEnd() throws Exception {
        var result = listAll(admin(), "page", "9", "size", "20");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var json = body(result);
        assertThat(json.get("content")).isEmpty();
        assertThat(json.get("totalElements").asLong()).isEqualTo(PETS);
    }

    @Test
    @DisplayName("Deve devolver só os pets do próprio cliente paginados")
    void shouldListOnlyOwnPetsPaginated() throws Exception {
        var ownerId = customerIds.get(0);

        var result = mockMvc.perform(get("/api/v1/customers/" + ownerId + "/pets")
                .header("Authorization", customer(ownerId))
                .param("size", "3")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var json = body(result);
        assertThat(json.get("totalElements").asLong()).isEqualTo(5);
        assertThat(json.get("totalPages").asInt()).isEqualTo(2);
        assertThat(json.get("content")).hasSize(3);
        assertThat(json.get("last").asBoolean()).isFalse();
        var owned = jdbc.queryForList("select id from customer_pets where customer_id = ?", UUID.class, ownerId);
        json.get("content").forEach(pet -> assertThat(owned).contains(UUID.fromString(pet.get("id").asText())));
    }

    @Test
    @DisplayName("Deve devolver 200 com página vazia quando o cliente existir sem pets")
    void shouldReturnEmptyPageWhenCustomerExistsWithoutPets() throws Exception {
        var id = UUID.randomUUID();
        jdbc.update("insert into customers (id, name, cpf, email, phone, enabled, must_change_password, version) "
                + "values (?, 'Sem Pets', '99999999999', 'sempets@mail.com', '11999999999', true, false, 0)", id);

        var result = mockMvc.perform(get("/api/v1/customers/" + id + "/pets")
                .header("Authorization", customer(id))).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result).get("content")).isEmpty();
        assertThat(body(result).get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("Deve devolver 404 quando o cliente não existir")
    void shouldReturnNotFoundWhenCustomerDoesNotExist() throws Exception {
        var result = mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID() + "/pets")
                .header("Authorization", admin())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Deve manter a autorização de dono e de papel")
    void shouldKeepOwnershipAndRoleAuthorization() throws Exception {
        var ownerId = customerIds.get(0);
        var otherId = customerIds.get(1);

        var othersPets = mockMvc.perform(get("/api/v1/customers/" + otherId + "/pets")
                .header("Authorization", customer(ownerId))).andReturn();
        var storeWideAsCustomer = listAll(customer(ownerId));
        var anonymous = mockMvc.perform(get("/api/v1/customers/" + ownerId + "/pets")).andReturn();
        var adminOnOther = mockMvc.perform(get("/api/v1/customers/" + otherId + "/pets")
                .header("Authorization", admin())).andReturn();

        assertThat(othersPets.getResponse().getStatus()).isEqualTo(403);
        assertThat(storeWideAsCustomer.getResponse().getStatus()).isEqualTo(403);
        assertThat(anonymous.getResponse().getStatus()).isEqualTo(401);
        assertThat(adminOnOther.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Deve responder 400 quando o typePets for inválido")
    void shouldReturnBadRequestWhenTypeHeaderIsInvalid() throws Exception {
        var result = mockMvc.perform(get("/api/v1/customers/list/pets")
                .header("Authorization", admin())
                .header("typePets", "bird")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
    }
}
