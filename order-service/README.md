# order-service

Microsserviço de carrinho, pedidos e pagamento simulado do WebCommerce/PetShop. Orquestra o fluxo completo de compra: carrinho com reserva de estoque, checkout, pagamento mockado, retirada e histórico, coordenando `product-registration` e `customer-management` via Feign e publicando eventos de notificação/histórico via Kafka.

## Stack técnica

- Java 21, Spring Boot 4.0.3, Spring Cloud 2025.1.2 (`spring-cloud-starter-openfeign` + `-loadbalancer`, sem Eureka/Consul — URLs estáticas por variável de ambiente)
- Spring Web, Spring Data JPA (PostgreSQL), Bean Validation, Actuator, `@EnableScheduling`
- Spring Security — infraestrutura apenas; validação de JWT feita pelo `petshop-commons`
- Spring Kafka (produtor)
- MapStruct + Lombok
- [`petshop-commons`](../petshop-commons) — JWT próprio e `BaseExceptionHandler`
- JaCoCo — gate de 90% de cobertura de instruções (exclui a classe principal)

## Endpoints — `/api/v1/orders`

| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/checkout?customerId={uuid}` | Autenticado | Cria pedido a partir do carrinho aberto do cliente; retorna `201` com `Location` |
| `GET` | `/{id}` | Autenticado (dono ou staff) | Busca pedido por id |

O carrinho (`/api/v1/cart/**`) é acessível sem autenticação, permitindo montar carrinho antes do login (sem reserva de estoque nesse caso — ver abaixo).

## Fluxo de carrinho e estoque

- `addItem` — reserva estoque no `product-registration` (`reserve`) quando o usuário está autenticado; caso contrário, apenas consulta informações do produto sem reservar.
- `removeItem` — libera a reserva de estoque (`release`) se havia uma.
- **Expiração automática** (`CartExpirationScheduler`, a cada `order.cart.expiration-check-interval-ms`, padrão 10 min): carrinhos abertos há mais de `order.cart.reservation-ttl-hours` (padrão 24h) são expirados, o estoque reservado é liberado e um e-mail de lembrete de carrinho abandonado é disparado.

## Pagamento e retirada (simulados)

- `PaymentMockScheduler` (a cada `order.payment.check-interval-ms`, padrão 10s) — simula aprovação assíncrona de pagamento após `order.payment.mock-delay-seconds` (padrão 30s): confirma o estoque (`confirm`), envia e-mails e publica evento `order-completed`.
- `PickupReadyScheduler` (a cada `order.pickup.check-interval-ms`, padrão 10s) — marca pedidos pagos como prontos para retirada após uma janela aleatória entre `order.pickup.min-delay-minutes` e `order.pickup.max-delay-minutes` (padrão 1–60 min), enviando e-mail de aviso.

## Segurança

JWT próprio via [`petshop-commons`](../petshop-commons) — este serviço só valida tokens. `/api/v1/cart/**` é público; o restante exige autenticação (401 em token ausente/inválido). `CustomerIdentityResolver` resolve o `customerId` a partir do JWT para contas `CUSTOMER` (ignora qualquer id enviado pelo cliente) e garante que cada cliente só acesse os próprios pedidos/carrinho. `FeignAuthConfig` repassa o header `Authorization` da requisição recebida nas chamadas Feign, ou emite um token de serviço local (`generateServiceToken`) quando a chamada é disparada por um scheduler (sem requisição de usuário em curso).

## Integração com outros serviços

- **Feign → product-registration** (`ProductFeign`, `external.api.product-service.url`): `GET /find/{id}`, `POST /{id}/reserve`, `POST /{id}/release`, `POST /{id}/confirm`
- **Feign → customer-management** (`CustomerFeign`, `external.api.customer-service.url`): `GET /api/v1/customers/find/customer/{id}`
- **Kafka (produtor)**:
  - `notification-commands` — e-mails de pedido aguardando pagamento, pagamento confirmado, nota emitida, janela de retirada, pedido pronto e lembrete de carrinho abandonado
  - `order-completed` — evento de conclusão de pedido, consumido pelo `customer-management` para o histórico de compras

## Configuração (`application.yaml`)

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8086` | Porta HTTP |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/order_db` | URL do PostgreSQL |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` | Broker Kafka |
| `KAFKA_ORDER_USERNAME` / `PASSWORD` | — | Credenciais SASL/PLAIN do Kafka |
| `EXTERNAL_API_PRODUCT_SERVICE_URL` | `http://localhost:8081` | URL do product-registration (Feign) |
| `EXTERNAL_API_CUSTOMER_SERVICE_URL` | `http://localhost:8082` | URL do customer-management (Feign) |
| `JWT_SECRET` | *(vazio)* | Segredo HS256 compartilhado (mín. 32 bytes) |
| `JWT_EXPIRATION_MINUTES` | `60` | Validade do token |
| `order.cart.reservation-ttl-hours` | `24` | TTL do carrinho antes de expirar |
| `order.payment.mock-delay-seconds` | `30` | Delay simulado de aprovação de pagamento |
| `order.pickup.min/max-delay-minutes` | `1` / `60` | Janela simulada até o pedido ficar pronto |

## Executando localmente

```bash
cd petshop-commons && ./mvnw clean install
cd ../order-service
./mvnw spring-boot:run
```

## Docker

Build multi-stage, **contexto na raiz do repositório** (depende do `petshop-commons` local):

```bash
docker build -f order-service/Dockerfile -t order-service .
```

No `docker-compose.yml` raiz, serviço `api-order`, exposto em **`localhost:8086`** (depende de `db`, `kafka`, `api-customers`, `api-products`):

```bash
docker compose up api-order
```

## Kubernetes

Manifests em [`k8s/api-order/`](../k8s/api-order): `deployment.yaml` (1 réplica, `api-order:latest`, probes `/actuator/health/{liveness,readiness}`, requests `256Mi`/`200m`, limits `512Mi`/`500m`), `configmap.yaml`, `service.yaml` (`NodePort`, `30086` → 8086) e `secret.yaml` (credenciais Kafka). Banco dedicado em [`k8s/db-order/`](../k8s/db-order); JWT via secret compartilhado `jwt-secret`.

## Testes

```bash
./mvnw test
./mvnw verify   # inclui gate de cobertura JaCoCo (90%)
```
