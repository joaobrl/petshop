package com.petshop.customermanagement.api.rest;

import com.petshop.customermanagement.api.rest.dto.PetResponseDto;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.port.in.CustomerPortIn;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.api.rest.dto.CustomerResponseDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerUpdateDto;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import com.petshop.customermanagement.infrastructure.security.CustomerIdentityResolver;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import com.petshop.commons.dto.PageResponse;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CustomerControllerImpl implements CustomerController{

    private final CustomerPortIn portIn;
    private final CustomerIdentityResolver customerIdentityResolver;

    @Override
    @Transactional
    public ResponseEntity<CustomerResponseDto> registerCustomer(@Valid @RequestBody CustomerRequestDto customerRequest, UriComponentsBuilder uriBuilder) {
        var customer = portIn.registerCustomer(customerRequest);
        var uri = uriBuilder.path("/customer/{id}").buildAndExpand(customer.getId()).toUri();
        return ResponseEntity.created(uri).body(new CustomerResponseDto(customer));
    }

    @Override
    public ResponseEntity<PageResponse<CustomerResponseDto>> listCustomers(Pageable pageable) {
        var page = portIn.customerListPage(pageable).map(CustomerResponseDto::new);
        return ResponseEntity.ok(new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()));
    }

    @Override
    public ResponseEntity<CustomerResponseDto> getCustomerById(String id, AuthenticatedUser user) {
        var customer = portIn.findCustomerByIdOrCpf(id);
        customerIdentityResolver.requireOwnershipIfCustomer(customer.getId(), user);
        return ResponseEntity.ok(new CustomerResponseDto(customer));
    }

    @Override
    public ResponseEntity<CustomerResponseDto> updateCustomer(UUID id, @Valid CustomerUpdateDto dto, AuthenticatedUser user) {
        customerIdentityResolver.requireOwnershipIfCustomer(id, user);
        var customer = portIn.updateCustomer(id, dto);
        return ResponseEntity.ok(new CustomerResponseDto(customer));
    }

    @Override
    public ResponseEntity<CustomerResponseDto> deleteCustomer(UUID id, AuthenticatedUser user) {
        customerIdentityResolver.requireOwnershipIfCustomer(id, user);
        portIn.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<CustomerResponseDto> addPetToCustomer(UUID customerId, List<PetRequestDto> pets, AuthenticatedUser user) {
        customerIdentityResolver.requireOwnershipIfCustomer(customerId, user);

        if (pets == null || pets.isEmpty()) {
            throw new IllegalArgumentException("É necessário informar ao menos um pet");
        }
        var addPet = portIn.addPetToCustomer(customerId, pets);
        return ResponseEntity.ok(new CustomerResponseDto(addPet));
    }

    @Override
    public ResponseEntity<List<PetResponseDto>> listPets(String typePets) {
        var petsStream = portIn.customerList()
                .stream()
                .map(Customer::getPet)
                .flatMap(List::stream);

        if (typePets != null && !typePets.isBlank()) {
            var type = PetType.valueOf(typePets.toUpperCase());
            petsStream = petsStream.filter(pet -> pet.getPetType() == type);
        }

        List<PetResponseDto> petsDto = petsStream
                .map(PetResponseDto::new)
                .toList();

        return ResponseEntity.ok(petsDto);
    }

    @Override
    public ResponseEntity<List<PetResponseDto>> listPetsOfCustomer(UUID customerId, AuthenticatedUser user) {
        customerIdentityResolver.requireOwnershipIfCustomer(customerId, user);

        List<PetResponseDto> petsDto = portIn.customerList()
                .stream()
                .filter(customer -> customer.getId().equals(customerId))
                .findFirst()
                .map(Customer::getPet)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + customerId))
                .stream()
                .map(PetResponseDto::new)
                .toList();

        return ResponseEntity.ok(petsDto);
    }

    @Override
    public ResponseEntity<CustomerResponseDto> updatePet(UUID customerId, UUID petId, PetRequestDto pet, AuthenticatedUser user) {
        customerIdentityResolver.requireOwnershipIfCustomer(customerId, user);
        var customer = portIn.updatePet(customerId, petId, pet);
        return ResponseEntity.ok(new CustomerResponseDto(customer));
    }

    @Override
    public ResponseEntity<CustomerResponseDto> removePetFromCustomer(UUID customerId, UUID petId, AuthenticatedUser user) {
        customerIdentityResolver.requireOwnershipIfCustomer(customerId, user);
        var customer = portIn.removePetFromCustomer(customerId, petId);
        return ResponseEntity.ok(new CustomerResponseDto(customer));
    }

}
