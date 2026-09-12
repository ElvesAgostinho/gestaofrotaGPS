# Traccar — posições e bloqueio remoto

> **Aviso.** Um corte de motor no momento errado mata pessoas. Nada nesta página
> substitui **testar numa viatura real, parada, num sítio controlado**, antes de
> confiar no bloqueio em operação. O que o rastreador faz com `engineStop`
> depende do fabricante e de como o relé foi instalado — não há como saber sem
> experimentar.

## 0. As posições: como entram

Os rastreadores (GT06, Teltonika, Queclink, …) falam o protocolo do fabricante
com o **Traccar**, nas portas 5001–5150. O IMBONDEIRO OS vai lá buscá-las de
duas formas, ambas configuradas em **Configurações → Servidor Traccar**:

| Via | O que faz | Precisa de |
|---|---|---|
| **Sondagem** (ligada por omissão) | De 20 em 20 s pede ao Traccar a última posição de cada aparelho | Nada além das credenciais |
| **Encaminhamento** (tempo real) | O Traccar envia cada posição ao chegar | Gerar o segredo e pôr duas linhas no `traccar.xml` (o ecrã mostra-as) |

Só entram posições de aparelhos **registados** no IMBONDEIRO OS com o mesmo
identificador (IMEI) que têm no Traccar. Um aparelho novo no Traccar não se
cria sozinho cá: registar é decisão da empresa.

Com as posições vêm: velocidade, ignição, odómetro (alimenta o medidor do ativo
— a revisão dos 10 000 km vence sozinha), horas de motor, e **nível de
combustível** do sensor, quando o aparelho o tem. A unidade do sensor (litros
ou percentagem) define-se no aparelho, em Rastreadores GPS.

## Porquê o Traccar para os comandos

O IMBONDEIRO OS não mantém a ligação TCP ao rastreador.
Não mantém nenhuma ligação aberta, portanto não tem por onde enviar um comando.

Quem tem essa ligação é o Traccar, que mantém a sessão TCP de cada rastreador e
conhece o protocolo de cada fabricante. O IMBONDEIRO OS fica com o que é dele — as
travas de segurança, a aprovação, a auditoria — e delega a entrega.

```
IMBONDEIRO OS ──HTTP──> Traccar ──TCP (protocolo do fabricante)──> Rastreador ──relé──> Motor
```

## 1. No servidor Traccar

### 1.1 Criar o utilizador de integração

Não use a conta de administrador. Crie um utilizador só para o IMBONDEIRO OS e
partilhe com ele **apenas os aparelhos** que devem poder ser bloqueados.

Em **Definições → Utilizadores → +**:

| Campo | Valor |
|---|---|
| Nome | `autocare-integracao` |
| Email | um endereço só para isto |
| Palavra-passe | gerada, longa |
| Administrador | **não** |
| Apenas leitura | **não** — precisa de enviar comandos |

Depois, em **Definições → Utilizadores → autocare-integracao → Aparelhos**,
associe os rastreadores.

### 1.2 Obter o token (preferível à palavra-passe)

Entre no Traccar com esse utilizador, vá a **Definições → Conta → Token** e gere
um. Um token pode ser revogado sem mudar a palavra-passe.

### 1.3 Confirmar que o aparelho aceita imobilização

Nem todos os protocolos suportam `engineStop`. No Traccar, abra o aparelho e veja
os comandos disponíveis — ou deixe o IMBONDEIRO OS perguntar por si:
**Definições → Servidor de comandos → Sincronizar**.

Se o resultado disser **"Não suportada"**, o bloqueio não vai funcionar com esse
rastreador, por mais que se configure. É uma limitação do equipamento.

### 1.4 Verificar a instalação do relé

O comando só faz alguma coisa se houver um relé ligado à saída do rastreador. Um
aparelho pode aceitar `engineStop`, responder "ok", e não acontecer nada — porque
não há relé. Confirme com quem fez a instalação.

## 2. No IMBONDEIRO OS

No `.env`:

```bash
TRACCAR_URL=https://traccar.suaempresa.ao
TRACCAR_TOKEN=<token gerado no passo 1.2>
# ou, em alternativa ao token:
# TRACCAR_USER=autocare-integracao
# TRACCAR_PASSWORD=<palavra-passe>
```

Reinicie e verifique em **Definições → Servidor de comandos → Testar ligação**,
ou por API:

```bash
curl -H "Authorization: Bearer <o seu token do IMBONDEIRO OS>" \
     http://localhost:8080/api/v1/telemetry/traccar/status
```

Respostas possíveis:

| `configured` | `reachable` | Significa |
|:---:|:---:|---|
| false | false | `TRACCAR_URL` vazio — o bloqueio está desligado |
| true | false | URL definido mas o servidor não responde, ou credenciais recusadas (a razão vem em `failureReason`) |
| true | true | Ligado; `version` traz a versão do Traccar |

## 3. O identificador do aparelho tem de coincidir

O IMBONDEIRO OS procura o aparelho no Traccar pelo **identificador único**
(`uniqueId`), que é normalmente o IMEI.

O campo **"Identificador do aparelho"** no IMBONDEIRO OS tem de ser **exactamente** o
mesmo que o **"Identificador"** no Traccar. Um espaço a mais, um zero à frente, e
o comando é recusado com "o aparelho não existe no Traccar".

## 4. Comandos usados

| Ação no IMBONDEIRO OS | Comando no Traccar | O que faz |
|---|---|---|
| Bloquear motor | `engineStop` | Impede o **próximo arranque** |
| Desbloquear | `engineResume` | Permite arrancar de novo |

**`engineStop` não mata o motor em andamento** — e é deliberado. Cortar um motor
em movimento tira a direcção assistida e os travões assistidos a quem vai ao
volante. É por isso que o IMBONDEIRO OS só envia o comando com a viatura parada,
confirmada por três leituras seguidas.

Se o seu rastreador se comportar de outra forma (há equipamento que corta de
imediato), **isso é um risco de segurança que tem de conhecer antes de usar a
funcionalidade**. Teste-o.

## 5. Como o IMBONDEIRO OS sabe se a viatura ficou mesmo bloqueada

Enviar não é executar. Depois do envio, de dois em dois minutos o sistema procura
prova no Traccar, por esta ordem:

1. **Atributo `blocked` na última posição** — o próprio aparelho a reportar o
   estado do imobilizador. É a melhor prova que existe.
2. **Evento `commandResult`** — o aparelho respondeu ao comando. Diz "recebi",
   não necessariamente "cortei".

Se nenhuma existir (há protocolos que nunca reportam nada), o comando fica em
**"Enviado — por confirmar pelo aparelho"**, que é a verdade. Nesse caso alguém
pode verificar no local e usar **"Declarar confirmado"** — mas fica registado
como *declaração de uma pessoa*, nunca como prova, e o ecrã mostra a diferença.

### Ao fim de 30 minutos o sistema desiste

Se nesse prazo não houver prova nenhuma, o comando passa a **"Enviado — o
aparelho nunca confirmou"** e deixa de ser sondado. O estado é desconfortável de
ler de propósito: descreve exatamente a situação — o comando saiu, e **ninguém
sabe** o que aconteceu do outro lado. Chamar-lhe "confirmado" ou "falhado" seria
escolher uma resposta que os dados não dão.

Sem este prazo os comandos nunca confirmados acumulavam-se e eram sondados para
sempre, e — pior — mantinham a viatura presa (ver a secção seguinte).

## 6. Aparelho offline

Se o rastreador estiver offline quando o comando sai, o Traccar responde **202**
e guarda-o para entregar quando ele ligar. O IMBONDEIRO OS mostra:

> O aparelho está offline. O comando ficou em fila no servidor e só será
> executado quando ele ligar — **a viatura não está bloqueada**.

Isto importa: um corte pedido de manhã pode chegar à tarde, com a viatura noutro
sítio. É por isso que os pedidos **caducam ao fim de 4 horas** se não houver
condições para os enviar.

## 7. O desbloqueio nunca é travado

Um bloqueio por confirmar **não impede** um desbloqueio. Se pedir o desbloqueio
de uma viatura que tem um bloqueio por resolver, o bloqueio é marcado como
**substituído** e o desbloqueio segue.

Isto é deliberado e importa perceber porquê: um bloqueio entregue a um rastreador
que nunca confirma nada ficaria em "enviado" indefinidamente. Se isso travasse
comandos novos, a viatura ficava **sem forma nenhuma de ser desbloqueada** — que
é exatamente o perigo que este módulo existe para evitar.

> **O que o IMBONDEIRO OS não consegue fazer.** Um comando que já saiu para o Traccar
> **não pode ser recolhido**. Se o aparelho estava offline, o Traccar guardou-o e
> vai entregá-lo quando ele ligar — possivelmente **depois** do desbloqueio. O
> aviso enviado aos gestores diz isto por palavras. Em caso de dúvida, confirme
> no local que a viatura arranca.

## 8. Lista de verificação antes de usar a sério

- [ ] Utilizador de integração criado, sem privilégios de administrador
- [ ] Aparelhos partilhados com esse utilizador
- [ ] Token gerado e no `.env`
- [ ] `Testar ligação` devolve `reachable: true`
- [ ] `Sincronizar` diz **"Imobilização suportada"** para cada aparelho
- [ ] Identificadores coincidem entre os dois sistemas
- [ ] Relé instalado e verificado por quem fez a montagem
- [ ] **Teste numa viatura parada, em sítio controlado, com alguém presente**
- [ ] **Teste também o desbloqueio** — e confirme que a viatura volta a arrancar
- [ ] Confirmado com a seguradora e com o jurídico da empresa

## 9. O que ainda não está feito

- **A confirmação dos comandos** é obtida por sondagem de dois em dois
  minutos, não em tempo real. (As posições, essas, chegam em tempo real com o
  encaminhamento ligado — ver secção 0.)
- **Um comando já entregue não pode ser cancelado no Traccar pelo IMBONDEIRO OS.**
  Substituir marca-o como substituído *deste lado*; o Traccar pode ainda
  entregá-lo a um aparelho que estava offline.
- **A integração nunca foi testada contra um Traccar real** nesta instalação.
  Foi testada ponta a ponta contra um servidor que fala a API documentada — o
  que prova tudo até à fronteira HTTP e nada para lá dela. O salto
  Traccar → rastreador → relé só um servidor a sério e uma viatura o confirmam.
