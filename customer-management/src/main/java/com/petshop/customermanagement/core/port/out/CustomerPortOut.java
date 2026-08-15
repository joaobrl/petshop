package com.petshop.customermanagement.core.port.out;

import com.petshop.customermanagement.core.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPortOut {

    Optional<Customer> findByCpf(String cpf);

    Optional<Customer> findByEmail(String email);

    Customer save(Customer customer);

    List<Customer> findAll();

    Page<Customer> findAllPage(Pageable pageable);

    Optional<Customer> findById(UUID id);
}
