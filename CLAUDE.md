# CLAUDE.md

Notas específicas deste repositório para evitar erros já encontrados antes.

## Gotchas deste repositório

- **Spring Cloud Gateway 5.x renomeou tudo**: não é mais `spring-cloud-starter-gateway`,
  é `spring-cloud-starter-gateway-server-webflux` (ou `-server-webmvc`). As propriedades
  também mudaram de `spring.cloud.gateway.*` para `spring.cloud.gateway.server.webflux.*`.

- **`mvn test` já roda testes com Testcontainers** (ex.: `ActuatorHealthAccessTest` em
  `customer-management`), apesar do README dizer "só unitários" — não suba o
  `docker compose` completo ao mesmo tempo, o Docker fica sem recurso e os
  containers de teste falham ao iniciar.

- **Múltiplos worktrees do Claude Code + `container_name` fixo no docker-compose = conflito**:
  cada conversa roda num worktree git separado, mas os nomes de container são fixos
  (`api-customers`, `shared-db`...). Se outro worktree já criou esses containers
  (mesmo parados), o `docker compose up` daqui falha com "nome já em uso" — precisa
  `docker rm` os containers do outro worktree antes.

- **`.env` é gitignored, não existe em worktree novo** — copiar manualmente do
  checkout principal antes de subir o `docker compose` num worktree.

- **`localhost` não resolve certo no Git Bash deste ambiente** (curl dá `HTTP 000`) —
  usar `127.0.0.1:<porta>` explicitamente ao testar portas do Docker.

- **`depends_on` no `docker-compose.yml` não tem healthcheck** — `customer-management`,
  `booking-service`, `staff-service` e `order-service` podem cair no primeiro boot
  porque o Postgres ainda não aceita conexão. Se cair, só reiniciar com
  `docker compose up -d <serviço>` depois que o Postgres já estiver de pé.
