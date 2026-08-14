package com.petshop.customermanagement.core.port.out;

import com.petshop.customermanagement.core.domain.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPortOut {

    Optional<Customer> findByCpf(String cpf);

    Optional<Customer> findByEmail(String email);

    Customer save(Customer customer);

    List<Customer> findAll();

    Optional<Customer> findById(UUID id);
}
