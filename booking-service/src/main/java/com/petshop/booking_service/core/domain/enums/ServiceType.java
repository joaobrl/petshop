package com.petshop.booking_service.core.domain.enums;

public enum ServiceType {
    TOSAGEM,
    BANHO,
    TOSA_E_BANHO,
    CONSULTA_VETERINARIA;

    /**
     * Cargo no staff-service responsável por esse serviço, usado pra pedir a escala via Feign. Mantido como
     * String (não um enum compartilhado) porque os dois são bounded contexts separados.
     */
    public String requiredStaffRole() {
        return this == CONSULTA_VETERINARIA ? "VETERINARIAN" : "GROOMER";
    }
}
