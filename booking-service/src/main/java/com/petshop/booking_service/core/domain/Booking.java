package com.petshop.booking_service.core.domain;

import com.petshop.booking_service.core.domain.enums.PaymentMethod;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.commons.exception.BusinessRuleException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Booking {

    private UUID id;

    private UUID petId;

    private String ownerName;

    private String ownerCpf;

    private String ownerContact;

    private String ownerEmail;

    private ServiceDetails serviceDetails;

    private String observations;

    private LocalDateTime bookingDateTime;

    private LocalDateTime createdAt;

    private StatusBooking status;

    private String employeeName;

    private LocalDateTime updatedAt;

    public Booking(BookingRequestDto bookingRequest) {
        this.petId = UUID.fromString(bookingRequest.getPetId());
        this.ownerName = bookingRequest.getOwnerName();
        this.ownerCpf = bookingRequest.getOwnerCpf();
        this.ownerContact = bookingRequest.getOwnerContact();
        this.ownerEmail = bookingRequest.getOwnerEmail();
        this.serviceDetails = new ServiceDetails(bookingRequest.getServiceType());
        this.observations = bookingRequest.getObservations();
        this.bookingDateTime = bookingRequest.getBookingDateTime();
        this.status = StatusBooking.SCHEDULED;
    }

    public void completeBooking(BookingUpdateDto bookingUpdate) {
        if (this.status != StatusBooking.SCHEDULED) {
            throw new IllegalStateException("Only scheduled bookings can be finalized.");
        }
        // Só sobrescreve se vier preenchido, senão apaga observações já registradas ao só confirmar a conclusão.
        if (bookingUpdate.getObservations() != null) {
            this.observations = bookingUpdate.getObservations();
        }
        this.status = StatusBooking.COMPLETED;
    }

    public void confirmPayment(PaymentMethod method) {
        if (this.status == StatusBooking.CANCELED) {
            throw new BusinessRuleException("Cannot confirm payment for a canceled booking");
        }
        this.serviceDetails.confirmPayment(method);
    }

    public void update(BookingUpdateDto bookingUpdate) {

        if (bookingUpdate.getServiceType() != null) {
            // new ServiceDetails(ServiceType) reseta o pagamento pra PENDING/null; preserva o que já havia pra não apagar uma confirmação de pagamento existente.
            var previousPaymentStatus = this.serviceDetails.getPaymentStatus();
            var previousPaymentMethod = this.serviceDetails.getPaymentMethod();
            this.serviceDetails = new ServiceDetails(bookingUpdate.getServiceType());
            this.serviceDetails.setPaymentStatus(previousPaymentStatus);
            this.serviceDetails.setPaymentMethod(previousPaymentMethod);
        }
        if (bookingUpdate.getObservations() != null) {
            this.observations = bookingUpdate.getObservations();
        }
        if (bookingUpdate.getBookingDateTime() != null) {
            this.bookingDateTime = bookingUpdate.getBookingDateTime();
        }
    }
}
