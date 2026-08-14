package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.enums.PetType;
import com.petshop.booking_service.core.domain.enums.SizeCategory;
import lombok.Data;

@Data
public class PetResponseDto {

    private String petName;

    private PetType petType;

    private String petBreed;

    private SizeCategory petSize;

    private Double weightInKg;

    private String petHealthIssues;

    private String ownerName;

}
