package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.StaffEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper.StaffMapper;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StaffPersistenceAdapterOut implements StaffPortOut {

    private final StaffRepository staffRepository;
    private final StaffMapper mapper;

    @Override
    public Optional<Staff> findByCpf(String cpf) {
        return staffRepository.findByCpf(cpf).map(mapper::toDomain);
    }

    @Override
    public Optional<Staff> findByEmail(String email) {
        return staffRepository.findByEmail(email).map(mapper::toDomain);
    }

    @Override
    public Optional<Staff> findById(UUID id) {
        return staffRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Staff> findAll() {
        return mapper.toDomainList(staffRepository.findAll());
    }

    @Override
    public Staff save(Staff staff) {
        StaffEntity entity = mapper.toEntity(staff);
        // Mesmo cuidado do CustomerPersistenceAdapterOut: mapper.toEntity()
        // sempre monta uma entidade nova (isNew volta pro default true),
        // então update precisa avisar explicitamente que o registro já
        // existe, senão o Spring Data tenta persist() num id duplicado.
        if (staffRepository.existsById(entity.getId())) {
            entity.setNew(false);
        }
        return mapper.toDomain(staffRepository.save(entity));
    }
}
