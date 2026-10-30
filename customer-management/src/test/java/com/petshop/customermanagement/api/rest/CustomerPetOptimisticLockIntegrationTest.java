package com.petshop.customermanagement.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * SDD-010 ponta a ponta no Postgres real. A corrida é determinística: o spy
 * do CustomerPortOut segura cada thread numa CyclicBarrier(2) logo depois do
 * findById real, então as duas só seguem para o save quando ambas já leram a
 * mesma versão do agregado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CustomerPetOptimisticLockIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    // O application.yaml de teste fixa o dialeto do H2.
    @DynamicPropertySource
    static void postgresDialect(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private CustomerPortOut customerPortOut;

    @MockitoBean
    private BookingHistoryPortOut bookingHistoryPortOut;

    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    @MockitoBean
    private NotificationPortOut notificationPortOut;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // CA1: duas requisições "adicionar pet" leem o cliente na mesma versão;
    // uma grava, a outra recebe 409 e o pet da vencedora não é sobrescrito.
    @Test
    void concurrentAddPetYieldsOneOkAndOneConflictWithoutLosingPets() throws Exception {
        var customerId = registerCustomer("52998224725", "lock1@mail.com");
        addPet(customerId, "Rex").andReturnStatus(200);

        var barrier = new CyclicBarrier(2);
        doAnswer(inv -> {
            var result = inv.callRealMethod();
            barrier.await(30, TimeUnit.SECONDS);
            return result;
        }).when(customerPortOut).findById(any());

        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> a = executor.submit(() -> addPet(customerId, "Thor").result());
            Future<MvcResult> b = executor.submit(() -> addPet(customerId, "Mel").result());
            var results = List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));

            var statuses = results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
            assertThat(statuses).containsExactly(200, 409);

            var conflict = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
            var body = objectMapper.readTree(conflict.getResponse().getContentAsString());
            assertThat(body.get("detail").asText())
                    .isEqualTo("O registro foi alterado por outra requisição. Recarregue e tente novamente.");

            // Rex + o pet da vencedora: nenhum foi sobrescrito
            assertThat(petCount(customerId)).isEqualTo(2);
            assertThat(versionOf(customerId)).isEqualTo(2L);
        } finally {
            executor.shutdownNow();
            reset(customerPortOut);
        }
    }

    // Mesmo cenário no nível do adapter: a segunda cópia do agregado, salva
    // com a versão antiga, lança ObjectOptimisticLockingFailureException.
    @Test
    void savingStaleCopyOfTheAggregateThrowsOptimisticLockFailure() throws Exception {
        var customerId = registerCustomer("11144477735", "lock2@mail.com");

        var first = load(customerId);
        var second = load(customerId);
        assertThat(second.getVersion()).isEqualTo(first.getVersion());

        first.getPet().add(pet("Rex"));
        var saved = customerPortOut.save(first);
        assertThat(saved.getVersion()).isEqualTo(first.getVersion() + 1);

        second.getPet().add(pet("Thor"));
        assertThatThrownBy(() -> customerPortOut.save(second))
                .isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(petCount(customerId)).isEqualTo(1);
    }

    // CA3: cadastro, adicionar, editar e remover pet em sequência, cada
    // requisição relendo a versão atual.
    @Test
    void sequentialPetFlowKeepsWorkingAndBumpsVersion() throws Exception {
        var customerId = registerCustomer("39053344705", "lock3@mail.com");
        assertThat(versionOf(customerId)).isZero();

        addPet(customerId, "Rex").andReturnStatus(200);
        var petId = jdbc.queryForObject("select id from customer_pets where customer_id = ?", UUID.class, customerId);

        var editStatus = mockMvc.perform(patch("/api/v1/customers/" + customerId + "/pets/" + petId)
                        .header("Authorization", "Bearer " + token(customerId))
                        .contentType("application/json")
                        .content("{\"petName\":\"Rex II\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(editStatus).isEqualTo(200);
        assertThat(jdbc.queryForObject("select pet_name from customer_pets where customer_id = ?",
                String.class, customerId)).isEqualTo("Rex II");

        var removeStatus = mockMvc.perform(delete("/api/v1/customers/" + customerId + "/pets/" + petId)
                        .header("Authorization", "Bearer " + token(customerId)))
                .andReturn().getResponse().getStatus();
        assertThat(removeStatus).isEqualTo(200);

        assertThat(petCount(customerId)).isZero();
        assertThat(versionOf(customerId)).isEqualTo(3L);
    }

    // CA2: cliente criado antes da mudança (tabela sem a coluna version). O
    // SchemaUpdate é o mesmo mecanismo do ddl-auto=update: precisa adicionar
    // a coluna já com 0 nas linhas existentes, e editar pet leva a versão a 1.
    @Test
    void customerWithoutVersionColumnGetsZeroAndFirstEditBumpsToOne() {
        var legacyId = UUID.randomUUID();
        jdbc.execute("alter table customers drop column version");
        jdbc.update("insert into customers (id, name, cpf, email, phone, enabled, must_change_password) "
                + "values (?, 'Legado', '86288366757', 'legado@mail.com', '11999999999', true, false)", legacyId);

        // buildSessionFactory com hbm2ddl.auto=update roda o mesmo caminho do ddl-auto=update
        schemaMetadata().buildSessionFactory().close();

        assertThat(versionOf(legacyId)).isZero();

        var customer = load(legacyId);
        assertThat(customer.getVersion()).isZero();
        customer.getPet().add(pet("Rex"));
        customerPortOut.save(customer);

        assertThat(versionOf(legacyId)).isEqualTo(1L);
        assertThat(petCount(legacyId)).isEqualTo(1);
    }

    private org.hibernate.boot.Metadata schemaMetadata() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.datasource", dataSource)
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.hbm2ddl.auto", "update")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .build();
        return new MetadataSources(registry).addAnnotatedClass(CustomerEntity.class).buildMetadata();
    }

    // Fora de uma requisição web não há OpenEntityManagerInView: a leitura
    // precisa de transação para o mapper alcançar a coleção lazy de pets.
    private com.petshop.customermanagement.core.domain.Customer load(UUID id) {
        return new TransactionTemplate(transactionManager).execute(s -> customerPortOut.findById(id).orElseThrow());
    }

    private static Pet pet(String name) {
        var pet = new Pet();
        pet.setId(UUID.randomUUID());
        pet.setPetName(name);
        pet.setPetType(PetType.DOG);
        pet.setPetSize(SizeCategory.MEDIUM);
        return pet;
    }

    private long petCount(UUID customerId) {
        return jdbc.queryForObject("select count(*) from customer_pets where customer_id = ?", Long.class, customerId);
    }

    private long versionOf(UUID customerId) {
        return jdbc.queryForObject("select version from customers where id = ?", Long.class, customerId);
    }

    private UUID registerCustomer(String cpf, String email) throws Exception {
        var request = new CustomerRequestDto("Maria", cpf, email, "11999999999", "Senha123", "Senha123");
        mockMvc.perform(post("/api/v1/customers/register/customer")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request))).andReturn();
        return jdbc.queryForObject("select id from customers where cpf = ?", UUID.class, cpf);
    }

    private static String token(UUID customerId) {
        return TEST_JWT_SERVICE.generateToken(customerId, "maria@mail.com", "Maria", "12345678900",
                "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    private PetCall addPet(UUID customerId, String petName) throws Exception {
        var result = mockMvc.perform(patch("/api/v1/customers/register/" + customerId + "/pet")
                        .header("Authorization", "Bearer " + token(customerId))
                        .contentType("application/json")
                        .content("[{\"petName\":\"" + petName + "\",\"petType\":\"DOG\",\"petSize\":\"MEDIUM\"}]"))
                .andReturn();
        return new PetCall(result);
    }

    private record PetCall(MvcResult result) {
        void andReturnStatus(int expected) {
            assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        }
    }
}
