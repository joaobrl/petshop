# gateway-service

API Gateway do WebCommerce/PetShop. É o único ponto de entrada HTTP pensado
para o frontend: roteia por path para os 5 microsserviços que expõem REST
(`customer-management`, `product-registration`, `staff-service`,
`booking-service`, `order-service`) e centraliza CORS, em vez de cada
serviço expor sua própria porta e sua própria configuração de CORS
diretamente para o browser. Resolve os itens 1.1 (CORS) e 1.2 (seis URLs
base, sem gateway) do backlog de pendências para conectar um frontend.

`notification-service` não tem API REST (só consumidor Kafka) e por isso
não tem rota aqui.

## Stack técnica

- Java 21, Spring Boot 4.0.3, Spring Cloud 2025.1.2 (`spring-cloud-starter-gateway`)
- Modelo **reativo** (WebFlux/Netty) — diferente dos outros serviços do
  repositório, que são todos MVC/servlet. É assim que o Spring Cloud
  Gateway funciona; não há mistura de modelo dentro deste módulo.
- Sem persistência própria, sem `petshop-commons` — é puro roteamento de
  configuração, não tem lógica de domínio nem valida o JWT (quem continua
  validando é cada serviço downstream, como hoje).

## Como funciona o roteamento

Nenhum dos 6 serviços colide em prefixo de path (`/api/v1/<recurso>`), então
o gateway não precisa reescrever path nenhum — o path que chega aqui é
exatamente o mesmo que cada serviço já espera receber direto:

| Rota (`spring.cloud.gateway.server.webflux.routes[].id`) | Paths | Destino (`uri`) |
|---|---|---|
| `customer-management` | `/api/v1/auth/**`, `/api/v1/customers/**`, `/api/v1/staff/**`, `/api/v1/history/**` | `GATEWAY_CUSTOMERS_URL` |
| `product-registration` | `/api/v1/products/**` | `GATEWAY_PRODUCTS_URL` |
| `staff-service` | `/api/v1/availability/**`, `/api/v1/weekend-allocation/**` | `GATEWAY_STAFF_URL` |
| `booking-service` | `/api/v1/bookings/**` | `GATEWAY_BOOKING_URL` |
| `order-service` | `/api/v1/orders/**`, `/api/v1/cart/**` | `GATEWAY_ORDER_URL` |

Um frontend passa a chamar só `http://localhost:8080/api/v1/...` (ou a URL
do gateway em produção) — nunca mais as 5 portas diretamente.

## CORS

Configurado uma única vez, em
`spring.cloud.gateway.server.webflux.globalcors` (`application.yaml`) — os 5 serviços downstream não precisam mais de
`CorsConfigurationSource` nenhuma, porque o browser só fala com o gateway.
Métodos liberados: `GET/POST/PUT/PATCH/DELETE/OPTIONS`; header
`Authorization` liberado; `allowCredentials: false` (o modelo de auth é
Bearer token no header, não cookie de sessão — ver
`docs/pendencias-frontend.md`, item 2.2).

## Configuração (`application.yaml`)

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8080` | Porta HTTP do gateway |
| `GATEWAY_PRODUCTS_URL` | `http://localhost:8081` | URL do `product-registration` |
| `GATEWAY_CUSTOMERS_URL` | `http://localhost:8082` | URL do `customer-management` |
| `GATEWAY_STAFF_URL` | `http://localhost:8085` | URL do `staff-service` |
| `GATEWAY_BOOKING_URL` | `http://localhost:8083` | URL do `booking-service` |
| `GATEWAY_ORDER_URL` | `http://localhost:8086` | URL do `order-service` |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Origens liberadas no CORS, separadas por vírgula |

Os padrões (`localhost:808x`) batem com as portas que cada serviço já expõe
fora do Docker, então `./mvnw spring-boot:run` funciona sem configurar nada
se os outros serviços também estiverem rodando localmente nessas portas.

## Executando localmente

```bash
./mvnw spring-boot:run
```

Requer os serviços downstream no ar nas portas padrão (`8081`–`8083`,
`8085`, `8086`) — ver README de cada um.

## Docker

```bash
docker build -f gateway-service/Dockerfile -t gateway-service .
```

No `docker-compose.yml` raiz, serviço `api-gateway`, exposto em
**`localhost:8080`**, com as 5 URLs já apontando para os nomes de container
corretos (`depends_on` os 5 serviços REST):

```bash
docker compose up api-gateway
```

## O que não está incluído (de propósito, fora do escopo deste módulo)

- **Validação de JWT no gateway** — cada serviço continua validando o
  próprio token via `petshop-commons`, como hoje. Centralizar isso no
  gateway é um passo natural depois, mas está ligado à pendência maior de
  autenticação (`docs/pendencias/seguranca-autenticacao.md` — Keycloak/JWKS),
  não faz sentido resolver pela metade aqui.
- **Rate limiting** — hoje só existe em login/forgot-password
  (`customer-management`). O gateway seria o lugar natural de centralizar
  isso no futuro (`RequestRateLimiter` do Spring Cloud Gateway + Redis),
  mas não foi adicionado ainda.
- **Service discovery (Eureka/Consul)** — as URLs dos serviços downstream
  continuam estáticas por variável de ambiente, mesmo padrão já usado nas
  chamadas Feign entre os outros serviços.

## Testes

```bash
./mvnw test
```

Cobre só o essencial (contexto sobe, as 5 rotas são declaradas) — não há
gate de cobertura JaCoCo neste módulo, já que não há lógica de negócio
própria para medir, só configuração de roteamento.
