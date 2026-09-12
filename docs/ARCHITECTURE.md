# Arquitetura AutoCare

CMMS/EAM **desktop-first** para gestão de manutenção preventiva, preditiva e
corretiva de frotas de viaturas, máquinas e geradores em empresas.

## Visão geral

```
┌───────────────────────────┐        ┌──────────────────────────────┐
│  App web (desktop-first)   │  HTTPS │  Backend AutoCare            │
│  React + Vite + TypeScript │───────▶│  Spring Boot 3 (API REST /v1)│
│  tabelas densas, teclado   │◀───────│  ├─ Spring Security + JWT    │
│  claro/escuro, pt-AO       │        │  ├─ Domínio por módulos      │
└───────────────────────────┘        │  ├─ Spring Data JPA          │
                                     │  ├─ Scheduler (planos → OM)  │
   ┌──────────────────────┐          │  └─ Adapters externos*       │
   │ App de campo (Android)│  HTTPS   └───────────────┬──────────────┘
   │ PAUSADA — Fase 12     │──────────────────────────┘
   └──────────────────────┘                          │
                     ┌───────────────────────────────┼───────────────────────┐
                     ▼                               ▼                       ▼
              H2 (dev/teste) /                Object Storage*          Telemetry / labs*
              PostgreSQL (prod)               (fotos, PDFs, laudos)    (GPS, horímetro, óleo)

  * fases seguintes — arquitetura preparada, sem implementação falsa (regra #78)
```

## Backend (Java 21 / Spring Boot 3.3)

- **Modular.** Cada área de negócio é um pacote em `ao.autocare.modules`.
  - Fase 1 (feito): `auth`, `user`, `appconfig`, `catalog`, `audit`, `health`.
  - CMMS: `asset`, `location`, `meter`, `criticality`, `maintenanceplan`,
    `checklist`, `workorder`, `failure`, `part`, `stock`, `kpi`, `report`,
    `predictive`, `notification`, `document`, `subscription`, `telemetry`.
- **Esquema de BD é propriedade do Flyway** (`ddl-auto=none`). Entidades JPA
  acrescentadas por fase. As migrações da Fase 1 (`V1`, `V2`) cobrem o domínio
  antigo "viatura"; a Fase 2 introduz as migrações do domínio CMMS
  (`V3__cmms_core.sql`, …) e descontinua as tabelas não usadas.
- **Portabilidade H2 ↔ PostgreSQL**: PKs `VARCHAR(36)` (UUID da aplicação), sem
  enums nem arrays nativos, JSON em `TEXT` com conversores. Evitar palavras
  reservadas (ex.: `year` → `model_year`).
- **Multi-tenant.** `organizations` + `memberships` com papéis
  (owner / manager / planner / technician / viewer). Todos os dados do CMMS
  pertencem a uma organização; consultas sempre filtradas por `organization_id`.
- **Scheduler.** Um job periódico avalia planos vencidos (por medidor projetado
  ou por data) e cria Ordens de Manutenção; outro dispara alertas.

## Domínio CMMS — entidades principais

```
Organization ─< Membership >─ User
Organization ─< Location (empresa → obra/parque, hierárquica)
Organization ─< AssetType (viatura | máquina | gerador | …) ─< AssetSystem (motor, hidráulico…)
Location ─< Asset ─ AssetType
Asset ─ CriticalityAssessment (produção, segurança, financeiro → geral)
Asset ─< Meter (HOURMETER | ODOMETER) ─< MeterReading (valor, data, fonte, origem)
Asset ─< AssetDocument (seguro, inspeção, manual…) [Fase 9]

MaintenancePlan (modelo; por AssetType ou AssetModel)
  ─< PlanTask (título, sistema, instruções, duração estimada)
       ─< PlanTaskTrigger (METER a cada N h/km | CALENDAR a cada N dias | FIXED_METER 250/500…)
       ─< PlanTaskPart (peça prevista + quantidade)
       ─< PlanTaskTool (ferramenta/material)
AssetPlan (associação Asset ↔ MaintenancePlan, com o "relógio" de cada gatilho)

ChecklistTemplate ─< ChecklistItem      (inspeção diária antes do arranque)
ChecklistExecution ─< ChecklistResult   (por ativo, por turno)

WorkOrder (PREVENTIVA gerada de plano | CORRETIVA | INSPEÇÃO)
  estado: OPEN → PLANNED → IN_PROGRESS → DONE → VERIFIED
  ─ requestedBy / assignedTo / approvedBy
  ─< WorkOrderTask (de PlanTask ou ad-hoc) ─< WorkOrderTaskResult
  ─< LaborEntry (técnico, horas)
  ─< PartUsage (peça, quantidade, custo) → StockMovement
  ─ downtimeStart / downtimeEnd
Failure (ativo, data, descrição, causa) ← alimenta MTBF
Repair (workOrder, tempo de reparação)  ← alimenta MTTR

Part (catálogo, por sistema) ─< StockItem (armazém, quantidade, mínimo, custo médio)
Warehouse ─< StockItem ─< StockMovement (IN | OUT_WO | ADJUST | TRANSFER)

PredictiveTask (VIBRATION mensal | THERMOGRAPHY trimestral | OIL_ANALYSIS semestral)
  ─< PredictiveResult (data, medições, laudo, recomendação) [Fase 7]

Notification ─ NotificationPreference [Fase 8]
Plan ─ Subscription ─ Payment [Fase 10]
AuditLog · AppConfig
```

### Cálculo de vencimento de um plano

Cada gatilho de tarefa guarda o ponto do último cumprimento (data e/ou valor de
medidor). O sistema projeta a data de vencimento usando a **média de utilização
diária** do medidor (das leituras recentes) e aplica a regra **"o que ocorrer
primeiro"** entre o gatilho por medidor e o gatilho por calendário. Estados:
`EM_DIA`, `A_VENCER` (dentro da tolerância), `VENCIDA`.

### Indicadores (fórmulas fixas do documento de referência)

| KPI | Meta | Fórmula |
|---|---|---|
| Disponibilidade | ≥ 90% | (Horas Disponíveis / Horas Planeadas) × 100 |
| MTBF | ≥ 500 h | Horas de Operação / Nº de Falhas |
| MTTR | ≤ 4 h | Tempo Total de Reparação / Nº de Reparações |
| Cumprimento do Plano | ≥ 95% | (OM Executadas / OM Planeadas) × 100 |

Calculados por ativo, por local e por período. "Horas de Operação" vem do delta
do horímetro no período; "Horas Planeadas" da agenda de disponibilidade do ativo.

## Autenticação (Fase 1 — feito)

- Access token JWT curto (15 min) + refresh token (30 dias) com **rotação**.
- Refresh tokens com hash SHA-256; cada JWT tem `jti` único.
- BCrypt; tempo de resposta constante no login.
- `JwtAuthenticationFilter` stateless + `SecurityConfig` com rotas públicas
  explícitas e `/api/v1/admin/**` restrito a `ROLE_ADMIN`.

## Adapters para tudo o que é externo (regra #78)

- `TelemetryProvider` — `getMeterValue / getPosition / getStatus` (GPS + horímetro
  automático). Sem provedor real → `status = UNCONFIGURED`.
- `PredictiveProvider` — importação de laudos de laboratório / sensores.
- `NotificationChannel` — in-app, email, SMS, WhatsApp.
- `PaymentProvider` — Multicaixa Express, referência, transferência, fatura.
- `StorageProvider` — object storage para fotos, PDFs e laudos.

Nenhuma funcionalidade finge estar ligada: o que não está configurado mostra
"modo demonstração" e nunca simula dados reais.

## Frontend (app web)

- **React + Vite + TypeScript.** Desenhada para ecrãs grandes: tabelas com muitas
  linhas (ativos, OM, peças), filtros persistentes, ações em massa, atalhos de
  teclado, painéis lado a lado.
- Biblioteca de UI/tabela/router/estado — a decidir no arranque da Fase 2.
- Cliente HTTP com injeção de access token e renovação automática em 401.
- Tema claro/escuro com a paleta do produto. Todos os textos em pt-AO,
  centralizados (i18n).
- Autenticação exigida; navegação por: Dashboard · Ativos · Planos · Ordens ·
  Peças · Preditiva · Relatórios · Administração.

## Segurança e privacidade

- Headers do Spring Security, CORS restrito por ambiente, Bean Validation estrita.
- Isolamento por organização em todas as consultas.
- Auditoria (`audit_logs`) de login, cadastro, criação/alteração/eliminação de
  ativo, plano, OM, movimento de stock, bloqueio de motor e configuração.
  A tabela tem `organization_id` (V20) e **a empresa é obrigatória na assinatura
  de `AuditService.record(...)`** — não por gosto de cerimónia, mas porque
  enquanto era opcional uma linha mal atribuída era indistinguível de uma linha
  correta, e o registo inteiro ficava impossível de mostrar sem mostrar a
  atividade de todas as empresas. Agora o compilador obriga quem escreve uma
  ação nova a decidir a que empresa ela pertence.
  Leitura em `GET /api/v1/audit`, só para o Dono, sempre filtrada por empresa.
- Exportação e eliminação de dados expostas ao administrador da organização.

## Ambientes

| | Dev / Teste | Produção |
|---|---|---|
| BD | H2 modo PostgreSQL | PostgreSQL |
| Ficheiros | disco local | `StorageProvider` (object storage) |
| Config | `application.yml` + env | variáveis de ambiente |
