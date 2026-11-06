# customer-management

Microsserviço central de identidade do WebCommerce/PetShop: cadastro de clientes e pets, gestão de funcionários (staff) e histórico de compras/agendamentos do cliente. Desde a SDD-024 F3 o login, a senha e a emissão de tokens são do **Keycloak**: este serviço cria e mantém os usuários no realm `petshop` pela Admin API e **não guarda nem valida senha** (nenhum serviço além do Keycloak assina token).

## Stack técnica

- Java 21, Spring Boot 4.0.3
- Spring Web, Spring Data JPA (PostgreSQL), Spring Data MongoDB (histórico), Bean Validation, Actuator
- Spring Security — Resource Server (`spring-boot-starter-oauth2-resource-server`): valida o access token do Keycloak via JWKS (SDD-024)
- `spring-security-oauth2-client` — Client Credentials do client `customer-management-admin` para a Admin API do Keycloak (`RestClient`, `KeycloakAdminAdapterOut`)
- Spring Kafka (produtor e consumidor)
- MapStruct + Lombok
- [`petshop-commons`](../petshop-commons) — configuração do Resource Server, `TokenPropagation` (Client Credentials) e `BaseExceptionHandler`
- Testcontainers (PostgreSQL, MongoDB, Kafka) nos testes de integração
- JaCoCo — gate de 90% de cobertura de instruções

## Arquitetura de dados

- **PostgreSQL** (`clientes_db`) — clientes, pets, funcionários (via JPA)
- **MongoDB** (`customer_history_db`) — histórico de agendamentos e compras (read model alimentado por eventos Kafka)

## Endpoints

### `/api/v1/auth`
| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/verify-email` | Público | Confirma o e-mail do cliente (SDD-020) e marca `emailVerified=true` também no Keycloak (claim `email_verified`) |
| `POST` | `/resend-verification` | Público | Reenvia o link de verificação; sempre 202 (rate limit 5/min por IP) |

Login, "esqueci a senha" e troca de senha não existem mais aqui (SDD-024 F3): são as telas do Keycloak (login com "Lembrar de mim", `resetPasswordAllowed`, Account Console em `/realms/petshop/account`).

### `/api/v1/customers`
| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/register/customer` | Público | Autocadastro de cliente (senha escolhida no próprio cadastro, gravada só no Keycloak); cria o usuário no Keycloak (papel `CUSTOMER`, `emailVerified=false`) e dispara e-mails de boas-vindas e de verificação via Brevo |
| `GET` | `/list/customers` | `ADMIN`/`RECEPTIONIST` | Lista todos os clientes |
| `GET` | `/find/customer/{id}` | Autenticado (dono ou staff) | Busca cliente por id **ou CPF** — tenta como UUID primeiro, cai pra CPF se não achar |
| `PATCH` | `/update/customer/{id}` | Autenticado | Atualiza cliente |
| `DELETE` | `/delete/customer/{id}` | Autenticado | Desativa o cliente (soft-delete) e desabilita o usuário no Keycloak |
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
`POST /create` (cria também no Keycloak, sem senha, e o Keycloak envia o e-mail para o funcionário definir a senha), `GET /all` (só habilitados; também chamado via Feign pelo `staff-service`), `GET /{id}/find` (id **ou CPF** — mesma lógica de fallback do find de cliente), `PATCH /{id}/update`, `DELETE /{id}/delete` (soft-delete, `enabled=false`; usuário desabilitado no Keycloak), `PATCH /{id}/reactivate` (`enabled=true` de novo — volta a aparecer em `GET /all` e é reabilitado no Keycloak)

## Segurança

Resource Server (SDD-024): valida o access token do Keycloak via JWKS (`iss`, `exp`, `aud=customer-management`). Rotas de usuário exigem um papel do projeto (token de serviço recebe 403); as chamadas internas têm permissão própria: `GET /api/v1/staff/all` aceita `ADMIN` ou `svc:customers:staff-read` (staff-service) e `GET /api/v1/customers/find/customer/{id}` aceita usuário ou `svc:customers:customer-read` (order-service). `CustomerIdentityResolver` garante que um token do tipo `CUSTOMER` só acesse os próprios dados; `ADMIN`/`RECEPTIONIST` têm acesso amplo.

### Usuários no Keycloak (SDD-024 F3)

`IdentityProviderPortOut` (porta) / `KeycloakAdminAdapterOut` (adapter REST da Admin API) criam e mantêm os usuários: username = e-mail, `firstName`/`lastName` a partir do nome (primeira palavra / resto), atributos `user_id` (UUID local), `cpf`, `phone`, `type` (`CUSTOMER`/`STAFF`) e o realm role do papel — viram os claims do token.

- **Cadastro de cliente**: credencial = senha escolhida (`temporary=false`), `emailVerified=false`. O usuário é criado no Keycloak dentro da transação local: falha no Keycloak desfaz o cadastro local; falha no commit depois de criar no Keycloak remove o usuário de lá (compensação). E-mail já usado no Keycloak → 409.
- **Funcionário** (`POST /staff/create`): sem senha; o Keycloak envia o e-mail de ação `UPDATE_PASSWORD` (SMTP do realm). Falha de SMTP não desfaz o cadastro (o funcionário pode usar "Esqueci a senha" no login).
- **Atualização** de cliente/funcionário sincroniza nome, e-mail, telefone e papel; **desativação** desabilita o usuário e encerra as sessões (CA4); **reativação** de funcionário reabilita.
- **Verificação de e-mail** (SDD-020): ao confirmar, `emailVerified=true` no Keycloak.

### Admin de bootstrap

`BootstrapAdminRunner`: localmente, cria o `ADMIN` (`BOOTSTRAP_ADMIN_EMAIL`, padrão `admin@petshop.local`) se a tabela de staff estiver vazia; no Keycloak, garante a cada start (idempotente) que esse ADMIN existe. Com `BOOTSTRAP_ADMIN_PASSWORD`, ela é a credencial (não temporária); vazia, o usuário nasce sem senha e o Keycloak envia o e-mail para definir a senha. Não existe senha provisória gerada. Se o Keycloak estiver fora do ar, o serviço sobe e loga erro; o próximo start tenta de novo.

### Seed de funcionários de desenvolvimento (profile `dev`)

Com `SPRING_PROFILES_ACTIVE=dev` (desligado por padrão; no compose, defina no `.env` e recrie o `api-customers`), `DevStaffSeedRunner` cria, de forma idempotente, local + Keycloak:

| Nome | E-mail | Papel |
|---|---|---|
| Carla Souza | `carla@petshop.dev` | `RECEPTIONIST` |
| Paulo Lima | `paulo@petshop.dev` | `GROOMER` |
| Henrique Costa | `henrique@petshop.dev` | `VETERINARIAN` |

Senha de dev de todos: `DEV_SEED_STAFF_PASSWORD` (padrão `Petshop@dev123` — só para dev). O profile `dev` também liga logs DEBUG de segurança/SQL.

## Integração com outros serviços

Não possui clientes Feign de saída — é consumido por outros serviços:
- **`staff-service`** chama `GET /api/v1/staff/all` para montar a escala de fim de semana.
- **`order-service`** consulta dados de cliente (`EXTERNAL_API_CUSTOMER_SERVICE_URL`).

**Kafka:**
- Consome `booking-completed` (grupo `customer-management-group`) → grava histórico de agendamentos no Mongo
- Consome `order-completed` (grupo `customer-management-group`) → grava histórico de compras no Mongo
- Produz em `notification-commands` (via `NotificationPortOut`/`NotificationKafkaAdapterOut`) — e-mails de boas-vindas (`CADASTRO_CONFIRMADO`) e de verificação de e-mail (`VERIFICACAO_EMAIL`). O de senha (`RESET_SENHA`) não é mais publicado: senha é do Keycloak.

**Keycloak Admin API** (`KEYCLOAK_ADMIN_URL`): criação/atualização/desativação de usuários, ver [Usuários no Keycloak](#usuários-no-keycloak-sdd-024-f3).

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
| `KEYCLOAK_ADMIN_URL` | `http://localhost:8180` | Base da Admin API (no compose, `http://keycloak:8080`) |
| `KEYCLOAK_REALM` | `petshop` | Realm dos usuários |
| `KEYCLOAK_TOKEN_URI` | `http://localhost:8180/realms/petshop/protocol/openid-connect/token` | Token endpoint do Client Credentials |
| `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` | `customer-management-admin` / *(vazio)* | Client da Admin API (segredo = `KEYCLOAK_CLIENT_SECRET_CUSTOMER_MANAGEMENT_ADMIN` do `.env`) |
| `KEYCLOAK_WEB_CLIENT_ID` | `web` | Client do link do e-mail de definir senha (volta para `APP_FRONTEND_URL`) |
| `BOOTSTRAP_ADMIN_NAME/EMAIL/CPF/PHONE/PASSWORD` | ver acima | Dados do admin do bootstrap (`PASSWORD` opcional; vazio = sem senha + e-mail do Keycloak) |
| `SPRING_PROFILES_ACTIVE` | *(vazio)* | `dev` liga o seed de funcionários de dev |
| `DEV_SEED_STAFF_PASSWORD` | `Petshop@dev123` | Senha dos funcionários de seed (só profile `dev`) |

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

Manifests em [`k8s/api-clientes/`](../k8s/api-clientes): `deployment.yaml` (1 réplica, `api-clientes:latest`, probes `/actuator/health/{liveness,readiness}`, requests `256Mi`/`200m`, limits `512Mi`/`500m`), `configmap.yaml` e `service.yaml` (`NodePort`, `30082` → 8080). Segredos: `db-clientes-secret`, `api-clientes-secret` (Kafka) e, do `keycloak-secret` (`k8s/keycloak`), `KEYCLOAK_CLIENT_SECRET_CUSTOMER_MANAGEMENT_ADMIN`.

## Testes

```bash
./mvnw test
./mvnw verify   # inclui gate de cobertura JaCoCo (90%)
```
