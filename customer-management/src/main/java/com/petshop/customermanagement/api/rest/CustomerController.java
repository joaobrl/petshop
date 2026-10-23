package com.petshop.customermanagement.api.rest;

import com.petshop.commons.dto.PageResponse;
import com.petshop.customermanagement.api.rest.dto.CustomerResponseDto;
import com.petshop.customermanagement.api.rest.dto.PetResponseDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerUpdateDto;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/v1/customers")
public interface CustomerController {

    @PostMapping("/register/customer")
    ResponseEntity<CustomerResponseDto> registerCustomer(@Valid @RequestBody CustomerRequestDto clientRequest, UriComponentsBuilder uriBuilder);

    @GetMapping("/list/customers")
    ResponseEntity<PageResponse<CustomerResponseDto>> listCustomers(@PageableDefault(size = 20) Pageable pageable);

    @GetMapping("/find/customer/{id}")
    ResponseEntity<CustomerResponseDto> getCustomerById(@PathVariable String id, @RequestHeader("Authorization") String token);

    @PatchMapping("/update/customer/{id}")
    ResponseEntity<CustomerResponseDto> updateCustomer(@PathVariable UUID id, @RequestBody @Valid CustomerUpdateDto dto, @RequestHeader("Authorization") String token);

    @DeleteMapping("/delete/customer/{id}")
    ResponseEntity<CustomerResponseDto> deleteCustomer(@PathVariable UUID id, @RequestHeader("Authorization") String token);

    @PatchMapping("register/{customerId}/pet")
    ResponseEntity<CustomerResponseDto> addPetToCustomer(@PathVariable UUID customerId, @RequestBody @Valid List<PetRequestDto> pets, @RequestHeader("Authorization") String token);

    @GetMapping("/list/pets")
    ResponseEntity<List<PetResponseDto>> listPets(@RequestHeader(value = "typePets", required = false) String typePets);

    @GetMapping("/{customerId}/pets")
    ResponseEntity<List<PetResponseDto>> listPetsOfCustomer(@PathVariable UUID customerId, @RequestHeader("Authorization") String token);

    @PatchMapping("/{customerId}/pets/{petId}")
    ResponseEntity<CustomerResponseDto> updatePet(@PathVariable UUID customerId, @PathVariable UUID petId, @RequestBody @Valid PetRequestDto pet, @RequestHeader("Authorization") String token);

    @DeleteMapping("/{customerId}/pets/{petId}")
    ResponseEntity<CustomerResponseDto> removePetFromCustomer(@PathVariable UUID customerId, @PathVariable UUID petId, @RequestHeader("Authorization") String token);

}
