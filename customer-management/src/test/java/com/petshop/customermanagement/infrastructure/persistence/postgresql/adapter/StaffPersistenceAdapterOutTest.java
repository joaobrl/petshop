package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.StaffEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper.StaffMapper;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffPersistenceAdapterOutTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private StaffMapper mapper;

    private StaffPersistenceAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new StaffPersistenceAdapterOut(staffRepository, mapper);
    }

    private Staff sampleDomain() {
        return new Staff(UUID.randomUUID(), "João", "12345678900", "joao@petshop.com", "11999999999", true, Role.RECEPTIONIST);
    }

    @Test
    void findByCpfReturnsMappedStaffWhenEntityExists() {
        var entity = new StaffEntity();
        var domain = sampleDomain();
        when(staffRepository.findByCpf("12345678900")).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.findByCpf("12345678900")).contains(domain);
    }

    @Test
    void findByCpfReturnsEmptyWhenEntityDoesNotExist() {
        when(staffRepository.findByCpf("00000000000")).thenReturn(Optional.empty());

        assertThat(adapter.findByCpf("00000000000")).isEmpty();
    }

    @Test
    void findByEmailReturnsMappedStaffWhenEntityExists() {
        var entity = new StaffEntity();
        var domain = sampleDomain();
        when(staffRepository.findByEmail("joao@petshop.com")).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.findByEmail("joao@petshop.com")).contains(domain);
    }

    @Test
    void findByEmailReturnsEmptyWhenEntityDoesNotExist() {
        when(staffRepository.findByEmail("ninguem@petshop.com")).thenReturn(Optional.empty());

        assertThat(adapter.findByEmail("ninguem@petshop.com")).isEmpty();
    }

    @Test
    void findByIdReturnsMappedStaffWhenEntityExists() {
        var id = UUID.randomUUID();
        var entity = new StaffEntity();
        var domain = sampleDomain();
        when(staffRepository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.findById(id)).contains(domain);
    }

    @Test
    void findByIdReturnsEmptyWhenEntityDoesNotExist() {
        var id = UUID.randomUUID();
        when(staffRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findAllReturnsMappedList() {
        var entities = List.of(new StaffEntity(), new StaffEntity());
        var domains = List.of(sampleDomain(), sampleDomain());
        when(staffRepository.findAll()).thenReturn(entities);
        when(mapper.toDomainList(entities)).thenReturn(domains);

        assertThat(adapter.findAll()).isEqualTo(domains);
    }

    @Test
    void findAllReturnsEmptyWhenNoStaff() {
        when(staffRepository.findAll()).thenReturn(List.of());
        when(mapper.toDomainList(List.of())).thenReturn(List.of());

        assertThat(adapter.findAll()).isEmpty();
    }

    // O adapter mexe direto na flag isNew (via existsById/entity.setNew)
    // pra evitar que um update tente persist() num id que já existe (ver
    // comentário no próprio StaffPersistenceAdapterOut) — por isso usamos
    // uma StaffEntity real aqui em vez de mock, senão não dá pra observar
    // o efeito do setNew(false).
    @Nested
    class SaveNewVsUpdateFlag {

        @Test
        void marksEntityAsNotNewWhenIdAlreadyExists() {
            var domain = sampleDomain();
            var entity = new StaffEntity();
            entity.setId(domain.getId());
            var savedEntity = new StaffEntity();
            var savedDomain = sampleDomain();
            when(mapper.toEntity(domain)).thenReturn(entity);
            when(staffRepository.existsById(entity.getId())).thenReturn(true);
            when(staffRepository.save(entity)).thenReturn(savedEntity);
            when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

            var result = adapter.save(domain);

            assertThat(entity.isNew()).isFalse();
            assertThat(result).isEqualTo(savedDomain);
        }

        @Test
        void leavesEntityAsNewWhenIdDoesNotExistYet() {
            var domain = sampleDomain();
            var entity = new StaffEntity();
            entity.setId(domain.getId());
            var savedEntity = new StaffEntity();
            var savedDomain = sampleDomain();
            when(mapper.toEntity(domain)).thenReturn(entity);
            when(staffRepository.existsById(entity.getId())).thenReturn(false);
            when(staffRepository.save(entity)).thenReturn(savedEntity);
            when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

            var result = adapter.save(domain);

            assertThat(entity.isNew()).isTrue();
            assertThat(result).isEqualTo(savedDomain);
        }
    }
}
