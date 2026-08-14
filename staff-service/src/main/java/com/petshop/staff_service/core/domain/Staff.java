package com.petshop.staff_service.core.domain;

import com.petshop.commons.security.Role;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

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
}
