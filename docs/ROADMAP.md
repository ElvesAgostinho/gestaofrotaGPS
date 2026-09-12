# Roadmap AutoCare — CMMS/EAM de manutenção de frotas e ativos

Sistema **desktop-first** (app web) para **empresas** com muitas viaturas, máquinas
e geradores. Modelado a partir do documento de referência "Plano de Manutenção
Preventiva" (Caterpillar CAT 416F2).

Desenvolvimento por fases. Só se avança com a fase anterior **a funcionar, testada
e sem erros conhecidos** — ver [phase-testing](./phase-testing.md).

O **MVP utilizável** são as Fases 2–6: cadastrar ativos, definir planos, gerar e
executar ordens de manutenção, controlar stock de peças e ver os indicadores +
gerar o PDF do plano.

---

## Fase 1 — Fundação  ✅ (concluída)
Backend Java/Spring Boot: arquitetura modular, autenticação (JWT + refresh com
rotação), **organizações multi-tenant + membros**, utilizadores, configuração da
app, auditoria, erros em PT, OpenAPI. 31 testes a passar.
*(O esquema de dados da Fase 1 cobre o domínio antigo "viatura"; é expandido/
refeito a partir da Fase 2 para o domínio CMMS.)*
App Android — escrita, **pausada**.

## Execução por fatias verticais (2026-09-09)

Após o pivot, o trabalho passou a ser feito em **fatias verticais completas**
(backend testado + ecrã React polido). O backend de todas as fatias é construído
primeiro; o frontend React completo entra a seguir.

### Fatia 1 — Ativos + fotografias  ✅ backend (66 testes)
Multi-tenant, locais, tipos de ativo (+8 sistemas), ativos genéricos
(viatura/máquina/gerador), medidores com deteção de inconsistências, matriz de
criticidade, **fotografias** (upload, tipos, galeria, URLs assinados).

### Fatia 2 — Plano de manutenção (documento CAT)  ✅ backend
- **Checklists** de inspeção: modelos + execução com resultado por item
  (Verificar / Inspecionar / Testar), outcome OK/ISSUES.
- **Planos de manutenção**: tarefas por sistema, gatilhos por medidor
  (50/250/500/1000/2000 h) e/ou calendário, regra "o que ocorrer primeiro".
- **Atribuição a ativo** + motor de cálculo de "próxima manutenção / vencida"
  (usa a média de utilização diária); recálculo automático a cada leitura.
- **Marcar tarefa executada** repõe o relógio; histórico de execuções.
- **Geração de PDF** "Plano de Manutenção Preventiva" no formato do documento.
- Seed do plano exato da CAT 416F2 (33 tarefas + checklist de 11 itens).

### Fatia 3 — Ordens de Manutenção + Peças + Indicadores  ✅ backend (101 testes)
- **Peças e stock**: catálogo por sistema, armazéns, movimentos
  (entrada/saída/ajuste/transferência), custo médio ponderado, stock mínimo.
- **Ordens de Manutenção**: numeração `OM-000123` por empresa, corretiva /
  preventiva / inspeção, `from-due` gera a ordem das tarefas vencidas do plano,
  ciclo `OPEN → PLANNED → IN_PROGRESS → DONE → VERIFIED`, mão de obra e peças.
  Concluir uma ordem repõe os relógios do plano, consome o stock, devolve o
  ativo a operacional e regista a reparação (MTTR).
- **Indicadores** com as fórmulas do documento: Disponibilidade ≥ 90 %,
  MTBF ≥ 500 h, MTTR ≤ 4 h, Cumprimento do plano ≥ 95 %; painel da frota.
- **Agendador**: recálculo dos planos de hora a hora e geração automática de
  ordens preventivas às 06:00.

### Fatia 5 — Equipa e permissões  🔄 backend
- **Papéis**: Dono / Gestor / Técnico / Consulta, hierárquicos.
- **Convites por email** com token de 14 dias (só o hash é guardado); aceitação
  cria conta nova ou liga uma conta existente à empresa.
- **Guarda de permissões** (`@RequireRole`) em todos os endpoints de escrita.
- Suspender / reativar / remover membros, com as invariantes "sempre um dono
  ativo" e "ninguém se altera a si próprio".
- Sem serviço de email configurado, o convite entra em **modo demonstração**:
  o link vem na resposta em vez de o sistema fingir que enviou.

### Fatia 4 — GPS + telemetria  ✅ backend (161 testes)
- **Aparelhos** com chave de publicação (SHA-256, rotação), ingestão pública.
- **Qualidade dos dados**: descarte por satélites, precisão e saltos impossíveis;
  odómetro do aparelho com prioridade sobre a soma de linhas rectas.
- **Viagens** agregadas por ignição/movimento, com fecho por paragem ou silêncio.
- **Geocercas** em círculo e polígono, com tabela de presença (uma consulta por
  posição) e eventos de entrada/saída.
- **Tempo real por SSE** com bilhete de uso único (o `EventSource` não envia
  cabeçalhos) e batida de vida.
- **Alertas**: excesso de velocidade (zona > ativo > empresa, por episódio) e
  perda de comunicação do aparelho.


### Fatia 6 — Notificações  ✅ backend (169 testes)
Avisos dentro da aplicação com origem única (não repetem) e resolução automática
quando o problema desaparece. Porta `EmailSender` com implementação de
demonstração enquanto não há SMTP — `emailState` nunca mente.

### Fatia 7 — Manutenção preditiva  ✅ backend (181 testes)
Programas de monitorização de condição por ativo (vibração, termografia, análise
de óleo, ultrassom, alinhamento, isolamento), agenda de calendário com janela de
aviso proporcional, registo classificado de medições, abertura opcional de ordem
corretiva, e integração com o PDF do plano.

### Fatia 8 — Documentos do ativo  ✅ backend (192 testes)
Manuais, apólices, livretes, certificados, com vigilância de validade. Avisos em
marcos (30/7/0 dias e depois de caducar) em vez de repetição diária; renovar
limpa o aviso.

### Fatia 9 — Importação em massa  ✅ backend (213 testes)
CSV de ativos e peças, com verificação sem gravar (`dryRun`), erro por linha com
o número da linha do ficheiro, e reimportação que atualiza em vez de duplicar.
Leitor de CSV próprio (ponto e vírgula ou vírgula, BOM do Excel, aspas).

### Fatia 10 — Relatórios  ✅ backend (221 testes)
Sete exportações em CSV que abrem corretamente no Excel português (ponto e
vírgula, decimais com vírgula, BOM). O relatório de indicadores leva os números
que alimentam as fórmulas, para poder ser auditado.

### Fatia 11 — Produção  ✅ backend (230 testes)
`ProductionSafetyCheck` recusa arrancar com perfil `prod` se os segredos forem os
do código-fonte, forem curtos, forem iguais entre si, se a base de dados for H2
local ou se o CORS estiver aberto. Limitação de tentativas por endpoint,
`application-prod.yml`, Dockerfile multi-etapa, docker-compose com PostgreSQL,
`docs/DEPLOY.md`.

### Fatia 12 — Combustível  ✅ backend
Registo de abastecimentos com cálculo de consumo entre enchimentos completos e
comparação com a média do próprio ativo. **É contabilidade, não controlo**: não
deteta desvios em tempo real, e o ecrã di-lo. O modelo está preparado para
receber sensor de nível (`FuelSource.SENSOR`, capacidade do depósito).

### Fatia 13 — Email por SMTP  ✅ backend
`SmtpEmailSender` com modelo HTML em Thymeleaf, ativado por `MAIL_HOST`. Sem
servidor configurado continua o modo demonstração — nada é marcado como enviado.

### Fatia 14 — Bloqueio remoto do motor  ✅ backend
Delegado num servidor Traccar, que é quem mantém a ligação aos aparelhos.
Travas: motivo obrigatório, confirmação explícita, aprovação em segundo passo
(por outra pessoa quando há mais de um dono), envio só com a viatura parada em
três leituras seguidas, posição recente obrigatória, prazo de validade, e
auditoria completa. Desbloquear é imediato e sem travas.

### Aplicação web  ✅
React 18 + Vite + TypeScript + Mantine 7 + TanStack Query + MapLibre.
Ecrãs: entrada, painel com indicadores, ativos (lista e ficha com plano,
preditiva, combustível, documentos, fotografias, criticidade e bloqueio), mapa
ao vivo por SSE com fundo de satélite, ordens de manutenção, preditiva, peças,
documentos, alertas, notificações com preferências, relatórios, equipa e
definições com importação em massa.

---

## O que falta para vender

1. **Configurar em produção** — SMTP para os emails saírem, e Traccar se quiser
   bloqueio remoto. Ver `docs/DEPLOY.md`.
2. **Confirmar o bloqueio no terreno** antes de confiar nele: o que o aparelho
   faz com `engineStop` depende do fabricante e da instalação.
3. **Sensor de combustível**, se quiser deteção de desvio em tempo real —
   precisa de calibração ponto a ponto por viatura.

---

## (Referência original das fases)

## Fase 3 — Planos de manutenção preventiva
- **Modelos de plano** por modelo ou tipo de ativo (reutilizáveis).
- **Tarefas** agrupadas por sistema, com ferramentas/materiais e peças previstas.
- **Gatilhos** por medidor (cada X horas / X km) **e/ou** calendário. Regra "o que
  ocorrer primeiro". Tolerância configurável.
- **Checklists de inspeção diária** (antes do arranque): modelos + execução.
- Cálculo de **próxima manutenção** e estado (em dia / a vencer / vencida).

## Fase 4 — Ordens de Manutenção (OM)
- **Geração automática** de OM quando um plano vence (por medidor ou data).
- **OM corretivas** (avaria / pedido).
- Ciclo de vida: aberta → planeada → em execução → concluída → verificada/aprovada.
  Fluxo **elaborado por / aprovado por**.
- Registo de **mão de obra**, tempo de reparação, tempo de paragem, **peças
  consumidas**, custo.
- **Registo de falhas** (alimenta MTBF) e **reparações** (alimenta MTTR).
- Anexos e assinaturas.

## Fase 5 — Peças e stock
- **Catálogo de peças** por sistema (filtro de óleo, mangueiras, relés, dentes da
  concha, pinos e buchas…).
- **Armazéns**, nível mínimo, ponto de encomenda, custo médio.
- **Movimentos**: entrada, saída por OM, ajuste, transferência.
- Alertas de stock baixo. Valorização do inventário.

## Fase 6 — Indicadores + geração de PDF + relatórios
- **Dashboard de KPIs** com as fórmulas exatas do documento:
  - Disponibilidade ≥ 90% = (Horas Disponíveis / Horas Planeadas) × 100
  - MTBF ≥ 500h = Horas de Operação / Nº de Falhas
  - MTTR ≤ 4h = Tempo Total de Reparação / Nº de Reparações
  - Cumprimento do Plano ≥ 95% = (OM Executadas / OM Planeadas) × 100
  Por ativo, por obra, por período.
- Custos de manutenção (por ativo, por sistema, preventiva vs corretiva).
- **Geração do PDF** "Plano de Manutenção Preventiva" igual ao documento de
  referência (identificação, criticidade, inspeção diária, lubrificação, plano por
  horas, preditiva, KPIs, peças em stock, observações, elaborado/aprovado).
- Exportação CSV / Excel. Relatórios agendados.

## Fase 7 — Manutenção preditiva
- Agenda de **análise de vibração** (mensal), **termografia** (trimestral),
  **análise de óleo** (semestral) por ativo/componente.
- Registo de resultados, laudos anexados, tendências.
- Gerar OM a partir de uma recomendação.
- `PredictiveProvider` adapter (laboratórios / sensores externos — modo demo até configurar).

## Fase 8 — Notificações + alertas
- Motor de alertas: manutenção a vencer, OM atrasada, stock baixo, criticidade
  elevada sem plano, documento a caducar, leitura de medidor em atraso.
- `NotificationChannel` multi-canal: in-app, email (SMS/WhatsApp depois).
- Agrupamento, prioridades, preferências por utilizador e por papel.

## Fase 9 — Documentos + conformidade
- Documentos do ativo: seguro, inspeção, licenciamento, garantias, manuais de
  operação e serviço.
- Alertas de caducidade (60/30/15/7/1/0 dias, configurável).
- Requisitos legais por tipo de ativo.

## Fase 10 — Assinaturas B2B + faturação
- Planos por nº de ativos / utilizadores / módulos ativos.
- `PaymentProvider` adapter: Multicaixa Express, referência, transferência, fatura
  pró-forma. Gestão de contratos.

## Fase 11 — Telemetria / GPS / IoT
- `TelemetryProvider` adapter multi-marca (Traccar, REST, MQTT…).
- **Leitura automática de medidores** (dispensa registo manual do horímetro).
- Localização de ativos, geofencing, alertas.
- Combustível: abastecimentos, consumo, deteção de desvios e furtos.

## Fase 12 — IA + avançado
- Previsão de falhas sobre o histórico real da conta.
- Otimização de planos e de níveis de stock.
- Assistente de manutenção.
- API pública, integrações com ERP.
- Retoma da **app de campo** (Android) para técnicos: leituras + checklists offline.

---

## Arquitetura preparada desde já
- **Multi-tenant** com isolamento de dados por organização (obras, equipas, papéis).
- **Adapters** para telemetria/GPS, pagamentos, notificações, análise preditiva e storage.
- Auditoria de todas as ações relevantes.
- Nada de integrações falsas: o que não está configurado mostra "modo demonstração".
