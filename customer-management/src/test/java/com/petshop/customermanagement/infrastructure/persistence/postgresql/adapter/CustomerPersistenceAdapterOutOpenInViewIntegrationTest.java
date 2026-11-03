package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@Testcontainers
class CustomerPersistenceAdapterOutOpenInViewIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    @DynamicPropertySource
    static void postgresDialect(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private CustomerPortOut customerPortOut;

    @MockitoBean
    private BookingHistoryPortOut bookingHistoryPortOut;

    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    @MockitoBean
    private NotificationPortOut notificationPortOut;

    private Customer saveCustomerWithPet(String cpf, String email) {
        var customer = new Customer("Cliente Teste", cpf, email, "11999990000");
        customer.setPasswordHash("hash");
        customer.getPet().add(new Pet(UUID.randomUUID(), "Rex", PetType.DOG, "SRD", SizeCategory.MEDIUM, 12.5, null));
        return customerPortOut.save(customer);
    }

    @Test
    @DisplayName("Deve carregar os pets ao buscar cliente por id sem open-in-view")
    void shouldLoadPetsWhenFindingById() {
        var saved = saveCustomerWithPet("11111111111", "id@petshop.com");

        var found = customerPortOut.findById(saved.getId()).orElseThrow();

        assertThat(found.getPet()).extracting(Pet::getPetName).containsExactly("Rex");
    }

    @Test
    @DisplayName("Deve carregar os pets ao buscar cliente por e-mail sem open-in-view")
    void shouldLoadPetsWhenFindingByEmail() {
        saveCustomerWithPet("22222222222", "email@petshop.com");

        var found = customerPortOut.findByEmail("email@petshop.com").orElseThrow();

        assertThat(found.getPet()).extracting(Pet::getPetName).containsExactly("Rex");
    }

    @Test
    @DisplayName("Deve carregar os pets ao listar todos os clientes sem open-in-view")
    void shouldLoadPetsWhenFindingAll() {
        var saved = saveCustomerWithPet("33333333333", "all@petshop.com");

        var found = customerPortOut.findAll();

        assertThat(found).filteredOn(c -> c.getId().equals(saved.getId()))
                .singleElement()
                .satisfies(c -> assertThat(c.getPet()).extracting(Pet::getPetName).containsExactly("Rex"));
    }

    @Test
    @DisplayName("Deve carregar os pets ao listar clientes paginados sem open-in-view")
    void shouldLoadPetsWhenFindingAllPage() {
        var saved = saveCustomerWithPet("44444444444", "page@petshop.com");

        var page = customerPortOut.findAllPage(PageRequest.of(0, 100));

        assertThat(page.getContent()).filteredOn(c -> c.getId().equals(saved.getId()))
                .singleElement()
                .satisfies(c -> assertThat(c.getPet()).extracting(Pet::getPetName).containsExactly("Rex"));
    }
}
