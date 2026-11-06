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

## Frontend

[`web`](web) — SPA em React + Vite + TypeScript (loja, agendamento e um
painel interno simples), integrada aos microsserviços reais via `fetch`; ver
[`web/README.md`](web/README.md) para como rodar, CORS e o script de seed.

## Arquitetura

```
   Frontend ──/api/v1/**──► gateway-service (roteamento + CORS)
                                  │
                        ┌─────────────────────┐
                        │  customer-management │◄──────────────┐
                        │  (usuários no KC)     │                │
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

O Keycloak (realm `petshop`) emite os tokens; todos os serviços os validam
pelo JWKS (`petshop-commons`) e nenhum guarda senha nem assina token. Chamadas síncronas usam OpenFeign; eventos
assíncronos (histórico, notificação) usam Kafka.

| Serviço | Responsabilidade | Porta (compose) | Banco | README |
|---|---|---|---|---|
| [`gateway-service`](gateway-service) | API Gateway (Spring Cloud Gateway) — ponto único de entrada para o frontend, roteia por path e centraliza CORS | `8080` | — | [detalhes](gateway-service/README.md) |
| [`customer-management`](customer-management) | Identidade: clientes, pets, funcionários, usuários no Keycloak, histórico consultável | `8082` | PostgreSQL (`customer_db`) + MongoDB (histórico) | [detalhes](customer-management/README.md) |
| [`product-registration`](product-registration) | Catálogo de produtos e controle de estoque (reserve/release/confirm) | `8081` | MongoDB (`produtos_db`) | [detalhes](product-registration/README.md) |
| [`staff-service`](staff-service) | Disponibilidade de funcionários e escala rotativa de sábado | `8085` | PostgreSQL (`staff_db`) | [detalhes](staff-service/README.md) |
| [`booking-service`](booking-service) | Agendamento de serviços (banho/tosa/consulta), horários disponíveis | `8083` | PostgreSQL (`booking_db`) | [detalhes](booking-service/README.md) |
| [`order-service`](order-service) | Carrinho, checkout, pagamento e retirada simulados | `8086` | PostgreSQL (`order_db`) | [detalhes](order-service/README.md) |
| [`notification-service`](notification-service) | Envio de e-mails (Brevo, templates gerenciados no Brevo), consumidor Kafka puro, sem REST | `8084` | — | [detalhes](notification-service/README.md) |
| [`petshop-commons`](petshop-commons) | Biblioteca compartilhada: validação de token via JWKS (Resource Server) e `BaseExceptionHandler` (RFC 7807) | — (JAR) | — | [detalhes](petshop-commons/README.md) |

Infra compartilhada (via `docker-compose.yml`): um PostgreSQL único com um
banco por serviço relacional (`customer-management`/`staff-service`/
`booking-service`/`order-service`), um MongoDB único compartilhado por
`customer-management` (histórico) e `product-registration` (catálogo, cada
um com seu próprio banco lógico dentro da mesma instância), e um Kafka
(SASL_PLAINTEXT, um usuário por serviço produtor/consumidor).

## Stack técnica

- **Java 21**, **Spring Boot 4.0.3** em todos os 8 módulos
- `gateway-service` é o único módulo reativo (Spring Cloud Gateway,
  WebFlux/Netty) — os outros 7 são MVC/servlet
- Spring Web, Spring Data JPA, Spring Data MongoDB, Spring Kafka, Spring
  Cloud OpenFeign (sem service discovery — URLs estáticas por variável de
  ambiente), Bean Validation, Actuator
- Autenticação: Keycloak (realm `petshop`) emite os tokens; todos os serviços
  validam via JWKS (`iss`/`exp`/`aud`) com `petshop-commons`
- MapStruct + Lombok
- Testcontainers (PostgreSQL/MongoDB/Kafka) e JUnit 5 + Mockito nos testes
  de integração/unitários; gate de cobertura JaCoCo (90% de instruções nos
  7 módulos com lógica de domínio — `gateway-service` não tem esse gate,
  é só configuração de roteamento)
- Docker + Docker Compose para rodar tudo localmente; manifests Kubernetes
  em [`k8s/`](k8s) para os 6 serviços + Postgres/Mongo/Kafka/Keycloak

## Como rodar localmente

### Docker Compose (recomendado)

1. Crie um `.env` na raiz (não versionado) com pelo menos:

   ```env
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=postgres
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

   Acrescente as variáveis do Keycloak (modelo em [`.env.example`](.env.example)):
   `KC_BOOTSTRAP_ADMIN_USERNAME`/`KC_BOOTSTRAP_ADMIN_PASSWORD`, os
   `KEYCLOAK_CLIENT_SECRET_*` (customer-management-admin, booking, order e
   staff) e o SMTP dos e-mails de senha (`KEYCLOAK_SMTP_*`). Detalhes em
   [`infra/keycloak/README.md`](infra/keycloak/README.md).

2. Suba tudo:

   ```bash
   docker compose up --build
   ```

   Serviços expostos: `8080` gateway (use este a partir do frontend —
   ver [`gateway-service/README.md`](gateway-service/README.md)), `8081`
   produtos, `8082` clientes, `8083` agendamentos, `8084` notificações,
   `8085` staff, `8086` pedidos; Postgres em `5432`, MongoDB em `27017`,
   Kafka (listener externo) em `9094`. As portas `8081`–`8086` continuam
   abertas pra depuração direta de cada serviço; nenhuma delas tem CORS
   configurado (de propósito — só o gateway precisa).

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

# opção 2: builda os 8 módulos na ordem certa a partir da raiz
mvn clean install
```

Requer PostgreSQL/MongoDB/Kafka acessíveis conforme as variáveis de cada
serviço — ver a tabela de configuração no README de cada um.

## Autenticação

O Keycloak (realm `petshop`) é o IdP: emite os tokens e guarda as credenciais.
Os serviços são Resource Servers e validam o access token pelo JWKS
(assinatura, `iss`, `exp` e `aud`, com o próprio id do serviço no `aud`);
nenhum serviço guarda senha nem assina token. Papéis do realm: `ADMIN`,
`RECEPTIONIST`, `GROOMER`, `VETERINARIAN`, `CUSTOMER`.

- **Web:** login por Authorization Code + PKCE no client público `web`.
- **Cadastro:** o `customer-management` cria/atualiza/desabilita os usuários
  pela Admin API do Keycloak (client `customer-management-admin`). Não há
  senha provisória: o cliente define a senha no cadastro e o funcionário criado
  pelo admin recebe do Keycloak o e-mail para definir a senha; "Esqueci a
  senha" e a troca de senha são do Keycloak.
- **Serviço-a-serviço:** Client Credentials (`booking-service`,
  `order-service`, `staff-service`) com permissões `svc:*` por rota (o papel
  genérico `SERVICE` não existe mais).
- **Admin de bootstrap:** no start, o `customer-management` garante o `ADMIN`
  inicial no Keycloak (ver
  [`customer-management/README.md`](customer-management/README.md#admin-de-bootstrap)).

Clients, audiences, permissões, SMTP e como subir: [`infra/keycloak/README.md`](infra/keycloak/README.md).

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
`staff-service`, `booking-service`, `order-service` e o `db-keycloak` do
Keycloak), mais um `mongo/`
único compartilhado (`customer-management` + `product-registration`,
que não tem `db-produtos/` próprio), `kafka/` e o `keycloak/` (modo `start`, Postgres
em `db-keycloak/`; secrets de exemplo em `secret.example.yaml`). Padrão de estudo/uso
local: réplica única, `NodePort`, `imagePullPolicy: Never` (build da imagem
local, sem registry) e credenciais placeholder nos `Secret` — trocar antes
de qualquer uso real. Ver a seção "Kubernetes" de cada README de serviço
para a porta `NodePort` específica.

O `gateway-service` tem manifests em [`k8s/api-gateway/`](k8s/api-gateway)
(`NodePort` `30080`), com a mesma configuração do `docker-compose.yml`: rotas
para os `*-svc`, CORS, Redis do rate limiting (`redis-svc`) e o JWKS do Keycloak
(`KEYCLOAK_ISSUER_URI`/`KEYCLOAK_JWK_SET_URI`). Fora de escopo: um
Ingress/LoadBalancer na frente dele (ver SDD-028).

## Documentação adicional

- [`docs/documentacao-tecnica.md`](docs/documentacao-tecnica.md) —
  documentação técnica completa: arquitetura, o que cada módulo faz por
  dentro, cenários de funcionamento (caminho feliz e erros), configuração
  e como rodar/testar. Ponto de partida pra quem não conhece o código.

