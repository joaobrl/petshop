# product-registration

Microsserviço de cadastro e controle de estoque de produtos do WebCommerce/PetShop. Expõe o catálogo de produtos e o mecanismo de reserva/confirmação/liberação de estoque usado pelo fluxo de carrinho e pedidos do `order-service`.

## Stack técnica

- Java 21, Spring Boot 4.0.3 (`spring-boot-starter-parent`)
- Spring Web (MVC), Spring Data MongoDB, Spring Security, Bean Validation, Actuator
- MongoDB (driver reativo síncrono padrão do Spring Data) — migrado de PostgreSQL nesta sessão; ver seção Persistência
- MapStruct 1.5.5 + Lombok
- [`petshop-commons`](../petshop-commons) — JWT próprio (substitui Keycloak) e `BaseExceptionHandler`
- JaCoCo — gate de cobertura de 90% de instruções (exclui a classe principal e os mappers gerados pelo MapStruct)
- Não usa Feign, Kafka, Eureka/Consul ou service discovery — é consumido apenas via REST direto por outros serviços.

## Persistência (MongoDB)

Coleção `products`, documento `ProductDocument` (`infrastructure/persistence/mongo/entity`) — mapeamento 1:1 com o domínio `Product`, incluindo `DimensionsDocument` como sub-documento embutido (equivalente ao antigo `@Embeddable` do JPA, sem anotação nenhuma — o driver do Spring Data Mongo serializa objetos aninhados automaticamente).

**Id continua `Long`, não `ObjectId`/`String`** — de propósito: `order-service` (via Feign) e o restante do sistema já referenciam produto por `Long id` (`CartItem`/`OrderItem`, endpoints de reserve/release/confirm); mudar o tipo aqui exigiria propagar a mudança por lugares que não têm nada a ver com "qual banco guarda produto". Como o Mongo não tem `IDENTITY`/`SERIAL` nativo, o id é gerado por `ProductSequenceGenerator` (`infrastructure/persistence/mongo/sequence`) — um contador atômico (`findAndModify` com `$inc`) numa coleção auxiliar `database_sequences`, o padrão usual do Spring Data Mongo pra emular auto-increment. Ids podem ficar com "buracos" se um insert falhar depois do contador já ter incrementado (ex.: nome duplicado) — mesmo comportamento que `SERIAL` do Postgres teria numa transação revertida, não é bug.

O índice único em `name` (`@Indexed(unique = true)` em `ProductDocument`) só tem efeito com `spring.data.mongodb.auto-index-creation: true` — sem isso a anotação não cria o índice de verdade (Spring Data não cria índice automaticamente por padrão, decisão deliberada deles pra não surpreender em produção com coleções grandes; aqui é seguro habilitar).

Sem transação: os métodos de `ProductService` não têm mais `@Transactional` — um MongoDB standalone (sem replica set, que é como sobe no `docker-compose`/k8s deste projeto) não suporta transação multi-documento, e cada operação já é uma única escrita atômica por natureza do próprio Mongo.

## Arquitetura

Organização hexagonal simples: `api/rest` (controller + interface) → `core`/domínio → `infrastructure`. Pacote base: `com.petshop.product_registration`.

## Endpoints — `/api/v1/products`

| Método | Path | Acesso | Descrição |
|---|---|---|---|
| `POST` | `/register` | `ROLE_ADMIN` | Cadastra um novo produto |
| `GET` | `/list` | Público, token opcional | Lista todos os produtos — estoque só aparece pra quem manda um token de `STAFF` (ver Segurança) |
| `GET` | `/find/{id}` | Público, token opcional | Busca produto por id — mesma regra de estoque do `/list` (só `STAFF`) |
| `PATCH` | `/update/{id}` | `ROLE_ADMIN` | Atualiza produto |
| `DELETE` | `/delete/{id}` | `ROLE_ADMIN` | Remove produto |
| `POST` | `/{id}/reserve` | Autenticado | Reserva quantidade de estoque (incrementa `reservedStock`, não toca em `stock`) — usado pelo `order-service` ao adicionar item ao carrinho |
| `POST` | `/{id}/release` | Autenticado | Libera estoque reservado — usado quando um item é removido do carrinho ou o carrinho expira |
| `POST` | `/{id}/confirm` | Autenticado | Confirma a venda: decrementa `stock` e `reservedStock` — usado na conclusão do pagamento |

O `ProductResponseDto` expõe `stock`, `reservedStock`, `reservedPercentage`
(`reservedStock / stock * 100`, `0` se `stock` for `0`) e o campo calculado
`availableStock` (`stock - reservedStock`). Em `GET /list`, esses 4 campos
só entram no JSON se quem pediu tiver um token válido de `STAFF`
(`AccountType.STAFF` — funcionário logado via `customer-management`);
sem token ou com token de `CUSTOMER`, o campo simplesmente não aparece no
corpo da resposta (não vem `null`, vem omitido — `@JsonInclude(NON_NULL)`).

## Segurança

JWT próprio compartilhado via [`petshop-commons`](../petshop-commons) (`JwtService`/`JwtAuthenticationFilter`) — este serviço apenas **valida** tokens assinados com o mesmo `JWT_SECRET` usado pelos demais serviços; não emite tokens. Sessão stateless, CSRF desabilitado, 401 em caso de token ausente/inválido em endpoint que exige autenticação.

O `JwtAuthenticationFilter` roda em toda requisição, mesmo em endpoint `permitAll()` — se vier um Bearer válido, o `AuthenticatedUser` é populado no contexto de qualquer forma. `GET /list` usa isso pra token **opcional**: endpoint continua público, mas só mostra estoque pra quem manda um token de `STAFF` (ver tabela de endpoints acima).

## Integração com outros serviços

Não chama nenhum outro serviço. É **consumido** pelo [`order-service`](../order-service) via Feign (`ProductFeign`, `ProductStockAdapterOut`) para consulta de produto e para o ciclo reserve → release/confirm do carrinho.

## Configuração (`application.yaml`)

| Variável | Padrão | Descrição |
|---|---|---|
| `SPRING_DATA_MONGODB_URI` | `mongodb://localhost:27017/produtos_db` | URI do MongoDB |
| `JWT_SECRET` | *(vazio)* | Segredo HS256 compartilhado entre serviços (mín. 32 bytes) |
| `JWT_EXPIRATION_MINUTES` | `60` | Validade do token |

Porta padrão: **8080** (interna; ver mapeamento externo abaixo).

## Executando localmente

Requer MongoDB disponível e o `petshop-commons` instalado no `.m2` local:

```bash
cd petshop-commons && ./mvnw clean install
cd ../product-registration
./mvnw spring-boot:run
```

## Docker

O `Dockerfile` é multi-stage e **precisa ser construído com o contexto na raiz do repositório**, pois depende do `petshop-commons` local:

```bash
docker build -f product-registration/Dockerfile -t product-registration .
```

No `docker-compose.yml` da raiz, o serviço é `api-products`, exposto em **`localhost:8081`** (mapeado para a porta 8080 do container):

```bash
docker compose up api-products
```

## Kubernetes

Manifests em [`k8s/api-produtos/`](../k8s/api-produtos): `deployment.yaml` (1 réplica, imagem local `api-produtos:latest`, probes de liveness/readiness em `/actuator/health/{liveness,readiness}`, requests `256Mi`/`200m`, limits `512Mi`/`500m`), `configmap.yaml` (aponta pro `mongo-svc` compartilhado, ver [`k8s/mongo/`](../k8s/mongo)) e `service.yaml` (`NodePort`, `30081` → 8080). Sem secret de banco próprio — só `jwt-secret` (Mongo aqui não tem autenticação configurada, mesmo padrão do `customer-management`). O antigo `k8s/db-produtos/` (Postgres dedicado) foi removido na migração pra Mongo.

## Testes

```bash
./mvnw test
./mvnw verify   # roda também o gate de cobertura JaCoCo (90%)
```
