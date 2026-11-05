package com.petshop.commons.security.oauth2;

import com.petshop.commons.security.ServicePermissions;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;

/**
 * Converte o access token do Keycloak (já validado: assinatura, iss, exp, aud) em
 * {@link KeycloakAuthenticationToken} (SDD-024 F2/F4).
 *
 * <ul>
 *   <li>Com claim {@code user_id}: usuário. Principal {@code AuthenticatedUser}; authority
 *       {@code ROLE_<papel>} a partir de {@code realm_access.roles}, o que mantém os
 *       {@code hasRole}/{@code hasAnyRole} dos SecurityConfig.</li>
 *   <li>Sem {@code user_id}: service account (Client Credentials). Principal
 *       {@link ServiceAccount} com o {@code azp}; authorities só as permissões
 *       {@code svc:*} de {@code realm_access.roles} ({@link ServicePermissions}), nunca
 *       {@code ROLE_*}.</li>
 * </ul>
 *
 * Claims obrigatórios faltando ou inválidos viram 401 ({@link InvalidBearerTokenException}).
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    static final String CLAIM_AZP = "azp";
    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (jwt.hasClaim(AuthenticatedUser.CLAIM_USER_ID)) {
            return userAuthentication(jwt);
        }
        return serviceAuthentication(jwt);
    }

    private static KeycloakAuthenticationToken userAuthentication(Jwt jwt) {
        AuthenticatedUser user;
        try {
            user = AuthenticatedUser.fromClaims(jwt.getClaims());
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("Claims de usuário inválidos: " + ex.getMessage(), ex);
        }
        List<GrantedAuthority> authorities = user.role() == null
                ? List.of()
                : List.of(new SimpleGrantedAuthority(ROLE_PREFIX + user.role().name()));
        return new KeycloakAuthenticationToken(jwt, user, user.id().toString(), authorities);
    }

    private static KeycloakAuthenticationToken serviceAuthentication(Jwt jwt) {
        String clientId = jwt.getClaimAsString(CLAIM_AZP);
        if (clientId == null || clientId.isBlank()) {
            throw new InvalidBearerTokenException("Token sem 'user_id' nem 'azp'.");
        }
        List<GrantedAuthority> authorities = AuthenticatedUser.realmRoles(jwt.getClaims()).stream()
                .filter(role -> role.startsWith(ServicePermissions.PREFIX))
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        return new KeycloakAuthenticationToken(jwt, new ServiceAccount(clientId), clientId, authorities);
    }
}
