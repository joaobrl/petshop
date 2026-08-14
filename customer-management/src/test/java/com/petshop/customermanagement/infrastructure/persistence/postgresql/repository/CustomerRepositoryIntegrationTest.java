package com.petshop.customermanagement.infrastructure.persistence.postgresql.repository;

import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.PetEmbeddable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sobe um H2 em memória (ver src/test/resources/application.yaml) — não
 * precisa de Docker nem de um Postgres real.
 */
@DataJpaTest
class CustomerRepositoryIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    // CustomerEntity não tem @GeneratedValue (o id vem pronto do domínio,
    // ver CustomerEntity) — sem setId aqui, o Hibernate rejeita o persist
    // com "Identifier must be manually assigned".
    private CustomerEntity newCustomer(String cpf, String email) {
        var entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Maria");
        entity.setCpf(cpf);
        entity.setEmail(email);
        entity.setPhone("11999999999");
        entity.setEnabled(true);
        entity.setPet(new ArrayList<>());
        return entity;
    }

    @Test
    void savesAndFindsCustomerById() {
        var saved = customerRepository.save(newCustomer("12345678900", "maria@mail.com"));

        var found = customerRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Maria");
        assertThat(found.get().getCpf()).isEqualTo("12345678900");
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(customerRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByCpfReturnsMatchingCustomer() {
        customerRepository.save(newCustomer("98765432100", "outro@mail.com"));

        var found = customerRepository.findByCpf("98765432100");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("outro@mail.com");
    }

    @Test
    void findByCpfReturnsEmptyWhenNoMatch() {
        assertThat(customerRepository.findByCpf("00000000000")).isEmpty();
    }

    @Test
    void rejectsDuplicateCpf() {
        customerRepository.saveAndFlush(newCustomer("11122233344", "primeiro@mail.com"));

        assertThatThrownBy(() -> customerRepository.saveAndFlush(newCustomer("11122233344", "segundo@mail.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateEmail() {
        customerRepository.saveAndFlush(newCustomer("11111111111", "mesmo@mail.com"));

        assertThatThrownBy(() -> customerRepository.saveAndFlush(newCustomer("22222222222", "mesmo@mail.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void persistsAndReloadsEmbeddedPets() {
        var pet = new PetEmbeddable();
        pet.setId(UUID.randomUUID());
        pet.setPetName("Rex");
        pet.setPetType(PetType.DOG);
        pet.setPetBreed("Labrador");
        pet.setPetSize(SizeCategory.LARGE);
        pet.setWeightInKg(30.5);
        pet.setPetHealthIssues("Nenhum");

        var customer = newCustomer("33344455566", "comrex@mail.com");
        customer.setPet(new ArrayList<>(List.of(pet)));
        var saved = customerRepository.save(customer);

        var reloaded = customerRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPet()).hasSize(1);
        assertThat(reloaded.getPet().get(0).getPetName()).isEqualTo("Rex");
        assertThat(reloaded.getPet().get(0).getPetType()).isEqualTo(PetType.DOG);
        assertThat(reloaded.getPet().get(0).getWeightInKg()).isEqualTo(30.5);
    }

    @Test
    void findAllReturnsEveryPersistedCustomer() {
        customerRepository.save(newCustomer("44444444444", "a@mail.com"));
        customerRepository.save(newCustomer("55555555555", "b@mail.com"));

        assertThat(customerRepository.findAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void disablingACustomerPersistsTheFlag() {
        var saved = customerRepository.save(newCustomer("66666666666", "c@mail.com"));
        saved.setEnabled(false);
        customerRepository.save(saved);

        var reloaded = customerRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getEnabled()).isFalse();
    }
}
