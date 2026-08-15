package com.petshop.customermanagement.core.application.service;

import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.port.in.CustomerPortIn;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.in.dto.CustomerUpdateDto;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService implements CustomerPortIn {

    private final CustomerPortOut customerPortOut;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPortOut notificationPortOut;

    @Override
    public Customer registerCustomer (CustomerRequestDto request) {
        customerPortOut.findByCpf(request.getCpf())
                .ifPresent(c -> {
                    throw new ConflictException("CPF already exists: " + request.getCpf());
                });

        var customer = new Customer(
                request.getName(),
                request.getCpf(),
                request.getEmail(),
                request.getPhone()
        );
        // Cliente escolhe a própria senha no cadastro (validado em
        // CustomerRequestDto — password/confirmPassword) — diferente do
        // Staff, que ainda nasce com senha = CPF (troca no primeiro login,
        // ver StaffService), porque quem cria o funcionário é o ADMIN, não
        // a própria pessoa.
        customer.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        customer.setMustChangePassword(false);
        var saved = customerPortOut.save(customer);
        notificationPortOut.sendRegistrationConfirmation(saved.getEmail(), saved.getName());
        log.info("Customer registered with CPF: {}", request.getCpf());
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
    public Customer updateCustomer(UUID id, CustomerUpdateDto dto) {
        var customer = findCustomer(id);
        customer.update(dto.getName(), dto.getEmail(), dto.getPhone());
        log.info("Customer with ID: {} updated.", id);
        return customerPortOut.save(customer);
    }

    @Override
    public Customer findCustomer(UUID id) {
        return customerPortOut.findById(id)
                .orElseThrow(() ->  new NotFoundException("Customer", id));
    }

    @Override
    public Customer findCustomerByIdOrCpf(String idOrCpf) {
        return findByUuidIfValid(idOrCpf)
                .or(() -> customerPortOut.findByCpf(idOrCpf))
                .orElseThrow(() -> new NotFoundException("Customer", idOrCpf));
    }

    private Optional<Customer> findByUuidIfValid(String idOrCpf) {
        try {
            return customerPortOut.findById(UUID.fromString(idOrCpf));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    @Override
    public Customer deleteCustomer(UUID id) {
        var customer = findCustomer(id);
        customer.setEnabled(false);
        log.info("Customer with ID {} deleted.", id);
        return customerPortOut.save(customer);
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
