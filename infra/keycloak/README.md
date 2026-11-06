# Keycloak (SDD-024)

IdP do projeto: o realm `petshop` importado no boot (Fase 1). Desde as Fases 2 e 4
todos os serviços são Resource Servers (validam o access token pelo JWKS: assinatura,
`iss`, `exp` e `aud`) e as chamadas serviço-a-serviço usam Client Credentials com
permissões próprias (ver [Permissões de serviço](#permissões-de-serviço-fase-4)).
Desde a Fase 3 o login do `web/` é Authorization Code + PKCE no client `web`, e o
customer-management cria/mantém os usuários pela Admin API (client
`customer-management-admin`). Nenhum serviço guarda senha nem assina token.

## Subir

1. No `.env` da raiz, defina as variáveis do Keycloak (modelo em `.env.example`):
   `KC_BOOTSTRAP_ADMIN_PASSWORD`, os `KEYCLOAK_CLIENT_SECRET_*` e, para os e-mails
   de senha, os `KEYCLOAK_SMTP_*` (ver [E-mail (SMTP)](#e-mail-smtp)).
2. `docker compose up -d keycloak` (sobe o `db` junto).

O Keycloak usa o database `keycloak_db` do Postgres `db`. O `postgres-init/init.sql`
só roda na criação do volume: em um volume já existente, crie o database uma vez:

```
docker exec shared-db psql -U postgres -c "CREATE DATABASE keycloak_db;"
```

O realm é importado no primeiro boot (`--import-realm`, estratégia "ignorar se
existir"). Alterar `realm-petshop.json` não afeta um realm já importado: para
reaplicar, apague o database (`docker compose down -v`) ou edite pelo console.

## URLs

| O quê | URL |
|---|---|
| Admin console | http://localhost:8180/admin (usuário `KC_BOOTSTRAP_ADMIN_USERNAME`, padrão `admin`) |
| Issuer | `http://localhost:8180/realms/petshop` |
| Discovery | `http://localhost:8180/realms/petshop/.well-known/openid-configuration` |
| JWKS | `http://localhost:8180/realms/petshop/protocol/openid-connect/certs` |
| Health | `http://keycloak:9000/health/ready` (porta de management, só na rede interna) |

`KC_HOSTNAME=http://localhost:8180` fixa o `iss` dos tokens. Entre containers o
Keycloak é acessado em `http://keycloak:8080` (use isso no `jwk-set-uri`, mantendo o
`issuer-uri` como acima).

## Clients

| Client | Tipo | Para quê |
|---|---|---|
| `web` | público, Authorization Code + PKCE S256 | SPA em `http://localhost:5173` (Fase 3): redirect `/auth/callback`, renovação silenciosa `/auth/silent`, logout volta para `/`. Sem direct access grants (nenhum client tem password grant). |
| `customer-management-admin` | confidencial, só service account | Admin API (`manage-users`, `view-users`, `query-users`) para o customer-management criar/atualizar/desabilitar usuários e disparar o e-mail de definir senha (Fase 3). Como o client tem `fullScopeAllowed=false`, esses papéis também estão no escopo dele (`clientScopeMappings` de `realm-management`) — sem isso a Admin API responde 403. |
| `booking-service` | confidencial, só service account | Client Credentials das chamadas Feign do booking-service (staff-service). |
| `order-service` | confidencial, só service account | Idem, order-service (customer-management, product-registration). |
| `staff-service` | confidencial, só service account | Idem, staff-service (customer-management). |

Os segredos vêm das variáveis `KEYCLOAK_CLIENT_SECRET_*` (`${...}` no JSON, resolvido
no import); nenhum segredo é versionado. Os mesmos valores vão para os serviços
chamadores como `KEYCLOAK_CLIENT_SECRET` (docker-compose / `keycloak-secret` no k8s).

## Audience (`aud`)

Cada serviço exige o próprio id no `aud` do token (`keycloak.audience`). Os ids são
`customer-management`, `product-registration`, `staff-service`, `booking-service` e
`order-service`. Não há client no realm para todos eles (customer-management e
product-registration não chamam ninguém): os audience mappers usam
`included.custom.audience` com o id em texto, o que o `oidc-audience-mapper` permite
sem o client existir — por isso não foram criados clients bearer-only.

| Client | Audience mappers (protocol mappers do client) |
|---|---|
| `web` | `customer-management`, `product-registration`, `staff-service`, `booking-service`, `order-service` (todos que o frontend chama via gateway; o token do usuário também é repassado entre serviços) |
| `booking-service` | `staff-service` |
| `order-service` | `customer-management`, `product-registration` |
| `staff-service` | `customer-management` |

## Permissões de serviço (Fase 4)

O papel genérico `SERVICE` não existe mais. Cada chamada Feign tem um realm role
`svc:<destino>:<ação>`, atribuído só ao service account do chamador
(`users[].realmRoles`) e liberado no escopo do client (`scopeMappings`, já que os
clients de serviço têm `fullScopeAllowed=false`). O conversor do `petshop-commons`
transforma esses roles em authorities com o mesmo nome (`ServicePermissions`), e o
`SecurityConfig` do destino libera a rota com `hasAuthority`.

| Chamador | Rota no destino | Permissão |
|---|---|---|
| `booking-service` | staff-service `GET /api/v1/availability/general`, `GET /api/v1/availability/schedule` | `svc:staff:availability-read` |
| `order-service` | customer-management `GET /api/v1/customers/find/customer/{id}` | `svc:customers:customer-read` |
| `order-service` | product-registration `POST /api/v1/products/{id}/reserve`, `/release`, `/confirm` | `svc:products:stock-write` |
| `order-service` | product-registration `GET /api/v1/products/find/{id}`, `GET /api/v1/products/find?ids=` | — (rotas públicas; só o `aud` precisa bater) |
| `staff-service` | customer-management `GET /api/v1/staff/all` | `svc:customers:staff-read` |

Token de service account só recebe authorities `svc:*` (nunca `ROLE_*`): em qualquer
rota de usuário (`PetshopAuthorization.anyUser()`) ou que não libere a permissão dele,
a resposta é **403**. Num serviço que não está no `aud` do token, a resposta é **401**
(o token é recusado antes da autorização).

## Realm

- Papéis: `ADMIN`, `RECEPTIONIST`, `GROOMER`, `VETERINARIAN`, `CUSTOMER` (claim `realm_access.roles`).
- User Profile com `user_id` (UUID do customer-management), `cpf`, `phone`, `type`
  (`CUSTOMER`/`STAFF`/`SERVICE`), editáveis só por admin. O client scope padrão
  `petshop-claims` os emite com o mesmo nome no access token e no id token; `email` e
  `name` vêm dos escopos padrão `email` e `profile`. `lastName` é opcional (clientes com nome único).
- Cadastro público desligado (`registrationAllowed=false`): quem cria o usuário é o customer-management.
- Usuários: username = e-mail; credencial só no Keycloak. Cliente define a senha no cadastro;
  funcionário criado pelo admin nasce sem senha e recebe o e-mail de ação `UPDATE_PASSWORD`.
- "Esqueci a senha" na tela de login (`resetPasswordAllowed=true`); "Minha conta / alterar
  senha" no Account Console (`/realms/petshop/account`).
- Access token: 5 min. Refresh token com rotação (`revokeRefreshToken=true`, `refreshTokenMaxReuse=0`).
- Sessão (SSO), deslizante pela inatividade:

| | Idle | Max |
|---|---|---|
| "Lembrar de mim" marcado | 30 dias | 90 dias |
| Desmarcado | 1 dia | 1 dia |

  O Keycloak só distingue as duas situações por esses dois pares de campos
  (`ssoSessionIdleTimeout`/`ssoSessionMaxLifespan` e as variantes `...RememberMe`).
  Sem "Lembrar de mim" o cookie de sessão é descartado ao fechar o navegador (CA6).

## E-mail (SMTP)

`smtpServer` do realm usa `${KEYCLOAK_SMTP_HOST}`, `${KEYCLOAK_SMTP_PORT}`,
`${KEYCLOAK_SMTP_FROM}`, `${KEYCLOAK_SMTP_USER}` e `${KEYCLOAK_SMTP_PASSWORD}`, resolvidos
no import (STARTTLS, com autenticação). Padrão: SMTP relay da Brevo
(`smtp-relay.brevo.com:587`; usuário = login SMTP da conta, senha = chave SMTP, `from` =
remetente validado na Brevo). No compose vêm do `.env`; no k8s, do `keycloak-secret`
(`secret.example.yaml`). Sem SMTP configurado, os e-mails de definir/redefinir senha falham
(o cadastro de funcionário segue e loga aviso). Como os demais itens do realm, só vale num
realm importado do zero (ou ajuste em *Realm settings → Email* no console).

## Validar

Token de um client de serviço (substitua pelo valor de `KEYCLOAK_CLIENT_SECRET_BOOKING_SERVICE`):

```
curl -s -X POST http://localhost:8180/realms/petshop/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=booking-service -d client_secret=<segredo>
```

Cole o `access_token` em https://jwt.io (ou decodifique o payload) e confira `iss`
`http://localhost:8180/realms/petshop`, `exp` ~5 min e `azp=booking-service`. A
assinatura confere com a chave de `kid` correspondente em:

```
curl -s http://localhost:8180/realms/petshop/protocol/openid-connect/certs
```

Sem segredo (ou com segredo errado) o endpoint de token responde 401.

O token do `booking-service` traz `aud: ["staff-service"]` e
`realm_access.roles: ["svc:staff:availability-read"]`. Com ele (Fase 4):

```
# 200: rota liberada para a permissão
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8085/api/v1/availability/schedule?dateTime=2026-08-15T09:00:00&role=GROOMER'
# 403: rota de usuário no mesmo serviço
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8085/api/v1/weekend-allocation/generate
# 401: customer-management não está no aud do booking-service
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  -X DELETE http://localhost:8082/api/v1/customers/delete/customer/00000000-0000-0000-0000-000000000000
```

Alterou o realm (ex.: estas permissões) com um volume já existente? O import ignora
realm existente: recrie o database do Keycloak ou aplique pelo console.
