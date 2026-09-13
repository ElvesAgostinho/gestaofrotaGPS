# Instalação do IMBONDEIRO OS em produção

Backend Java 21 / Spring Boot, aplicação web React servida por Nginx, PostgreSQL 16,
ficheiros em disco, cópias de segurança automáticas, motor de rotas opcional.

## 1. Antes de começar

| Requisito | Mínimo | Notas |
|---|---|---|
| CPU | 2 vCPU | |
| Memória | 2 GB | 1 GB para a aplicação, o resto para o PostgreSQL |
| Disco | 20 GB | Cresce com as posições de GPS e os ficheiros carregados |
| Docker | 24+ | Com o plugin `compose` |

Um proxy à frente (Nginx, Traefik, ou o balanceador da nuvem) com **TLS** é
obrigatório: os tokens de sessão viajam em cada pedido.

## 2. Segredos

```bash
cp .env.example .env
openssl rand -base64 48   # JWT_ACCESS_SECRET
openssl rand -base64 48   # JWT_REFRESH_SECRET  (tem de ser diferente)
openssl rand -base64 24   # DATABASE_PASSWORD
```

Preencha o `.env` e confirme que **não** vai para o repositório (já está no
`.gitignore`).

A aplicação **recusa arrancar** com o perfil `prod` se algum segredo continuar
a ser o valor de exemplo, tiver menos de 32 caracteres, se os dois segredos
forem iguais, se a base de dados for H2 local, ou se `CORS_ORIGINS` estiver
aberto a toda a gente. A mensagem de erro lista tudo o que falta de uma vez —
ver `ProductionSafetyCheck`.

## 3. Subir

```bash
docker compose up -d --build
docker compose logs -f api      # acompanhar o primeiro arranque
curl http://localhost:${WEB_PORT:-80}/api/v1/health
```

Sobem quatro serviços: `db` (PostgreSQL), `api`, `web` (Nginx com a aplicação,
que serve `/api` pelo mesmo endereço) e `backup`. No primeiro arranque o Flyway
cria o esquema (migrações V1 a V34). Demora alguns segundos.

A aplicação abre em `http://<servidor>:${WEB_PORT}`. Só a `web` está exposta; a
API e a base de dados só são alcançáveis de dentro da rede do compose.

### Motor de rotas (opcional)

Calcula a distância e a duração das rotas pelas estradas reais, sem Internet
e sem mandar os dados da frota para fora.

```bash
docker compose --profile rotas run --rm osrm-preparar   # uma vez: ~5 min, ~2 GB RAM
docker compose --profile rotas up -d osrm
```

Depois, em **Configurações → Motor de rotas**, o endereço é `http://osrm:5000`
e o botão **Testar** pede um percurso a sério — um motor sem o mapa carregado
reprova, de propósito.

## 3b. Easypanel (VPS já com Easypanel)

Não precisa do Nginx nem do Caddy: o Easypanel traz o Traefik e o HTTPS.

1. **Projeto → + Serviço → Compose**, colar `deploy/easypanel/docker-compose.yml`.
   Copiar para a mesma pasta `deploy/easypanel/traccar.xml` e `rclone.conf`.
2. **Environment**: as variáveis de `.env.example` (segredos gerados com
   `openssl rand -base64 48`).
3. **Domínios**: `frota.suaempresa.ao` → serviço `web`, porta 80;
   `traccar.suaempresa.ao` → serviço `traccar`, porta 8082.
4. **Firewall da VPS**: abrir 5001–5150 TCP e UDP — é por aí que os
   rastreadores falam com o Traccar, sem passar pelo Traefik.
5. Dentro do IMBONDEIRO OS, em Configurações → Servidor Traccar, o endereço é
   `http://traccar:8082` (nome do serviço no compose); gerar o segredo de
   encaminhamento e colá-lo no `traccar.xml`; reiniciar o serviço `traccar`.
6. No Traccar, criar o utilizador de integração e registar os aparelhos pelo
   IMEI; registar os mesmos IMEI em Rastreadores GPS. As posições começam a
   entrar em 20 s.

## 4. Proxy e TLS (exemplo com Nginx)

```nginx
location / {
    proxy_pass http://127.0.0.1:80;   # o serviço «web» do compose
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # O mapa em tempo real usa SSE: sem isto o Nginx guarda os eventos
    # em buffer e o mapa só se mexe de minuto a minuto.
    proxy_buffering off;
    proxy_read_timeout 3600s;
}
```

O `X-Forwarded-For` é indispensável: sem ele todos os pedidos parecem vir do
proxy e a limitação de tentativas passa a contar toda a gente no mesmo balde.
Em contrapartida, o proxy tem de **reescrever** esse cabeçalho e não aceitar o
que venha do cliente — caso contrário qualquer um contorna o limite.

## 5. Cópias de segurança

São **automáticas**: o serviço `backup` faz, todos os dias à hora de
`BACKUP_HOUR`, uma cópia da base de dados (`bd-<data>.dump`, formato do
`pg_restore`) e uma dos ficheiros carregados (`ficheiros-<data>.tar.gz`) para a
pasta `./backups`, e apaga as mais antigas do que `BACKUP_KEEP_DAYS`. Faz
também uma cópia logo ao arrancar.

O que é preciso salvaguardar são **duas** coisas: a base de dados e os
ficheiros. Só uma delas não chega — uma ficha de ativo sem as fotografias e
sem a apólice não repõe nada. **Copie a pasta `./backups` para fora da
máquina** (outro servidor, disco externo, armazenamento na nuvem): uma cópia
no mesmo disco que morre com o disco.

Para repor:

```bash
docker compose stop api web
docker compose exec -T db pg_restore -U "$DATABASE_USER" -d "$DATABASE_NAME" --clean --if-exists   < backups/bd-2026-09-12_0200.dump
docker run --rm -v carrosgps_files:/files -v "$PWD/backups:/backups" alpine   sh -c "rm -rf /files/* && tar xzf /backups/ficheiros-2026-09-12_0200.tar.gz -C /files"
docker compose start api web
```

## 6. Atualizar

```bash
git pull
docker compose up -d --build api
```

As migrações correm sozinhas no arranque. Faça sempre a cópia da base de dados
**antes** de atualizar: o Flyway não desfaz migrações.

## 7. Variáveis de ambiente

| Variável | Por omissão | Para que serve |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | Tem de ser `prod` |
| `DATABASE_URL` · `DATABASE_USER` · `DATABASE_PASSWORD` | — | PostgreSQL |
| `JWT_ACCESS_SECRET` · `JWT_REFRESH_SECRET` | — | Assinatura das sessões |
| `ADMIN_EMAIL` · `ADMIN_PASSWORD` · `ADMIN_NAME` | — | A conta do administrador da plataforma (criada no 1.º arranque) |
| `REGISTRATION_OPEN` | `false` em `prod` | `true` deixa qualquer pessoa criar uma empresa no ecrã de entrada |
| `CORS_ORIGINS` | — | Origens da aplicação web, separadas por vírgula |
| `APP_WEB_URL` | `http://localhost:5173` | Base dos links dos convites |
| `STORAGE_PATH` | `/var/lib/autocare/files` | Ficheiros carregados |
| `RATE_LIMIT_ENABLED` | `true` | Travagem de tentativas |
| `SCHEDULER_ENABLED` | `true` | Tarefas periódicas |
| `DB_POOL_MAX` | `10` | Ligações ao PostgreSQL por instância |
| `LOG_LEVEL` | `INFO` | Detalhe dos registos |

## 8. Limites conhecidos

Coisas que funcionam numa instância e precisam de trabalho antes de escalar
para várias atrás de um balanceador:

- **Tempo real (SSE)** — as ligações vivem na memória de cada instância; cada
  uma só alimenta os mapas ligados a si. Precisa de um canal partilhado (Redis).
- **Limitação de tentativas** — o contador é por instância, portanto o limite
  efetivo multiplica-se pelo número delas. O sítio certo passa a ser o proxy.
- **Bilhetes do mapa em tempo real** — em memória; um bilhete pedido a uma
  instância não serve noutra. Com balanceador, use sessões coladas ou mova-os
  para armazenamento partilhado.
- **Ficheiros em disco local** — com várias instâncias é preciso armazenamento
  partilhado (S3 ou compatível). A interface `StorageProvider` já existe para
  isso; falta a implementação.

## 9. O que ainda não existe

- **Envio de emails.** Sem SMTP configurado, os convites e os avisos ficam em
  modo demonstração: existem dentro da aplicação e o link do convite é devolvido
  a quem convida, para enviar por outro meio. Nada é marcado como enviado.
- **Controlo de combustível** e **bloqueio remoto do motor** — decisões de
  produto por tomar; ver as notas no ROADMAP.
