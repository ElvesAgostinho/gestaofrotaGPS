# API AutoCare — Fatias 1–7

Base: `http://localhost:8080/api/v1` · Documentação interativa: `/docs`

Autenticação por `Authorization: Bearer <accessToken>`. Rotas marcadas
"pública" não exigem token. Todos os recursos CMMS operam no contexto da
**organização** do utilizador autenticado (multi-tenant).

### Papéis e permissões

Cada membro tem um papel na empresa. Um papel cobre tudo o que os papéis
abaixo dele permitem.

| Papel | Pode |
|---|---|
| `OWNER` (Dono) | Tudo, incluindo gerir a equipa e a empresa |
| `MANAGER` (Gestor) | Ativos, tipos, locais, planos, peças/stock, verificar e cancelar ordens |
| `TECHNICIAN` (Técnico) | Leituras, checklists, fotografias, abrir e executar ordens de manutenção |
| `VIEWER` (Consulta) | Apenas leitura |

Sem permissão a resposta é **403** e a mensagem diz que papel é necessário.
Ler (`GET`) está aberto a qualquer membro. `DRIVER` é um papel legado
equivalente a `TECHNICIAN` e não é oferecido em novos convites.

## Autenticação

| Método | Rota | Pública | Descrição |
|---|---|:---:|---|
| POST | `/auth/register` | ✔ | Criar conta e empresa (`name`, `email` ou `phone`, `password`, `acceptTerms`, `organizationName?`) |
| POST | `/auth/login` | ✔ | Iniciar sessão (`identifier`, `password`) |
| POST | `/auth/refresh` | ✔ | Renovar tokens (`refreshToken`) — rotação |
| POST | `/auth/logout` | ✔ | Revogar refresh token |
| POST | `/auth/forgot-password` | ✔ | Pedir reposição (`identifier`) → devolve `demoMode: true` |
| POST | `/auth/reset-password` | ✔ | Definir nova palavra-passe (`token`, `password`) |
| GET | `/auth/me` | | Dados da conta autenticada |

Resposta de `register` / `login`:

```json
{
  "user": { "id": "...", "name": "...", "email": "...", "admin": false, "theme": "SYSTEM" },
  "accessToken": "jwt...",
  "refreshToken": "jwt...",
  "tokenType": "Bearer",
  "expiresInSeconds": 900
}
```

## Utilizadores

| Método | Rota | Descrição |
|---|---|---|
| PATCH | `/users/me` | Atualizar perfil (`name`, `locale`, `currency`, `theme`, `avatarUrl`) |
| POST | `/users/me/change-password` | `currentPassword`, `newPassword` (revoga sessões) |
| GET | `/users/me/export` | Exportar os dados da conta |
| DELETE | `/users/me` | Eliminar conta e dados associados |

## Configuração e catálogo

| Método | Rota | Pública | Descrição |
|---|---|:---:|---|
| GET | `/config` | ✔ | Nome do produto, moeda, locale, contactos de apoio |
| GET | `/catalog/plans` | ✔ | Planos (FREE / PERSONAL / GPS / FLEET) |
| GET | `/catalog/brands` | ✔ | Marcas e modelos de referência |
| GET | `/admin/config` | (admin) | Todas as chaves de configuração |
| PUT | `/admin/config` | (admin) | Definir uma chave (`key`, `value`) |

## Empresa (Fase 2)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/organization` | Empresa atual: nome, tipo, o meu papel, contagens (ativos, tipos, locais, membros) |
| PATCH | `/organization` | Renomear a empresa (`name`) — só OWNER/MANAGER |

## Equipa e convites (Fatia 5)

| Método | Rota | Papel mínimo | Descrição |
|---|---|---|---|
| GET | `/team/members` | membro | Equipa da empresa (nome, email, papel, cargo, suspenso) |
| GET | `/team/roles` | membro | Papéis atribuíveis, com descrição — para preencher listas na interface |
| PATCH | `/team/members/{membershipId}` | OWNER | Alterar `role`, `jobTitle` ou `suspended` |
| DELETE | `/team/members/{membershipId}` | OWNER | Remover da empresa |
| GET | `/team/invitations` | MANAGER | Convites enviados e o seu estado |
| POST | `/team/invitations` | OWNER | Convidar (`email`, `name?`, `jobTitle?`, `role`) |
| POST | `/team/invitations/{id}/resend` | OWNER | Gerar um novo link (o anterior deixa de servir) |
| DELETE | `/team/invitations/{id}` | OWNER | Anular um convite pendente |
| POST | `/team/invitations/accept` | membro | Aceitar com a conta atual (`token`) |
| GET | `/invitations/{token}` | **pública** | Ver empresa, papel e email do convite |
| POST | `/invitations/{token}/accept` | **pública** | Criar conta e entrar (`name`, `password`) |

Regras que o servidor garante:

- a empresa tem sempre pelo menos **um dono ativo** — não é possível remover,
  suspender ou despromover o último;
- ninguém altera o próprio papel nem se suspende/remove a si próprio;
- suspender revoga as sessões abertas do membro nesse instante;
- um convite vale **14 dias**; só o SHA-256 do token é guardado;
- se o email do convite já tiver conta AutoCare, o caminho público devolve
  **409** e a pessoa aceita a partir da sua própria sessão.

Sem serviço de email configurado, `POST /team/invitations` devolve
`demoMode: true`, `acceptUrl` e `token` para o gestor enviar o link pelo meio
que quiser — o sistema não finge que enviou (regra: nada de integrações falsas).

```json
{
  "invitation": { "id": "...", "email": "tecnico@empresa.ao", "role": "TECHNICIAN",
                  "roleLabel": "Técnico", "status": "PENDENTE", "expiresAt": "..." },
  "demoMode": true,
  "message": "Ainda não há serviço de email configurado. Envie este link a ...",
  "acceptUrl": "http://localhost:5173/convite/<token>",
  "token": "<token>"
}
```

## Locais (Fase 2)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/locations` | Lista plana (com nº de ativos por local) |
| GET | `/locations/tree` | Árvore hierárquica (empresa → obra → parque → ...) |
| GET | `/locations/{id}` | Um local |
| POST | `/locations` | Criar (`name`, `kind?`, `code?`, `parentId?`, `notes?`) — `kind`: SITE, YARD, WAREHOUSE, DEPARTMENT, OTHER |
| PATCH | `/locations/{id}` | Atualizar (inclui mover: `parentId`; sem ciclos) |
| DELETE | `/locations/{id}` | Eliminar (bloqueado se tiver sublocais ou ativos) |

## Tipos de ativo (Fase 2)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/asset-types` | Lista (com nº de ativos e sistemas) |
| GET | `/asset-types/{id}` | Um tipo |
| POST | `/asset-types` | Criar (`name`, `category?`, `primaryMeter?`, `secondaryMeter?`, `useStandardSystems?`, `systems?`) |
| PATCH | `/asset-types/{id}` | Atualizar |
| DELETE | `/asset-types/{id}` | Eliminar (bloqueado se houver ativos deste tipo) |

`category`: VEHICLE, MACHINE, GENERATOR, IMPLEMENT, OTHER ·
`primaryMeter`/`secondaryMeter`: HOURMETER, ODOMETER ·
`useStandardSystems` (por omissão `true`) cria os 8 sistemas do documento de
referência: Motor, Sistema Hidráulico, Sistema de Combustível, Sistema de
Transmissão, Eixos e Diferenciais, Sistema Elétrico, Sistema de Travagem,
Estrutura e Chassi.

## Ativos (Fase 2)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/assets` | Lista paginada (`archived`, `page`, `size`) — resumo com medidores e criticidade |
| GET | `/assets/{id}` | Ficha completa do ativo |
| POST | `/assets` | Criar (`tag`, `name`, `assetTypeId`, `locationId?`, `responsibleUserId?`, `responsibleLabel?`, `manufacturer?`, `model?`, `serialNumber?`, `modelYear?`, `plate?`, `objective?`, `initialMeterValue?`, ...) |
| PATCH | `/assets/{id}` | Atualizar |
| POST | `/assets/{id}/archive?archived=true\|false` | Arquivar / reativar |
| DELETE | `/assets/{id}` | Eliminar |
| GET | `/assets/{id}/criticality` | Matriz de criticidade |
| PUT | `/assets/{id}/criticality` | Definir (`productionImpact`, `safetyImpact`, `financialImpact` de 1 a 5; `overall?` para sobrepor) |

Ao criar um ativo, os medidores (horímetro / hodómetro) são criados
automaticamente a partir do tipo de ativo. **Criticidade geral** = pior dos três
impactos: 5 → CRITICAL, 4 → HIGH, 3 → MEDIUM, 1–2 → LOW (com sobreposição manual).

## Fotografias de ativos (Fatia 1)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/assets/{assetId}/photos` | Fotografias do ativo (principal primeiro) |
| POST | `/assets/{assetId}/photos` | Carregar (multipart: `file`, `kind?`, `caption?`) — máx. 15 MB, JPG/PNG/WEBP/GIF/PDF |
| PATCH | `/assets/{assetId}/photos/{photoId}` | Legenda, tipo, `primary`, ordem |
| DELETE | `/assets/{assetId}/photos/{photoId}` | Eliminar (promove a próxima a principal) |
| GET | `/files/{fileId}?sig=...` | Servir os bytes — **URL assinado**, sem token (para `<img src>`) |

`kind`: GENERAL, PLATE (matrícula), DAMAGE (avaria), DOCUMENT, METER. A primeira
fotografia fica automaticamente como principal. Os URLs assinados expiram
(`autocare.storage.url-ttl-seconds`, por omissão 1 h).

## Checklists de inspeção (Fatia 2)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/checklist-templates` | Modelos de checklist |
| POST | `/checklist-templates` | Criar (`name`, `estimatedMinutes?`, `assetTypeId?`, `items:[{text, verification, critical?}]`) |
| PUT | `/checklist-templates/{id}` | Atualizar (substitui os itens) |
| DELETE | `/checklist-templates/{id}` | Eliminar |
| GET | `/assets/{assetId}/checklist-executions` | Histórico de inspeções (paginado) |
| POST | `/assets/{assetId}/checklist-executions` | Registar (`templateId?`, `meterValue?`, `performedByLabel?`, `notes?`, `items:[{text, result, note?}]`) |
| GET | `/checklist-executions/{id}` | Uma inspeção completa |

`verification`: VERIFY (verificar), INSPECT (inspecionar), TEST (testar). `result`:
OK, NOT_OK, NA. Se algum item ficar NOT_OK, o `outcome` da execução é ISSUES.
Se não enviar `items`, parte do modelo com tudo OK.

## Planos de manutenção preventiva (Fatia 2)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/maintenance-plans` | Planos (resumo) |
| GET | `/maintenance-plans/{id}` | Plano com tarefas, gatilhos e peças |
| POST | `/maintenance-plans` | Criar (`name`, `assetTypeId?`, `tasks:[{systemCode?, systemName?, title, instructions?, tools?, triggers:[…], parts?:[…]}]`) |
| PUT | `/maintenance-plans/{id}` | Atualizar (substitui as tarefas) |
| DELETE | `/maintenance-plans/{id}` | Eliminar |

Gatilho: `{type: METER_INTERVAL, meterKind: HOURMETER|ODOMETER, interval: N, tolerance?: N}`
ou `{type: CALENDAR_DAYS, interval: N_dias, tolerance?: N_dias}`. Uma tarefa pode
ter vários — vale **o que ocorrer primeiro**.

### Atribuir e acompanhar num ativo

| Método | Rota | Descrição |
|---|---|---|
| GET | `/assets/{assetId}/maintenance-plans` | Planos do ativo + estado de cada tarefa (OK / DUE_SOON / OVERDUE, `nextDueMeter`, `nextDueAt`, `remainingMeter`, `remainingDays`) |
| POST | `/assets/{assetId}/maintenance-plans` | Atribuir (`planId`, `startFromNow?`) |
| DELETE | `/assets/{assetId}/maintenance-plans/{assetPlanId}` | Remover atribuição |
| POST | `/assets/{assetId}/plan-tasks/{taskStateId}/complete` | Marcar tarefa executada (`meterValue?`, `performedByLabel?`, `notes?`) — repõe o relógio |
| GET | `/assets/{assetId}/plan-task-completions` | Histórico de tarefas executadas |
| POST | `/assets/{assetId}/maintenance-plans/recompute` | Forçar recálculo |
| GET | `/assets/{assetId}/maintenance-plan.pdf` | **PDF** do "Plano de Manutenção Preventiva" do ativo |

O vencimento é recalculado automaticamente sempre que se regista uma leitura de
medidor. A projeção de dias usa a **média de utilização diária** do medidor.
O PDF reproduz o documento de referência: identificação, criticidade (estrelas),
inspeção diária, lubrificação, plano por horas, preditiva, indicadores e observações.

## Medidores (Fase 2)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/assets/{assetId}/meters` | Medidores do ativo com valor corrente e média diária |
| GET | `/assets/{assetId}/meters/{kind}/readings` | Histórico de leituras (mais recente primeiro), paginado |
| POST | `/assets/{assetId}/meters/{kind}/readings` | Registar leitura (`value`, `readingAt?`, `note?`) |

`kind`: `HOURMETER` ou `ODOMETER`. A leitura é sempre gravada; o sistema **sinaliza
inconsistências** (`flagged`, `flagReason`): valor inferior à leitura anterior,
data no futuro, aumento de horas superior ao tempo decorrido, ou km/dia
implausível. O valor corrente do medidor não recua.

## Peças e stock (Fatia 3)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/parts` | Catálogo de peças (com quantidade total e sinal de stock baixo) |
| GET | `/parts/low-stock` | Peças abaixo do mínimo |
| GET | `/parts/{id}` | Peça com stock por armazém |
| POST · PATCH · DELETE | `/parts` `/parts/{id}` | Gerir o catálogo |
| GET · POST · PATCH | `/warehouses` `/warehouses/{id}` | Armazéns |
| GET | `/stock/movements` | Histórico de movimentos (`partId` opcional) |
| POST | `/stock/movements` | Entrada / saída / ajuste (`partId`, `warehouseId`, `type`, `quantity`, `unitCost?`) |
| POST | `/stock/transfers` | Transferir entre armazéns |

`type`: IN, OUT_OTHER, OUT_WORK_ORDER, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT.
Stock negativo é recusado (409). Entradas com `unitCost` recalculam o **custo médio ponderado**.

## Ordens de Manutenção (Fatia 3)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/work-orders` | Listar (filtros `status`, `assetId`, `assignedTo`; paginado) |
| GET | `/work-orders/{id}` | Ficha completa (tarefas, mão de obra, peças) |
| POST | `/work-orders` | Criar corretiva / inspeção (`assetId`, `type`, `title`, `failure?`) |
| POST | `/work-orders/from-due` | **Gerar preventiva** das tarefas de plano vencidas de um ativo |
| PATCH | `/work-orders/{id}` | Atualizar (título, prioridade, agendamento, responsável) |
| POST | `/work-orders/{id}/start` | Iniciar (`stopAsset?` põe o ativo em paragem) |
| POST | `/work-orders/{id}/complete` | **Concluir** — consome peças, repõe os relógios do plano, regista a reparação |
| POST | `/work-orders/{id}/verify` | Verificar / aprovar |
| POST | `/work-orders/{id}/cancel` | Cancelar |
| POST | `/work-orders/{id}/labor` | Registar mão de obra (`hours`, `technicianLabel?`) |
| POST | `/work-orders/{id}/parts` | Adicionar peça (consumida ao concluir) |
| POST | `/work-orders/{id}/tasks/{taskId}` | Marcar/desmarcar tarefa |
| POST | `/assets/{assetId}/failures` | Registar uma avaria (alimenta o MTBF) |

Ciclo: `OPEN → PLANNED → IN_PROGRESS → DONE → VERIFIED` (ou `CANCELLED`).
Número sequencial por empresa: `OM-000123`.

**Responsável.** `assignedToUserId` atribui a ordem a um membro da equipa: o
servidor confirma que a pessoa pertence à empresa e não está suspensa, e passa a
mostrar o nome dela. `assignedToLabel` sozinho continua a servir para prestadores
externos sem conta. `""` desatribui. Para "as minhas ordens" use
`GET /work-orders?assignedTo=me`.
Um **agendador** recalcula os planos de hora a hora e gera OM preventivas às 06:00
para ativos com tarefas vencidas (`autocare.scheduler.enabled=false` desliga).

## Indicadores e painel (Fatia 3)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/kpis?assetId=&from=&to=` | Disponibilidade, MTBF, MTTR, Cumprimento do plano |
| GET | `/dashboard` | Resumo executivo: ativos, críticos, parados, tarefas vencidas/a vencer, OM abertas, peças em falta, próximas manutenções + KPIs |

Fórmulas (do documento de referência):

| Indicador | Meta | Fórmula |
|---|---|---|
| Disponibilidade | ≥ 90 % | (Horas Disponíveis / Horas Planeadas) × 100 |
| MTBF | ≥ 500 h | Horas de Operação / Nº de Falhas |
| MTTR | ≤ 4 h | Tempo Total de Reparação / Nº de Reparações |
| Cumprimento do plano | ≥ 95 % | (Ordens Executadas / Ordens Planeadas) × 100 |

As **horas de operação** vêm do delta do horímetro no período; o **downtime** da
sobreposição das paragens das OM com o período.

## GPS e telemetria (Fatia 4)

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| POST | `/telemetry/positions` | **aparelho** | Publicar posição (`deviceId`, `key`, `position`) |
| GET | `/telemetry/live` | membro | Posição atual de toda a frota |
| POST | `/telemetry/stream-ticket` | membro | Bilhete de 60 s, uso único, para abrir o fluxo |
| GET | `/telemetry/stream?ticket=` | **bilhete** | Fluxo SSE de posições em tempo real |
| GET | `/assets/{id}/track?from&to` | membro | Trajeto + km + velocidade máxima (24 h por omissão) |
| GET | `/assets/{id}/positions` | membro | Histórico de posições |
| POST | `/assets/{id}/position` | TECHNICIAN | Marcar posição à mão (ativo sem aparelho) |
| GET | `/assets/{id}/trips` · `/trips` | membro | Viagens |
| GET/POST/PATCH/DELETE | `/gps-devices` | MANAGER (escrita) | Aparelhos; criar devolve a chave uma única vez |
| POST | `/gps-devices/{id}/rotate-key` | MANAGER | Chave nova; a anterior deixa de servir |

**Ingestão.** O aparelho autentica-se com o id externo + chave (guardada só em
SHA-256). Uma posição má devolve **200 com `accepted: false`**, não um erro — um
rastreador que receba 4xx entra em ciclo de repetição e gasta o plano de dados.

**Qualidade dos dados.** Descartam-se posições com menos de 4 satélites, erro
declarado acima de 100 m, coordenadas (0,0) — o que os aparelhos enviam sem
sinal — e saltos que implicariam mais de 250 km/h desde a leitura anterior. A
posição *atual* do ativo nunca recua: pontos atrasados entram no histórico mas
não movem o ícone.

**Distância.** O odómetro do aparelho tem prioridade sobre a soma de linhas
rectas entre pontos, que inflaciona o total com o ruído do GPS. Se o odómetro
recuar (aparelho substituído), volta-se ao cálculo geométrico.

**Viagens.** Abrem por ignição ligada ou movimento; fecham por ignição
desligada, 5 min parado, ou 30 min de silêncio do aparelho. Viagens abaixo de
100 m são deriva de estacionamento e descartam-se.

**Tempo real.** SSE em vez de WebSocket: o fluxo é só num sentido e o
`EventSource` religa-se sozinho. Como não deixa enviar cabeçalhos, a ligação usa
um bilhete de uso único em vez do token de acesso no URL. Há batida de vida cada
20 s para os proxies não fecharem a ligação em silêncio.

## Geocercas (Fatia 4)

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| GET/POST/PATCH/DELETE | `/geofences` | MANAGER (escrita) | Áreas em círculo ou polígono |
| GET | `/geofences/{id}/inside` | membro | Quem está dentro agora, e desde quando |
| GET | `/geofence-events` · `/assets/{id}/geofence-events` | membro | Entradas e saídas |
| POST | `/geofence-events/{id}/acknowledge` | TECHNICIAN | Marcar como visto |

Sem `assetIds`, a área aplica-se a toda a frota. A **primeira** posição de um
ativo numa área não gera evento: fixa o estado, porque inventar uma "entrada"
que ninguém observou seria falsificar um acontecimento. O estado vive em
`geofence_presence`, o que mantém **uma consulta por posição** independentemente
do número de áreas.

## Alertas de telemetria (Fatia 4)

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| GET | `/telemetry/alerts?kind&open` | membro | `SPEEDING` / `COMMS_LOST` |
| GET | `/assets/{id}/telemetry-alerts` | membro | Alertas de um ativo |
| POST | `/telemetry/alerts/{id}/acknowledge` | TECHNICIAN | Marcar como visto |

**Limite de velocidade: zona > ativo > empresa.** Zona em
`PATCH /geofences/{id}` (`speedLimitKph`), ativo em `PATCH /assets/{id}`,
empresa em `PATCH /organization` (`defaultSpeedLimitKph`). Sem nenhum definido
**não há vigilância** — não se inventa um limite. Margem de 5 km/h.

Um alerta é um **episódio**: enquanto o excesso dura é o mesmo registo que se
actualiza com o pico. Fecha quando a velocidade volta ao limite.

**Perda de comunicação** é alerta, não só um estado: 30 min de silêncio abrem um
episódio que começa na *última comunicação*, e fecha quando o aparelho volta.

## Notificações (Fatia 6)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/notifications?unread` | As minhas notificações |
| GET | `/notifications/unread-count` | Quantas por ler |
| POST | `/notifications/{id}/read` · `/notifications/read-all` | Marcar como lidas |
| GET | `/notifications/preferences` | Preferências por categoria |
| PATCH | `/notifications/preferences/{category}` | Ligar/desligar (`inApp`, `email`) |

Cada aviso traz a sua **origem** (`sourceKind` + `sourceId`), com índice único por
utilizador: o agendador passa de hora a hora pelas mesmas tarefas vencidas e não
repete o aviso. Quando o problema deixa de existir (stock reposto, tarefa
executada) o aviso é **apagado**, para que a próxima ocorrência volte a avisar.

Sem servidor de email configurado, `emailState` fica `DEMO_MODE` — nunca `SENT`.
O aviso dentro da aplicação existe sempre. Para enviar a sério, acrescenta-se o
starter de email e publica-se um `EmailSender` como `@Primary`.

Quem recebe: alertas de frota e stock vão para donos e gestores; a ordem
atribuída vai para o técnico a quem foi atribuída.

## Manutenção preditiva (Fatia 7)

| Método | Rota | Papel | Descrição |
|---|---|---|---|
| GET | `/predictive/techniques` | membro | Técnicas e periodicidade habitual |
| GET | `/predictive?status=` | membro | Programas da frota (`OK`/`DUE_SOON`/`OVERDUE`) |
| GET | `/assets/{id}/predictive` | membro | Programas de um ativo |
| POST | `/assets/{id}/predictive` | MANAGER | Criar programa |
| POST | `/assets/{id}/predictive/standard` | MANAGER | Aplicar o conjunto do documento CAT |
| PATCH/DELETE | `/predictive/{id}` | MANAGER | Alterar (sem trocar a técnica) / remover |
| POST | `/predictive/{id}/readings` | TECHNICIAN | Registar medição |
| GET | `/predictive/{id}/readings` · `/assets/{id}/predictive-readings` | membro | Histórico |

O conjunto do documento de referência é vibração **mensal**, termografia
**trimestral** e análise de óleo **semestral**.

A agenda é de calendário (o plano preventivo é que conta horas). A antecedência
do aviso é **proporcional**: 10 % do intervalo, nunca menos de três dias — sete
dias seria cedo demais para uma análise mensal e tarde demais para uma anual.

Uma medição antiga registada tarde **não empurra a agenda**: só a medição mais
recente reagenda. Arrumar papelada atrasada não deve adiar a próxima análise.

`result` é obrigatório (`NORMAL`/`ATTENTION`/`CRITICAL`): medir não é decidir.
`ATTENTION` e `CRITICAL` avisam quem gere; a ordem corretiva só é aberta com
`openWorkOrder: true` — `CRITICAL` abre urgente, `ATTENTION` alta.

O PDF do plano mostra os programas reais do ativo, caindo para a tabela do
documento de referência apenas quando ainda não há nenhum configurado.

## Frota: motoristas, filiais e rotas (Fatia 13)

**Filiais** não têm rota própria: uma filial **é** um local com `kind=BRANCH`,
criado em `/locations` com `costCenter`, `city`, `province`, coordenadas e
`radiusMeters`. Uma segunda hierarquia paralela obrigaria a decidir, em cada
conta de custos, qual delas manda.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/drivers` | Motoristas (`?search=`) |
| GET | `/drivers/summary` | Contagens para o painel |
| GET | `/drivers/licenses` | Cartas caducadas ou a caducar em 30 dias |
| GET/POST/PUT/DELETE | `/drivers[/{id}]` | Ficha do motorista |
| POST | `/driver-assignments` | Pôr alguém ao volante de um ativo |
| PATCH | `/driver-assignments/{id}/end` | Encerrar a atribuição |
| GET | `/assets/{id}/drivers` | Quem conduziu este ativo, ao longo do tempo |
| GET/POST/PUT/DELETE | `/routes[/{id}]` | Rotas previstas |

Um motorista **não precisa de conta** no sistema. A atribuição é guardada **no
tempo**: é ela que permite dizer, meses depois, quem conduzia no dia da
infração. Uma carta caducada impede a atribuição.

## Condução: infrações e pontuação (Fatia 14)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/driving-events` | Infrações da frota |
| GET | `/trips/{id}/driving-events` | Infrações de uma viagem |
| POST | `/driving-events/{id}/dismiss` | Anular, com razão obrigatória |
| GET | `/drivers/{id}/score` | Pontuação num período |
| GET | `/driving/summary` | Ranking e contagens |

As viagens passam a trazer `startPlaceName` / `endPlaceName`, resolvidos contra
filiais, locais e geocercas **da empresa** — sem geocodificação externa. Quando
nada bate certo fica `UNKNOWN` e mostram-se as coordenadas.

Pontuação: `100 − (penalizações ÷ km × 100)`. Abaixo de 50 km devolve
`insufficientData: true` e `score` ausente — dar 100 a quem conduziu três
quilómetros poria essa pessoa acima de quem fez cinco mil com duas infrações.
Excesso de velocidade **não é redetetado**: vem dos alertas de telemetria, que
já aplicam os limites por zona, ativo e empresa.

## Controlo de combustível (Fatia 15)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/fuel/anomalies` | O que não bate certo (`?status=`) |
| GET | `/assets/{id}/fuel/anomalies` | Anomalias de um ativo |
| POST | `/fuel/anomalies/{id}/resolve` | Fechar, com explicação obrigatória |
| GET | `/fuel/baselines` | Consumo normal de cada ativo |
| GET | `/fuel/dashboard` | Custo por km, por filial, por motorista — e o que está por explicar |

Regras de deteção, todas com litros e dinheiro em risco:
`VOLUME_EXCEEDS_TANK`, `CONSUMPTION_SPIKE`, `DISTANCE_MISMATCH`,
`REFUEL_AWAY_FROM_VEHICLE`, `DUPLICATE_REFUEL`, `ODOMETER_ROLLBACK`,
`REFUEL_WITHOUT_MOVEMENT`, `MISSING_ODOMETER`.

`gpsVerified` **ausente** significa *não foi possível verificar* — não é o mesmo
que verificado e correto. Litros acima da capacidade do depósito **não são
recusados**: recusar ensina quem lança a baixar o número até passar, e destrói
a prova.

## Ordem de manutenção: ficha completa (Fatia 16)

| Método | Rota | Descrição |
|---|---|---|
| POST | `/work-orders/{id}/external-services` | Serviço de oficina externa |
| DELETE | `/work-orders/{id}/external-services/{serviceId}` | Retirar |

Campos novos na ordem: `dueAt` / `slaMet`, `estimatedHours` / `estimatedCost`,
`totalLaborCost` / `totalExternalCost` / `totalCost` / `costOverrunPercent`,
`downtimeHours` / `downtimeCost`, `underWarranty` / `warrantyReference` /
`warrantyRecovered`, `rootCause` / `correctiveAction`, `cancellationReason`,
`closingMeterValue`, `systemCode`, `branchId`, `driverId`, `requiresShutdown`,
`safetyNotes`, `verifiedByName`.

`insights` traz avisos que a ordem dá por si — avaria repetida no mesmo sistema,
serviço ainda em garantia, custo acima do orçamento, corretiva fechada sem causa
apurada, prazo vencido, paragem mais cara do que a reparação. **Nenhum bloqueia
nada**: impedir o registo não corrige a realidade, apenas faz com que ela deixe
de ser registada.

Anular uma ordem exige razão escrita.

## Módulo de manutenção (Fatia 17)

Numeração passou a `OM-2026-000001` — contador por ano.

| Método | Rota | Descrição |
|---|---|---|
| POST | `/work-orders/{id}/diagnosis` | Sintoma, diagnóstico, causa e solução |
| POST | `/work-orders/{id}/quotes` | Registar um orçamento (vários por ordem) |
| POST | `/work-orders/{id}/quotes/{quoteId}/select` | Escolher o que vale para aprovação |
| POST | `/work-orders/{id}/request-approval` | Pedir aprovação |
| POST | `/work-orders/{id}/approve` \| `/reject` | Decidir (Dono) |
| POST | `/work-orders/{id}/status` | Transição validada, com registo |
| POST | `/work-orders/{id}/close` | Fechar em definitivo |
| GET/POST/PUT | `/suppliers[/{id}]` | Oficinas e fornecedores, com NIF |

**Workflow:** `OPEN → DIAGNOSIS → QUOTING → AWAITING_APPROVAL → APPROVED →
IN_PROGRESS ⇄ AWAITING_PARTS → TESTING → DONE → VERIFIED → CLOSED`, mais
`REJECTED` e `CANCELLED`. O fluxo curto de sempre (`OPEN → IN_PROGRESS → DONE`)
continua válido — o percurso longo é para quando é preciso.

`nextStatuses` vem na resposta: o ecrã não replica a máquina de estados, porque
duas cópias da mesma regra acabam por discordar. Cada transição grava uma linha
em `work_order_status_history` com **quanto tempo esteve no estado anterior**.

**Aprovação:** abaixo de `organizations.maintenance_approval_limit` aprova-se
sozinha. Obrigar um dono a aprovar a troca de uma lâmpada faz com que ninguém
aprove nada.

**Custos e permissões:** quem não é Gestor recebe a ordem **sem os campos de
dinheiro** — removidos no servidor, não escondidos no ecrã.

## Registo de auditoria

Exige o papel de **Dono**: o registo diz o que toda a gente fez, incluindo
entradas na conta e alterações de palavra-passe.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/audit` | Registo da empresa, com filtros e paginação |
| GET | `/audit/actions` | Ações presentes nesta empresa, para o filtro |

Filtros de `/audit`: `action`, `entityType`, `entityId`, `userId`, `from`, `to`,
`search` (procura no detalhe, tratando `%` e `_` como texto), `page`, `size`.

**Toda** a consulta é filtrada por empresa, sem exceção nem caminho alternativo.
Uma linha cuja empresa não foi possível determinar não aparece a ninguém — falha
segura: perde-se informação, nunca se mostra a empresa errada. O `organizationId`
não vem no JSON: quem lê já está dentro da sua empresa, e o campo só serviria
para revelar que existem outras.

## Sistema

| Método | Rota | Pública | Descrição |
|---|---|:---:|---|
| GET | `/health` | ✔ | Estado do serviço e da base de dados |

## Formato de erro

Nunca é devolvido um erro técnico:

```json
{
  "statusCode": 400,
  "message": "Já existe uma conta com estes dados.",
  "path": "/api/v1/auth/register",
  "timestamp": "2026-09-09T00:00:00Z"
}
```

Erros de validação incluem `errors: [{ "field": "...", "message": "..." }]`.
