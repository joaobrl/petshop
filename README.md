# WebCommerce — PetShop

Sistema de petshop construído como estudo de arquitetura de microsserviços em
Java/Spring Boot. Cobre dois fluxos de negócio principais — **venda de
produtos** (catálogo, carrinho, pagamento simulado) e **agendamento de
serviços** (banho, tosa, consulta veterinária) — mais os serviços de suporte
necessários para sustentá-los (identidade, escala de funcionários,
notificação por e-mail).

> Projeto de estudo, sem deploy público. O objetivo é simular o sistema
> completo (múltiplos serviços, mensageria, bancos poliglota, autenticação,
> testes de integração, CI) e servir de material de aprendizado.

## Arquitetura

```
                        ┌─────────────────────┐
                        │  customer-management │◄──────────────┐
                        │  (emite o JWT)        │                │
                        └─────────┬─────────────┘                │
                                  ▲ Feign                          │ Feign
                                  │                                │
┌────────────────┐   Feign   ┌───┴──────────┐   Feign      ┌──────┴────────┐
│  staff-service  │◄──────────┤ booking-svc  │              │ order-service │
└─────────────────┘           └──────┬───────┘              └───┬───────┬───┘
                                      │ Kafka                     │Feign  │Feign
                                      ▼                           ▼       ▼
                         ┌────────────────────┐      ┌─────────────────┐ ┌────────────────────┐
                         │ notification-service│◄─────┤ customer-mgmt    │ │ product-registration│
                         │  (Brevo)             │      │ (histórico Mongo)│ └────────────────────┘
                         └─────────────────────┘      └─────────────────┘
```

Todos os serviços validam o mesmo JWT (`petshop-commons`); só o
`customer-management` o emite. Chamadas síncronas usam OpenFeign; eventos
assíncronos (histórico, notificação) usam Kafka.

| Serviço | Responsabilidade | Porta (compose) | Banco | README |
|---|---|---|---|---|
| [`customer-management`](customer-management) | Identidade: clientes, pets, funcionários, login/JWT, histórico consultável | `8082` | PostgreSQL (`customer_db`) + MongoDB (histórico) | [detalhes](customer-management/README.md) |
| [`product-registration`](product-registration) | Catálogo de produtos e controle de estoque (reserve/release/confirm) | `8081` | MongoDB (`produtos_db`) | [detalhes](product-registration/README.md) |
| [`staff-service`](staff-service) | Disponibilidade de funcionários e escala rotativa de sábado | `8085` | PostgreSQL (`staff_db`) | [detalhes](staff-service/README.md) |
| [`booking-service`](booking-service) | Agendamento de serviços (banho/tosa/consulta), horários disponíveis | `8083` | PostgreSQL (`booking_db`) | [detalhes](booking-service/README.md) |
| [`order-service`](order-service) | Carrinho, checkout, pagamento e retirada simulados | `8086` | PostgreSQL (`order_db`) | [detalhes](order-service/README.md) |
| [`notification-service`](notification-service) | Envio de e-mails (Brevo, templates gerenciados no Brevo), consumidor Kafka puro, sem REST | `8084` | — | [detalhes](notification-service/README.md) |
| [`petshop-commons`](petshop-commons) | Biblioteca compartilhada: JWT (`JwtService`/`JwtAuthenticationFilter`) e `BaseExceptionHandler` (RFC 7807) | — (JAR) | — | [detalhes](petshop-commons/README.md) |

Infra compartilhada (via `docker-compose.yml`): um PostgreSQL único com um
banco por serviço relacional (`customer-management`/`staff-service`/
`booking-service`/`order-service`), um MongoDB único compartilhado por
`customer-management` (histórico) e `product-registration` (catálogo, cada
um com seu próprio banco lógico dentro da mesma instância), e um Kafka
(SASL_PLAINTEXT, um usuário por serviço produtor/consumidor).

## Stack técnica

- **Java 21**, **Spring Boot 4.0.3** em todos os 7 módulos
- Spring Web, Spring Data JPA, Spring Data MongoDB, Spring Kafka, Spring
  Cloud OpenFeign (sem service discovery — URLs estáticas por variável de
  ambiente), Bean Validation, Actuator
- Autenticação: JWT próprio (HS256, `io.jsonwebtoken`), emitido só pelo
  `customer-management`, validado por todos via `petshop-commons`
- MapStruct + Lombok
- Testcontainers (PostgreSQL/MongoDB/Kafka) e JUnit 5 + Mockito nos testes
  de integração/unitários; gate de cobertura JaCoCo (90% de instruções em
  todos os 7 módulos)
- Docker + Docker Compose para rodar tudo localmente; manifests Kubernetes
  em [`k8s/`](k8s) para os 6 serviços + Postgres/Mongo/Kafka

## Como rodar localmente

### Docker Compose (recomendado)

1. Crie um `.env` na raiz (não versionado) com pelo menos:

   ```env
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres
   JWT_SECRET=uma-chave-de-pelo-menos-32-bytes-aqui
   JWT_EXPIRATION_MINUTES=15
   KAFKA_BOOKING_USERNAME=booking
   KAFKA_BOOKING_PASSWORD=booking123
   KAFKA_ORDER_USERNAME=order
   KAFKA_ORDER_PASSWORD=order123
   KAFKA_CUSTOMER_USERNAME=customer
   KAFKA_CUSTOMER_PASSWORD=customer123
   KAFKA_NOTIFICATION_USERNAME=notification
   KAFKA_NOTIFICATION_PASSWORD=notification123
   BREVO_API_KEY=xkeysib-xxxx
   BREVO_TEMPLATE_REGISTRATION_CONFIRMED=1
   BREVO_TEMPLATE_BOOKING_SCHEDULED=2
   BREVO_TEMPLATE_BOOKING_COMPLETED=3
   BREVO_TEMPLATE_BOOKING_CANCELED=4
   BREVO_TEMPLATE_ORDER_AWAITING_PAYMENT=5
   BREVO_TEMPLATE_PAYMENT_CONFIRMED=6
   BREVO_TEMPLATE_INVOICE_ISSUED=7
   BREVO_TEMPLATE_PICKUP_WINDOW=8
   BREVO_TEMPLATE_ORDER_READY_FOR_PICKUP=9
   BREVO_TEMPLATE_CART_REMINDER=10
   BREVO_TEMPLATE_PASSWORD_RESET=11
   ```

2. Suba tudo:

   ```bash
   docker compose up --build
   ```

   Serviços expostos: `8081` produtos, `8082` clientes, `8083` agendamentos,
   `8084` notificações, `8085` staff, `8086` pedidos; Postgres em `5432`,
   MongoDB em `27017`, Kafka (listener externo) em `9094`.

Sem `BREVO_API_KEY`/`BREVO_TEMPLATE_*` o `notification-service` sobe
normalmente, mas o envio de e-mail falha silenciosamente (só logado).

### Rodando um serviço individualmente

`petshop-commons` precisa estar instalada no `.m2` local antes de qualquer
outro módulo. Isso pode ser feito manualmente ou de uma vez só, buildando
tudo a partir do pom agregador na raiz (`packaging=pom`, sem `<parent>` —
cada serviço continua herdando `spring-boot-starter-parent` diretamente;
Maven resolve a ordem certa pelo grafo de `<dependency>` do reactor):

```bash
# opção 1: manual, só o petshop-commons
cd petshop-commons && ./mvnw clean install
cd ../<algum-servico>
./mvnw spring-boot:run

# opção 2: builda os 7 módulos na ordem certa a partir da raiz
mvn clean install
```

Requer PostgreSQL/MongoDB/Kafka acessíveis conforme as variáveis de cada
serviço — ver a tabela de configuração no README de cada um.

## Autenticação

Esquema de JWT próprio (HS256), não Keycloak/OAuth2 — só o
`customer-management` emite tokens; os demais serviços só validam,
compartilhando o mesmo `JWT_SECRET`. Papéis (`Role`): `ADMIN`,
`RECEPTIONIST`, `GROOMER`, `VETERINARIAN`, `CUSTOMER`, `SERVICE` (token de
serviço-a-serviço). No primeiro start, o `customer-management` cria um
usuário `ADMIN` de bootstrap automaticamente (ver
[`customer-management/README.md`](customer-management/README.md#admin-de-bootstrap)).

> Uma fase anterior do projeto era baseada em Keycloak/OAuth2 (realm, client
> `petshop-app` etc.); isso foi substituído pelo esquema de JWT próprio
> acima, e os manifests `k8s/keycloak/` correspondentes, além do banco
> `keycloak_db` em `postgres-init/init.sql`, já foram removidos.

## Comunicação entre serviços

**Síncrona (OpenFeign):**
- `booking-service` → `staff-service` (disponibilidade/escala)
- `staff-service` → `customer-management` (registro de funcionários)
- `order-service` → `product-registration` (produto, reserve/release/confirm)
- `order-service` → `customer-management` (dados do cliente)

**Assíncrona (Kafka, SASL_PLAINTEXT):**
- `notification-commands` — comandos de e-mail; produzido por
  `booking-service`, `order-service` e `customer-management`, consumido por
  `notification-service`
- `booking-scheduled` / `booking-canceled` / `booking-completed` — eventos
  de histórico de agendamento; produzidos por `booking-service`, consumidos
  por `customer-management`
- `order-completed` — evento de conclusão de pedido; produzido por
  `order-service`, consumido por `customer-management`

## Testes e CI

Cada serviço roda `./mvnw test` (unitários) e `./mvnw verify` (inclui
integração com Testcontainers + gate de cobertura JaCoCo). O pipeline
[`.github/workflows/ci.yml`](.github/workflows/ci.yml) roda a cada
push/PR em qualquer branch: builda `petshop-commons` primeiro (dependência
compartilhada, instalada no repositório Maven local), depois cada serviço
com `mvn clean verify`. Só validação — sem etapa de deploy.

## Kubernetes

Manifests em [`k8s/`](k8s): um `Deployment`/`Service`/`ConfigMap` por
serviço de API (`api-*`), um Postgres dedicado por serviço relacional com
dados (`db-*`, com `PersistentVolumeClaim` — `customer-management`,
`staff-service`, `booking-service`, `order-service`), mais um `mongo/`
único compartilhado (`customer-management` + `product-registration`,
que não tem `db-produtos/` próprio), `kafka/` e o secret compartilhado
[`jwt-secret.yaml`](k8s/jwt-secret.yaml). Padrão de estudo/uso
local: réplica única, `NodePort`, `imagePullPolicy: Never` (build da imagem
local, sem registry) e credenciais placeholder nos `Secret` — trocar antes
de qualquer uso real. Ver a seção "Kubernetes" de cada README de serviço
para a porta `NodePort` específica.

## Documentação adicional

- [`docs/documentacao-tecnica.md`](docs/documentacao-tecnica.md) —
  documentação técnica completa: arquitetura, o que cada módulo faz por
  dentro, cenários de funcionamento (caminho feliz e erros), configuração
  e como rodar/testar. Ponto de partida pra quem não conhece o código.

