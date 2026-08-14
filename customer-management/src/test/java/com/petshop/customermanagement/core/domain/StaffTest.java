package com.petshop.customermanagement.core.domain;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffTest {

    private StaffRequestDto sampleRequest() {
        var request = new StaffRequestDto();
        request.setName("João");
        request.setCpf("12345678900");
        request.setEmail("joao@petshop.com");
        request.setPhone("11999999999");
        request.setRole(Role.RECEPTIONIST);
        return request;
    }

    @Nested
    class ConstructorFromRequestDto {

        @Test
        void copiesFieldsAndSetsDefaults() {
            var staff = new Staff(sampleRequest());

            assertThat(staff.getId()).isNotNull();
            assertThat(staff.getName()).isEqualTo("João");
            assertThat(staff.getCpf()).isEqualTo("12345678900");
            assertThat(staff.getEmail()).isEqualTo("joao@petshop.com");
            assertThat(staff.getPhone()).isEqualTo("11999999999");
            assertThat(staff.getRole()).isEqualTo(Role.RECEPTIONIST);
            assertThat(staff.getEnabled()).isTrue();
            assertThat(staff.getMustChangePassword()).isTrue();
            assertThat(staff.getPasswordHash()).isNull();
        }

        @Test
        void generatesDifferentIdsForDifferentInstances() {
            var staffA = new Staff(sampleRequest());
            var staffB = new Staff(sampleRequest());

            assertThat(staffA.getId()).isNotEqualTo(staffB.getId());
        }
    }

    @Nested
    class LegacyConstructor {

        @Test
        void setsAllSevenFields() {
            var id = UUID.randomUUID();

            var staff = new Staff(id, "Maria", "98765432100", "maria@petshop.com", "11888888888", true, Role.ADMIN);

            assertThat(staff.getId()).isEqualTo(id);
            assertThat(staff.getName()).isEqualTo("Maria");
            assertThat(staff.getCpf()).isEqualTo("98765432100");
            assertThat(staff.getEmail()).isEqualTo("maria@petshop.com");
            assertThat(staff.getPhone()).isEqualTo("11888888888");
            assertThat(staff.getEnabled()).isTrue();
            assertThat(staff.getRole()).isEqualTo(Role.ADMIN);
            assertThat(staff.getPasswordHash()).isNull();
            assertThat(staff.getMustChangePassword()).isNull();
        }
    }

    @Nested
    class Update {

        @Test
        void updatesOnlyNonNullFields() {
            var staff = new Staff(sampleRequest());

            var partialUpdate = new StaffRequestDto();
            partialUpdate.setName("João Atualizado");

            staff.update(partialUpdate);

            assertThat(staff.getName()).isEqualTo("João Atualizado");
            assertThat(staff.getEmail()).isEqualTo("joao@petshop.com");
            assertThat(staff.getPhone()).isEqualTo("11999999999");
            assertThat(staff.getRole()).isEqualTo(Role.RECEPTIONIST);
        }

        @Test
        void neverUpdatesCpfEvenIfPresentInRequest() {
            var staff = new Staff(sampleRequest());

            var request = sampleRequest();
            request.setCpf("00000000000");

            staff.update(request);

            assertThat(staff.getCpf()).isEqualTo("12345678900");
        }

        @Test
        void updatesAllMutableFieldsWhenAllProvided() {
            var staff = new Staff(sampleRequest());

            var request = new StaffRequestDto();
            request.setName("Novo Nome");
            request.setEmail("novo@petshop.com");
            request.setPhone("11777777777");
            request.setRole(Role.ADMIN);

            staff.update(request);

            assertThat(staff.getName()).isEqualTo("Novo Nome");
            assertThat(staff.getEmail()).isEqualTo("novo@petshop.com");
            assertThat(staff.getPhone()).isEqualTo("11777777777");
            assertThat(staff.getRole()).isEqualTo(Role.ADMIN);
        }
    }

    @Nested
    class EqualsAndHashCode {

        @Test
        void staffWithSameIdAreEqualEvenIfOtherFieldsDiffer() {
            var id = UUID.randomUUID();
            var staffA = new Staff(id, "João", "12345678900", "joao@petshop.com", "11999999999", true, Role.RECEPTIONIST);
            var staffB = new Staff(id, "Outro Nome", "00000000000", "outro@petshop.com", "11000000000", false, Role.ADMIN);

            assertThat(staffA).isEqualTo(staffB);
            assertThat(staffA.hashCode()).isEqualTo(staffB.hashCode());
        }

        @Test
        void staffWithDifferentIdsAreNotEqual() {
            var staffA = new Staff(sampleRequest());
            var staffB = new Staff(sampleRequest());

            assertThat(staffA).isNotEqualTo(staffB);
        }
    }
}
