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
- Sem persistência própria, sem `petshop-commons` — não tem lógica de
  domínio nem autentica ninguém (quem continua validando o JWT e
  rejeitando requisição é cada serviço downstream, como hoje).
- Redis (`spring-boot-starter-data-redis-reactive`) só para os baldes do
  rate limiting, e `spring-security-oauth2-jose` (só o
  `NimbusReactiveJwtDecoder`, não o starter de resource server, que ligaria
  o Spring Security WebFlux) só para ler o `sub` do Bearer validado via
  JWKS do Keycloak — ver [Rate limiting](#rate-limiting).

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

## Rate limiting

Limite por cliente em todas as rotas (SDD-022), com o `RedisRateLimiter`
do Spring Cloud Gateway (token bucket — balde de fichas — guardado no
Redis), aplicado por um `GlobalFilter` próprio
(`ratelimit/RateLimitGatewayFilter`). Não é o filtro `RequestRateLimiter`
por rota porque os grupos cruzam as rotas e dependem do método HTTP, e o
filtro nativo devolve o 429 sem corpo nem `Retry-After`.

| Grupo | Requisições | Chave | Limite padrão |
|---|---|---|---|
| `public-read` | `GET /api/v1/products/**`, `GET /api/v1/bookings/available-slots` | IP | 20 req/s, burst 40 |
| `registration` | `POST /api/v1/customers/register/**` | IP | 5 req/min (1 ficha/s, 12 fichas por requisição, balde 60) |
| `authenticated` | todo o resto que é roteado | `user:<sub>` se o Bearer tiver assinatura válida, senão `ip:<ip>` | 10 req/s, burst 20 |

- **Chave por usuário**: o gateway valida o `Authorization: Bearer` com a
  chave pública do JWKS do Keycloak (assinatura RS256, `iss` e `exp`;
  SDD-024), só para não aceitar um `sub` forjado — sem guardar segredo
  nenhum. O `aud` não é checado (o gateway não é audience de nenhum token).
  Token ausente, inválido ou expirado (ou Keycloak fora do ar) cai na chave
  por IP — o gateway **não** rejeita a requisição por isso.
- **IP**: é o do peer TCP. O gateway é a borda (sem LB/ingress na frente,
  ver SDD-028), então `X-Forwarded-For` enviado pelo cliente é ignorado.
- **Excedeu**: `429` com `ProblemDetail` (`application/problem+json`,
  `title: "Too many requests"`, mesmo formato do limite de login do
  `customer-management`) e `Retry-After` em segundos. Os headers
  `X-RateLimit-Remaining`, `-Replenish-Rate`, `-Burst-Capacity` e
  `-Requested-Tokens` do `RedisRateLimiter` vão em toda resposta limitada.
- **Redis fora do ar → fail-open**: a requisição passa sem limite e o
  gateway loga `WARN` (na primeira falha e depois no máximo 1x/min; volta
  a `INFO` quando o Redis responde). Decisão da SDD-022 para o limite não
  virar ponto único de falha. Timeouts do Redis são curtos (500 ms) para a
  requisição não ficar presa esperando. O log `ERROR` por requisição do
  próprio `RedisRateLimiter` está desligado no `application.yaml`.
- **Fora do limite**: `/actuator/**` (servido pelo próprio gateway, não
  passa pelas rotas) e Swagger (`/v3/api-docs/**`, `/swagger-ui/**`,
  `/swagger-ui.html`), via `gateway.rate-limit.excluded-paths`.
- O limite de login/forgot-password do `customer-management` continua
  existindo como segunda camada.

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
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | `localhost` / `6379` | Redis do rate limiting |
| `SPRING_DATA_REDIS_TIMEOUT` / `SPRING_DATA_REDIS_CONNECT_TIMEOUT` | `500ms` / `500ms` | Quanto esperar o Redis antes de entrar em fail-open |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/petshop` | `iss` esperado no Bearer |
| `KEYCLOAK_JWK_SET_URI` | `http://localhost:8180/realms/petshop/protocol/openid-connect/certs` | JWKS do Keycloak (no compose, `http://keycloak:8080/...`); vazio = tudo limitado por IP, com `WARN` na subida |
| `RATE_LIMIT_PUBLIC_READ_REPLENISH_RATE` / `_BURST_CAPACITY` / `_REQUESTED_TOKENS` | `20` / `40` / `1` | Grupo `public-read` |
| `RATE_LIMIT_REGISTRATION_REPLENISH_RATE` / `_BURST_CAPACITY` / `_REQUESTED_TOKENS` | `1` / `60` / `12` | Grupo `registration` |
| `RATE_LIMIT_AUTHENTICATED_REPLENISH_RATE` / `_BURST_CAPACITY` / `_REQUESTED_TOKENS` | `10` / `20` / `1` | Grupo `authenticated` |

Os padrões (`localhost:808x`) batem com as portas que cada serviço já expõe
fora do Docker, então `./mvnw spring-boot:run` funciona sem configurar nada
se os outros serviços também estiverem rodando localmente nessas portas.

## Executando localmente

```bash
./mvnw spring-boot:run
```

Requer os serviços downstream no ar nas portas padrão (`8081`–`8083`,
`8085`, `8086`) — ver README de cada um. Para o rate limiting valer, um
Redis em `localhost:6379` (`docker compose up -d redis`); sem ele o
gateway sobe e funciona em fail-open.

## Docker

```bash
docker build -f gateway-service/Dockerfile -t gateway-service .
```

No `docker-compose.yml` raiz, serviço `api-gateway`, exposto em
**`localhost:8080`**, com as 5 URLs já apontando para os nomes de container
corretos (`depends_on` os 5 serviços REST e o `redis` saudável), mais
`SPRING_DATA_REDIS_HOST=redis` e as URLs do Keycloak (`KEYCLOAK_ISSUER_URI`,
`KEYCLOAK_JWK_SET_URI`):

```bash
docker compose up api-gateway
```

## O que não está incluído (de propósito, fora do escopo deste módulo)

- **Autenticação no gateway** — a assinatura do Bearer é conferida só
  para escolher a chave do rate limit; quem autentica e rejeita continua
  sendo cada serviço, via `petshop-commons`. Centralizar a autenticação
  no gateway está ligado à pendência maior
  (`docs/pendencias/seguranca-autenticacao.md` — Keycloak/JWKS).
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
