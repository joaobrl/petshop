# booking-service

Microsserviço de agendamento de serviços (banho, tosa, consultas veterinárias etc.) do WebCommerce/PetShop. Gerencia a criação, listagem, atualização e cancelamento de agendamentos, calcula horários disponíveis e publica eventos de histórico e notificação por e-mail.

## Stack técnica

- Java 21, Spring Boot 4.0.3, Spring Cloud 2025.1.2 (`spring-cloud-starter-openfeign` + `-loadbalancer`, sem Eureka/Consul — URLs estáticas por variável de ambiente)
- Spring Web, Spring Data JPA (PostgreSQL em produção, H2 em testes), Bean Validation, Spring Cache (`@EnableCaching` + `ConcurrentMapCacheManager` com cache granular por cargo e dia), Spring Security (infra apenas — validação de token feita pelo `petshop-commons`)
- Spring Kafka (produtor)
- MapStruct + Lombok
- [`petshop-commons`](../petshop-commons) — Resource Server do Keycloak (conversor de claims, `TokenPropagation`) e `BaseExceptionHandler`
- Testcontainers (PostgreSQL) nos testes de integração
- JaCoCo — gate de 90% de cobertura de instruções (100% de cobertura de branches nas classes de serviço e cache)

## Endpoints — `/api/v1/bookings`

| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/create` | Autenticado | Cria agendamento. Se o solicitante for `CUSTOMER`, os dados do dono (nome/CPF/contato) vêm das claims do token, ignorando o corpo enviado |
| `GET` | `/list` | Autenticado | Lista agendamentos com filtros (`ownerCpf`, `petId`, `date` em `yyyy-MM-dd`, `employeeName`, `serviceType`, `status`). `CUSTOMER` só vê os próprios |
| `GET` | `/find/{id}` | Autenticado | Busca agendamento por id (dono ou staff) |
| `PATCH` | `/update/{id}` | Autenticado | Atualiza agendamento (dados + `status` opcional) |
| `DELETE` | `/cancel/{id}` | Autenticado | Cancela agendamento |
| `GET` | `/available-slots?serviceType=&date=&range=` | Público | Horários disponíveis para um tipo de serviço, a partir de uma data (`yyyy-MM-dd`), numa janela `DAY`/`WEEK`/`MONTH`, já descontando ocupados e antecedência mínima de 2h |

### Formato de datas (ISO-8601) — mudança quebradora

Toda data/hora trafegada em JSON (corpo REST e eventos Kafka de histórico) é **ISO-8601 sem offset** — `LocalDateTime` no formato `2026-08-15T09:00:00` — e é sempre **horário de Brasília** (`America/Sao_Paulo`), implícito. Datas em query params (`date` de `/list` e `/available-slots`) são `yyyy-MM-dd`. Isso vale para `bookingDateTime` (request e response), `dateTime` dos horários disponíveis e `suggestedSlot` do erro 422. Já o texto das mensagens lidas pelo usuário (ex.: `detail` do 422 de horário indisponível) continua em formato brasileiro (`dd/MM/yyyy HH:mm` e `dd/MM/yyyy`) — só campos e contratos são ISO.

> **Mudança quebradora de contrato (SDD-017):** até esta versão o booking-service usava o formato brasileiro `dd/MM/yyyy HH:mm` (e `dd/MM/yyyy` nos query params). Agora `"15/08/2026 09:00"` no corpo devolve **400**; clientes precisam enviar e ler ISO-8601. Os eventos `booking-scheduled`/`booking-canceled`/`booking-completed` também passam a levar `bookingDateTime` em ISO; o `customer-management` aceita os dois formatos durante a transição, para não perder mensagens antigas já enfileiradas.

A checagem de "dono só acessa o próprio agendamento" é feita na camada de controller (`AccountType` do `AuthenticatedUser`), não no `SecurityConfig`.

## Cache de horários disponíveis (`availableSlots`)

- **Granularidade por `(cargo, dia)` (`<requiredStaffRole>:<yyyy-MM-dd>`)**: `AvailableSlotsService` armazena no cache `availableSlots` (`CacheConfig`) a disponibilidade diária por cargo exigido (`GROOMER` para `BANHO`, `TOSAGEM` e `TOSA_E_BANHO`; `VETERINARIAN` para `CONSULTA_VETERINARIA`). Consultas `DAY`, `WEEK` e `MONTH` compõem o resultado a partir dos mesmos dias cacheados, e os três serviços de banho/tosa compartilham a mesma entrada diária de `GROOMER`.
- **Filtro dinâmico de antecedência mínima (`Clock`)**: a regra de 2 horas de antecedência (`LocalDateTime.now(clock).plusHours(2)`) é avaliada na leitura sobre a lista diária cacheada, evitando que slots do dia atual fiquem defasados à medida que o relógio avança.
- **Invalidação seletiva (`AvailableSlotsPortIn.evictAvailableSlots`)**:
  - `createBooking` e `cancelBooking` invalidam apenas a chave `<cargo>:<data>` do agendamento afetado.
  - `updateBooking` não invalida o cache ao concluir um atendimento (`status=COMPLETED`, pois agendamentos concluídos continuam `!= CANCELED`), ao alterar apenas `observations` ou ao trocar entre serviços do mesmo cargo (`BANHO` ↔ `TOSAGEM`) mantendo data e hora. Quando há mudança de horário no mesmo dia/cargo, invalida apenas aquela data; quando muda o dia ou o cargo (`GROOMER` ↔ `VETERINARIAN`), invalida tanto a chave anterior quanto a nova.

## Segurança

Resource Server (SDD-024): valida o access token do Keycloak via JWKS (`iss`, `exp`, `aud=booking-service`) com a configuração do [`petshop-commons`](../petshop-commons); não emite token. `/api/v1/bookings/available-slots` é público; todo o restante exige usuário com papel (token de serviço recebe 403). 401 ProblemDetail em requisições sem token válido (inclusive token inválido em rota pública).

## Integração com outros serviços

- **Feign → staff-service**: `CustomerManagementFeign` (`external.api.staff-service.url`) consulta `GET /api/v1/availability/general` e `GET /api/v1/availability/schedule` para checar disponibilidade e escala de funcionários antes de confirmar um agendamento. O `TokenPropagation` repassa o token do usuário da requisição; sem usuário, usa o token Client Credentials do client `booking-service` do Keycloak (permissão `svc:staff:availability-read`).
- **Kafka (produtor apenas — sem `@KafkaListener`)**:
  - Tópico `booking-scheduled`, `booking-canceled`, `booking-completed` — eventos de histórico (consumidos pelo `customer-management` para popular o histórico de agendamentos)
  - Tópico `notification-commands` — comandos de e-mail (`{to, tipo, params}`, sem HTML — o `notification-service` resolve `tipo` pro template gerenciado no Brevo)

## Configuração (`application.yaml`)

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8083` | Porta HTTP |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/pet-service_db` | URL do PostgreSQL (`booking_db` em compose/k8s) |
| `SPRING_DATASOURCE_USERNAME` / `PASSWORD` | `postgres` / `postgres` | Credenciais do banco |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | Estratégia de DDL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` | Broker Kafka |
| `KAFKA_BOOKING_USERNAME` / `PASSWORD` | — | Credenciais SASL/PLAIN do Kafka |
| `EXTERNAL_API_STAFF_SERVICE_URL` | `http://localhost:8085` | URL do staff-service (Feign) |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/petshop` | `iss` esperado no token |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8180/realms/petshop/protocol/openid-connect/certs` | JWKS (no compose, `http://keycloak:8080/...`) |
| `KEYCLOAK_AUDIENCE` | `booking-service` | `aud` exigido no token |
| `KEYCLOAK_TOKEN_URI` | `http://localhost:8180/realms/petshop/protocol/openid-connect/token` | Token endpoint do Client Credentials |
| `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` | `booking-service` / *(vazio)* | Client do Keycloak deste serviço (`KEYCLOAK_CLIENT_SECRET_BOOKING_SERVICE` do `.env`) |

## Executando localmente

```bash
cd petshop-commons && ./mvnw clean install
cd ../booking-service
./mvnw spring-boot:run
```

## Docker

Build multi-stage, **contexto na raiz do repositório** (depende do `petshop-commons` local):

```bash
docker build -f booking-service/Dockerfile -t booking-service .
```

No `docker-compose.yml` raiz, o serviço é `api-booking`, exposto em **`localhost:8083`**:

```bash
docker compose up api-booking
```

## Kubernetes

Manifests em [`k8s/api-booking/`](../k8s/api-booking): `deployment.yaml` (1 réplica, imagem local `api-booking:latest`, probes em `/actuator/health/{liveness,readiness}`, requests `256Mi`/`200m`, limits `512Mi`/`500m`), `configmap.yaml` e `service.yaml` (`NodePort`, `30083` → 8083). Credenciais via secrets `db-booking-secret`, `api-booking-secret` (Kafka) e o segredo do client em `keycloak-secret`. Banco dedicado em [`k8s/db-booking/`](../k8s/db-booking).

## Testes

```bash
./mvnw test
./mvnw verify   # inclui gate de cobertura JaCoCo (90%)
```
