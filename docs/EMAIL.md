# Envio de email — o que configurar

Sem servidor de email configurado, o AutoCare **continua a funcionar**: os avisos
aparecem dentro da aplicação e os convites devolvem o link para ser entregue por
outro meio. O que não acontece é o envio — e o sistema diz isso em vez de fingir
que enviou. Nenhum aviso é marcado como enviado sem o ter sido.

## 1. Preencher o `.env`

```bash
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=avisos@suaempresa.ao
MAIL_PASSWORD=<palavra-passe de aplicação>
MAIL_STARTTLS=true
MAIL_AUTH=true
MAIL_FROM=avisos@suaempresa.ao
MAIL_FROM_NAME=AutoCare
```

A palavra-passe **nunca** entra no código. É lida do ambiente e não aparece em
nenhum ecrã nem em nenhum registo.

## 2. Configurações por fornecedor

| Fornecedor | `MAIL_HOST` | `MAIL_PORT` | `MAIL_STARTTLS` | Nota |
|---|---|:---:|:---:|---|
| Gmail / Workspace | `smtp.gmail.com` | 587 | `true` | Exige **palavra-passe de aplicação** |
| Microsoft 365 | `smtp.office365.com` | 587 | `true` | A conta não pode ter MFA sem palavra-passe de aplicação |
| Zoho | `smtp.zoho.com` | 587 | `true` | |
| Servidor próprio (SSL) | o seu | 465 | `false` | SSL directo, sem STARTTLS |
| Servidor próprio (TLS) | o seu | 587 | `true` | |

### Gmail: palavra-passe de aplicação

A palavra-passe normal da conta **não funciona**. Tem de:

1. activar a verificação em duas etapas na conta Google;
2. ir a `myaccount.google.com/apppasswords`;
3. gerar uma palavra-passe para "Correio";
4. usar essa (16 caracteres, sem espaços) em `MAIL_PASSWORD`.

## 3. Testar

**Pela aplicação:** Definições → Envio de email → *Enviar email de teste*. Por
omissão envia para o seu próprio endereço, que é o caso seguro.

**Por API:**

```bash
# Ver se está configurado
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/email/status

# Enviar teste para o próprio
curl -X POST -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/email/test

# Enviar teste para outro endereço
curl -X POST -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/v1/email/test?to=alguem@empresa.ao"
```

Ambos exigem papel de **Dono**.

Resposta com `"sent": true` significa que o servidor de email aceitou a
mensagem. **Confirme a caixa de entrada, e a pasta de spam** — aceite pelo
servidor não é o mesmo que entregue ao destinatário.

## 4. Quando falha

| Sintoma | Causa habitual |
|---|---|
| `status` diz "não configurado" | `MAIL_HOST` vazio; a aplicação não foi reiniciada depois de mudar o `.env` |
| `Authentication failed` | Palavra-passe normal em vez da de aplicação (Gmail/365) |
| `Connection timed out` | Porta bloqueada pela firewall, ou porta errada (587 vs 465) |
| `Must issue a STARTTLS command first` | `MAIL_STARTTLS=false` numa porta que o exige |
| Enviado mas não chega | Remetente diferente de `MAIL_USERNAME`; falta SPF/DKIM no domínio |

A causa exacta fica sempre nos registos do servidor:

```bash
docker compose logs api | grep -i mail
```

## 5. O que é enviado

| Acontecimento | Quem recebe |
|---|---|
| Convite para a equipa | A pessoa convidada |
| Excesso de velocidade | Donos e gestores |
| Aparelho sem comunicar | Donos e gestores |
| Entrada/saída de geocerca | Donos e gestores |
| Stock no mínimo | Donos e gestores |
| Manutenção vencida | Donos e gestores |
| Documento a caducar | Donos e gestores |
| Análise preditiva com resultado grave | Donos e gestores |
| Ordem de manutenção atribuída | O técnico a quem foi atribuída |
| Pedido e resultado de bloqueio | Donos e gestores |

Cada pessoa desliga o que não quer em **Notificações → Preferências**, por
categoria e por canal.
