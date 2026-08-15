package com.petshop.customermanagement.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.StaffPortIn;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffControllerImplTest {

    @Mock
    private StaffPortIn portIn;

    private StaffControllerImpl staffController;

    @BeforeEach
    void setUp() {
        staffController = new StaffControllerImpl(portIn);
    }

    private Staff sampleStaff(UUID id) {
        return new Staff(id, "João", "12345678900", "joao@petshop.com", "11999999999", true, Role.RECEPTIONIST);
    }

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
    class CreateStaff {

        @Test
        void returnsCreatedWithLocationHeader() {
            var id = UUID.randomUUID();
            var staff = sampleStaff(id);
            var request = sampleRequest();
            when(portIn.createStaff(request)).thenReturn(staff);

            var result = staffController.createStaff(request, UriComponentsBuilder.fromUriString("http://localhost"));

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(result.getHeaders().getLocation()).hasToString("http://localhost/api/v1/staff/" + id + "/find");
            assertThat(result.getBody()).isNotNull();
            assertThat(result.getBody().getId()).isEqualTo(id);
            assertThat(result.getBody().getName()).isEqualTo("João");
        }
    }

    @Nested
    class GetAllStaff {

        private final PageRequest pageable = PageRequest.of(0, 20);

        @Test
        void returnsMappedList() {
            var staffA = sampleStaff(UUID.randomUUID());
            var staffB = sampleStaff(UUID.randomUUID());
            when(portIn.getAllStaff(pageable)).thenReturn(new PageImpl<>(List.of(staffA, staffB)));

            var result = staffController.getAllStaff(pageable);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().content()).hasSize(2);
        }

        @Test
        void returnsEmptyListWhenNoneRegistered() {
            when(portIn.getAllStaff(pageable)).thenReturn(new PageImpl<>(List.of()));

            var result = staffController.getAllStaff(pageable);

            assertThat(result.getBody().content()).isEmpty();
        }
    }

    @Nested
    class GetStaffById {

        @Test
        void returnsStaffMappedToDto() {
            var id = UUID.randomUUID();
            when(portIn.getStaffByIdOrCpf(id.toString())).thenReturn(sampleStaff(id));

            var result = staffController.getStaffById(id.toString());

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().getId()).isEqualTo(id);
        }
    }

    @Nested
    class UpdateStaff {

        @Test
        void returnsUpdatedStaffMappedToDto() {
            var id = UUID.randomUUID();
            var request = sampleRequest();
            when(portIn.updateStaff(id, request)).thenReturn(sampleStaff(id));

            var result = staffController.updateStaff(id, request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().getId()).isEqualTo(id);
        }
    }

    @Nested
    class DeleteStaff {

        @Test
        void returnsNoContentAndDelegates() {
            var id = UUID.randomUUID();
            when(portIn.deleteStaff(id)).thenReturn(sampleStaff(id));

            var result = staffController.deleteStaff(id);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @Nested
    class ReactivateStaff {

        @Test
        void returnsReactivatedStaffMappedToDto() {
            var id = UUID.randomUUID();
            when(portIn.reactivateStaff(id)).thenReturn(sampleStaff(id));

            var result = staffController.reactivateStaff(id);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody().getId()).isEqualTo(id);
        }
    }
}
