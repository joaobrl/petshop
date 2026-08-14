package com.petshop.booking_service.core.port.out;

import com.petshop.booking_service.core.domain.StaffAssignment;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomerManagementPortOut {

    /**
     * Funcionários escalados pra atender nesse dia/horário, filtrando pelo
     * cargo exigido pelo tipo de serviço (GROOMER pra banho/tosa,
     * VETERINARIAN pra consulta veterinária — ver
     * ServiceType.requiredStaffRole()). Pode vir vazio se a loja estiver
     * fechada nesse horário, se não houver ninguém daquele cargo escalado,
     * ou se o staff-service estiver fora do ar.
     */
    List<StaffAssignment> getScheduledStaff(LocalDateTime dateTime, String role);
}
