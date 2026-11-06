# Documentação Técnica — WebCommerce PetShop

> Público-alvo: desenvolvedores novos no projeto e revisores externos, sem
> conhecimento prévio do código. Este documento explica o que o sistema
> resolve, como os serviços se comunicam, o que cada módulo faz por dentro,
> e como os fluxos de negócio se comportam no caminho feliz e nos erros.
> Cada README de módulo (`<serviço>/README.md`) traz a referência rápida de
> configuração/endpoints; este documento foca em explicar o *porquê* e o
> *como* por trás do código.

## Índice

1. [Visão geral do sistema](#1-visão-geral-do-sistema)
2. [Arquitetura](#2-arquitetura)
3. [Módulos](#3-módulos)
4. [Cenários de funcionamento](#4-cenários-de-funcionamento)
5. [Configuração e variáveis de ambiente](#5-configuração-e-variáveis-de-ambiente)
6. [Como rodar localmente](#6-como-rodar-localmente)
7. [Como testar](#7-como-testar)

---

## 1. Visão geral do sistema

O WebCommerce PetShop é um sistema de e-commerce e agendamento de serviços
para um petshop, construído como projeto de estudo de arquitetura de
microsserviços em Java/Spring Boot. Não tem deploy público — o objetivo é
simular, de ponta a ponta, os problemas reais de um sistema distribuído:
múltiplos bancos de dados, comunicação síncrona e assíncrona entre
serviços, autenticação compartilhada, testes de integração e CI.

O sistema cobre dois fluxos de negócio independentes:

- **Venda de produtos** — catálogo, carrinho, checkout, pagamento simulado
  e retirada em loja (`product-registration` + `order-service`).
- **Agendamento de serviços** — banho, tosa e consulta veterinária, com
  checagem de disponibilidade de funcionários e escala rotativa de sábado
  (`booking-service` + `staff-service`).

Esses dois fluxos compartilham a mesma base de identidade
(`customer-management`, que cadastra clientes/pets/funcionários e emite o
token usado em todo o sistema) e o mesmo canal de notificação por e-mail
(`notification-service`).

Não existe front-end neste repositório: o sistema é consumido via API REST
(mais um consumidor Kafka puro, no caso das notificações).

---

## 2. Arquitetura

### 2.1 Serviços e portas

| Serviço | Responsabilidade | Porta | Banco |
|---|---|---|---|
| `gateway-service` | API Gateway (Spring Cloud Gateway) — ponto único de entrada para o frontend, roteia por path e centraliza CORS | `8080` | — |
| `customer-management` | Identidade: clientes, pets, funcionários (usuários no Keycloak), histórico | `8082` | PostgreSQL + MongoDB |
| `product-registration` | Catálogo e controle de estoque | `8081` | MongoDB |
| `staff-service` | Disponibilidade e escala de funcionários | `8085` | PostgreSQL |
| `booking-service` | Agendamento de serviços | `8083` | PostgreSQL |
| `order-service` | Carrinho, checkout, pagamento simulado | `8086` | PostgreSQL |
| `notification-service` | Envio de e-mail (Brevo, templates gerenciados no Brevo) | `8084` | — |
| `petshop-commons` | Biblioteca compartilhada (validação de token via JWKS, tratamento de erro) | — (JAR) | — |

### 2.2 Diagrama de componentes

```mermaid
graph TB
    FE["Frontend"] -- "/api/v1/**" --> GW["gateway-service<br/>(roteamento + CORS)"]
    GW --> CM
    GW --> PR
    GW --> SS
    GW --> BS
    GW --> OS

    subgraph Identidade
        CM["customer-management<br/>(cria usuários no Keycloak)"]
    end
    subgraph Catálogo
        PR["product-registration"]
    end
    subgraph Escala
        SS["staff-service"]
    end
    subgraph Agendamento
        BS["booking-service"]
    end
    subgraph Pedidos
        OS["order-service"]
    end
    subgraph Notificação
        NS["notification-service<br/>(Brevo)"]
    end

    BS -- "Feign: disponibilidade/escala" --> SS
    SS -- "Feign: registro de funcionários" --> CM
    OS -- "Feign: reserve/release/confirm estoque" --> PR
    OS -- "Feign: dados do cliente" --> CM

    BS -- "Kafka: booking-scheduled/completed/canceled" --> CM
    OS -- "Kafka: order-completed" --> CM
    BS -- "Kafka: notification-commands" --> NS
    OS -- "Kafka: notification-commands" --> NS
    CM -- "Kafka: notification-commands" --> NS
```

Todos os serviços (exceto `notification-service`, que não expõe recurso
protegido) validam o access token emitido pelo Keycloak (realm `petshop`) via
`petshop-commons`; nenhum serviço assina token. Chamadas síncronas usam **OpenFeign** (sem
service discovery — URL estática por variável de ambiente); eventos
assíncronos usam **Kafka** (`SASL_PLAINTEXT`, um usuário por
serviço produtor/consumidor).

`gateway-service` fica na frente dos 5 serviços REST (não do
`notification-service`, que não tem API), mas não participa dessa validação
de token — é puro roteamento por path, cada serviço continua validando o
próprio token exatamente como antes. Ver [3.8](#38-gateway-service).

### 2.3 Modelo de autenticação

O **Keycloak** (realm `petshop`, ver [`infra/keycloak/README.md`](../infra/keycloak/README.md))
emite os tokens e guarda as credenciais. Nenhum serviço tem material para
assinar token nem guarda senha; todos são Resource Servers, com a validação
implementada em `petshop-commons`:

- **Validação (JWKS)** — `KeycloakResourceServerConfig` / `KeycloakJwtDecoders`:
  o `JwtDecoder` busca a chave pública no JWKS do Keycloak (em cache,
  rebuscado em `kid` novo) e valida assinatura RS256, `exp`, `iss` e `aud`
  (cada serviço exige o próprio id em `keycloak.audience`). Propriedades:
  `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI` e `KEYCLOAK_AUDIENCE`. Token
  ausente/inválido → 401; a rota que exige papel/permissão que o token não
  tem → 403.
- **Login do web** — Authorization Code + PKCE no client público `web`; as
  telas de login, "Esqueci a senha" e troca de senha são do Keycloak.
- **Chamadas serviço-a-serviço** — Client Credentials: `booking-service`,
  `order-service` e `staff-service` têm cada um um client confidencial no
  realm e pedem o token ao Keycloak (`KEYCLOAK_TOKEN_URI`,
  `KEYCLOAK_CLIENT_ID`/`KEYCLOAK_CLIENT_SECRET`). O token de serviço só
  carrega authorities `svc:<destino>:<ação>` (nunca `ROLE_*`) e o `aud` do
  serviço de destino; o destino libera a rota com `hasAuthority`.
- **Envio do token nas chamadas Feign é declarativo**: todo método Feign
  declara `@RequestHeader("Authorization") String authorization`
  explicitamente (nada de header injetado por trás dos panos). Quem resolve
  o valor é o `TokenPropagation` (`petshop-commons`), em dois padrões:
  - **Relay-com-fallback** (`resolveAuthorizationHeader`, ex.:
    `order-service` → `CustomerFeign.getCustomerById`): repassa o
    `Authorization` recebido pelo controller, se houver; sem token (ex.:
    chamada disparada por um `@Scheduled`), usa o token Client Credentials
    do próprio serviço.
  - **Sempre serviço** (`mintServiceToken`): nunca repassa o token de quem
    originou o pedido. Usado nas rotas puramente internas.

  Permissões de serviço (detalhe em
  [`infra/keycloak/README.md`](../infra/keycloak/README.md#permissões-de-serviço-fase-4)):

  | Chamador | Rota no destino | Permissão |
  |---|---|---|
  | `booking-service` | `staff-service` `GET /api/v1/availability/general` e `/schedule` | `svc:staff:availability-read` |
  | `order-service` | `customer-management` `GET /api/v1/customers/find/customer/{id}` | `svc:customers:customer-read` |
  | `order-service` | `product-registration` `POST /api/v1/products/{id}/reserve`, `/release`, `/confirm` | `svc:products:stock-write` |
  | `staff-service` | `customer-management` `GET /api/v1/staff/all` | `svc:customers:staff-read` |

  Em todos os casos, um cliente com token válido não consegue chamar as
  rotas só-internas diretamente, e o token de serviço recebe 403 em
  qualquer rota de usuário.
- **Usuário autenticado** — `KeycloakJwtAuthenticationConverter` transforma o
  `Jwt` em um `AuthenticatedUser` (id = claim `user_id`, e-mail, nome, cpf,
  telefone, papel, tipo de conta) com authority `ROLE_<papel>` de
  `realm_access.roles`. Sem `user_id` (Client Credentials) o principal é um
  `ServiceAccount`.

Papéis de negócio (`Role`): `ADMIN`, `RECEPTIONIST`, `GROOMER`,
`VETERINARIAN`, `CUSTOMER` (o antigo `SERVICE` foi substituído pelas
permissões `svc:*`). Tipo de conta (`AccountType`): `CUSTOMER`, `STAFF`,
`SERVICE` — usado para diferenciar "é um cliente" de "é um funcionário"
mesmo quando o papel de negócio seria compatível.

A confiança entre os serviços vem da chave pública do Keycloak (JWKS): não há
segredo compartilhado entre eles, e a rotação de chave não exige restart.

---

## 3. Módulos

### 3.1 `petshop-commons`

Não é um serviço — é um JAR consumido por todos os demais módulos Maven
(precisa ser instalado no `.m2` local antes de qualquer outro build; o
`mvn clean install` a partir do `pom.xml` agregador na raiz cuida disso
automaticamente — ver [6](#6-como-rodar-localmente)).

**Principais classes:**
- `KeycloakResourceServerConfig` / `KeycloakJwtDecoders` /
  `KeycloakJwtAuthenticationConverter` / `AuthenticatedUser` /
  `ServicePermissions` / `PetshopAuthorization` — descritos acima.
- `Role`, `AccountType` — enums de domínio compartilhados.
- `BaseExceptionHandler` — classe abstrata estendida pelo
  `@RestControllerAdvice` de cada serviço. Centraliza a tradução de
  exceção → resposta HTTP no formato `ProblemDetail` (RFC 7807):

  | Exceção | Status | Observação |
  |---|---|---|
  | `NotFoundException` | 404 | Recurso não encontrado |
  | `ConflictException` | 409 | Ex.: CPF já cadastrado |
  | `DataIntegrityViolationException` (e `DuplicateKeyException`, do Mongo) | 409 | Violação de constraint do banco (corrida no cadastro, índice único). Mensagem genérica; detalhe do banco só no log (`WARN`) |
  | `BusinessRuleException` | 422 | Regra de negócio violada (estoque insuficiente, sem funcionário disponível...) |
  | `MethodArgumentNotValidException` | 400 | Bean Validation — lista de erros por campo em `problem.errors` |
  | `AccessDeniedException` | 403 | Autenticado, mas sem permissão |
  | `BadCredentialsException` | 401 | Login/senha inválidos |
  | `IllegalArgumentException` | 400 | Parâmetro inválido (ex.: horário de agendamento fora da regra) |
  | `MaxUploadSizeExceededException` | 413 | Upload multipart acima do limite configurado (`spring.servlet.multipart.*`) |
  | `Exception` (genérica) | 500 | Fallback — logada como erro, mensagem genérica ao cliente |

  Isso significa que **toda** `IllegalArgumentException`/`BusinessRuleException`
  lançada em qualquer camada de domínio (ver `BookingService.validateBookingTime`,
  `ProductService.reserveStock` etc.) já vira automaticamente uma resposta
  HTTP correta, sem tratamento repetido em cada controller.
- `NotFoundException`, `ConflictException`, `BusinessRuleException`,
  `BusinessException` — exceções de negócio que a camada de aplicação lança.

### 3.2 `customer-management`

Serviço central de identidade. Dono dos dados de cliente, pet e
funcionário; cria e mantém os usuários no Keycloak pela Admin API (não guarda
senha nem emite token); mantém o histórico consultável
(read model) de agendamentos e compras.

**Persistência:** PostgreSQL (`clientes_db`) para clientes/pets/staff via
JPA; MongoDB (`customer_history_db`) para o histórico, alimentado por
eventos Kafka — não por escrita direta de outro serviço.

**Principais classes:**
- `KeycloakAdminAdapterOut` (porta `IdentityProviderPortOut`) — cria,
  atualiza e desabilita usuários no Keycloak (client
  `customer-management-admin`). Login, "esqueci a senha" e troca de senha
  não existem mais aqui: são do Keycloak. Ver [4.1](#41-login) e
  [4.2](#42-recuperação-de-senha).
- `CustomerService` — CRUD de cliente e pets. Ver [4.3](#43-cadastro-de-cliente).
- `StaffService` — CRUD de funcionário (só `ADMIN`; é o registro que o
  `staff-service` consulta via Feign para calcular disponibilidade).
  `GET /staff/all` lista só os habilitados (`enabled=true` — desligados
  não somem do banco, só param de aparecer na listagem, mesmo soft-delete
  de `deleteStaff`) e aceita role `ADMIN` **ou** a permissão
  `svc:customers:staff-read` — essa segunda é quem o `staff-service` usa de verdade (ver [2.3](#23-modelo-de-autenticação)),
  já que um `CUSTOMER` agendando um serviço nunca tem `ADMIN`. `GET /staff/{id}/find` e
  `GET /customers/find/customer/{id}` aceitam **id ou CPF** no mesmo
  parâmetro: tenta interpretar como UUID e buscar por id primeiro, se não
  for um UUID válido (ou não achar) cai para busca por CPF
  (`getStaffByIdOrCpf`/`findCustomerByIdOrCpf`). No caso do cliente, a
  checagem de ownership (`CustomerIdentityResolver`) só acontece **depois**
  de resolver o registro — precisa saber o id real por trás do CPF antes de
  comparar com o dono do token. `PATCH /staff/{id}/reactivate` reverte o
  soft-delete (`enabled=true` de novo, volta a aparecer em `GET /staff/all`).
- `HistoryService` — leitura do histórico (agendamentos/compras),
  alimentado pelos `@KafkaListener`s abaixo.
- `BookingHistoryKafkaListener` / `OrderHistoryKafkaListener` — consomem
  `booking-completed` e `order-completed`, traduzem o DTO de mensageria
  para um command do core (`BookingCompletedCommand`,
  `PurchaseCompletedCommand`) e delegam a
  `UpdateBookingHistoryUseCase`/`UpdateCustomerPurchaseHistoryUseCase`, que
  gravam/atualizam o documento Mongo correspondente.
  `UpdateBookingHistoryUseCase.execute` é `@Transactional(readOnly = true)`
  — precisa manter a sessão Hibernate aberta enquanto resolve o cliente
  por CPF (`CustomerPortOut.findByCpf`), já que o mapper acessa a coleção
  lazy de pets; sem isso (bug encontrado e corrigido nesta sessão), a
  sessão fechava assim que o método de busca retornava e a leitura da
  coleção lazy explodia com `LazyInitializationException` — mascarado em
  qualquer fluxo disparado por HTTP graças ao `spring.jpa.open-in-view`
  (default `true`), mas fatal para um `@KafkaListener`, que não tem
  requisição HTTP nenhuma por trás.
- `KafkaConsumerConfig` — cada listener acima tem sua própria
  `ConcurrentKafkaListenerContainerFactory` (não a default que o Boot
  monta a partir de `spring.kafka.consumer.*`), com um `JsonDeserializer`
  construído já com o tipo-alvo fixo (`BookingEventDTO`/
  `OrderCompletedEventDTO`) e embrulhado em `ErrorHandlingDeserializer`.
  Dois motivos pra isso, achados nesta sessão ao investigar uma mensagem
  presa no dead-letter-topic:
  1. **Header de tipo desligado dos dois lados** (`spring.json.use.type.headers:
     false` no consumer daqui, `spring.json.add.type.headers: false` no
     producer de quem publica) é proposital — cada serviço tem sua própria
     classe de DTO pro mesmo evento (decisão consciente de não compartilhar
     classes de evento entre serviços), então o nome de classe no header de
     quem produziu não faria sentido aqui. Só que isso deixa o
     `JsonDeserializer` sem NENHUMA forma de saber o tipo-alvo a menos que
     seja fixado em código — sem isso, **toda** mensagem (não só payload
     inválido) falhava com `"No type information in headers and no default
     type provided"` e ia pro DLT à toa.
  2. Como os dois tópicos (`booking-completed`, `order-completed`) precisam
     de tipos diferentes, uma única factory genérica não serve pros dois —
     daí uma factory por tópico, cada uma com seu tipo fixo.

  A proteção contra "poison pill" continua vindo do
  `ErrorHandlingDeserializer` (embrulhando o `JsonDeserializer` de cada
  factory) + `KafkaErrorHandlingConfig` (`DefaultErrorHandler` injetado nas
  duas factories): um payload que não desserializa vira
  `DeserializationException`, tratada com retry + `DeadLetterPublishingRecoverer`
  publicando no tópico `.DLT`, sem travar o `poll()` nem o consumo dos
  próximos registros. O `notification-service` (ver
  [3.7](#37-notification-service)) tem proteção equivalente, com backoff
  exponencial.
- `BootstrapAdminRunner` — `ApplicationRunner` que roda no start: se a
  tabela de staff estiver vazia, cria o `ADMIN` inicial local e, a cada
  start (idempotente), garante que ele existe no Keycloak. Com
  `BOOTSTRAP_ADMIN_PASSWORD` essa é a credencial (não temporária); vazia, o
  usuário nasce sem senha e o Keycloak envia o e-mail para definir a senha.
  Não existe senha provisória gerada.

**Fluxo de dados típico (histórico de agendamento):**

```
booking-service publica "booking-completed" (Kafka)
  → BookingHistoryKafkaListener.consumeBookingEvent
  → traduz BookingEventDTO → BookingCompletedCommand
  → UpdateBookingHistoryUseCase.execute
  → BookingHistoryPortOut (Mongo) grava/atualiza CustomerHistoryBookings
```

### 3.3 `product-registration`

Catálogo de produtos e controle de estoque. Não chama nenhum outro
serviço — é consumido via REST direto pelo `order-service`.

**Persistência:** MongoDB (`produtos_db`, banco compartilhado com
`customer-management` na mesma instância — cada um com seu próprio banco
lógico), documento `ProductDocument` (`infrastructure/persistence/mongo`)
mapeado de/para o domínio `Product` via `ProductMapper` (MapStruct). Migrado
de PostgreSQL nesta sessão — o `id` continua `Long` (não `ObjectId`), já que
`order-service` e o resto do sistema referenciam produto por `Long id`;
`ProductSequenceGenerator` emula auto-increment via contador atômico
(`findAndModify` + `$inc`) numa coleção auxiliar `database_sequences`, já
que o Mongo não tem `IDENTITY`/`SERIAL` nativo. Índice único em `name`
precisa de `spring.data.mongodb.auto-index-creation: true` pra ter efeito
de verdade (Spring Data não cria índice a partir de `@Indexed` sozinho).

**Principais classes:**
- `ProductService` (implementa `ProductPortIn`) — CRUD de produto e as três
  operações de estoque: `reserveStock`, `releaseStock`, `confirmStock`.
- `Product` (domínio) — mantém `stock` (físico) e `reservedStock`
  (comprometido por carrinhos ainda não pagos); `availableStock()` é
  `stock - reservedStock`; `reservedPercentage()` é
  `reservedStock / stock * 100` (`0` se `stock` for `0`, evita divisão por
  zero). `reserve`/`release`/`confirmSale` são métodos do próprio domínio,
  não da service.
- `GET /list` e `GET /find/{id}` aceitam token **opcional**: o
  Resource Server (petshop-commons) roda em toda requisição e
  popula o `AuthenticatedUser` sempre que vem um Bearer válido, mesmo em
  endpoint `permitAll()`. O `ProductController` só inclui
  `stock`/`reservedStock`/`availableStock`/`reservedPercentage` no
  `ProductResponseDto` quando `user != null && user.type() ==
  AccountType.STAFF` — sem token ou com token de `CUSTOMER`, esses 4 campos
  saem do JSON (`@JsonInclude(NON_NULL)` no DTO, não aparecem nem como
  `null`).

**Fluxo de dados (ciclo de vida do estoque de um item):**

```
order-service: item adicionado ao carrinho (autenticado)
  → POST /api/v1/products/{id}/reserve
  → Product.reserve(qty)          // reservedStock += qty
                                    // availableStock cai, stock não muda

  (a) item removido do carrinho / carrinho expira
      → POST /{id}/release  → Product.release(qty)   // reservedStock -= qty

  (b) pagamento confirmado
      → POST /{id}/confirm  → Product.confirmSale(qty) // stock -= qty, reservedStock -= qty
```

### 3.4 `staff-service`

Não é dono do cadastro de funcionários — isso vive no
`customer-management`. Consulta esse registro via Feign e, em cima dele,
calcula disponibilidade e mantém a escala rotativa de sábado.

**Persistência:** PostgreSQL (`staff_db`) — usado **só** para
`WeekendAllocation` (a escala de sábado). `Staff` não é uma entidade
persistida localmente: é um objeto de domínio populado a cada chamada a
partir do Feign.

**Principais classes:**
- `AvailabilityService` — calcula, para um `dateTime`/`Role`, se há
  funcionário disponível e quem está escalado. Regras fixas: cada
  atendimento ocupa 30 minutos (`dateTime` precisa cair num slot exato);
  segunda a sexta, 9h–17h com almoço 12h–13h, todo `GROOMER`/`VETERINARIAN`
  habilitado atende; sábado 8h–11h só existe para `GROOMER` e só quem está
  na `WeekendAllocation` daquele sábado; domingo fechado para todos.
- `WeekendAllocationService.generateNextMonth` — gera, em rodízio round-robin
  (`cursor % groomers.size()`), a escala dos sábados do **próximo mês ainda
  não preenchido** (`nextMonthToFill()` olha a última data já alocada).
- `StaffRegistryFeign` — chama `GET /api/v1/staff/all` no
  `customer-management`. `TokenPropagation` aqui **sempre** assina um
  token de serviço (nunca repassa o token de quem originou a chamada) — ver
  o padrão "sempre serviço" em [2.3](#23-modelo-de-autenticação).
- `/api/v1/availability/**` (consultado pelo `booking-service`) exige
  `hasAuthority("svc:staff:availability-read")` no `SecurityConfig` — não é
  alcançável diretamente por um `CUSTOMER`/`STAFF` autenticado, só via a
  chamada Feign interna do `booking-service`, que sempre envia o token
  Client Credentials dele.

**Pendência:** `AvailabilityService.checkGeneralAvailability`
(`GET /api/v1/availability/general`) não tem nenhum chamador real hoje — o
`CustomerManagementFeign` do `booking-service` declara o método
`getGeneralAvailability`, mas nenhum fluxo de negócio o invoca; só
`getSchedule` (`/schedule`) é usado (ver fluxo de dados abaixo). Falta
decidir se esse endpoint deve ser efetivamente ligado a algum fluxo ou
removido.

**Fluxo de dados (checar disponibilidade):**

```
booking-service → GET /api/v1/availability/general?dateTime=...&role=GROOMER
  → AvailabilityService.checkGeneralAvailability
  → staffPortOut.findAll()  (Feign → customer-management GET /api/v1/staff/all)
  → filtra Staff habilitado + role == GROOMER
  → se sábado: cruza com WeekendAllocationPortOut.findByDate(sábado)
  → devolve AvailabilityResult(capacity, available, dateTime)
```

### 3.5 `booking-service`

Agendamento de serviços (banho, tosa, consulta veterinária): criação,
listagem, atualização, cancelamento e cálculo de horários disponíveis.

**Persistência:** PostgreSQL (`booking_db`, `pet-service_db` no valor
padrão de dev) via JPA.

**Principais classes:**
- `BookingService` (implementa `BookingPortIn`) — toda a regra de negócio
  de agendamento e invalidação seletiva do cache de horários disponíveis.
  Ver [4.4](#44-criação-de-agendamento).
- `AvailableSlotsService` (implementa `AvailableSlotsPortIn`) — calcula os
  horários disponíveis para `DAY`/`WEEK`/`MONTH` cacheando a disponibilidade
  diária por cargo (`<requiredStaffRole>:<yyyy-MM-dd>`) no cache
  `availableSlots` (`CacheConfig`) e aplicando na leitura o filtro de 2h de
  antecedência mínima via `Clock` (`ClockConfig`). Assim, `BANHO`, `TOSAGEM`
  e `TOSA_E_BANHO` compartilham a mesma entrada diária de `GROOMER`,
  janelas `WEEK`/`MONTH` reaproveitam os dias já cacheados e slots do dia
  atual nunca ficam defasados pela passagem do tempo.
- `Booking` (domínio) — estado (`SCHEDULED`/`COMPLETED`/`CANCELED`),
  `completeBooking` só permite a transição a partir de `SCHEDULED`.
- `ServiceType` (enum) — `TOSAGEM`, `BANHO`, `TOSA_E_BANHO`,
  `CONSULTA_VETERINARIA`; `requiredStaffRole()` mapeia o serviço para o
  cargo do `staff-service` (`VETERINARIAN` para consulta, `GROOMER` para
  os demais).
- `CustomerManagementFeign` (o nome é histórico — na prática aponta para o
  `staff-service`, ver `external.api.staff-service.url`) — consulta
  `/api/v1/availability/general` e `/schedule`, ambas travadas com
  `svc:staff:availability-read` no `staff-service`. `TokenPropagation` aqui segue o
  padrão "sempre serviço" ([2.3](#23-modelo-de-autenticação)): nunca
  repassa o token de quem chamou, mesmo havendo uma requisição HTTP em
  andamento — essas rotas só existem pra consumo interno.
- Produtor Kafka: eventos de histórico (`booking-scheduled`,
  `booking-completed`, `booking-canceled`) e comandos de e-mail
  (`notification-commands`, payload `{to, tipo, params}` — sem HTML, o
  template é resolvido pelo `notification-service`). `to` é sempre o
  `ownerEmail` do agendamento (nunca `ownerContact`, que é telefone) —
  capturado do corpo da requisição (`RECEPTIONIST`/`ADMIN`) ou sobrescrito
  com a claim `email` do token quando quem agenda é o próprio `CUSTOMER`
  ([4.4](#44-criação-de-agendamento)).

**Fluxo de dados (criação de agendamento):** ver [4.4](#44-criação-de-agendamento).

### 3.6 `order-service`

Orquestra o fluxo completo de compra: carrinho com reserva de estoque,
checkout, pagamento simulado, retirada e histórico — coordenando
`product-registration` e `customer-management` via Feign.

**Persistência:** PostgreSQL (`order_db`) — `Cart`/`CartItem` e
`Order`/`OrderItem`.

**Principais classes:**
- `CartService` (implementa `CartPortIn`) — `addItem`, `removeItem`,
  `getCart`, `expireOldCarts`. Ver [4.5](#45-carrinho-e-checkout).
- `OrderService` (implementa `OrderPortIn`) — `checkout`,
  `processPendingPayments`, `processPickupReadyOrders`.
- `Cart` (domínio) — `openFor` cria um carrinho com TTL
  (`renewExpiration`); `isExpired()` compara `expiresAt` com `now()`.
- `Order` (domínio) — `fromCart` congela os itens/preço no momento do
  checkout; `markAsPaid`, `schedulePickup`, `markReadyForPickup` são
  transições de estado.
- `CartExpirationScheduler`, `PaymentMockScheduler`, `PickupReadyScheduler`
  — `@Scheduled` que disparam, respectivamente, `expireOldCarts`,
  `processPendingPayments` e `processPickupReadyOrders` em intervalos
  configuráveis.
- `ProductFeign` / `ProductStockAdapterOut` — `find/{id}` (leitura pública,
  repassa o token de quem chamou via `TokenPropagation.resolveAuthorizationHeader`)
  e `{id}/reserve`, `{id}/release`, `{id}/confirm` (rotas internas do
  `product-registration`, travadas com `svc:products:stock-write` — sempre
  chamadas com o token Client Credentials do `order-service` via
  `TokenPropagation.mintServiceToken`, nunca o token do cliente).
- `CustomerFeign` / `CustomerAdapterOut` — `find/customer/{id}` no
  `customer-management`, endpoint de uso duplo (cliente vendo o próprio
  perfil e order-service resolvendo dados no checkout); `TokenPropagation`
  aqui segue o padrão relay-com-fallback: repassa o token de quem chamou
  quando há um, senão assina um token de serviço local
  (`jwtService.generateServiceToken("order-service")`, sem round-trip de
  rede) — usado, por exemplo, pelos schedulers (`PaymentMockScheduler`,
  `PickupReadyScheduler`), que não têm requisição HTTP em andamento.

### 3.7 `notification-service`

Serviço fino, orientado a eventos: consome comandos de e-mail via Kafka e
os envia via Brevo, usando templates gerenciados no painel do Brevo (o
serviço não renderiza HTML). Sem API REST própria, sem persistência.

**Principais classes:**
- `NotificationCommandConsumer` — `@KafkaListener(topics =
  KafkaTopics.NOTIFICATION_COMMANDS, groupId = "notification-group")`;
  a exceção do envio é relançada (e contada em `notification.send.failed`),
  e o `DefaultErrorHandler` de `KafkaErrorHandlingConfig` decide: falha
  transitória é retentada (3 novas tentativas, backoff 1s/2s/4s) e, se
  persistir, vai para `notification-commands.DLT`; 4xx do Brevo e erro de
  desserialização vão direto pro DLT, sem retry.
- `NotificationSender` (porta de saída, `enviar(TipoNotificacao,
  destinatarioEmail, variaveis)`) — implementada por `BrevoEmailAdapterOut`.
- `BrevoEmailAdapterOut` — resolve `TipoNotificacao` → `templateId` via
  `BrevoProperties.templates()` (`@ConfigurationProperties(prefix =
  "brevo")`, `Map<TipoNotificacao, Long>`) e chama `POST /smtpEmail` via
  `BrevoFeignClient` com o `templateId` e as `params` como variáveis do
  template — sem remetente/assunto/corpo no payload, fixos no template.

**Produtores conhecidos** do tópico `notification-commands` (todos
publicam o mesmo contrato `{to, tipo, params}` — `tipo` precisa bater com
uma constante de `TipoNotificacao`; só o `notification-service` sabe o
`templateId` correspondente): `order-service` (pedido aguardando
pagamento, pagamento confirmado, nota emitida, janela de retirada, pedido
pronto, carrinho abandonado), `booking-service` (agendamento
criado/concluído/cancelado) e `customer-management` (boas-vindas e
verificação de e-mail; o e-mail de senha é do Keycloak).

### 3.8 `gateway-service`

API Gateway (Spring Cloud Gateway, modelo reativo — WebFlux/Netty, único
módulo do repositório que não é MVC/servlet). Ponto único de entrada HTTP
pensado para o frontend: antes dele, um frontend em browser precisava
conhecer 5 URLs base diferentes e nenhum serviço tinha `CorsConfigurationSource`
configurada (bloqueador real para qualquer chamada vinda de um browser).

**Sem persistência, sem `petshop-commons`** — não tem lógica de domínio,
não autentica ninguém (cada serviço continua validando o próprio token, como
antes do gateway existir; o gateway só lê o `sub` do Bearer, validado pelo
JWKS, para a chave do rate limiting).

**Roteamento:** por `Path` predicate, sem `StripPrefix`/`RewritePath` —
nenhum dos 6 serviços colide em prefixo (`/api/v1/<recurso>`), então o path
que chega no gateway é exatamente o path que o serviço downstream já
esperava receber direto:

- `/api/v1/auth/**`, `/api/v1/customers/**`, `/api/v1/staff/**`,
  `/api/v1/history/**` → `customer-management`
- `/api/v1/products/**` → `product-registration`
- `/api/v1/availability/**`, `/api/v1/weekend-allocation/**` → `staff-service`
- `/api/v1/bookings/**` → `booking-service`
- `/api/v1/orders/**`, `/api/v1/cart/**` → `order-service`

`notification-service` não tem rota aqui — não expõe REST, só consome
Kafka.

**CORS:** centralizado em `spring.cloud.gateway.server.webflux.globalcors`
(`GATEWAY_CORS_ALLOWED_ORIGINS`) — os 5 serviços downstream não precisam
mais de CORS próprio, porque o browser passa a falar só com o gateway.
`allowCredentials: false` (Bearer token no header `Authorization`, não
cookie de sessão).

**Fora do escopo deste módulo, de propósito:** validação de token
centralizada (cada serviço valida o seu) e service discovery
(URLs downstream continuam estáticas por variável de ambiente, mesmo
padrão das chamadas Feign entre os outros serviços).

---

## 4. Cenários de funcionamento

### 4.1 Login

O login é do Keycloak (SDD-024): o web usa Authorization Code + PKCE no
client público `web` do realm `petshop`. Não existe mais
`POST /api/v1/auth/login` nem emissão de token no `customer-management`.

```mermaid
sequenceDiagram
    participant U as Usuário (browser)
    participant W as web (SPA)
    participant KC as Keycloak
    participant GW as gateway-service
    participant S as Serviço (Resource Server)

    U->>W: abre /entrar
    W->>KC: /auth?response_type=code&code_challenge (PKCE S256)
    KC->>U: tela de login (usuário = e-mail)
    U->>KC: e-mail + senha
    KC-->>W: redirect /auth/callback?code=...
    W->>KC: troca code + code_verifier por tokens
    KC-->>W: access token (5 min) + refresh token
    W->>GW: GET /api/v1/... Authorization: Bearer <access token>
    GW->>S: repassa a requisição
    S->>KC: JWKS (em cache)
    S->>S: valida assinatura, iss, exp e aud
    S-->>W: 200 (ou 401/403)
```

**Caminho feliz:** o access token dura 5 min e o refresh token é rotacionado
(`revokeRefreshToken=true`); o web renova antes de expirar e, num 401, tenta
uma renovação. A sessão (SSO) é deslizante: com "Lembrar de mim" 30 dias de
inatividade (máx. 90), sem ele 1 dia.

**Reativação (cliente):** cliente desativado
(`DELETE /customers/delete/customer/{id}`, soft-delete, usuário desabilitado
no Keycloak) é reativado pelo `ADMIN` em
`PATCH /customers/reactivate/customer/{id}`, que também reabilita o usuário
no Keycloak. Funcionário: `PATCH /staff/{id}/reactivate` (ver
[3.2](#32-customer-management)). Usuário desabilitado não consegue logar.

**Erros:**
- Senha errada ou usuário inexistente: tratado na tela do Keycloak (o
  serviço nunca vê a credencial).
- Token ausente, inválido, expirado ou com `aud` que não é o do serviço →
  **401** (`ProblemDetailAuthenticationEntryPoint`).
- Token válido sem o papel/permissão da rota → **403**.

### 4.2 Recuperação de senha

Não há mais `POST /api/v1/auth/forgot-password`: "Esqueci a senha" é a
tela de login do Keycloak (`resetPasswordAllowed=true`), que envia o e-mail
pelo SMTP do realm (`KEYCLOAK_SMTP_*`, padrão Brevo); a troca de senha é o
Account Console (`/realms/petshop/account`). Não há senha provisória.

**Funcionário novo:** `POST /staff/create` cria o usuário no Keycloak sem
senha e o Keycloak envia o e-mail de ação `UPDATE_PASSWORD` para ele definir
a senha. Falha de SMTP não desfaz o cadastro (o funcionário pode usar
"Esqueci a senha"). O mesmo vale para o `ADMIN` de bootstrap sem
`BOOTSTRAP_ADMIN_PASSWORD`.

### 4.3 Cadastro de cliente

`POST /api/v1/customers/register/customer`, `CustomerService.registerCustomer`.

**Caminho feliz:** CPF ainda não cadastrado → cria `Customer` localmente e o
usuário no Keycloak (papel `CUSTOMER`, username = e-mail, `emailVerified=false`)
com a senha escolhida pelo próprio cliente no cadastro
(`password`/`confirmPassword` no `CustomerRequestDto`); a senha vai direto
ao Keycloak, nunca é gravada neste serviço, e não é provisória. O usuário é
criado dentro da transação local: falha no Keycloak desfaz o cadastro, e
e-mail já usado no Keycloak → 409. Diferente do `Staff` (criado pelo `ADMIN`),
que nasce sem senha e recebe o e-mail do Keycloak para defini-la (ver
[4.2](#42-recuperação-de-senha)). Depois de salvar, publica em
`notification-commands` (via `NotificationPortOut.sendRegistrationConfirmation`)
um comando `{to, tipo: "CADASTRO_CONFIRMADO", params: {name}}` — e-mail de
boas-vindas via Brevo, mesmo mecanismo assíncrono das demais notificações
([4.8](#48-envio-de-notificação-por-e-mail)). Responde 201 com o cliente criado.

**Validação do `CustomerRequestDto`:** `password` exige mínimo de 6
caracteres; `confirmPassword` é validado contra `password` via
`@AssertTrue` no próprio DTO (`isConfirmPasswordMatching()`) — feito no
backend, não só no front, porque o cadastro é público e pode ser chamado
direto na API sem passar por nenhum formulário.

**Erro:**
```java
customerPortOut.findByCpf(request.getCpf())
        .ifPresent(c -> {
            throw new ConflictException("CPF already exists: " + request.getCpf());
        });
```
CPF já existente → `ConflictException` → **409**.

### 4.4 Criação de agendamento

`POST /api/v1/bookings/create`, `BookingService.createBooking`.

```mermaid
sequenceDiagram
    participant C as Cliente HTTP
    participant BS as BookingService
    participant SS as staff-service (Feign)
    participant Repo as BookingPortOut
    participant Kafka as Kafka

    C->>BS: POST /bookings/create {petId, serviceType, bookingDateTime,...}
    BS->>BS: validateBookingTime (dia/horário/antecedência)
    alt horário inválido
        BS-->>C: 400 IllegalArgumentException
    else horário válido
        BS->>SS: getSchedule(dateTime, requiredStaffRole)
        SS-->>BS: [funcionários escalados]
        BS->>Repo: findActiveBookingsAt(dateTime)
        Repo-->>BS: [agendamentos já ocupando o horário]
        alt nenhum funcionário livre
            BS-->>C: 422 BusinessRuleException
        else há funcionário livre
            BS->>Repo: save(Booking)
            BS->>Kafka: notification-commands (e-mail "agendado")
            BS->>Kafka: booking-scheduled (histórico)
            BS-->>C: 201 Location: /bookings/{id}
        end
    end
```

**Regras de horário (`validateBookingTime`):**
- Domingo: loja fechada, para qualquer serviço.
- `CONSULTA_VETERINARIA` aos sábados: não atende.
- Segunda a sexta: 9h–17h, com intervalo de almoço 12h–13h bloqueado.
- Sábado (exceto consulta): 8h–11h.
- Antecedência mínima de **2 horas** a partir de `now()`.
- Qualquer violação → `IllegalArgumentException` → **400**.

**Atribuição de funcionário
(`ensureAvailabilityAndAssignEmployee`):** cruza quem está escalado
(Feign para `staff-service`, já considerando cargo exigido pelo
`ServiceType` e o rodízio de sábado) com quem já está ocupado nesse exato
horário (`bookingPortOut.findActiveBookingsAt`), e pega o primeiro nome
livre. Se ninguém sobra → `BusinessRuleException("Não há funcionário
disponível para o horário selecionado: " + bookingDateTime)` → **422**.

**Efeitos colaterais no sucesso:** grava o `Booking` (`status =
SCHEDULED`), invalida no cache `availableSlots` apenas a chave `<cargo>:<data>`
afetada (`AvailableSlotsPortIn.evictAvailableSlots`, preservando outros dias e
outros cargos; em `updateBooking`, só invalida se o horário ou o cargo mudar,
incluindo a chave antiga e a nova quando o dia ou o cargo mudam, e não invalida
nada ao concluir atendimento com `status=COMPLETED` ou editar só `observations`),
publica o e-mail de confirmação (`notification-commands`) e o evento de histórico
(`booking-scheduled`) — se qualquer publicação falhar, a transação de
banco **não é desfeita** por causa disso (a chamada acontece depois do
`save`, dentro do mesmo método `@Transactional`, mas o Kafka não participa
da transação JPA).

**Regra de identidade:** se quem chama é `CUSTOMER`
(`BookingControllerImpl.applyOwnerIdentity`), nome/CPF/contato/e-mail do
dono são sobrescritos com as claims do próprio token — o corpo da
requisição é ignorado nesses campos, então um cliente não consegue agendar
em nome de outro CPF (nem direcionar a notificação por e-mail pra outro
endereço). `RECEPTIONIST`/`ADMIN` usam o corpo como está.

**Conclusão de atendimento** (`PATCH .../{id}?status=COMPLETED`,
`Booking.completeBooking`): só sobrescreve `observations` se o
`BookingUpdateDto` trouxer o campo preenchido — finalizar um atendimento
sem passar observação nova preserva o que já tinha sido registrado durante
o serviço, em vez de apagar.

### 4.5 Carrinho e checkout

**Adicionar item** (`CartService.addItem`): se o cliente está autenticado,
reserva o estoque de imediato (`POST /products/{id}/reserve`); se não está
(carrinho anônimo, sem login), só consulta o produto sem reservar nada —
a reserva de fato acontece depois, no checkout
(`OrderService.ensureAllItemsReserved`).

**Checkout** (`POST /api/v1/orders/checkout?customerId=`,
`OrderService.checkout`):

```mermaid
sequenceDiagram
    participant C as Cliente HTTP
    participant OS as OrderService
    participant PR as product-registration (Feign)
    participant CM as customer-management (Feign)
    participant Kafka as Kafka

    C->>OS: POST /orders/checkout?customerId=...
    OS->>OS: cartPortOut.findOpenCartByCustomerId
    alt sem carrinho aberto
        OS-->>C: 422 "Não há carrinho aberto para o cliente..."
    else carrinho vazio
        OS-->>C: 422 "Não é possível finalizar um carrinho vazio."
    else carrinho com itens
        OS->>PR: reserve(productId, qty) para itens ainda não reservados
        alt estoque insuficiente em algum item
            PR-->>OS: 422 (propagado)
            OS-->>C: 422 (rollback — nada é criado)
        else tudo reservado
            OS->>OS: Order.fromCart(cart)  // congela itens e total
            OS->>CM: findCustomerById
            OS->>Kafka: notification-commands (pedido aguardando pagamento)
            OS-->>C: 201 Order{status=AWAITING_PAYMENT}
        end
    end
```

**Pagamento simulado** (`PaymentMockScheduler`, a cada
`order.payment.check-interval-ms`, padrão 10s):
`OrderService.processPendingPayments` aprova qualquer pedido
`AWAITING_PAYMENT` criado há mais de `order.payment.mock-delay-seconds`
(padrão 30s) atrás — como se fosse um webhook assíncrono de gateway real.
Isso `confirm`a o estoque no `product-registration` (baixa definitiva),
sorteia um prazo de preparo entre `order.pickup.min/max-delay-minutes`
(padrão 1–60 min, propositalmente curto para acelerar o teste), envia três
e-mails (pagamento confirmado, nota emitida, janela de retirada) e publica
`order-completed` (para o histórico de compras no `customer-management`).

**Retirada** (`PickupReadyScheduler`): quando `pickupReadyAt` vence, o
pedido vira `READY_FOR_PICKUP` e dispara o e-mail de aviso.

### 4.6 Expiração de carrinho abandonado

`CartExpirationScheduler` chama `CartService.expireOldCarts` a cada
`order.cart.expiration-check-interval-ms` (padrão 10 min). Para todo
carrinho `OPEN` com `expiresAt` no passado
(`order.cart.reservation-ttl-hours`, padrão 24h):

1. Libera (`release`) o estoque reservado de cada item que estava
   reservado.
2. Marca o carrinho como `EXPIRED`.
3. Se o carrinho tinha itens, dispara e-mail de lembrete de carrinho
   abandonado — **só** se `customerId` corresponder a um cliente
   cadastrado de verdade; carrinho anônimo (nunca logou) apenas gera um log
   de aviso, sem e-mail (não haveria destinatário).

### 4.7 Geração de escala de sábado

`POST /api/v1/weekend-allocation/generate` (`ADMIN`),
`WeekendAllocationService.generateNextMonth`.

**Caminho feliz:** busca todo `Staff` com `role == GROOMER` e `enabled ==
true` (via Feign, ordenado por id para o rodízio ser determinístico),
descobre o próximo mês ainda sem escala
(`weekendAllocationPortOut.findLastAllocatedDate` + 1 mês, ou o mês atual
se nunca gerou nada) e distribui `staff.groomer.saturday-staff-count`
(padrão 2) tosadores por sábado daquele mês, em rodízio round-robin a
partir de um cursor cumulativo (`weekendAllocationPortOut.countAll()`) —
para o rodízio continuar de onde parou entre execuções, não recomeçar do
zero todo mês.

**Erro:** menos tosadores habilitados do que
`staff.groomer.saturday-staff-count` → `BusinessRuleException` → **422**,
nenhuma alocação é criada.

### 4.8 Envio de notificação por e-mail

```mermaid
sequenceDiagram
    participant P as Produtor (booking/order/customer-mgmt)
    participant K as Kafka (notification-commands)
    participant NC as NotificationCommandConsumer
    participant BE as BrevoEmailAdapterOut
    participant API as API Brevo

    P->>K: publish {to, tipo, params}
    K->>NC: @KafkaListener consume
    NC->>BE: NotificationSender.enviar(tipo, to, params)
    BE->>BE: resolve tipo -> templateId (brevo.templates)
    BE->>API: POST /smtpEmail {templateId, to, params}
    alt status 2xx
        API-->>BE: sucesso
        BE-->>NC: sucesso (log info)
    else status 4xx/5xx
        API-->>BE: erro
        BE-->>NC: FeignException
        NC->>NC: conta notification.send.failed e relança
        Note over NC: DefaultErrorHandler: retry (5xx/timeout) ou DLT (4xx)
    end
```

**Ponto importante:** falha de envio não é mais engolida. Falha retentável
(5xx do Brevo, timeout, erro de conexão) é reprocessada até 3 vezes com
backoff exponencial (1s, 2s, 4s); 4xx (chave inválida, `templateId`
inexistente, payload inválido) e JSON inválido vão direto para
`notification-commands.DLT`, assim como as mensagens que esgotam as
tentativas, com os headers padrão do Spring Kafka
(`kafka_dlt-exception-message`, `-fqcn`, offset/partição originais). O DLT
não é reprocessado automaticamente; a métrica `notification.dlt.published`
permite alertar quando for > 0. Se o Brevo aceitou o envio mas a resposta
se perdeu, o retry reenvia o e-mail (duplicidade aceitável para e-mail
transacional). Sem `BREVO_API_KEY`/`BREVO_TEMPLATE_*` configurados, o
serviço sobe normalmente e todo envio falha (vai pro DLT).

---

## 5. Configuração e variáveis de ambiente

Variáveis comuns a (quase) todos os serviços:

| Variável | Descrição |
|---|---|
| `KEYCLOAK_ISSUER_URI` | `iss` esperado no token (ex.: `http://localhost:8180/realms/petshop`) |
| `KEYCLOAK_JWK_SET_URI` | JWKS do Keycloak (no compose, `http://keycloak:8080/realms/petshop/protocol/openid-connect/certs`) |
| `KEYCLOAK_AUDIENCE` | `aud` exigido no token (o id do próprio serviço) |
| `KEYCLOAK_TOKEN_URI` / `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` | Só quem chama outro serviço (`booking-service`, `order-service`, `staff-service`) e a Admin API do `customer-management`: Client Credentials do client do serviço |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | Conexão PostgreSQL (todos exceto `notification-service`) |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Broker Kafka (`customer-management`, `booking-service`, `order-service`, `notification-service`) |

Variáveis específicas relevantes:

| Serviço | Variável | Padrão | Descrição |
|---|---|---|---|
| `customer-management` | `SPRING_DATA_MONGODB_URI` | — | URI do Mongo (histórico) |
| `customer-management` | `BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` / ... | `admin@petshop.local` / vazio (gera aleatória) | Admin criado no primeiro start |
| `booking-service` | `EXTERNAL_API_STAFF_SERVICE_URL` | `http://localhost:8085` | URL do staff-service (Feign) |
| `staff-service` | `EXTERNAL_API_CUSTOMER_MANAGEMENT_URL` | `http://localhost:8082` | URL do customer-management (Feign) |
| `staff-service` | `STAFF_GROOMER_SATURDAY_COUNT` | `2` | Tosadores escalados por sábado |
| `order-service` | `EXTERNAL_API_PRODUCT_SERVICE_URL` / `_CUSTOMER_SERVICE_URL` | `:8081` / `:8082` | URLs Feign |
| `order-service` | `order.cart.reservation-ttl-hours` | `24` | TTL do carrinho |
| `order-service` | `order.payment.mock-delay-seconds` | `30` | Delay simulado de aprovação de pagamento |
| `order-service` | `order.pickup.min/max-delay-minutes` | `1` / `60` | Janela simulada até "pronto para retirada" |
| `notification-service` | `BREVO_API_KEY` / `BREVO_TEMPLATE_*` (10 vars) | vazio | Sem isso, envios falham silenciosamente |
| `*` (Kafka) | `KAFKA_<SERVIÇO>_USERNAME` / `_PASSWORD` | — | Credenciais SASL/PLAIN por serviço produtor/consumidor |

A lista completa e detalhada por serviço está no README de cada módulo
(seção "Configuração"), e um `.env` de exemplo pronto para
`docker compose up` está descrito na seção seguinte.

---

## 6. Como rodar localmente

### Docker Compose (recomendado)

1. Crie um `.env` na raiz do repositório (não versionado):

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
   BREVO_TEMPLATE_BOOKING_SCHEDULED=1
   BREVO_TEMPLATE_BOOKING_COMPLETED=2
   BREVO_TEMPLATE_BOOKING_CANCELED=3
   BREVO_TEMPLATE_ORDER_AWAITING_PAYMENT=4
   BREVO_TEMPLATE_PAYMENT_CONFIRMED=5
   BREVO_TEMPLATE_INVOICE_ISSUED=6
   BREVO_TEMPLATE_PICKUP_WINDOW=7
   BREVO_TEMPLATE_ORDER_READY_FOR_PICKUP=8
   BREVO_TEMPLATE_CART_REMINDER=9
   BREVO_TEMPLATE_PASSWORD_RESET=10
   ```

   Acrescente as variáveis do Keycloak (modelo em `.env.example`):
   `KC_BOOTSTRAP_ADMIN_USERNAME`/`KC_BOOTSTRAP_ADMIN_PASSWORD`, os
   `KEYCLOAK_CLIENT_SECRET_*` (`CUSTOMER_MANAGEMENT_ADMIN`, `BOOKING_SERVICE`,
   `ORDER_SERVICE`, `STAFF_SERVICE`) e o SMTP dos e-mails de senha
   (`KEYCLOAK_SMTP_HOST`/`PORT`/`FROM`/`USER`/`PASSWORD`). O compose sobe o
   `keycloak` (database `keycloak_db` no Postgres `db`) antes dos serviços;
   ver [`infra/keycloak/README.md`](../infra/keycloak/README.md).

2. Suba tudo:

   ```bash
   docker compose up --build
   ```

   Serviços expostos: `8081` produtos, `8082` clientes, `8083`
   agendamentos, `8084` notificações, `8085` staff, `8086` pedidos;
   Postgres em `5432`, MongoDB em `27017`, Kafka (listener externo) em
   `9094`.

### Rodando um serviço individualmente

O `pom.xml` na raiz é um agregador só de orquestração de build
(`packaging=pom`, sem `<parent>` — cada serviço continua herdando
`spring-boot-starter-parent` diretamente, Maven só permite um `<parent>`
por módulo). `mvn clean install` a partir da raiz builda os 7 módulos na
ordem certa (Maven resolve pelo grafo de `<dependency>` do reactor),
incluindo instalar `petshop-commons` no `.m2` local antes dos demais —
não é preciso fazer isso manualmente:

```bash
mvn clean install          # a partir da raiz do repositório
cd <algum-servico>
./mvnw spring-boot:run
```

Se preferir buildar só `petshop-commons` e um serviço específico (sem
passar pelos outros 6), o passo manual antigo ainda funciona:

```bash
cd petshop-commons && ./mvnw clean install
cd ../<algum-servico>
./mvnw spring-boot:run
```

Requer PostgreSQL/MongoDB/Kafka acessíveis conforme as variáveis daquele
serviço (ver seção 5, ou o README do serviço).

### Verificando que subiu

```bash
curl http://localhost:8082/actuator/health
```

Todo serviço expõe Actuator (`/actuator/health/liveness`,
`/actuator/health/readiness`) — usado também nas probes do Kubernetes
(manifests em `k8s/`).

---

## 7. Como testar

Cada serviço roda dois níveis de teste via Maven:

```bash
./mvnw test      # só testes unitários (JUnit 5 + Mockito)
./mvnw verify     # inclui testes de integração (Testcontainers: PostgreSQL/MongoDB/Kafka)
                  # + gate de cobertura JaCoCo
```

Gate de cobertura: **90%** de instruções em todos os serviços. O gate
exclui a classe principal (`*Application.class`) e os mappers gerados
pelo MapStruct (`*MapperImpl.class`).

**CI:** o pipeline [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
roda a cada push/PR em qualquer branch — builda `petshop-commons` primeiro
(instalado no repositório Maven local), depois cada serviço com `mvn clean
verify`. Só validação, sem etapa de deploy.

**Testando manualmente um fluxo completo** (exemplo: agendar um banho):

```bash
# 1. Obtenha um access token do admin de bootstrap (exige
#    BOOTSTRAP_ADMIN_PASSWORD no .env): faça login no web e copie o
#    access_token da sessão (o client `web` é Authorization Code + PKCE,
#    sem password grant). Detalhes em infra/keycloak/README.md

# 2. Com o token, criar um agendamento (petId precisa existir antes,
#    via /api/v1/customers/register/*/pet)
curl -X POST http://localhost:8083/api/v1/bookings/create \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"petId":"<uuid>","serviceType":"BANHO","bookingDateTime":"2026-08-15T10:00:00"}'
```

Se não houver `staff-service` no ar (ou nenhum `GROOMER` cadastrado e
habilitado), o passo 2 responde **422** com a mensagem "Não há funcionário
disponível para o horário selecionado" — é a checagem de disponibilidade
descrita em [4.4](#44-criação-de-agendamento) falhando de propósito.
