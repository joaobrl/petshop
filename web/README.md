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

3. **Dados iniciais**: o banco sobe vazio — sem produtos nem contas de staff
   além do admin de bootstrap. Rode o script de seed (entra no Keycloak como
   admin, com o mesmo Authorization Code + PKCE do SPA, e cadastra produtos)
   depois que os containers estiverem de pé; exige `BOOTSTRAP_ADMIN_PASSWORD`
   definida (admin com senha):

   ```bash
   node scripts/seed-backend.mjs
   ```

   As contas de staff de exemplo não são mais criadas pelo script: o
   customer-management as cria (local + Keycloak, senha de dev conhecida)
   quando roda com o profile `dev` — `SPRING_PROFILES_ACTIVE=dev` no `.env`
   (desligado por padrão; ver "Seed de funcionários de desenvolvimento" em
   `customer-management/README.md`). Clientes continuam pelo cadastro
   público (`/cadastro`).

4. **Keycloak**: login, "Manter conectado" (Remember Me), "Esqueci a senha" e
   troca de senha são telas do Keycloak (SDD-024 F3). O app usa Authorization
   Code + PKCE no client público `web` (`oidc-client-ts`, `src/services/oidc.ts`):
   `/entrar` e `/interno/entrar` só redirecionam para o Keycloak, o retorno é
   `/auth/callback` e a renovação silenciosa usa `/auth/silent`. Variáveis
   (opcionais): `VITE_KEYCLOAK_URL` (padrão `http://localhost:8180`),
   `VITE_KEYCLOAK_REALM` (`petshop`), `VITE_KEYCLOAK_CLIENT_ID` (`web`).
   O access token fica **só em memória** (nunca em `localStorage`/`sessionStorage`);
   é renovado pelo refresh token do Keycloak e, num 401, uma renovação
   compartilhada é tentada antes do fluxo de sessão expirada (SDD-015).
   Recarregar a página recupera a sessão pelo SSO do Keycloak (signin silencioso).
   Só o `state`/`code_verifier` do redirect fica em `sessionStorage` durante a
   ida e volta ao Keycloak. "Minha conta / alterar senha" abre o Account Console
   (`/realms/petshop/account`); "Sair" é o `end_session` do Keycloak.

### Login para testar

- **Cliente**: crie uma conta em `/cadastro` (a senha escolhida vai para o
  Keycloak) e entre pela tela do Keycloak.
- **Painel interno** (`/interno/entrar`): a conta admin de bootstrap
  (`BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` no `.env`), uma das
  contas de staff do profile `dev` (senha `DEV_SEED_STAFF_PASSWORD`) ou um
  funcionário criado em "Equipe" (define a senha pelo e-mail do Keycloak).

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
  context/       AuthContext (login OIDC no Keycloak, token só em memória, claims decodificados no cliente), CartContext (carrinho real)
  components/
    ui/          Button, Badge, Card (classe .card), Field, Logo, PetAvatar, InitialsAvatar, PawBackground, Icon
    layout/      Header, Footer, StoreLayout, InternalLayout, RequireAuth, RequireStaff
    store/       ProductCard
  pages/
    store/       catálogo, produto, carrinho, checkout, confirmação
    account/     login (redireciona ao Keycloak), callback OIDC, cadastro, verificação de e-mail, meus pets, meus pedidos
    booking/     novo agendamento, meus agendamentos
    internal/    login do painel (Keycloak), dashboard, agendamentos, pedidos (busca por id), equipe
scripts/
  seed-backend.mjs   popula produtos via API (admin logado no Keycloak), depois do docker compose up
```

## O que não está coberto

- Edição/remoção de pet (só cadastro de novo pet) — os endpoints existem no
  backend (`PATCH`/`DELETE /customers/{id}/pets/{petId}`), só não têm tela.
- Paginação/filtro/busca no catálogo. (Recuperação de senha é a tela
  "Esqueci a senha" do Keycloak.)
- Fila de pedidos e atualização de status pela equipe — ver "Limitações
  conhecidas do backend" acima.
