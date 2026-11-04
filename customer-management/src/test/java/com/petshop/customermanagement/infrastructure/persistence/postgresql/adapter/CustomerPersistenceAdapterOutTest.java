package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper.CustomerMapper;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerPersistenceAdapterOutTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerMapper mapper;

    private CustomerPersistenceAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new CustomerPersistenceAdapterOut(customerRepository, mapper);
    }

    @Test
    void findByCpfReturnsMappedCustomerWhenEntityExists() {
        var entity = new CustomerEntity();
        var domain = new Customer("Maria", "12345678900", "maria@mail.com", "119999999");
        when(customerRepository.findByCpf("12345678900")).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        var result = adapter.findByCpf("12345678900");

        assertThat(result).contains(domain);
    }

    @Test
    void findByCpfReturnsEmptyWhenEntityDoesNotExist() {
        when(customerRepository.findByCpf("00000000000")).thenReturn(Optional.empty());

        assertThat(adapter.findByCpf("00000000000")).isEmpty();
    }

    @Test
    void saveConvertsToEntitySavesAndConvertsBackToDomain() {
        var domain = new Customer("Maria", "12345678900", "maria@mail.com", "119999999");
        var entity = new CustomerEntity();
        var savedEntity = new CustomerEntity();
        var savedDomain = new Customer("Maria", "12345678900", "maria@mail.com", "119999999");
        when(mapper.toEntity(domain)).thenReturn(entity);
        when(customerRepository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

        var result = adapter.save(domain);

        assertThat(result).isEqualTo(savedDomain);
    }

    @Test
    void findAllReturnsMappedList() {
        var entities = List.of(new CustomerEntity(), new CustomerEntity());
        var domains = List.of(new Customer("A", "1", "a@mail.com", "1"), new Customer("B", "2", "b@mail.com", "2"));
        when(customerRepository.findAll()).thenReturn(entities);
        when(mapper.toDomainList(entities)).thenReturn(domains);

        assertThat(adapter.findAll()).isEqualTo(domains);
    }

    @Test
    void findAllReturnsEmptyWhenNoCustomers() {
        when(customerRepository.findAll()).thenReturn(List.of());
        when(mapper.toDomainList(List.of())).thenReturn(List.of());

        assertThat(adapter.findAll()).isEmpty();
    }

    @Test
    void findAllPageReturnsMappedPage() {
        var pageable = PageRequest.of(0, 20);
        var entity = new CustomerEntity();
        var domain = new Customer("Maria", "12345678900", "maria@mail.com", "119999999");
        when(customerRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.findAllPage(pageable).getContent()).containsExactly(domain);
    }

    @Test
    void findByIdReturnsMappedCustomerWhenEntityExists() {
        var id = UUID.randomUUID();
        var entity = new CustomerEntity();
        var domain = new Customer("Maria", "12345678900", "maria@mail.com", "119999999");
        when(customerRepository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.findById(id)).contains(domain);
    }

    @Test
    void findByIdReturnsEmptyWhenEntityDoesNotExist() {
        var id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("Deve delegar existsById ao repositório")
    void shouldDelegateExistsByIdToRepository() {
        var id = UUID.randomUUID();
        when(customerRepository.existsById(id)).thenReturn(true);

        assertThat(adapter.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("Deve devolver a página do repositório e repassar o tipo ao listar pets")
    void shouldReturnRepositoryPageAndPassTypeWhenFindingPets() {
        var page = new PageImpl<>(List.of(new Pet()));
        when(customerRepository.findPets(eq(PetType.CAT), any(Pageable.class))).thenReturn(page);

        var result = adapter.findPets(PetType.CAT, PageRequest.of(0, 20));

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("Deve devolver a página do repositório e repassar o cliente ao listar pets do cliente")
    void shouldReturnRepositoryPageAndPassCustomerIdWhenFindingPetsByCustomer() {
        var customerId = UUID.randomUUID();
        var page = new PageImpl<>(List.of(new Pet()));
        when(customerRepository.findPetsByCustomerId(eq(customerId), any(Pageable.class))).thenReturn(page);

        var result = adapter.findPetsByCustomerId(customerId, PageRequest.of(0, 20));

        assertThat(result).isSameAs(page);
    }

    @Test
    @DisplayName("Deve ordenar por p.petName e desempatar por p.id quando não houver sort")
    void shouldSortByPetNameThenIdWhenNoSortIsRequested() {
        var captor = stubFindPets();

        adapter.findPets(null, PageRequest.of(2, 15));

        var sent = captor.getValue();
        assertThat(sent.getPageNumber()).isEqualTo(2);
        assertThat(sent.getPageSize()).isEqualTo(15);
        assertThat(sent.getSort().stream().map(o -> o.getProperty() + ":" + o.getDirection()).toList())
                .containsExactly("p.petName:ASC", "p.id:ASC");
    }

    @ParameterizedTest
    @CsvSource({
            "id,p.id",
            "name,p.petName",
            "petName,p.petName",
            "type,p.petType",
            "petType,p.petType",
            "petBreed,p.petBreed",
            "petSize,p.petSize",
            "weightInKg,p.weightInKg",
            "petHealthIssues,p.petHealthIssues"
    })
    @DisplayName("Deve traduzir cada campo permitido para o atributo qualificado e manter a direção")
    void shouldTranslateAllowedSortFieldAndKeepDirection(String requested, String expected) {
        var captor = stubFindPets();

        adapter.findPets(null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, requested)));

        var orders = captor.getValue().getSort().toList();
        assertThat(orders.get(0).getProperty()).isEqualTo(expected);
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(orders.size() - 1).getProperty()).isEqualTo("p.id");
        assertThat(orders.get(orders.size() - 1).getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("Deve traduzir vários campos de sort preservando a ordem e acrescentar p.id no fim")
    void shouldTranslateMultipleSortFieldsInOrderAndAppendId() {
        var captor = stubFindPets();

        adapter.findPets(null, PageRequest.of(0, 20, Sort.by(Sort.Order.asc("type"), Sort.Order.desc("name"))));

        assertThat(captor.getValue().getSort().stream().map(o -> o.getProperty() + ":" + o.getDirection()).toList())
                .containsExactly("p.petType:ASC", "p.petName:DESC", "p.id:ASC");
    }

    @Test
    @DisplayName("Deve aplicar a mesma tradução de sort ao listar pets de um cliente")
    void shouldApplySameSortTranslationWhenFindingPetsByCustomer() {
        var customerId = UUID.randomUUID();
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(customerRepository.findPetsByCustomerId(eq(customerId), captor.capture())).thenReturn(new PageImpl<>(List.of()));

        adapter.findPetsByCustomerId(customerId, PageRequest.of(1, 5, Sort.by("name")));

        var sent = captor.getValue();
        assertThat(sent.getPageNumber()).isEqualTo(1);
        assertThat(sent.getPageSize()).isEqualTo(5);
        assertThat(sent.getSort().stream().map(Sort.Order::getProperty).toList()).containsExactly("p.petName", "p.id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cpf", "p.petName", "name; drop table customers", "pet.id", "unknown"})
    @DisplayName("Deve lançar IllegalArgumentException sem consultar o repositório quando o campo de sort for inválido")
    void shouldThrowIllegalArgumentWithoutQueryingWhenSortFieldIsInvalid(String field) {
        var pageable = PageRequest.of(0, 20, Sort.by(field));

        assertThatThrownBy(() -> adapter.findPets(null, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(field);
        assertThatThrownBy(() -> adapter.findPetsByCustomerId(UUID.randomUUID(), pageable))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(customerRepository);
    }

    @Test
    @DisplayName("Deve rejeitar o sort inteiro quando apenas um dos campos for inválido")
    void shouldRejectWholeSortWhenOnlyOneFieldIsInvalid() {
        var pageable = PageRequest.of(0, 20, Sort.by("name", "cpf"));

        assertThatThrownBy(() -> adapter.findPets(null, pageable))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(customerRepository);
    }

    private ArgumentCaptor<Pageable> stubFindPets() {
        var captor = ArgumentCaptor.forClass(Pageable.class);
        when(customerRepository.findPets(any(), captor.capture())).thenReturn(new PageImpl<>(List.of()));
        return captor;
    }
}
