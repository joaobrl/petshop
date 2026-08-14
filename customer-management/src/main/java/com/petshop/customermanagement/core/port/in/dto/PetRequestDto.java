package com.petshop.customermanagement.core.port.in.dto;

import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import lombok.Data;

@Data
public class PetRequestDto {

    private String petName;
    private PetType petType;
    private String petBreed;
    private SizeCategory petSize;
    private Double weightInKg;
    private String petHealthIssues;
}
