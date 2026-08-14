# petshop-commons

Biblioteca compartilhada (JAR) usada pelos microsserviços do WebCommerce/PetShop. Centraliza autenticação JWT, tratamento de exceções padronizado e exceções de negócio comuns, evitando duplicação de código entre os serviços.

## Conteúdo

### Segurança / JWT (`com.petshop.commons.security`)

- **`JwtService`** — geração e validação de tokens JWT (HS256, via `jjwt`). Emite tokens de usuário (`generateToken`, com claims `email`, `name`, `cpf`, `phone`, `role`, `type`) e tokens de serviço-a-serviço (`generateServiceToken`, usado para chamadas internas entre microsserviços com `Role.SERVICE` / `AccountType.SERVICE`). Exige `JWT_SECRET` com no mínimo 32 bytes.
- **`JwtAuthenticationFilter`** — filtro `OncePerRequestFilter` que extrai e valida o token do header `Authorization`, populando o contexto de segurança do Spring.
- **`AuthenticatedUser`** — representação do usuário autenticado extraído do token (id, email, nome, cpf, telefone, role, tipo de conta).
- **`Role`** — papéis de negócio: `ADMIN`, `RECEPTIONIST`, `GROOMER`, `VETERINARIAN`, `CUSTOMER`, `SERVICE`.
- **`AccountType`** — tipo de conta associada ao token: `CUSTOMER`, `STAFF`, `SERVICE`.

> O sistema usa um esquema de JWT próprio (assinado com segredo compartilhado via `JWT_SECRET`), não Keycloak/OAuth2 externo — cada serviço valida o token localmente usando esta biblioteca.

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

## Stack técnica

- Java 21
- Empacotado como `jar` puro (sem `spring-boot-starter-parent`) — dependências Spring/Servlet/Security/Validation declaradas com escopo `provided`, pois todo serviço consumidor já é uma aplicação Spring Boot e as traz transitivamente.
- `io.jsonwebtoken` (`jjwt-api`, `jjwt-impl`, `jjwt-jackson`) `0.12.6` — única dependência propagada de verdade (nenhum serviço já trazia JWT no classpath).
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
