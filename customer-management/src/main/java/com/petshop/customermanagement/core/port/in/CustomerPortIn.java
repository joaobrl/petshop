package com.petshop.customermanagement.core.port.in;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerUpdateDto;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPortIn {

    Customer registerCustomer (CustomerRequestDto clientRequest);

    List<Customer> customerList();

    Page<Customer> customerListPage(Pageable pageable);

    Customer updateCustomer(UUID id, CustomerUpdateDto dto);

    Customer findCustomer(UUID id);

    /**
     * Busca flexível pro endpoint {@code GET /find/customer/{id}}: tenta o
     * valor como UUID primeiro, se não achar (ou não for um UUID válido)
     * tenta como CPF.
     */
    Customer findCustomerByIdOrCpf(String idOrCpf);

    Customer deleteCustomer(UUID id);

    Customer addPetToCustomer(UUID customerId, List<PetRequestDto> pets);

    Customer updatePet(UUID customerId, UUID petId, PetRequestDto pet);

    Customer removePetFromCustomer(UUID customerId, UUID petId);

    /**
     * Usado pra resolver "quem é o dono do token" (claim cpf do JWT) e
     * checar ownership nos endpoints que um CUSTOMER só pode acessar pra
     * si mesmo — ver CustomerIdentityResolver.
     */
    Optional<Customer> findByCpf(String cpf);
}
