package com.petshop.customermanagement.core.domain;

import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
public class Customer {

    private UUID id;
    private String name;
    private String cpf;
    private String email;
    private String phone;
    private Boolean enabled;
    private List<Pet> pet;
    private String passwordHash;
    private Boolean mustChangePassword;

    public Customer(String name, String cpf, String email, String phone) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.cpf = cpf;
        this.email = email;
        this.phone = phone;
        this.enabled = true;
        this.pet = new ArrayList<>();
        this.mustChangePassword = true;
    }

    public Customer(UUID id, String name, String cpf, String email, String phone, Boolean enabled, List<Pet> pet) {
        this.id = id;
        this.name = name;
        this.cpf = cpf;
        this.email = email;
        this.phone = phone;
        this.enabled = enabled;
        this.pet = pet;
    }

    public void update(String name, String email, String phone) {
        if (name != null) this.name = name;
        if (email != null) this.email = email;
        if (phone != null) this.phone = phone;
    }

}
