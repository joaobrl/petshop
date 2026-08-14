package com.petshop.booking_service.core.port.in.dto;

import com.petshop.booking_service.core.domain.enums.PetType;
import com.petshop.booking_service.core.domain.enums.SizeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PetRequestDto {

    @NotBlank
    private String petName;
    @NotNull
    private PetType petType;
    @NotBlank
    private String petBreed;
    @NotNull
    private SizeCategory petSize;

    private Double weightInKg;

    private String petHealthIssues;
    @NotBlank
    private String ownerName;
    @NotBlank
    private String ownerCpf;
    @NotBlank
    private String ownerContact;
}
