package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper.CustomerMapper;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
}
