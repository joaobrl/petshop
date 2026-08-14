package com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "weekend_allocation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeekendAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private LocalDate allocationDate;

    private UUID staffId;

    private String staffName;
}
