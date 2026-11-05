package com.petshop.commons.security.oauth2;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.util.Collection;
import java.util.Map;

/**
 * Authentication de um access token do Keycloak já validado pelo JwtDecoder.
 * {@code principal} = {@code AuthenticatedUser} (usuário) ou {@link ServiceAccount}
 * (Client Credentials); {@code credentials} = token bruto, o único que o
 * {@code TokenPropagation} repassa nas chamadas Feign (SDD-027).
 */
public class KeycloakAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

    private final String name;

    public KeycloakAuthenticationToken(Jwt jwt, Object principal, String name,
                                       Collection<? extends GrantedAuthority> authorities) {
        super(jwt, principal, jwt.getTokenValue(), authorities);
        this.name = name;
        setAuthenticated(true);
    }

    @Override
    public Map<String, Object> getTokenAttributes() {
        return getToken().getClaims();
    }

    @Override
    public String getName() {
        return name;
    }
}
