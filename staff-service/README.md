# staff-service

Microsserviço de disponibilidade e escala de funcionários do WebCommerce/PetShop. Não é dono do cadastro de funcionários (isso vive em [`customer-management`](../customer-management)) — consulta esse registro via Feign e calcula disponibilidade/escala em cima dele, além de gerenciar a escala rotativa de tosadores aos sábados.

## Stack técnica

- Java 21, Spring Boot 4.0.3, Spring Cloud 2025.1.2 (`spring-cloud-starter-openfeign` + `-loadbalancer`, sem Eureka/Consul — URL estática por variável de ambiente)
- Spring Web, Spring Data JPA (PostgreSQL — usado só para a escala de fim de semana), Bean Validation, Actuator
- Spring Security — Resource Server (valida o access token do Keycloak via JWKS) e `spring-security-oauth2-client` (Client Credentials do Feign)
- MapStruct + Lombok
- [`petshop-commons`](../petshop-commons) — configuração do Resource Server, `TokenPropagation` e `BaseExceptionHandler`
- Testcontainers (PostgreSQL) nos testes de integração
- JaCoCo — gate de 90% de cobertura de instruções

> O pom.xml declara `spring-kafka`, mas não há nenhum código de Kafka no serviço — dependência vestigial, não usada atualmente (o `spring-boot-starter-data-mongodb` vestigial que existia junto foi removido nesta sessão — estava causando um hang de ~30s em toda chamada a `/actuator/health`, tentando alcançar um Mongo em `localhost:27017` que não existe aqui).

## Endpoints

### `/api/v1/availability`
| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/general?dateTime=&role=` | Autenticado | Disponibilidade (capacidade/booleano) para uma role numa data/hora (`yyyy-MM-dd'T'HH:mm:ss`) |
| `GET` | `/schedule?dateTime=&role=` | Autenticado | Lista de funcionários escalados para aquele horário/role |

### `/api/v1/weekend-allocation`
| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/generate` | `ADMIN` | Gera a escala de tosadores (GROOMER) para os sábados do próximo mês ainda não preenchido, em rodízio; lança erro de negócio se houver menos tosadores habilitados que `STAFF_GROOMER_SATURDAY_COUNT` |
| `GET` | `?date=yyyy-MM-dd` | Autenticado | Consulta a escala de uma data específica |

`Staff` não é mais uma entidade persistida localmente — é um objeto de domínio populado a partir das chamadas Feign ao `customer-management`. Só `WeekendAllocation` é persistida no banco próprio (`staff_db`).

### Formato de datas (ISO-8601) — mudança quebradora

Toda data/hora trafegada (query params e JSON) é **ISO-8601 sem offset**, sempre em **horário de Brasília** implícito: `dateTime` de `/availability/*` e `validatedDateTime` da resposta como `2026-08-15T09:00:00`; `date` de `/weekend-allocation` como `2026-11-15`. Mensagens de texto lidas pelo usuário seguem em formato brasileiro.

> **Mudança quebradora de contrato (SDD-035):** até esta versão o serviço usava `dd/MM/yyyy HH:mm` / `dd/MM/yyyy`. Agora `15/08/2026 09:00` e `15/11/2026` devolvem **400**. O armazenamento no banco não muda.
>
> **Ordem de deploy:** o contrato com o `booking-service` (Feign síncrono) não tolera os dois formatos — publicar `staff-service` e `booking-service` **no mesmo release** (subir juntos). Deploy fora de ordem faz o staff responder 400; o `booking-service` trata 4xx como contrato quebrado e repassa o erro, então a criação de agendamento falha com **500**. O `web/` também deve sair junto (envia `yyyy-MM-dd` para `weekend-allocation`).

## Segurança

Resource Server (SDD-024): valida o access token do Keycloak via JWKS (`iss`, `exp`, `aud=staff-service`). `GET /api/v1/availability/**` aceita usuário ou o token de serviço do booking-service com `svc:staff:availability-read`; `/api/v1/weekend-allocation/**` exige role `ADMIN`; o resto exige usuário com papel. 401 ProblemDetail em token ausente/inválido.

## Integração com outros serviços

- **Feign → customer-management**: `StaffRegistryFeign` chama `GET /api/v1/staff/all` (`external.api.customer-management.url`) para obter o registro de funcionários. Sempre com o token Client Credentials do client `staff-service` do Keycloak (`TokenPropagation.mintServiceToken`, permissão `svc:customers:staff-read`).
- **Consumido por**: [`booking-service`](../booking-service), que chama `GET /api/v1/availability/general` e `/schedule` antes de confirmar um agendamento.

## Configuração (`application.yaml`)

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8085` | Porta HTTP |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/staff_db` | URL do PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | `postgres` / `postgres` | Credenciais do banco |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | Estratégia de DDL |
| `EXTERNAL_API_CUSTOMER_MANAGEMENT_URL` | `http://localhost:8082` | URL do customer-management (Feign) |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/petshop` | `iss` esperado no token |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8180/realms/petshop/protocol/openid-connect/certs` | JWKS (no compose, `http://keycloak:8080/...`) |
| `KEYCLOAK_AUDIENCE` | `staff-service` | `aud` exigido no token |
| `KEYCLOAK_TOKEN_URI` | `http://localhost:8180/realms/petshop/protocol/openid-connect/token` | Token endpoint do Client Credentials |
| `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` | `staff-service` / *(vazio)* | Client do Keycloak deste serviço (`KEYCLOAK_CLIENT_SECRET_STAFF_SERVICE` do `.env`) |
| `STAFF_GROOMER_SATURDAY_COUNT` | `2` | Número de tosadores escalados por sábado |

## Executando localmente

```bash
cd petshop-commons && ./mvnw clean install
cd ../staff-service
./mvnw spring-boot:run
```

## Docker

Build multi-stage, **contexto na raiz do repositório** (depende do `petshop-commons` local):

```bash
docker build -f staff-service/Dockerfile -t staff-service .
```

No `docker-compose.yml` raiz, serviço `api-staff`, exposto em **`localhost:8085`** (depende de `db` e `api-customers`):

```bash
docker compose up api-staff
```

## Kubernetes

Manifests em [`k8s/api-staff/`](../k8s/api-staff): `deployment.yaml` (1 réplica, `api-staff:latest`, probes `/actuator/health/{liveness,readiness}`, requests `256Mi`/`200m`, limits `512Mi`/`500m`), `configmap.yaml` e `service.yaml` (`NodePort`, `30085` → 8085). Segredos: `db-staff-secret` e o segredo do client em `keycloak-secret`. Banco dedicado em [`k8s/db-staff/`](../k8s/db-staff).

> O `configmap.yaml` atual não define `EXTERNAL_API_CUSTOMER_MANAGEMENT_URL`, então em k8s o serviço cairia no valor padrão (`http://localhost:8082`) — ajuste antes de um deploy real.

## Testes

```bash
./mvnw test
./mvnw verify   # inclui gate de cobertura JaCoCo (90%)
```
