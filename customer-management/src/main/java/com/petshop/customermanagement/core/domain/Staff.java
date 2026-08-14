package com.petshop.customermanagement.core.domain;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Migrado do staff-service — aqui só o CRUD; escala (Availability/WeekendAllocation)
 * continua lá, buscando esses dados via Feign. {@code role} reusa o enum
 * compartilhado ({@link Role}, o mesmo do JWT) em vez de um enum de negócio
 * próprio, evitando manter dois enums sincronizados manualmente.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Staff {

    private UUID id;
    private String name;
    private String cpf;
    private String email;
    private String phone;
    private Boolean enabled;
    private Role role;
    private String passwordHash;
    private Boolean mustChangePassword;

    public Staff(StaffRequestDto request) {
        this.id = UUID.randomUUID();
        this.name = request.getName();
        this.cpf = request.getCpf();
        this.email = request.getEmail();
        this.phone = request.getPhone();
        this.enabled = true;
        this.role = request.getRole();
        this.mustChangePassword = true;
    }

    // Construtor "original" (7 campos, pré-login) preservado por
    // compatibilidade com testes — ver Customer.java pro mesmo raciocínio.
    public Staff(UUID id, String name, String cpf, String email, String phone, Boolean enabled, Role role) {
        this.id = id;
        this.name = name;
        this.cpf = cpf;
        this.email = email;
        this.phone = phone;
        this.enabled = enabled;
        this.role = role;
    }

    public void update(StaffRequestDto request) {
        if (request.getName() != null) this.name = request.getName();
        if (request.getEmail() != null) this.email = request.getEmail();
        if (request.getPhone() != null) this.phone = request.getPhone();
        if (request.getRole() != null) this.role = request.getRole();
    }
}
