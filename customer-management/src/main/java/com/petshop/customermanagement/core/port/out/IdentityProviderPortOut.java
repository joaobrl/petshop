package com.petshop.customermanagement.core.port.out;

import com.petshop.customermanagement.core.port.out.dto.IdentityUser;

import java.util.Optional;
import java.util.UUID;

/**
 * Provedor de identidade (Keycloak, SDD-024 F3): único dono da credencial. O
 * customer-management cria e mantém os usuários pela Admin API; nunca vê nem guarda hash
 * de senha.
 *
 * <p>Falha de comunicação vira {@code DependencyUnavailableException} (503); e-mail já
 * usado no IdP vira {@code ConflictException} (409).
 */
public interface IdentityProviderPortOut {

    /**
     * Cria o usuário com o papel de {@code user.role()}. {@code password} nulo = sem
     * credencial (o usuário define a senha pelo e-mail de ação, ver
     * {@link #sendSetPasswordEmail}). Devolve o id do usuário no IdP.
     */
    String createUser(IdentityUser user, String password);

    /** Id no IdP do usuário com o atributo {@code user_id} igual ao UUID local. */
    Optional<String> findIdByUserId(UUID userId);

    /** Id no IdP do usuário com esse e-mail (exato). */
    Optional<String> findIdByEmail(String email);

    /** Remove o usuário (compensação de cadastro que não chegou a ser gravado localmente). */
    void deleteUser(String idpUserId);

    /** Sincroniza nome, e-mail, telefone e papel do usuário já existente. */
    void updateUser(IdentityUser user);

    /** Habilita/desabilita o login. Desabilitar também encerra as sessões ativas (CA4). */
    void setEnabled(UUID userId, boolean enabled);

    /** {@code emailVerified=true}, refletido no claim {@code email_verified} (SDD-020). */
    void markEmailVerified(UUID userId);

    /** E-mail de ação do Keycloak (UPDATE_PASSWORD) para o usuário definir a própria senha. */
    void sendSetPasswordEmail(String idpUserId);
}
