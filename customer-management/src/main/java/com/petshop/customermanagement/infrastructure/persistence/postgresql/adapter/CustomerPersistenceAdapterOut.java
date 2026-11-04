package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper.CustomerMapper;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerPersistenceAdapterOut implements CustomerPortOut {

    private final CustomerRepository customerRepository;
    private final CustomerMapper mapper;

    // readOnly aqui (e não no use case): o mapper toca a coleção lazy de pets,
    // e fora de requisição HTTP (ex.: consumer Kafka) não há open-in-view
    // segurando a sessão do Hibernate.
    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByCpf(String cpf) {
        return customerRepository.findByCpf(cpf)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findByEmail(String email) {
        return customerRepository.findByEmail(email)
                .map(mapper::toDomain);
    }

    @Override
    public Customer save(Customer customer) {
        CustomerEntity entity = mapper.toEntity(customer);
        if (customerRepository.existsById(entity.getId())) {
            entity.setNew(false);
        }
        return mapper.toDomain(customerRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return mapper.toDomainList(customerRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Customer> findAllPage(Pageable pageable) {
        return customerRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(UUID id) {
        return customerRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return customerRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Pet> findPets(PetType type, Pageable pageable) {
        return customerRepository.findPets(type, petPageable(pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Pet> findPetsByCustomerId(UUID customerId, Pageable pageable) {
        return customerRepository.findPetsByCustomerId(customerId, petPageable(pageable));
    }

    // Propriedades de ordenacao aceitas (campos de Pet) -> atributo no JPQL.
    // "name"/"type" sao apelidos. Qualquer outra gera IllegalArgumentException
    // em vez de virar JPQL arbitrario. id entra sempre como desempate para a
    // paginacao ser estavel.
    private static final Map<String, String> PET_SORT_FIELDS = Map.ofEntries(
            Map.entry("id", "p.id"),
            Map.entry("name", "p.petName"),
            Map.entry("petName", "p.petName"),
            Map.entry("type", "p.petType"),
            Map.entry("petType", "p.petType"),
            Map.entry("petBreed", "p.petBreed"),
            Map.entry("petSize", "p.petSize"),
            Map.entry("weightInKg", "p.weightInKg"),
            Map.entry("petHealthIssues", "p.petHealthIssues"));

    private Pageable petPageable(Pageable pageable) {
        Sort requested = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by("name");
        Sort sort = Sort.by(requested.stream()
                .map(order -> {
                    var field = PET_SORT_FIELDS.get(order.getProperty());
                    if (field == null) {
                        throw new IllegalArgumentException("Campo de ordenacao invalido: " + order.getProperty());
                    }
                    return order.withProperty(field);
                })
                .toList())
                .and(Sort.by("p.id"));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
