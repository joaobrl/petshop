package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.enums.PetType;
import com.petshop.booking_service.core.domain.enums.SizeCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PetResponseDtoTest {

    @Test
    void settersAndGettersRoundTrip() {
        var dto = new PetResponseDto();
        dto.setPetName("Rex");
        dto.setPetType(PetType.DOG);
        dto.setPetBreed("Labrador");
        dto.setPetSize(SizeCategory.LARGE);
        dto.setWeightInKg(30.5);
        dto.setPetHealthIssues("Nenhum");
        dto.setOwnerName("Ciclana");

        assertThat(dto.getPetName()).isEqualTo("Rex");
        assertThat(dto.getPetType()).isEqualTo(PetType.DOG);
        assertThat(dto.getPetBreed()).isEqualTo("Labrador");
        assertThat(dto.getPetSize()).isEqualTo(SizeCategory.LARGE);
        assertThat(dto.getWeightInKg()).isEqualTo(30.5);
        assertThat(dto.getPetHealthIssues()).isEqualTo("Nenhum");
        assertThat(dto.getOwnerName()).isEqualTo("Ciclana");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new PetResponseDto();
        a.setPetName("Rex");
        var b = new PetResponseDto();
        b.setPetName("Rex");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
