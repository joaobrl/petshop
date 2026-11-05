# customer-management

Microsserviço central de identidade do WebCommerce/PetShop: cadastro de clientes e pets, autenticação (login/senha), gestão de funcionários (staff) e histórico de compras/agendamentos do cliente. É o serviço que **emite** os tokens JWT usados por todo o sistema.

## Stack técnica

- Java 21, Spring Boot 4.0.3
- Spring Web, Spring Data JPA (PostgreSQL), Spring Data MongoDB (histórico), Bean Validation, Actuator
- Spring Security — `PasswordEncoder` (BCrypt) e Resource Server (`spring-boot-starter-oauth2-resource-server`): valida o access token do Keycloak via JWKS (SDD-024)
- Spring Kafka (produtor e consumidor)
- MapStruct + Lombok
- [`petshop-commons`](../petshop-commons) — configuração do Resource Server, `JwtService` (emissão legada) e `BaseExceptionHandler`
- Testcontainers (PostgreSQL, MongoDB, Kafka) nos testes de integração
- JaCoCo — gate de 90% de cobertura de instruções

## Arquitetura de dados

- **PostgreSQL** (`clientes_db`) — clientes, pets, funcionários (via JPA)
- **MongoDB** (`customer_history_db`) — histórico de agendamentos e compras (read model alimentado por eventos Kafka)

## Endpoints

### `/api/v1/auth`
| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/login` | Público | Autentica e retorna JWT — se for Customer desativado e a senha bater, reativa a conta automaticamente |
| `POST` | `/forgot-password` | Público | Sempre responde 200 (não revela se o e-mail existe); publica evento Kafka para envio de e-mail |
| `PATCH` | `/change-password` | Autenticado | Troca a própria senha (usuário só pode alterar a sua) |

### `/api/v1/customers`
| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/register/customer` | Público | Autocadastro de cliente (senha escolhida no próprio cadastro); dispara e-mail de boas-vindas via Brevo |
| `GET` | `/list/customers` | `ADMIN`/`RECEPTIONIST` | Lista todos os clientes |
| `GET` | `/find/customer/{id}` | Autenticado (dono ou staff) | Busca cliente por id **ou CPF** — tenta como UUID primeiro, cai pra CPF se não achar |
| `PATCH` | `/update/customer/{id}` | Autenticado | Atualiza cliente |
| `DELETE` | `/delete/customer/{id}` | Autenticado | Remove/desativa cliente |
| `PATCH` | `/register/{customerId}/pet` | Autenticado | Adiciona pet(s) ao cliente |
| `GET` | `/list/pets` | `ADMIN`/`RECEPTIONIST` | Lista pets paginados (filtro opcional por header `typePets`, aplicado antes da paginação) — ver [Paginação de pets](#paginação-de-pets) |
| `GET` | `/{customerId}/pets` | Autenticado (dono ou staff) | Lista pets paginados de um cliente; `404` se o cliente não existir — ver [Paginação de pets](#paginação-de-pets) |
| `PATCH` | `/{customerId}/pets/{petId}` | Autenticado | Atualiza pet |
| `DELETE` | `/{customerId}/pets/{petId}` | Autenticado | Remove pet |

#### Paginação de pets
`GET /list/pets` e `GET /{customerId}/pets` aceitam os query params:
- `page` — número da página (começa em `0`);
- `size` — itens por página (padrão `20`, máximo `100` nessas rotas; valores maiores são limitados a `100`);
- `sort` — ordenação, ex.: `sort=name,desc`. Campos aceitos: `name`/`petName`, `type`/`petType`, `petBreed`, `petSize`, `weightInKg`, `petHealthIssues`, `id`. Padrão: `name`. Campo fora dessa lista retorna `400`.

A resposta é um `PageResponse`: `content`, `page`, `size`, `totalElements`, `totalPages`, `last`.

### `/api/v1/history`
| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `GET` | `/bookings/{customerId}` | Autenticado (dono ou staff) | Histórico de agendamentos do cliente |
| `GET` | `/purchases/{customerId}` | Autenticado (dono ou staff) | Histórico de compras do cliente |
| `GET` | `/bookings` | `ADMIN`/`RECEPTIONIST` | Histórico de agendamentos da loja toda |
| `GET` | `/purchases` | `ADMIN`/`RECEPTIONIST` | Histórico de compras da loja toda |

### `/api/v1/staff` (todos `ADMIN`)
`POST /create`, `GET /all` (só habilitados; também chamado via Feign pelo `staff-service`), `GET /{id}/find` (id **ou CPF** — mesma lógica de fallback do find de cliente), `PATCH /{id}/update`, `DELETE /{id}/delete` (soft-delete, `enabled=false`), `PATCH /{id}/reactivate` (`enabled=true` de novo — volta a aparecer em `GET /all`)

## Segurança

Resource Server (SDD-024): valida o access token do Keycloak via JWKS (`iss`, `exp`, `aud=customer-management`). Rotas de usuário exigem um papel do projeto (token de serviço recebe 403); as chamadas internas têm permissão própria: `GET /api/v1/staff/all` aceita `ADMIN` ou `svc:customers:staff-read` (staff-service) e `GET /api/v1/customers/find/customer/{id}` aceita usuário ou `svc:customers:customer-read` (order-service). `CustomerIdentityResolver` garante que um token do tipo `CUSTOMER` só acesse os próprios dados; `ADMIN`/`RECEPTIONIST` têm acesso amplo.

> **Transitório (até a SDD-024 F3):** o login ainda emite o token HS256 legado (`JwtService`, `JWT_SECRET`), que nenhum serviço aceita mais — o login passa a ser do Keycloak na F3.

### Admin de bootstrap

No primeiro start (se a tabela de staff estiver vazia), `BootstrapAdminRunner` cria automaticamente um usuário `ADMIN`:
- E-mail: `BOOTSTRAP_ADMIN_EMAIL` (padrão `admin@petshop.local`)
- Senha inicial: `BOOTSTRAP_ADMIN_PASSWORD` se definida, senão uma senha aleatória de 20 caracteres gerada em runtime (nunca o CPF) e impressa **uma única vez** no log de start, com `mustChangePassword=true` — troca obrigatória via `PATCH /api/v1/auth/change-password` no primeiro login.

## Integração com outros serviços

Não possui clientes Feign de saída — é consumido por outros serviços:
- **`staff-service`** chama `GET /api/v1/staff/all` para montar a escala de fim de semana.
- **`order-service`** consulta dados de cliente (`EXTERNAL_API_CUSTOMER_SERVICE_URL`).

**Kafka:**
- Consome `booking-completed` (grupo `customer-management-group`) → grava histórico de agendamentos no Mongo
- Consome `order-completed` (grupo `customer-management-group`) → grava histórico de compras no Mongo
- Produz em `notification-commands` (via `NotificationPortOut`/`NotificationKafkaAdapterOut`) — e-mail de "esqueci minha senha"

## Configuração (`application.yaml`)

| Variável | Padrão | Descrição |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/clientes_db` | URL do PostgreSQL |
| `SPRING_DATA_MONGODB_URI` | — | URI do MongoDB (histórico) |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` | Broker Kafka |
| `KAFKA_CUSTOMER_USERNAME` / `PASSWORD` | — | Credenciais SASL/PLAIN do Kafka |
| `KAFKA_CONSUMER_GROUP` | `customer-group` | Grupo de consumo Kafka |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/petshop` | `iss` esperado no token |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8180/realms/petshop/protocol/openid-connect/certs` | JWKS (no compose, `http://keycloak:8080/...`) |
| `KEYCLOAK_AUDIENCE` | `customer-management` | `aud` exigido no token |
| `JWT_SECRET` | *(vazio)* | Só a emissão legada do login (mín. 32 bytes); sai na SDD-024 F3 |
| `JWT_EXPIRATION_MINUTES` | `60` | Validade do token legado emitido |
| `BOOTSTRAP_ADMIN_NAME/EMAIL/CPF/PHONE/PASSWORD` | ver acima | Dados do admin criado no bootstrap (`PASSWORD` opcional; se vazio, gera senha aleatória) |

Porta padrão: **8080** (interna).

## Executando localmente

Requer PostgreSQL, MongoDB e Kafka disponíveis, e o `petshop-commons` instalado no `.m2`:

```bash
cd petshop-commons && ./mvnw clean install
cd ../customer-management
./mvnw spring-boot:run
```

## Docker

Build multi-stage, **contexto na raiz do repositório**:

```bash
docker build -f customer-management/Dockerfile -t customer-management .
```

No `docker-compose.yml` raiz, serviço `api-customers`, exposto em **`localhost:8082`**:

```bash
docker compose up api-customers
```

## Kubernetes

Manifests em [`k8s/api-clientes/`](../k8s/api-clientes): `deployment.yaml` (1 réplica, `api-clientes:latest`, probes `/actuator/health/{liveness,readiness}`, requests `256Mi`/`200m`, limits `512Mi`/`500m`), `configmap.yaml` e `service.yaml` (`NodePort`, `30082` → 8080). Segredos: `db-clientes-secret`, `api-clientes-secret` (Kafka) e `jwt-secret` (só a emissão legada, até a SDD-024 F3).

## Testes

```bash
./mvnw test
./mvnw verify   # inclui gate de cobertura JaCoCo (90%)
```
