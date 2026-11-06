package com.petshop.customermanagement.core.application.service;

import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.port.in.CustomerPortIn;
import com.petshop.customermanagement.core.port.in.EmailVerificationPortIn;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerUpdateDto;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.dto.IdentityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService implements CustomerPortIn {

    private final CustomerPortOut customerPortOut;
    private final IdentityProviderPortOut identityProviderPortOut;
    private final NotificationPortOut notificationPortOut;
    private final EmailVerificationPortIn emailVerificationPortIn;

    // Transação: o usuário é criado no Keycloak antes do commit local. Falha no Keycloak
    // (inclusive 409 de e-mail já usado) desfaz o cadastro local; falha no commit depois
    // de criar no Keycloak remove o usuário de lá (IdentityCompensation).
    @Override
    @Transactional
    public Customer registerCustomer (CustomerRequestDto request) {
        customerPortOut.findByCpf(request.getCpf())
                .ifPresent(c -> {
                    throw new ConflictException("CPF já cadastrado");
                });

        var customer = new Customer(
                request.getName(),
                request.getCpf(),
                request.getEmail(),
                request.getPhone()
        );
        var saved = customerPortOut.save(customer);
        // Cliente escolhe a própria senha no cadastro (validada em CustomerRequestDto);
        // ela vai direto para o Keycloak, único dono da credencial (SDD-024 F3).
        var idpUserId = identityProviderPortOut.createUser(IdentityUser.of(saved), request.getPassword());
        IdentityCompensation.deleteOnRollback(identityProviderPortOut, idpUserId);
        // Publicação dos e-mails só depois do commit: se o commit falhar, ninguém recebe
        // e-mail de uma conta que não existe.
        AfterCommit.run(() -> notificationPortOut.sendRegistrationConfirmation(saved.getEmail(), saved.getName()));
        // SDD-020: conta nasce com emailVerified=false (ver Customer); o link de
        // confirmação vai num e-mail separado do de boas-vindas. O token é gravado aqui,
        // dentro da transação; o envio do link é adiado para o commit (EmailVerificationService).
        emailVerificationPortIn.issueVerification(saved);
        log.info("Customer registered: id={}", saved.getId());
        return saved;
    }

    @Override
    public List<Customer> customerList() {
        return customerPortOut.findAll();
    }

    @Override
    public Page<Customer> customerListPage(Pageable pageable) {
        return customerPortOut.findAllPage(pageable);
    }

    @Override
    @Transactional
    public Customer updateCustomer(UUID id, CustomerUpdateDto dto) {
        var customer = findCustomer(id);
        customer.update(dto.getName(), dto.getEmail(), dto.getPhone());
        var saved = customerPortOut.save(customer);
        // Nome, e-mail e telefone também são claims do token: mantém o Keycloak igual.
        identityProviderPortOut.updateUser(IdentityUser.of(saved));
        log.info("Customer with ID: {} updated.", id);
        return saved;
    }

    @Override
    public Customer findCustomer(UUID id) {
        return customerPortOut.findById(id)
                .orElseThrow(() ->  new NotFoundException("Cliente", id));
    }

    @Override
    public Customer findCustomerByIdOrCpf(String idOrCpf) {
        return findByUuidIfValid(idOrCpf)
                .or(() -> customerPortOut.findByCpf(idOrCpf))
                .orElseThrow(() -> new NotFoundException("Cliente não encontrado(a)"));
    }

    private Optional<Customer> findByUuidIfValid(String idOrCpf) {
        try {
            return customerPortOut.findById(UUID.fromString(idOrCpf));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Customer deleteCustomer(UUID id) {
        var customer = findCustomer(id);
        customer.setEnabled(false);
        var saved = customerPortOut.save(customer);
        // Soft-delete local + usuário desabilitado no Keycloak: sem novo login nem refresh (CA4).
        identityProviderPortOut.setEnabled(id, false);
        log.info("Customer with ID {} deleted.", id);
        return saved;
    }

    @Override
    @Transactional
    public Customer reactivateCustomer(UUID id) {
        var customer = findCustomer(id);
        customer.setEnabled(true);
        var saved = customerPortOut.save(customer);
        // Falha no Keycloak propaga e desfaz a reativação local (mesmo comportamento da desativação).
        identityProviderPortOut.setEnabled(id, true);
        log.info("Customer with ID {} reactivated.", id);
        return saved;
    }

    @Override
    public Page<Pet> listPets(PetType type, Pageable pageable) {
        return customerPortOut.findPets(type, pageable);
    }

    @Override
    public Page<Pet> listPetsOfCustomer(UUID customerId, Pageable pageable) {
        var page = customerPortOut.findPetsByCustomerId(customerId, pageable);
        // Pagina vazia pode ser cliente sem pets ou cliente inexistente (404).
        if (page.getTotalElements() == 0 && !customerPortOut.existsById(customerId)) {
            throw new NotFoundException("Cliente", customerId);
        }
        return page;
    }

    @Override
    public Customer addPetToCustomer(UUID customerId, List<PetRequestDto> petRequests) {
        var customer = findCustomer(customerId);
        var pets = petRequests.stream().map(Pet::new).toList();
        customer.getPet().addAll(pets);
        log.info("{} pet(s) added to Customer with ID: {}", pets.size(), customerId);
        return customerPortOut.save(customer);
    }

    @Override
    public Customer updatePet(UUID customerId, UUID petId, PetRequestDto petRequest) {
        var customer = findCustomer(customerId);
        var pet = customer.getPet().stream()
                .filter(p -> p.getId().equals(petId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Pet", petId));
        pet.update(petRequest);
        log.info("Pet with ID: {} updated for Customer with ID: {}", petId, customerId);
        return customerPortOut.save(customer);
    }

    @Override
    public Customer removePetFromCustomer(UUID customerId, UUID petId) {
        var customer = findCustomer(customerId);
        var petToRemove = customer.getPet().stream()
                .filter(p -> p.getId().equals(petId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Pet", petId));
        customer.getPet().remove(petToRemove);
        log.info("Pet with ID: {} removed from Customer with ID: {}", petId, customerId);
        return customerPortOut.save(customer);
    }

    @Override
    public Optional<Customer> findByCpf(String cpf) {
        return customerPortOut.findByCpf(cpf);
    }

}
