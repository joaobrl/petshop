# web — frontend do WebCommerce PetShop

SPA em **React + Vite + TypeScript** para a loja (catálogo, carrinho,
checkout), o agendamento de serviços (banho, tosa, consulta veterinária) e um
painel interno simples (recepção/staff). Usa o [Design System WebCommerce
PetShop](https://claude.ai/artifact/CfSwhVcNMPnwuGSt8XMpRK) como base visual:
tokens, logotipo e avatares de pet foram portados para cá. Integra com os
microsserviços reais do repositório — ver "Backend" abaixo.

## Como rodar

```bash
cd web
npm install
npm run dev
```

Abre em `http://localhost:5173`. `npm run build` gera o bundle de produção
(`dist/`); `npx tsc --noEmit` roda só o type-check.

## Backend

O frontend chama os microsserviços reais via `fetch` (ver `src/services/`) —
nenhum dado é mockado. Todas as chamadas vão para o **gateway-service**
(`http://localhost:8080`, o mesmo que o gateway expõe no `docker compose`),
que roteia por path para cada microsserviço — o frontend não usa mais as
portas individuais. Para outra base, defina `VITE_API_BASE_URL` (ver
`.env.example`). Pré-requisitos:

1. **Suba o backend** a partir da raiz do repositório (crie o `.env` descrito
   no README principal, incluindo `BOOTSTRAP_ADMIN_PASSWORD` se quiser uma
   senha previsível):

   ```bash
   docker compose up --build -d
   ```

2. **CORS**: nenhum dos 5 módulos com API REST tinha CORS configurado — foi
   adicionado um `CorsConfigurationSource` compartilhado
   (`petshop-commons/src/main/java/com/petshop/commons/web/CorsSupport.java`)
   liberando só `http://localhost:5173`, ligado em cada
   `infrastructure/security/SecurityConfig.java` de
   customer-management/product-registration/order-service/booking-service/staff-service.
   Para outro host/porta, edite a chamada `CorsSupport.defaultConfigurationSource(...)`
   em cada um.

3. **Dados iniciais (seed)**: o banco sobe vazio — só existe o admin de
   bootstrap. Com a stack de pé (gateway em `http://localhost:8080`), rode em
   `web/`:

   ```bash
   # credenciais do admin de bootstrap (as do .env da raiz); sem default no script
   export BOOTSTRAP_ADMIN_EMAIL=...
   export BOOTSTRAP_ADMIN_PASSWORD=...
   # o admin de bootstrap nasce com troca de senha obrigatória; o seed troca
   # para este valor na 1ª execução (mín. 6 caracteres)
   export SEED_ADMIN_NEW_PASSWORD=...
   npm run seed
   ```

   O script usa só a API (nada de SQL), é **idempotente** (rodar de novo não
   duplica) e **recusa** rodar se a base URL não for `localhost`/`127.0.0.1`.
   Cria: 10 produtos (um com estoque baixo e um esgotado), staff de cada cargo
   (recepcionista, 2 tosadores, veterinário), escala de sábados, 3 clientes com
   pets, agendamentos (futuro, cancelado e concluído), pedidos e um carrinho.
   Ao final imprime um resumo (criado / já existia / pulado) e os logins de
   exemplo.

   Configuração (`web/.env.seed.example` é versionado, com valores de teste):
   variáveis do ambiente > `web/.env.seed.local` > `web/.env.seed` >
   `web/.env.seed.example`. `API_BASE_URL` (padrão `http://localhost:8080`) e,
   opcionalmente, `CUSTOMERS_URL`/`PRODUCTS_URL`/`STAFF_URL`/`BOOKING_URL`/
   `ORDERS_URL` para falar direto com um serviço (também só local). Senhas de
   exemplo: `SEED_CUSTOMER_PASSWORD` e `SEED_STAFF_PASSWORD`. Use
   `.env.seed.local` ou `.env.seed` (ambos ignorados pelo git) para valores seus.

   Observações: (a) os clientes de exemplo são verificados pelo seed lendo o
   token do tópico Kafka `notification-commands` via `docker exec petshop-kafka`
   (SDD-020; sem Docker/Kafka o seed pula agendamentos e pedidos desses clientes
   e avisa no resumo); (b) o gateway limita login/cadastro a ~5/min por IP — o
   seed espera o `Retry-After` do 429 e segue, então a primeira execução pode
   levar alguns minutos; (c) o pagamento dos pedidos é simulado pelo
   `order-service` (aprova após ~30 s; não pagos expiram em 30 min); (d) os ids
   dos pedidos criados ficam em `web/.seed-state.local` (ignorado pelo git) para
   a idempotência, já que não existe endpoint de listar pedidos; (e) "Meus
   pedidos" lê os ids do `localStorage` do navegador, então os pedidos do seed
   aparecem no histórico de compras do cliente (após pagos) e na busca por id do
   painel, não na lista "Meus pedidos".

### Login para testar

- **Cliente**: crie uma conta em `/cadastro`, ou use uma conta já criada.
- **Painel interno** (`/interno/entrar`): a conta admin de bootstrap
  (`BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` no `.env`) ou uma das
  contas de staff do seed (senha `SEED_STAFF_PASSWORD`, ver acima).

## Bug de backend corrigido durante a integração

Testando o fluxo de carrinho real, duas chamadas de "adicionar ao carrinho"
próximas no tempo (sem carrinho aberto ainda) criavam **dois** carrinhos
`OPEN` para o mesmo cliente — `order-service`/`CartService.addItem` faz um
find-or-create sem nenhuma trava, e não há índice único no banco. A partir
daí, toda leitura do carrinho desse cliente quebrava com
`NonUniqueResultException` (500), porque a consulta original
(`findByCustomerIdAndStatus`) esperava resultado único. Corrigido trocando a
consulta por uma lista ordenada por `createdAt` e usando a mais recente
(`CartRepository`/`CartPersistenceAdapterOut`, com teste de regressão em
`CartRepositoryIntegrationTest`) — não impede novos duplicados, só impede que
virem erro 500; a causa raiz (ausência de lock/constraint no
find-or-create) segue sem resolver, fora do escopo desta integração.

## Limitações conhecidas do backend (não deste frontend)

- **`order-service`** só expõe `GET /orders/{id}` — não há endpoint para
  listar pedidos (nem do cliente, nem da loja toda) nem para a equipe
  atualizar o status de um pedido. "Meus pedidos" (`src/services/orderService.ts`)
  contorna isso guardando os ids de pedido criados neste navegador em
  `localStorage` e buscando cada um individualmente; a página interna de
  Pedidos virou uma busca por id em vez de uma fila. Resolver isso de verdade
  exige um endpoint novo no backend.
- **`booking-service`** não devolve o nome do pet nem do funcionário
  designado na resposta de um agendamento (só os ids/CPF) — a tela do
  cliente resolve o nome do pet localmente a partir do cadastro; a tela
  interna mostra o CPF do dono em vez do nome do pet.
- Catálogo de produtos: quem não é staff (inclui clientes logados) não recebe
  os campos de estoque na resposta — por isso o frontend não tenta mostrar
  "esgotado" antes de adicionar ao carrinho; o erro de estoque, se houver,
  vem do próprio `POST /cart/items`.

## Estrutura

```
src/
  styles/        tokens.css, base.css, components.css — direto do design system
  types/         tipos espelhando os DTOs reais dos módulos Java
  mocks/         só avatarOptions.ts — mapa dos avatares de pet enviados pro design system (não é dado mockado)
  services/      fetch real, todo via gateway (URL base em src/services/config.ts)
  context/       AuthContext (JWT real, decodificado no cliente), CartContext (carrinho real)
  components/
    ui/          Button, Badge, Card (classe .card), Field, Logo, PetAvatar, InitialsAvatar, PawBackground, Icon
    layout/      Header, Footer, StoreLayout, InternalLayout, RequireAuth, RequireStaff
    store/       ProductCard
  pages/
    store/       catálogo, produto, carrinho, checkout, confirmação
    account/     login (com fluxo de troca de senha obrigatória), cadastro, meus pets, meus pedidos
    booking/     novo agendamento, meus agendamentos
    internal/    login do painel, dashboard, agendamentos, pedidos (busca por id), equipe
scripts/
  seed-backend.mjs   `npm run seed`: produtos, staff, escala, clientes, pets, agendamentos e pedidos via API
```

## O que não está coberto

- Edição/remoção de pet (só cadastro de novo pet) — os endpoints existem no
  backend (`PATCH`/`DELETE /customers/{id}/pets/{petId}`), só não têm tela.
- Recuperação de senha (o backend tem `POST /auth/forgot-password`, sem UI
  aqui), paginação/filtro/busca no catálogo.
- Fila de pedidos e atualização de status pela equipe — ver "Limitações
  conhecidas do backend" acima.
