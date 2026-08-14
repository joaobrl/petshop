package com.petshop.booking_service.infrastructure.persistence.postgresql.entity;

import com.petshop.booking_service.core.domain.enums.PaymentMethod;
import com.petshop.booking_service.core.domain.enums.PaymentStatus;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class ServiceDetailsEmbeddable {

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type")
    private ServiceType serviceType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    private Integer durationInMinutes;

    // Sem nullable = false: ddl-auto é "update" (sem Flyway/Liquibase), então
    // uma constraint NOT NULL sem default quebraria o ALTER TABLE em bancos
    // com linhas existentes. A garantia de "nasce sempre PENDING" fica na
    // camada de domínio (ServiceDetails(ServiceType)).
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;
}
