# Aplicação do motorista — plano

A aplicação que o motorista usa no telemóvel: reportar o que se passa com a
viatura, seguir a rota que o gestor definiu e ser localizado em tempo real.
É um **PWA** — instala-se a partir do browser, sem loja nenhuma, no Android e
no iPhone.

O gestor continua a controlar tudo. O motorista **não configura nada**, não vê
custos, não vê outras viaturas e não se regista sozinho.

## O que é preciso saber antes

**Um PWA não faz GPS com o ecrã apagado.** É limitação do browser, não do
código. Com a aplicação aberta e o ecrã ligado funciona bem; com o ecrã
apagado, o Android suspende ao fim de pouco tempo e o iPhone pára logo.

Por isso:

1. O **modo viagem** mantém o ecrã ligado (Wake Lock) enquanto se conduz —
   telemóvel no suporte e no carregador, como fazem as apps de estafetas.
2. O **GPS da viatura continua a ser a fonte principal**. O telemóvel
   complementa e cobre as viaturas que ainda não têm rastreador.
3. Para rastreio com o ecrã apagado só há app nativa. O caminho barato é
   embrulhar este mesmo PWA com Capacitor: no Android distribui-se o APK
   directamente (sem loja); no iPhone exigiria o programa da Apple.

## Fases

### Fase 1 — Contas criadas pelo gestor ✅ feito em 2026-09-20

- O gestor abre a ficha do motorista → separador **«Acesso à app»** → *Criar
  acesso*. O sistema gera um identificador curto (`MOT-0412`) e uma
  palavra-passe de 8 caracteres.
- A palavra-passe aparece **uma única vez**, em letras grandes, pronta a
  imprimir ou fotografar. Depois fica cifrada: nem o gestor lhe volta a chegar.
- O alfabeto da palavra-passe não tem `O`, `0`, `I`, `1` nem `S`/`5` — o que se
  confunde num papel dentro da cabina.
- O motorista entra **sem email**, com o identificador. No primeiro acesso é
  obrigado a escolher uma palavra-passe nova.
- O gestor pode **repor a palavra-passe** e **bloquear o acesso**; bloquear
  termina a sessão aberta no telemóvel (aparelho perdido, pessoa que saiu).
- Tudo fica no registo de auditoria: quem criou, quando, e a quem.

Implementação: `V51__driver_access.sql` (`users.login_id`,
`users.must_change_password`), `DriverAccessService`, endpoints
`/api/v1/drivers/{id}/access[...]`, `AcessoApp.tsx`, `TrocarPalavraPasse.tsx`.
Testes: `DriverAccessIntegrationTest`.

### Fase 2 — A aplicação, com o aspeto certo

Shell própria nas cores do sistema (preto `#141416`, âmbar `#FFC62F`, Barlow
Condensed), modo escuro para condução nocturna, cartões grandes e botões para
dedo de luva. Ecrãs: **Hoje** (a minha viatura, a minha rota, o que falta
fazer), Inspeção diária, Abastecimento, Ocorrência, Documentos da viatura,
Perfil.

### Fase 3 — GPS em tempo real pelo telemóvel

Botão **«Iniciar viagem»** → wake lock + leitura contínua → fila local →
envio em lote (mais espaçado quando parado, para poupar bateria e dados). No
mapa do gestor aparece na hora, com a origem identificada como «telemóvel»,
distinta do rastreador da viatura — nunca se apresenta uma coisa pela outra.

### Fase 4 — A rota no mapa do motorista

A rota que o gestor definiu, com as paragens, e um botão para alternar entre
**percurso do gestor** e **caminho calculado A→B**. A seta anda no mapa, com
distância e hora prevista de chegada, e avisa quando sai do corredor — o mesmo
alerta que o gestor recebe.

### Fase 5 — Fotografar e relatar

Acidente ou ocorrência com fotografias (comprimidas no telemóvel), local e
hora automáticos. Abre a ocorrência no sistema e notifica o gestor.

### Fase 6 — Offline a sério

Tudo o que ele faz sem rede fica em fila («3 por enviar») e sobe quando houver
rede. Em Angola isto não é um extra.

### Fase 7 — Acabamentos

Instalação guiada, notificações push («nova rota atribuída») e o cartão de
acesso pronto a imprimir e entregar.

## Limites do motorista (em todas as fases)

| Pode | Não pode |
|---|---|
| Ver as suas viaturas e as suas rotas | Ver outras viaturas ou outros motoristas |
| Fazer a inspeção diária | Ver custos, orçamentos ou faturas |
| Registar abastecimentos | Editar planos, ativos ou ordens |
| Comunicar avarias e ocorrências com fotografias | Apagar o que enviou |
| Ver as ordens da sua viatura | Aceder ao sistema de gestão |

Tudo o que envia fica registado com hora, local e autor.
