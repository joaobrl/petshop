package com.petshop.customermanagement.core.domain;

import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import com.petshop.customermanagement.core.port.in.dto.PetRequestDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pet {

    private UUID id;
    private String petName;
    private PetType petType;
    private String petBreed;
    private SizeCategory petSize;
    private Double weightInKg;
    private String petHealthIssues;

    public Pet(PetRequestDto petRequest) {
        this.id = UUID.randomUUID();
        this.petName = petRequest.getPetName();
        this.petType = petRequest.getPetType();
        this.petBreed = petRequest.getPetBreed();
        this.petSize = petRequest.getPetSize();
        this.weightInKg = petRequest.getWeightInKg();
        this.petHealthIssues = petRequest.getPetHealthIssues();
    }

    /**
     * Atualização parcial — reaproveita o mesmo PetRequestDto do cadastro
     * (nenhum campo é obrigatório), só troca o que vier preenchido.
     */
    public void update(PetRequestDto petRequest) {
        if (petRequest.getPetName() != null) this.petName = petRequest.getPetName();
        if (petRequest.getPetType() != null) this.petType = petRequest.getPetType();
        if (petRequest.getPetBreed() != null) this.petBreed = petRequest.getPetBreed();
        if (petRequest.getPetSize() != null) this.petSize = petRequest.getPetSize();
        if (petRequest.getWeightInKg() != null) this.weightInKg = petRequest.getWeightInKg();
        if (petRequest.getPetHealthIssues() != null) this.petHealthIssues = petRequest.getPetHealthIssues();
    }
}
