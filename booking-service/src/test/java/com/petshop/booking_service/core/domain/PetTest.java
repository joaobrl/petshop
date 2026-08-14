package com.petshop.booking_service.core.domain;

import com.petshop.booking_service.core.domain.enums.PetType;
import com.petshop.booking_service.core.domain.enums.SizeCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PetTest {

    @Test
    void allArgsConstructorAndGetters() {
        var pet = new Pet("Rex", PetType.DOG, "Labrador", SizeCategory.LARGE, 30.5, "Nenhum",
                "Ciclana", "12345678900", "11999990000");

        assertThat(pet.getPetName()).isEqualTo("Rex");
        assertThat(pet.getPetType()).isEqualTo(PetType.DOG);
        assertThat(pet.getPetBreed()).isEqualTo("Labrador");
        assertThat(pet.getPetSize()).isEqualTo(SizeCategory.LARGE);
        assertThat(pet.getWeightInKg()).isEqualTo(30.5);
        assertThat(pet.getPetHealthIssues()).isEqualTo("Nenhum");
        assertThat(pet.getOwnerName()).isEqualTo("Ciclana");
        assertThat(pet.getOwnerCpf()).isEqualTo("12345678900");
        assertThat(pet.getOwnerContact()).isEqualTo("11999990000");
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        var pet = new Pet();
        pet.setPetName("Mimi");
        pet.setPetType(PetType.CAT);
        pet.setPetBreed("Siames");
        pet.setPetSize(SizeCategory.SMALL);
        pet.setWeightInKg(4.2);
        pet.setPetHealthIssues(null);
        pet.setOwnerName("Beltrano");
        pet.setOwnerCpf("11122233344");
        pet.setOwnerContact("11988887777");

        assertThat(pet.getPetName()).isEqualTo("Mimi");
        assertThat(pet.getPetType()).isEqualTo(PetType.CAT);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new Pet("Rex", PetType.DOG, "Labrador", SizeCategory.LARGE, 30.5, "Nenhum",
                "Ciclana", "12345678900", "11999990000");
        var b = new Pet("Rex", PetType.DOG, "Labrador", SizeCategory.LARGE, 30.5, "Nenhum",
                "Ciclana", "12345678900", "11999990000");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("petName");
    }
}
