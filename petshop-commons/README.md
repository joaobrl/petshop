# petshop-commons

Biblioteca compartilhada (JAR) usada pelos microsserviços do WebCommerce/PetShop. Centraliza a validação do access token do Keycloak (Resource Server), tratamento de exceções padronizado e exceções de negócio comuns, evitando duplicação de código entre os serviços.

## Conteúdo

### Segurança (`com.petshop.commons.security`) — SDD-024

Cada serviço é um Resource Server: valida o access token do Keycloak (realm `petshop`, ver [`infra/keycloak`](../infra/keycloak)) com a chave pública do JWKS. Nenhum serviço tem material para assinar token.

- **`oauth2.KeycloakResourceServerConfig`** — `@Configuration` que o `SecurityConfig` de cada serviço importa: `JwtDecoder`, conversor e entry point. Propriedades `keycloak.issuer-uri`, `keycloak.jwk-set-uri` e `keycloak.audience` (id do próprio serviço).
- **`oauth2.KeycloakJwtDecoders`** — `NimbusJwtDecoder` por `jwk-set-uri` (JWKS em cache, rebuscado em `kid` novo: rotação sem restart) que valida assinatura RS256, `exp`, `iss` e `aud`. Token HS256 é recusado.
- **`oauth2.KeycloakJwtAuthenticationConverter`** — `Jwt` → `KeycloakAuthenticationToken`. Com claim `user_id`: principal `AuthenticatedUser` e authority `ROLE_<papel>` de `realm_access.roles` (os `hasRole`/`hasAnyRole` continuam iguais). Sem `user_id` (Client Credentials): principal `ServiceAccount(azp)` e authorities só as permissões `svc:*`. Credentials = token bruto (relay do Feign).
- **`ServicePermissions`** — permissões das chamadas serviço-a-serviço (`svc:<destino>:<ação>`), realm roles atribuídos só ao service account do chamador.
- **`PetshopAuthorization`** — `anyUser()` (qualquer papel de usuário; usado no lugar de `authenticated()`, para token de serviço receber 403 fora das rotas dele), `anyUserOr(perm)` e `rolesOr(perm, roles...)`.
- **`ProblemDetailAuthenticationEntryPoint`** / **`ProblemDetailAccessDeniedHandler`** — 401 e 403 com corpo `ProblemDetail` (SDD-034).
- **`AuthenticatedUser`** — usuário autenticado (id = claim `user_id`, email, nome, cpf, telefone, role, tipo de conta).
- **`Role`** — papéis de usuário: `ADMIN`, `RECEPTIONIST`, `GROOMER`, `VETERINARIAN`, `CUSTOMER` (o antigo `SERVICE` não existe mais).
- **`AccountType`** — tipo de conta: `CUSTOMER`, `STAFF`, `SERVICE`.
- **`JwtService`** — só a emissão legada do token HS256 do login do customer-management (`generateToken`). Sai na SDD-024 F3.

### Tratamento de erros (`com.petshop.commons.handler` / `exception`)

- **`BaseExceptionHandler`** — classe abstrata com `@ExceptionHandler`s prontos (estendida pelos `@RestControllerAdvice` de cada serviço), retornando respostas no formato `ProblemDetail` (RFC 7807):
  - `NotFoundException` → 404
  - `ConflictException` → 409
  - `BusinessRuleException` → 422
  - `MethodArgumentNotValidException` → 400 (com lista de erros por campo)
  - `AccessDeniedException` → 403
  - `BadCredentialsException` → 401
  - `IllegalArgumentException` → 400
  - `Exception` (genérica) → 500
- **`NotFoundException`**, **`ConflictException`**, **`BusinessRuleException`**, **`BusinessException`** — exceções de negócio usadas pelas camadas de aplicação/domínio dos serviços.
- **`ErrorResponseDto`** — DTO auxiliar de erro.

### Feign (`com.petshop.commons.feign`)

- `TokenPropagation`: resolve o header `Authorization` das chamadas Feign (`resolveAuthorizationHeader` repassa o token do usuário autenticado ou usa o token de serviço; `mintServiceToken` sempre usa o de serviço). Token de serviço = Client Credentials no Keycloak com o client do próprio serviço (`OAuth2AuthorizedClientManager`, reaproveitado até expirar). Cada serviço chamador declara o `@Bean` com `TokenPropagation.clientCredentials(keycloak.client.token-uri, keycloak.client.id, keycloak.client.secret)`.
- `CorrelationIdFeignConfig`: `RequestInterceptor` que propaga o correlation id; o serviço faz `@Import(CorrelationIdFeignConfig.class)`.
- OpenFeign (`feign-core`) é dependência `provided`: só quem usa Feign precisa tê-lo no classpath.

## Stack técnica

- Java 21
- Empacotado como `jar` puro (sem `spring-boot-starter-parent`) — dependências Spring/Servlet/Security/Validation declaradas com escopo `provided`, pois todo serviço consumidor já é uma aplicação Spring Boot e as traz transitivamente.
- `spring-security-oauth2-resource-server`/`-jose`/`-client` `7.0.3` (`provided`): cada serviço declara `spring-boot-starter-oauth2-resource-server` (e, os chamadores, `spring-security-oauth2-client`).
- `io.jsonwebtoken` (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) `0.12.6` — só para a emissão legada do `JwtService`; sai na SDD-024 F3.
- Testes: JUnit 5, Mockito, AssertJ. Cobertura mínima de 90% de instruções verificada via JaCoCo (`mvn verify`).

## Uso pelos demais serviços

Cada microsserviço (exceto `notification-service`, que não expõe resource server OAuth2/JWT) declara esta lib como dependência Maven:

```xml
<dependency>
    <groupId>com.petshop</groupId>
    <artifactId>petshop-commons</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Build

Como é dependência dos demais módulos, instale no repositório local Maven antes de buildar os serviços:

```bash
cd petshop-commons
./mvnw clean install
```

## Testes

```bash
./mvnw test
```

Para gerar o relatório de cobertura (JaCoCo) e validar o gate de 90%:

```bash
./mvnw verify
```
