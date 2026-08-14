package com.petshop.customermanagement.core.domain;

import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PetTest {

    private PetRequestDto fullRequest() {
        var dto = new PetRequestDto();
        dto.setPetName("Rex");
        dto.setPetType(PetType.DOG);
        dto.setPetBreed("Labrador");
        dto.setPetSize(SizeCategory.LARGE);
        dto.setWeightInKg(30.5);
        dto.setPetHealthIssues("Nenhum");
        return dto;
    }

    @Nested
    class Construction {

        @Test
        void requestConstructorGeneratesIdAndCopiesAllFields() {
            var pet = new Pet(fullRequest());

            assertThat(pet.getId()).isNotNull();
            assertThat(pet.getPetName()).isEqualTo("Rex");
            assertThat(pet.getPetType()).isEqualTo(PetType.DOG);
            assertThat(pet.getPetBreed()).isEqualTo("Labrador");
            assertThat(pet.getPetSize()).isEqualTo(SizeCategory.LARGE);
            assertThat(pet.getWeightInKg()).isEqualTo(30.5);
            assertThat(pet.getPetHealthIssues()).isEqualTo("Nenhum");
        }

        @Test
        void requestConstructorGeneratesDifferentIdsPerInstance() {
            var pet1 = new Pet(fullRequest());
            var pet2 = new Pet(fullRequest());

            assertThat(pet1.getId()).isNotEqualTo(pet2.getId());
        }

        @Test
        void noArgsConstructorLeavesFieldsNull() {
            var pet = new Pet();

            assertThat(pet.getId()).isNull();
            assertThat(pet.getPetName()).isNull();
        }

        @Test
        void allArgsConstructorSetsEveryField() {
            var id = UUID.randomUUID();
            var pet = new Pet(id, "Mimi", PetType.CAT, "SRD", SizeCategory.SMALL, 4.2, "Alergia");

            assertThat(pet.getId()).isEqualTo(id);
            assertThat(pet.getPetName()).isEqualTo("Mimi");
            assertThat(pet.getPetType()).isEqualTo(PetType.CAT);
            assertThat(pet.getPetBreed()).isEqualTo("SRD");
            assertThat(pet.getPetSize()).isEqualTo(SizeCategory.SMALL);
            assertThat(pet.getWeightInKg()).isEqualTo(4.2);
            assertThat(pet.getPetHealthIssues()).isEqualTo("Alergia");
        }
    }

    @Nested
    class Update {

        @Test
        void updatesAllFieldsWhenAllPresent() {
            var pet = new Pet(fullRequest());

            var newData = new PetRequestDto();
            newData.setPetName("Rex II");
            newData.setPetType(PetType.CAT);
            newData.setPetBreed("Persa");
            newData.setPetSize(SizeCategory.MEDIUM);
            newData.setWeightInKg(12.0);
            newData.setPetHealthIssues("Diabetes");

            pet.update(newData);

            assertThat(pet.getPetName()).isEqualTo("Rex II");
            assertThat(pet.getPetType()).isEqualTo(PetType.CAT);
            assertThat(pet.getPetBreed()).isEqualTo("Persa");
            assertThat(pet.getPetSize()).isEqualTo(SizeCategory.MEDIUM);
            assertThat(pet.getWeightInKg()).isEqualTo(12.0);
            assertThat(pet.getPetHealthIssues()).isEqualTo("Diabetes");
        }

        @Test
        void keepsAllFieldsWhenUpdateDtoIsEmpty() {
            var pet = new Pet(fullRequest());

            pet.update(new PetRequestDto());

            assertThat(pet.getPetName()).isEqualTo("Rex");
            assertThat(pet.getPetType()).isEqualTo(PetType.DOG);
            assertThat(pet.getPetBreed()).isEqualTo("Labrador");
            assertThat(pet.getPetSize()).isEqualTo(SizeCategory.LARGE);
            assertThat(pet.getWeightInKg()).isEqualTo(30.5);
            assertThat(pet.getPetHealthIssues()).isEqualTo("Nenhum");
        }

        @Test
        void updatesOnlyPetNameWhenOnlyPetNameProvided() {
            var pet = new Pet(fullRequest());
            var partial = new PetRequestDto();
            partial.setPetName("Only Name Changed");

            pet.update(partial);

            assertThat(pet.getPetName()).isEqualTo("Only Name Changed");
            assertThat(pet.getPetType()).isEqualTo(PetType.DOG);
            assertThat(pet.getPetBreed()).isEqualTo("Labrador");
        }

        @Test
        void updatesOnlyWeightWhenOnlyWeightProvided() {
            var pet = new Pet(fullRequest());
            var partial = new PetRequestDto();
            partial.setWeightInKg(99.9);

            pet.update(partial);

            assertThat(pet.getWeightInKg()).isEqualTo(99.9);
            assertThat(pet.getPetName()).isEqualTo("Rex");
        }

        @Test
        void updatesOnlyHealthIssuesWhenOnlyHealthIssuesProvided() {
            var pet = new Pet(fullRequest());
            var partial = new PetRequestDto();
            partial.setPetHealthIssues("Nova condicao");

            pet.update(partial);

            assertThat(pet.getPetHealthIssues()).isEqualTo("Nova condicao");
            assertThat(pet.getPetName()).isEqualTo("Rex");
        }

        @Test
        void updatesOnlyBreedAndSizeWhenOnlyThoseProvided() {
            var pet = new Pet(fullRequest());
            var partial = new PetRequestDto();
            partial.setPetBreed("Vira-lata");
            partial.setPetSize(SizeCategory.SMALL);

            pet.update(partial);

            assertThat(pet.getPetBreed()).isEqualTo("Vira-lata");
            assertThat(pet.getPetSize()).isEqualTo(SizeCategory.SMALL);
            assertThat(pet.getPetName()).isEqualTo("Rex");
            assertThat(pet.getWeightInKg()).isEqualTo(30.5);
        }
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var id = UUID.randomUUID();
        var pet1 = new Pet(id, "Rex", PetType.DOG, "Labrador", SizeCategory.LARGE, 30.5, "Nenhum");
        var pet2 = new Pet(id, "Rex", PetType.DOG, "Labrador", SizeCategory.LARGE, 30.5, "Nenhum");
        var pet3 = new Pet(id, "Different", PetType.DOG, "Labrador", SizeCategory.LARGE, 30.5, "Nenhum");

        assertThat(pet1).isEqualTo(pet2);
        assertThat(pet1.hashCode()).isEqualTo(pet2.hashCode());
        assertThat(pet1).isNotEqualTo(pet3);
        assertThat(pet1).isNotEqualTo(null);
        assertThat(pet1).isNotEqualTo("not a pet");
    }

    @Test
    void toStringContainsPetName() {
        var pet = new Pet(fullRequest());

        assertThat(pet.toString()).contains("Rex");
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var pet = new Pet();
        var id = UUID.randomUUID();

        pet.setId(id);
        pet.setPetName("Name");
        pet.setPetType(PetType.CAT);
        pet.setPetBreed("Breed");
        pet.setPetSize(SizeCategory.MEDIUM);
        pet.setWeightInKg(5.0);
        pet.setPetHealthIssues("None");

        assertThat(pet.getId()).isEqualTo(id);
        assertThat(pet.getPetName()).isEqualTo("Name");
        assertThat(pet.getPetType()).isEqualTo(PetType.CAT);
        assertThat(pet.getPetBreed()).isEqualTo("Breed");
        assertThat(pet.getPetSize()).isEqualTo(SizeCategory.MEDIUM);
        assertThat(pet.getWeightInKg()).isEqualTo(5.0);
        assertThat(pet.getPetHealthIssues()).isEqualTo("None");
    }
}
