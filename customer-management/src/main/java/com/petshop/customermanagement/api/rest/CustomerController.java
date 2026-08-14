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
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * Cadastro é público. Para o resto, CUSTOMER só acessa o próprio
 * cadastro/pets (ownership checado em CustomerControllerImpl via
 * CustomerIdentityResolver); ADMIN/RECEPTIONIST acessam qualquer um.
 * Listagens gerais (visão da loja) são só ADMIN/RECEPTIONIST.
 */
@RequestMapping("/api/v1/customers")
public interface CustomerController {

    @PostMapping("/register/customer")
    ResponseEntity<CustomerResponseDto> registerCustomer(@Valid @RequestBody CustomerRequestDto clientRequest, UriComponentsBuilder uriBuilder);

    @GetMapping("/list/customers")
    ResponseEntity<PageResponse<CustomerResponseDto>> listCustomers(@PageableDefault(size = 20) Pageable pageable);

    @GetMapping("/find/customer/{id}")
    ResponseEntity<CustomerResponseDto> getCustomerById(@PathVariable String id, @AuthenticationPrincipal AuthenticatedUser user);

    @PatchMapping("/update/customer/{id}")
    ResponseEntity<CustomerResponseDto> updateCustomer(@PathVariable UUID id, @RequestBody @Valid CustomerUpdateDto dto, @AuthenticationPrincipal AuthenticatedUser user);

    @DeleteMapping("/delete/customer/{id}")
    ResponseEntity<CustomerResponseDto> deleteCustomer(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user);
    
    @PatchMapping("register/{customerId}/pet")
    ResponseEntity<CustomerResponseDto> addPetToCustomer(@PathVariable UUID customerId, @RequestBody @Valid List<PetRequestDto> pets, @AuthenticationPrincipal AuthenticatedUser user);

    @GetMapping("/list/pets")
    ResponseEntity<List<PetResponseDto>> listPets(@RequestHeader(value = "typePets", required = false) String typePets);

    @GetMapping("/{customerId}/pets")
    ResponseEntity<List<PetResponseDto>> listPetsOfCustomer(@PathVariable UUID customerId, @AuthenticationPrincipal AuthenticatedUser user);

    @PatchMapping("/{customerId}/pets/{petId}")
    ResponseEntity<CustomerResponseDto> updatePet(@PathVariable UUID customerId, @PathVariable UUID petId, @RequestBody @Valid PetRequestDto pet, @AuthenticationPrincipal AuthenticatedUser user);

    @DeleteMapping("/{customerId}/pets/{petId}")
    ResponseEntity<CustomerResponseDto> removePetFromCustomer(@PathVariable UUID customerId, @PathVariable UUID petId, @AuthenticationPrincipal AuthenticatedUser user);

}
