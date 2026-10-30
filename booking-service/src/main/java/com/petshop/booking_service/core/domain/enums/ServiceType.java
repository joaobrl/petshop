package com.petshop.booking_service.core.domain.enums;

public enum ServiceType {
    TOSAGEM,
    BANHO,
    TOSA_E_BANHO,
    CONSULTA_VETERINARIA;

    public String requiredStaffRole() {
        return this == CONSULTA_VETERINARIA ? "VETERINARIAN" : "GROOMER";
    }
}
