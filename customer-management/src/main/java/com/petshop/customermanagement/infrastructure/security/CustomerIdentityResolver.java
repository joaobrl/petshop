package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Amarra identidade de token a customerId — CUSTOMER só acessa/edita o
 * próprio cadastro, pets e histórico; ADMIN/RECEPTIONIST não têm essa
 * restrição.
 * <p>
 * Com o JWT próprio, o id do Customer é o próprio claim {@code sub} do
 * token (foi o customer-management quem emitiu, então já sabe o id de
 * quem logou) — diferente do fluxo antigo via Keycloak, que precisava
 * buscar o Customer pelo claim cpf pra descobrir o dono. Nenhuma consulta
 * ao banco é necessária aqui agora.
 */
@Component
public class CustomerIdentityResolver {

    public void requireOwnershipIfCustomer(UUID targetCustomerId, AuthenticatedUser user) {
        if (user == null || user.type() != AccountType.CUSTOMER) {
            return;
        }
        if (!user.id().equals(targetCustomerId)) {
            throw new AccessDeniedException("Cliente só pode acessar o próprio cadastro.");
        }
    }
}
