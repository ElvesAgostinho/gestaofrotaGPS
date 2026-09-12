# Instalar o IMBONDEIRO OS no Easypanel (VPS Hostinger) — passo a passo

Resultado no fim: `https://frota.o-seu-dominio` a servir a aplicação,
`https://traccar.o-seu-dominio` a servir o Traccar, os rastreadores a entrar
pelas portas 5001–5150, cópias de segurança diárias.

Tempo: cerca de 40 minutos, quase todo a esperar por builds.

---

## 0. O que precisa antes de começar

| | |
|---|---|
| VPS | Hostinger KVM1 com o Easypanel já instalado (2 vCPU / 4 GB chega) |
| Domínio | Dois subdomínios a apontar para o IP da VPS: `frota.` e `traccar.` (registos **A**) |
| GitHub | O repositório `ElvesAgostinho/gestaofrotaGPS` ligado ao Easypanel (Settings → GitHub) |
| Segredos | Gere-os agora, num terminal qualquer: |

```bash
openssl rand -base64 48   # JWT_ACCESS_SECRET
openssl rand -base64 48   # JWT_REFRESH_SECRET  (diferente do anterior)
openssl rand -base64 24   # DATABASE_PASSWORD
```
Sem `openssl`: qualquer gerador de palavras-passe com 40+ caracteres serve.

---

## 1. Criar o projeto

Easypanel → **Projects → + Create** → nome `frota`.

Todos os serviços abaixo vão dentro deste projeto. **Dentro de um projeto os
serviços falam entre si pelo nome**: `db`, `api`, `traccar`. (Se um serviço não
encontrar outro pelo nome, use `frota_db`, `frota_api` — o nome interno
completo que o Easypanel mostra no separador Domains/Advanced do serviço.)

---

## 2. Base de dados — serviço `db`

**+ Service → Postgres**
- Service name: `db`
- Image: deixe a que vier (Postgres 16)
- Password: o `DATABASE_PASSWORD` que gerou

Anote o que o Easypanel mostra em *Credentials*: utilizador (normalmente
`postgres`), base de dados (normalmente `db` ou `postgres`) e o **Internal
host**. Vai precisar deles no passo 3.

---

## 3. A API — serviço `api`

**+ Service → App**
- Service name: `api`
- **Source**: GitHub → repositório `ElvesAgostinho/gestaofrotaGPS`, branch `main`
- **Build**: Dockerfile · **Build path** `/backend` · Dockerfile `Dockerfile`
- **Environment** (separador Environment; substitua os valores):

```
SPRING_PROFILES_ACTIVE=prod
APP_NAME=IMBONDEIRO OS
DATABASE_URL=jdbc:postgresql://db:5432/NOME_DA_BASE
DATABASE_USER=UTILIZADOR_DO_PASSO_2
DATABASE_PASSWORD=A_PALAVRA_PASSE_DO_PASSO_2
JWT_ACCESS_SECRET=cole-o-primeiro-segredo
JWT_REFRESH_SECRET=cole-o-segundo-segredo
APP_WEB_URL=https://frota.o-seu-dominio
CORS_ORIGINS=https://frota.o-seu-dominio
STORAGE_PUBLIC_BASE_URL=https://frota.o-seu-dominio
STORAGE_PATH=/var/lib/autocare/files
MAIL_FROM_NAME=IMBONDEIRO OS
```
  `DATABASE_URL`: se o Easypanel deu ao Postgres outro nome interno, use-o em
  vez de `db` (ex.: `jdbc:postgresql://frota_db:5432/...`).

- **Mounts** → + Volume: nome `files`, mount path `/var/lib/autocare/files`.
  (São as fotografias, documentos e o logótipo: sem o volume perdem-se a cada
  deploy.)
- **Domains**: nenhum. A API não fica exposta; a web serve-a por `/api`.
- **Deploy**. O primeiro build demora 5–8 minutos (Maven descarrega tudo).

Como saber que está bem: em *Logs* aparece `Started AutoCareApplication` e,
antes, `Successfully applied N migrations`. Se aparecer `[CONFIGURAÇÃO]` a
vermelho, a mensagem diz exatamente que variável falta — a aplicação recusa
arrancar com segredos de exemplo.

---

## 4. A aplicação web — serviço `web`

**+ Service → App**
- Service name: `web`
- Source: o mesmo repositório, branch `main`
- Build: Dockerfile · **Build path** `/web`
- Environment:
```
API_UPSTREAM=api:8080
```
  (Se a web der 502 em `/api`, troque por `frota_api:8080`.)
- **Domains** → + Domain: `frota.o-seu-dominio`, porta **80**, HTTPS ligado.
- Deploy (3–5 minutos).

Abra `https://frota.o-seu-dominio`. A página pública abre; **Criar conta**
regista a primeira empresa (o primeiro utilizador é o Dono dela).

---

## 5. O Traccar — serviço `traccar`

**+ Service → App**
- Service name: `traccar`
- Source: **Docker image** → `traccar/traccar:6.6-alpine`
- **Mounts**:
  - + Volume `traccar-data` → `/opt/traccar/data`
  - + Volume `traccar-logs` → `/opt/traccar/logs`
  - + **File** → mount path `/opt/traccar/conf/traccar.xml`, conteúdo: o
    ficheiro `deploy/easypanel/traccar.xml` deste repositório. Deixe
    `SEGREDO` por agora; volta cá no passo 7.
- **Ports** (separador Ports): publique **5001–5150 TCP** e **5001–5150 UDP**
  (published = target). São as portas por onde os rastreadores falam com o
  Traccar; não passam pelo proxy do Easypanel.
- **Domains** → `traccar.o-seu-dominio`, porta **8082**, HTTPS.
- Deploy.

**Firewall da Hostinger** (hPanel → VPS → Firewall): acrescente regras a
permitir **TCP 5001–5150** e **UDP 5001–5150** de qualquer origem. Sem isto
os aparelhos não chegam.

Abra `https://traccar.o-seu-dominio`: entra com `admin` / `admin` e
**mude a palavra-passe já**. Depois:
- Definições → Utilizadores → **+** → um utilizador só para a integração
  (ex.: `imbondeiro`), não administrador, com os aparelhos partilhados.
- Entrar com esse utilizador → Definições → Conta → **Token** → gerar.

---

## 6. Ligar o IMBONDEIRO OS ao Traccar

No IMBONDEIRO OS, **Configurações → Servidor Traccar**:
- Endereço: `http://traccar:8082` (ou `http://frota_traccar:8082`)
- Token: o token do passo 5
- **Guardar** → **Testar ligação**. Tem de dizer «Credenciais aceites».

A partir daqui a **sondagem** traz as posições de 20 em 20 s para os
aparelhos que existirem nos dois lados com o mesmo IMEI (Rastreadores GPS no
IMBONDEIRO OS; Aparelhos no Traccar).

---

## 7. Tempo real (encaminhamento)

Ainda em Configurações → Servidor Traccar → **Gerar o segredo de
encaminhamento**. Copie o `forward.url` que aparece.

Volte ao serviço `traccar` no Easypanel → Mounts → o ficheiro `traccar.xml`
→ substitua a linha `forward.url` pela copiada (o endereço interno é
`http://api:8080/...` ou `http://frota_api:8080/...`) → guardar → **Restart**.

Nas Configurações, «última posição recebida» passa a atualizar ao segundo
quando um aparelho reportar.

---

## 8. Cópias de segurança — serviço `backup`

**+ Service → App**
- Service name: `backup`
- Source: Docker image → `postgres:16-alpine`
- Mounts:
  - + File → `/backup.sh`, conteúdo: `deploy/backup.sh`
  - + Volume `backups` → `/backups`
  - o volume `files` do serviço `api` → `/files` (só leitura, se a opção existir)
  - o volume `traccar-data` → `/traccar`
  - (opcional) + File → `/root/.config/rclone/rclone.conf`, conteúdo:
    `deploy/easypanel/rclone.conf` preenchido
- Environment:
```
DATABASE_NAME=NOME_DA_BASE
DATABASE_USER=UTILIZADOR
DATABASE_PASSWORD=PALAVRA_PASSE
BACKUP_HOUR=02
BACKUP_KEEP_DAYS=30
BACKUP_REMOTE=
```
  `BACKUP_REMOTE` vazio = as cópias ficam só na VPS. Preenchido (ex.:
  `copias:imbondeiro`, com o `rclone.conf` montado) = vão para fora todos os
  dias. **Uma cópia no mesmo disco morre com o disco** — configure o remoto.
- Advanced → **Command**: `sh /backup.sh`
- Deploy. Nos logs aparece «feito: N ficheiros em /backups» logo ao arrancar.

O script usa `db` como host do Postgres; se o nome interno for outro, edite a
variável `DATABASE_HOST` (por omissão `db`).

---

## 9. Motor de rotas (opcional, recomendado)

Só se quiser distâncias pelas estradas reais em vez de linha reta.

**+ Service → App** `osrm-preparar`, image `ghcr.io/project-osrm/osrm-backend:v5.27.1`,
volume `osrm-data` → `/data`, File `/preparar.sh` = `deploy/osrm-preparar.sh`,
Command `sh /preparar.sh`. Deploy, esperar 5–10 min até os logs dizerem
«Pronto», e depois **Stop** (é um passo único).

**+ Service → App** `osrm`, mesma imagem, mesmo volume `osrm-data` → `/data`,
Command `osrm-routed --algorithm mld /data/angola-latest.osrm`. Deploy.

No IMBONDEIRO OS: Configurações → Motor de rotas → `http://osrm:5000` →
Testar. Um motor sem mapa **reprova** — é assim que se sabe que o passo
anterior correu.

---

## 10. Verificação final

- [ ] `https://frota.…` abre e a barra de baixo diz **servidor: ligado**
- [ ] Criou a empresa e entrou como Dono
- [ ] Configurações → Traccar → Testar ligação: «Credenciais aceites»
- [ ] Um rastreador real configurado para o IP da VPS e a porta do seu protocolo
      aparece no Traccar **e**, registado com o mesmo IMEI, no mapa do IMBONDEIRO OS
- [ ] Serviço `backup` com «feito» nos logs; pasta de cópias com dois ficheiros
- [ ] Configurações → Empresa → NIF, morada e logótipo — é o que sai nos impressos

## Atualizar

Push para `main` no GitHub → no Easypanel, `api` e `web` → **Deploy**
(ou ative Auto Deploy no serviço). As migrações da base de dados correm
sozinhas no arranque da `api`.

## Se algo falhar

| Sintoma | Causa habitual |
|---|---|
| `api` não arranca, log com `[CONFIGURAÇÃO]` | Variável em falta ou com valor de exemplo — o log lista quais |
| `api` não arranca, `Connection refused` ao Postgres | `DATABASE_URL` com host errado: use o nome interno do serviço `db` |
| Web abre mas tudo dá «sem ligação» | `API_UPSTREAM` errado; teste `frota_api:8080` |
| Testar ligação ao Traccar falha | Endereço interno errado, ou token de um utilizador sem aparelhos |
| Aparelho no Traccar mas não no mapa | IMEI diferente nos dois lados, ou o aparelho não está registado no IMBONDEIRO OS |
| Aparelho não chega ao Traccar | Portas 5001–5150 não publicadas no serviço **ou** fechadas na firewall da Hostinger |
