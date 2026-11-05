# Keycloak (SDD-024, Fase 1)

IdP do projeto. Aqui só está a infra: o realm `petshop` importado no boot. Os
serviços ainda não o usam (isso é das Fases 2 a 4 da SDD-024).

## Subir

1. No `.env` da raiz, defina as variáveis do Keycloak (modelo em `.env.example`):
   `KC_BOOTSTRAP_ADMIN_PASSWORD` e os `KEYCLOAK_CLIENT_SECRET_*`.
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
| `web` | público, Authorization Code + PKCE S256 | SPA em `http://localhost:5173` (Fase 3). Sem direct access grants. |
| `customer-management-admin` | confidencial, só service account | Admin API (`manage-users`, `view-users`, `query-users`) para o customer-management criar usuários (Fase 3). |
| `booking-service` | confidencial, só service account | Client Credentials das chamadas Feign do booking-service (staff-service). |
| `order-service` | confidencial, só service account | Idem, order-service (customer-management, product-registration). |
| `staff-service` | confidencial, só service account | Idem, staff-service (customer-management). |

Escopos/papéis finos por rota são da Fase 4. Os segredos vêm das variáveis
`KEYCLOAK_CLIENT_SECRET_*` (`${...}` no JSON, resolvido no import); nenhum segredo é versionado.

## Realm

- Papéis: `ADMIN`, `RECEPTIONIST`, `GROOMER`, `VETERINARIAN`, `CUSTOMER` (claim `realm_access.roles`).
- User Profile com `user_id` (UUID do customer-management), `cpf`, `phone`, `type`
  (`CUSTOMER`/`STAFF`/`SERVICE`), editáveis só por admin. O client scope padrão
  `petshop-claims` os emite com o mesmo nome no access token e no id token; `email` e
  `name` vêm dos escopos padrão `email` e `profile`. `lastName` é opcional (clientes com nome único).
- Cadastro público desligado (`registrationAllowed=false`): quem cria o usuário é o customer-management.
- Access token: 5 min. Refresh token com rotação (`revokeRefreshToken=true`, `refreshTokenMaxReuse=0`).
- Sessão (SSO), deslizante pela inatividade:

| | Idle | Max |
|---|---|---|
| "Lembrar de mim" marcado | 30 dias | 90 dias |
| Desmarcado | 1 dia | 1 dia |

  O Keycloak só distingue as duas situações por esses dois pares de campos
  (`ssoSessionIdleTimeout`/`ssoSessionMaxLifespan` e as variantes `...RememberMe`).
  Sem "Lembrar de mim" o cookie de sessão é descartado ao fechar o navegador (CA6).

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
