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
ADMIN_EMAIL=o-seu-email@dominio
ADMIN_PASSWORD=uma-palavra-passe-forte-so-sua
ADMIN_NAME=O seu nome
TRACCAR_URL=https://traccar.o-seu-dominio
TRACCAR_USER=admin@frota.local
TRACCAR_PASSWORD=a-palavra-passe-do-admin-do-traccar
ROUTING_URL=http://IP-DA-VPS-DO-OSRM:5000
WHATSAPP_TOKEN=o-token-permanente-da-meta
WHATSAPP_PHONE_ID=o-phone-number-id
WHATSAPP_TEMPLATE=aviso_frota
```
  `TRACCAR_*`: o **seu** Traccar (passo 5), com o administrador. É com isto que
  o ecrã Plataforma cria uma conta no Traccar por cada empresa cliente — a
  empresa nunca vê estas credenciais. `ROUTING_URL`: o motor de rotas (passo 9),
  usado por todas as empresas sem configurarem nada.
  `WHATSAPP_*`: os avisos graves chegam ao telemóvel pelo WhatsApp (API
  oficial da Meta — ver `.env.example` para os passos; sem estas variáveis o
  ecrã de Notificações diz honestamente «sem WhatsApp nem SMS»). Em
  alternativa `SMS_GATEWAY_URL` com uma gateway de SMS.
  `ADMIN_EMAIL` / `ADMIN_PASSWORD`: a **sua** conta de administrador da
  plataforma — quem vende o sistema. É criada no primeiro arranque (a
  palavra-passe só conta nesse momento; depois muda-a no Perfil). Em produção
  o registo livre está fechado: ninguém cria empresas pelo ecrã de entrada,
  só você, no ecrã **Plataforma**.
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
  (Se a web der 502 em `/api`, troque por `frota_api:80`.)
- **Domains** → + Domain: `frota.o-seu-dominio`, porta **80**, HTTPS ligado.
- Deploy (3–5 minutos).

Abra `https://frota.o-seu-dominio` e entre com o `ADMIN_EMAIL` /
`ADMIN_PASSWORD` do passo 3. Entra diretamente no ecrã **Plataforma**:

- **Nova empresa** → nome, NIF, o nome e o email do Dono, a validade da
  licença. O sistema gera uma palavra-passe temporária para o Dono e
  mostra-a **uma única vez** — copie-a e entregue-lha.
- O Dono entra com ela, muda-a no Perfil, e a partir daí gere a empresa
  dele: convida a equipa, liga o Traccar, põe o timbre.
- **Suspender** trava todos os utilizadores da empresa (os dados ficam
  intactos) até **Reativar**; a licença vencida faz o mesmo sozinha. Quem é
  travado vê o motivo ao entrar.
- **Palavra-passe** gera uma nova para o Dono quando ele a perde.

Ninguém consegue criar uma empresa pelo ecrã de entrada — só aqui.

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

## 6. Ligar cada empresa ao Traccar

Com `TRACCAR_URL/USER/PASSWORD` no ambiente da API (passo 3), **não há nada a
fazer por empresa**: ao criar a empresa em **Plataforma → Nova empresa** (com
«Criar acesso ao Traccar da plataforma» ligado) o sistema cria-lhe uma conta
no Traccar, gera um token e guarda-o nas Configurações dela. Para uma empresa
já existente, o botão **Criar acesso** na lista faz o mesmo.

A empresa só regista os rastreadores em **Rastreadores GPS** (IMEI): cada um
passa a existir no Traccar, na conta dela, e a **sondagem** traz as posições
de 20 em 20 s. Uma empresa nunca vê os aparelhos de outra.

(Uma empresa com o seu próprio Traccar pode, em alternativa, colar o endereço
e o token em **Configurações → Servidor Traccar → Guardar e testar**.)

---

## 7. Tempo real (encaminhamento)

Ainda em Configurações → Servidor Traccar → **Gerar o segredo de
encaminhamento**. Copie o `forward.url` que aparece.

Volte ao serviço `traccar` no Easypanel → Mounts → o ficheiro `traccar.xml`
→ substitua a linha `forward.url` pela copiada (o endereço interno é
`http://api:8080/...` ou `http://frota_api:80/...`) → guardar → **Restart**.

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
variável `DATABASE_HOST` (por omissão `db`; no projeto `top_n8n` é
`top_n8n_imbondeiro-db`). O volume `files` da API monta-se como **Bind
Mount** de `/var/lib/docker/volumes/PROJETO_api_files/_data` → `/files`.

---

## 9. Motor de rotas (opcional, recomendado)

Só se quiser distâncias pelas estradas reais em vez de linha reta.

**Opção A — noutra VPS, com Docker à mão** (é assim que está instalado na
VPS do Traccar, `187.124.218.242`; o Easypanel já tem carga que chegue):

```bash
mkdir -p /opt/osrm/data && cd /opt/osrm/data
wget -O angola-latest.osm.pbf https://download.geofabrik.de/africa/angola-latest.osm.pbf
docker run --rm -v /opt/osrm/data:/data -v /caminho/deploy/osrm-preparar.sh:/preparar.sh:ro \
  --entrypoint sh ghcr.io/project-osrm/osrm-backend:v5.27.1 /preparar.sh     # ~2 min, 600 MB de RAM
docker run -d --name osrm --restart unless-stopped -p 5000:5000 -v /opt/osrm/data:/data \
  --memory 1200m ghcr.io/project-osrm/osrm-backend:v5.27.1 \
  osrm-routed --algorithm mld --max-table-size 100 /data/angola-latest.osrm
ufw allow from IP_DA_VPS_DO_EASYPANEL to any port 5000 proto tcp   # só a aplicação lhe fala
```
O mapa descarrega-se **fora** do contentor: a imagem oficial do OSRM não
tem `wget` nem `curl`.

No IMBONDEIRO OS: Configurações → Motor de rotas → `http://IP_DA_VPS:5000`
→ Testar.

**Opção B — dentro do Easypanel** (precisa de ~1 GB de memória livre):

**+ Service → App** `osrm-mapa`, image `alpine:3.20`, volume `osrm-data` →
`/data`, Command
`sh -c "wget -O /data/angola-latest.osm.pbf https://download.geofabrik.de/africa/angola-latest.osm.pbf"`.
Deploy, esperar o log acabar, **Stop**.

**+ Service → App** `osrm-preparar`, image `ghcr.io/project-osrm/osrm-backend:v5.27.1`,
o mesmo volume `osrm-data` → `/data`, File `/preparar.sh` = `deploy/osrm-preparar.sh`,
Command `sh /preparar.sh`. Deploy, esperar os logs dizerem «Pronto», **Stop**.

**+ Service → App** `osrm`, mesma imagem, mesmo volume, Command
`osrm-routed --algorithm mld /data/angola-latest.osrm`. Deploy.

Configurações → Motor de rotas → `http://osrm:5000` → Testar. Um motor sem
mapa **reprova** — é assim que se sabe que o passo anterior correu.

Para atualizar o mapa (o OpenStreetMap de Angola melhora todos os meses):
apague `/data` e repita.

---

## 10. Verificação final

- [ ] `https://frota.…` abre e a barra de baixo diz **servidor: ligado**
- [ ] Entrou com a conta `ADMIN_EMAIL` e viu o ecrã Plataforma
- [ ] Criou a primeira empresa e o Dono entrou com a palavra-passe temporária
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
| Web abre mas tudo dá «sem ligação» | `API_UPSTREAM` errado; teste `frota_api:80` |
| Testar ligação ao Traccar falha | Endereço interno errado, ou token de um utilizador sem aparelhos |
| Aparelho no Traccar mas não no mapa | IMEI diferente nos dois lados, ou o aparelho não está registado no IMBONDEIRO OS |
| Aparelho não chega ao Traccar | Portas 5001–5150 não publicadas no serviço **ou** fechadas na firewall da Hostinger |
