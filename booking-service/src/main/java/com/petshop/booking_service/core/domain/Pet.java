package com.petshop.booking_service.core.domain;

import com.petshop.booking_service.core.domain.enums.PetType;
import com.petshop.booking_service.core.domain.enums.SizeCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Pet {

    private String petName;
    private PetType petType;
    private String petBreed;
    private SizeCategory petSize;
    private Double weightInKg;
    private String petHealthIssues;
    private String ownerName;
    private String ownerCpf;
    private String ownerContact;
}
