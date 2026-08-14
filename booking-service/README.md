# booking-service

Microsserviço de agendamento de serviços (banho, tosa, consultas veterinárias etc.) do WebCommerce/PetShop. Gerencia a criação, listagem, atualização e cancelamento de agendamentos, calcula horários disponíveis e publica eventos de histórico e notificação por e-mail.

## Stack técnica

- Java 21, Spring Boot 4.0.3, Spring Cloud 2025.1.2 (`spring-cloud-starter-openfeign` + `-loadbalancer`, sem Eureka/Consul — URLs estáticas por variável de ambiente)
- Spring Web, Spring Data JPA (PostgreSQL em produção, H2 em testes), Bean Validation, Spring Cache (`@EnableCaching`), Spring Security (infra apenas — validação de token feita pelo `petshop-commons`)
- Spring Kafka (produtor)
- MapStruct + Lombok
- [`petshop-commons`](../petshop-commons) — JWT próprio e `BaseExceptionHandler`
- Testcontainers (PostgreSQL) nos testes de integração
- JaCoCo — gate de 90% de cobertura de instruções

## Endpoints — `/api/v1/bookings`

| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/create` | Autenticado | Cria agendamento. Se o solicitante for `CUSTOMER`, os dados do dono (nome/CPF/contato) vêm das claims do token, ignorando o corpo enviado |
| `GET` | `/list` | Autenticado | Lista agendamentos com filtros (`ownerCpf`, `petId`, `date`, `employeeName`, `serviceType`, `status`). `CUSTOMER` só vê os próprios |
| `GET` | `/find/{id}` | Autenticado | Busca agendamento por id (dono ou staff) |
| `PATCH` | `/update/{id}` | Autenticado | Atualiza agendamento (dados + `status` opcional) |
| `DELETE` | `/cancel/{id}` | Autenticado | Cancela agendamento |
| `GET` | `/available-slots?serviceType=&date=&range=` | Público | Horários disponíveis para um tipo de serviço, a partir de uma data (`dd/MM/yyyy`), numa janela `DAY`/`WEEK`/`MONTH`, já descontando ocupados e antecedência mínima de 2h |

A checagem de "dono só acessa o próprio agendamento" é feita na camada de controller (`AccountType` do `AuthenticatedUser`), não no `SecurityConfig`.

## Segurança

JWT próprio via [`petshop-commons`](../petshop-commons) — booking-service apenas valida tokens (não emite). `/api/v1/bookings/available-slots` é público; todo o restante exige autenticação. 401 (não 403) em requisições sem token válido.

## Integração com outros serviços

- **Feign → staff-service**: `CustomerManagementFeign` (`external.api.staff-service.url`) consulta `GET /api/v1/availability/general` e `GET /api/v1/availability/schedule` para checar disponibilidade e escala de funcionários antes de confirmar um agendamento. O token da requisição original é repassado via `FeignTokenRelayConfig`.
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
| `JWT_SECRET` | *(vazio)* | Segredo HS256 compartilhado (mín. 32 bytes) |
| `JWT_EXPIRATION_MINUTES` | `60` | Validade do token |

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

Manifests em [`k8s/api-booking/`](../k8s/api-booking): `deployment.yaml` (1 réplica, imagem local `api-booking:latest`, probes em `/actuator/health/{liveness,readiness}`, requests `256Mi`/`200m`, limits `512Mi`/`500m`), `configmap.yaml` e `service.yaml` (`NodePort`, `30083` → 8083). Credenciais via secrets `db-booking-secret`, `api-booking-secret` (Kafka) e `jwt-secret` compartilhado. Banco dedicado em [`k8s/db-booking/`](../k8s/db-booking).

## Testes

```bash
./mvnw test
./mvnw verify   # inclui gate de cobertura JaCoCo (90%)
```
