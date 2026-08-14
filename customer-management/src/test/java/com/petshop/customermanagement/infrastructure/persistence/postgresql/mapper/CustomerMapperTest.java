package com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.PetEmbeddable;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a implementação gerada pelo MapStruct em tempo de compilação
 * (CustomerMapperImpl), obtida via Mappers.getMapper — sem precisar de
 * contexto Spring.
 */
class CustomerMapperTest {

    private final CustomerMapper mapper = Mappers.getMapper(CustomerMapper.class);

    private PetEmbeddable petEmbeddable() {
        var pet = new PetEmbeddable();
        pet.setId(UUID.randomUUID());
        pet.setPetName("Rex");
        pet.setPetType(PetType.DOG);
        pet.setPetBreed("Labrador");
        pet.setPetSize(SizeCategory.LARGE);
        pet.setWeightInKg(30.5);
        pet.setPetHealthIssues("Nenhum");
        return pet;
    }

    private CustomerEntity fullEntity() {
        var entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Maria");
        entity.setCpf("12345678900");
        entity.setEmail("maria@mail.com");
        entity.setPhone("11999999999");
        entity.setEnabled(true);
        entity.setPet(new ArrayList<>(List.of(petEmbeddable())));
        return entity;
    }

    @Test
    void toDomainMapsAllScalarFields() {
        var entity = fullEntity();

        var domain = mapper.toDomain(entity);

        assertThat(domain.getId()).isEqualTo(entity.getId());
        assertThat(domain.getName()).isEqualTo("Maria");
        assertThat(domain.getCpf()).isEqualTo("12345678900");
        assertThat(domain.getEmail()).isEqualTo("maria@mail.com");
        assertThat(domain.getPhone()).isEqualTo("11999999999");
        assertThat(domain.getEnabled()).isTrue();
    }

    @Test
    void toDomainMapsPetListElementByElement() {
        var entity = fullEntity();

        var domain = mapper.toDomain(entity);

        assertThat(domain.getPet()).hasSize(1);
        var pet = domain.getPet().get(0);
        assertThat(pet.getId()).isEqualTo(entity.getPet().get(0).getId());
        assertThat(pet.getPetName()).isEqualTo("Rex");
        assertThat(pet.getPetType()).isEqualTo(PetType.DOG);
        assertThat(pet.getPetBreed()).isEqualTo("Labrador");
        assertThat(pet.getPetSize()).isEqualTo(SizeCategory.LARGE);
        assertThat(pet.getWeightInKg()).isEqualTo(30.5);
        assertThat(pet.getPetHealthIssues()).isEqualTo("Nenhum");
    }

    @Test
    void toDomainReturnsNullWhenEntityIsNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    void toDomainHandlesEmptyPetList() {
        var entity = fullEntity();
        entity.setPet(new ArrayList<>());

        var domain = mapper.toDomain(entity);

        assertThat(domain.getPet()).isEmpty();
    }

    @Test
    void toEntityMapsAllScalarFieldsAndPets() {
        var pet = new Pet(UUID.randomUUID(), "Mimi", PetType.CAT, "SRD", SizeCategory.SMALL, 4.2, "Alergia");
        var domain = new Customer(UUID.randomUUID(), "Joao", "99988877766", "joao@mail.com", "11888888888", false, List.of(pet));

        var entity = mapper.toEntity(domain);

        assertThat(entity.getId()).isEqualTo(domain.getId());
        assertThat(entity.getName()).isEqualTo("Joao");
        assertThat(entity.getCpf()).isEqualTo("99988877766");
        assertThat(entity.getEmail()).isEqualTo("joao@mail.com");
        assertThat(entity.getPhone()).isEqualTo("11888888888");
        assertThat(entity.getEnabled()).isFalse();
        assertThat(entity.getPet()).hasSize(1);
        assertThat(entity.getPet().get(0).getPetName()).isEqualTo("Mimi");
        assertThat(entity.getPet().get(0).getPetType()).isEqualTo(PetType.CAT);
    }

    @Test
    void toEntityReturnsNullWhenDomainIsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toDomainListMapsEveryEntity() {
        var entities = List.of(fullEntity(), fullEntity());

        var domains = mapper.toDomainList(entities);

        assertThat(domains).hasSize(2);
    }

    @Test
    void toDomainListReturnsEmptyForEmptyInput() {
        assertThat(mapper.toDomainList(List.of())).isEmpty();
    }

    @Test
    void toDomainListReturnsNullWhenInputIsNull() {
        assertThat(mapper.toDomainList(null)).isNull();
    }
}
