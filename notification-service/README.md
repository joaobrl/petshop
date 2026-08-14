# notification-service

Microsserviço de envio de notificações por e-mail do WebCommerce/PetShop. É um serviço fino, orientado a eventos: consome comandos de e-mail via Kafka e os envia através do Brevo, usando templates gerenciados no painel do Brevo (o serviço não renderiza HTML). Não expõe API REST própria e não possui persistência.

## Stack técnica

- Java 21, Spring Boot 4.0.3
- Spring Web (apenas infraestrutura/Actuator — sem controllers REST), Spring Kafka, Bean Validation
- Spring Cloud OpenFeign — cliente HTTP para a API transacional do Brevo
- Lombok
- JaCoCo — gate de 90% de cobertura de instruções

## Arquitetura

Hexagonal (ports & adapters), único fluxo de ponta a ponta:

```
Kafka (tópico "notification-commands")
  → NotificationCommandConsumer (adapter/in/kafka)
  → NotificationSender (porta de saída)
  → BrevoEmailAdapterOut (adapter/out/brevo)
  → BrevoFeignClient → API do Brevo (envio via templateId)
```

## Consumo de eventos

- **Tópico:** `notification-commands`, **grupo:** `notification-group`
- Payload (`SendEmailCommandDto`): `to`, `tipo` (um dos valores de `TipoNotificacao`), `params` — um mapa de variáveis para o template Brevo correspondente. O serviço produtor não pré-renderiza HTML nem escolhe o template; só o notification-service conhece o mapeamento `tipo → templateId` (`brevo.templates` em `application.yaml`).
- **Produtores conhecidos** (todos publicam neste mesmo tópico):
  - [`order-service`](../order-service) — pedido aguardando pagamento, pagamento confirmado, nota emitida, janela de retirada, pedido pronto, lembrete de carrinho abandonado
  - [`booking-service`](../booking-service) — agendamento criado, concluído, cancelado
  - [`customer-management`](../customer-management) — cadastro confirmado (boas-vindas), recuperação de senha
- Erros no envio são capturados e logados, sem retry nem dead-letter topic configurado.

## Envio de e-mail (Brevo)

`BrevoEmailAdapterOut` resolve `TipoNotificacao` → `templateId` via `BrevoProperties.templates()` e chama `POST /smtpEmail` do Brevo (`BrevoFeignClient`) com o `templateId` e `params` como `Map<String, Object>` de variáveis do template — sem remetente/assunto/corpo no payload, pois isso é fixo em cada template no painel do Brevo.

## Configuração (`application.yaml`)

| Variável | Padrão | Descrição |
|---|---|---|
| `server.port` | `8084` | Porta HTTP (apenas Actuator) |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker Kafka |
| `KAFKA_NOTIFICATION_USERNAME`/`PASSWORD` | — | Credenciais SASL/PLAIN do consumidor Kafka |
| `BREVO_API_KEY` | *(vazio)* | Chave de API do Brevo |
| `BREVO_BASE_URL` | `https://api.brevo.com/v3` | Base URL da API do Brevo |
| `BREVO_TEMPLATE_*` (11 variáveis, uma por `TipoNotificacao`) | *(vazio)* | ID numérico do template Brevo correspondente |

## Executando localmente

```bash
cd notification-service
./mvnw spring-boot:run
```

Requer um broker Kafka acessível e uma chave válida do Brevo (com os templates criados no painel) para o envio efetivamente funcionar (sem `BREVO_API_KEY`/`BREVO_TEMPLATE_*`, o serviço sobe normalmente, mas os envios falham).

## Docker

Build multi-stage: como o serviço depende da lib compartilhada `petshop-commons`
(não publicada em repositório remoto), o build precisa do contexto na raiz do
repositório, igual aos demais serviços:

```bash
docker build -t notification-service -f notification-service/Dockerfile .
```

No `docker-compose.yml` raiz, serviço `api-notification`, exposto em **`localhost:8084`**:

```bash
docker compose up api-notification
```

## Kubernetes

Manifests em [`k8s/api-notification/`](../k8s/api-notification): `deployment.yaml` (1 réplica, `api-notification:latest`, probes `/actuator/health/{liveness,readiness}`, requests `256Mi`/`200m`, limits `512Mi`/`500m`, `ClusterIP`), `configmap.yaml` (bootstrap Kafka + os 11 `BREVO_TEMPLATE_*`, não são segredo) e `secret.yaml` (`api-notification-secret` — contém placeholder para `BREVO_API_KEY`/credenciais Kafka; **substitua pelos valores reais antes de um deploy de verdade**).

## Testes

```bash
./mvnw test
./mvnw verify   # inclui gate de cobertura JaCoCo (90%)
```
