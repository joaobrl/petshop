package com.petshop.booking_service.infrastructure.persistence.postgresql.entity;

import com.petshop.booking_service.core.domain.enums.StatusBooking;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pet_bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID petId;

    private String ownerName;

    private String ownerCpf;

    private String ownerContact;

    private String ownerEmail;

    @Embedded
    private ServiceDetailsEmbeddable serviceDetails;

    @Column(length = 500)
    private String observations;

    @Column(nullable = false)
    private LocalDateTime bookingDateTime;

    @CreationTimestamp
    @Column(nullable = false)
    private LocalDateTime createdAt;

    private StatusBooking status;

    private String employeeName;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
