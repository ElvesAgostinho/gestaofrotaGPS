# IMBONDEIRO OS

Gestão de frotas e manutenção (CMMS/EAM) para empresas em Angola: viaturas,
máquinas pesadas e geradores. Ordens de serviço, planos de manutenção,
combustível com controlo por sensor e cartões, GPS via Traccar, guias de
transporte, pneus, motoristas (cartas, infrações, escalas), orçamento anual,
impressos com o timbre da empresa e selo de autenticidade verificável,
avisos por WhatsApp/SMS, marca branca por cliente, permissões por módulo, e
uma app do telemóvel (PWA instalável) para o motorista comunicar avarias com
fotografia, fazer a inspeção diária e abastecer, e para o mecânico executar
as suas ordens.

| Pasta | O que é |
|---|---|
| `backend/` | API — Java 21, Spring Boot 3, Flyway, PostgreSQL (H2 em desenvolvimento) |
| `web/` | Aplicação web — React 18, Vite, TypeScript, Mantine, MapLibre |
| `deploy/` | Instalação: `docker compose`, Easypanel, cópias de segurança, OSRM |
| `docs/` | Guias: **[EASYPANEL.md](docs/EASYPANEL.md)** (passo a passo), DEPLOY.md, TRACCAR.md, ARCHITECTURE.md |
| `web/src/pages/mobile/` | A app do telemóvel (PWA em `/m`): instala-se a partir do browser, sem loja |
| `mobile/` | App Android nativa (pausada; substituída pela PWA) |

## Correr em desenvolvimento

```bash
# API (porta 8080; base de dados H2 em backend/data)
cd backend && mvn spring-boot:run

# Web (porta 5173; /api reencaminhado para 8080)
cd web && npm install && npm run dev
```

Conta de demonstração criada no primeiro arranque: `demo@autocare.ao` / `demo1234`
(é administrador da plataforma: vê o ecrã **Plataforma**, onde se criam as
empresas clientes). Em desenvolvimento o registo livre está aberto; em
produção está fechado e o administrador vem de `ADMIN_EMAIL` / `ADMIN_PASSWORD`.

## Testes

```bash
cd backend && mvn test          # 455 testes de integração
cd web && npx tsc --noEmit      # tipos
```

A regra do projeto: **não avança sem testar**. Cada funcionalidade tem testes
contra servidores reais (Traccar, OSRM, geocodificador levantados no próprio
teste) e os ecrãs são percorridos num navegador a sério.

## Produção

Ver [docs/EASYPANEL.md](docs/EASYPANEL.md) (VPS com Easypanel) ou
[docs/DEPLOY.md](docs/DEPLOY.md) (`docker compose` num servidor Linux).
