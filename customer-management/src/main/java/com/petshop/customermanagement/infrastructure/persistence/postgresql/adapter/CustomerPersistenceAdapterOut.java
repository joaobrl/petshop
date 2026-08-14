package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper.CustomerMapper;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerPersistenceAdapterOut implements CustomerPortOut {

    private final CustomerRepository customerRepository;
    private final CustomerMapper mapper;

    @Override
    public Optional<Customer> findByCpf(String cpf) {
        return customerRepository.findByCpf(cpf)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Customer> findByEmail(String email) {
        return customerRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public Customer save(Customer customer) {
        CustomerEntity entity = mapper.toEntity(customer);
        // mapper.toEntity() sempre monta um CustomerEntity novo (via
        // construtor vazio + setters), então o campo transient "isNew"
        // volta pro default (true) em TODA chamada — mesmo quando
        // customer já existe no banco (update/delete/add-pet chegam aqui
        // depois de um findCustomer()). Sem checar isso, o Spring Data
        // tentaria persist() num id que já existe -> erro de PK
        // duplicada. Um exists() a mais aqui é barato (index scan por PK)
        // e evita esse problema por completo.
        if (customerRepository.existsById(entity.getId())) {
            entity.setNew(false);
        }
        return mapper.toDomain(customerRepository.save(entity));
    }

    @Override
    public List<Customer> findAll() {
        return mapper.toDomainList(customerRepository.findAll());
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id)
                .map(mapper::toDomain);
    }
}
