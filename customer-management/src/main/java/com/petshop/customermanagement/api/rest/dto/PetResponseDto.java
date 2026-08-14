package com.petshop.customermanagement.api.rest.dto;

import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import lombok.Data;

import java.util.UUID;

@Data
public class PetResponseDto {

    private UUID id;
    private String petName;
    private PetType petType;
    private String petBreed;
    private SizeCategory petSize;
    private Double weightInKg;
    private String petHealthIssues;

    public PetResponseDto(Pet pet) {
        this.id = pet.getId();
        this.petName = pet.getPetName();
        this.petType = pet.getPetType();
        this.petBreed = pet.getPetBreed();
        this.petSize = pet.getPetSize();
        this.weightInKg = pet.getWeightInKg();
        this.petHealthIssues = pet.getPetHealthIssues();
    }
}

