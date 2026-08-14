package com.petshop.customermanagement.infrastructure.persistence.postgresql.entity;

import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class PetEmbeddable {

    private UUID id;

    private String petName;

    @Enumerated(EnumType.STRING)
    @Column(name = "pet_type")
    private PetType petType;

    private String petBreed;

    @Enumerated(EnumType.STRING)
    @Column(name = "pet_size")
    private SizeCategory petSize;

    private Double weightInKg;

    private String petHealthIssues;
}