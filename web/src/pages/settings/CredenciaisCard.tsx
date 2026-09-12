/**
 * Credenciais do Traccar e do servidor de email.
 *
 * <p>Até aqui só se configuravam por variáveis de ambiente — o que serve um
 * servidor próprio, mas não um produto vendido a várias empresas, cada uma com
 * o seu Traccar e o seu email.
 *
 * <p>As palavras-passe nunca voltam do servidor: quando já estão guardadas o
 * campo mostra uma máscara, e gravar sem lhe tocar mantém a que lá está. Isso é
 * deliberado — se o segredo voltasse, este ecrã seria uma forma de o ler.
 */
import { Alert, Badge, Button, Grid, Group, PasswordInput, Select, Stack, Switch, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCheck,
  IconMail,
  IconPlugConnected,
  IconSatellite,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Painel, SeccaoForm } from '../../components/erp';
import { fmtDateTime } from '../../lib/format';

/** O que o servidor mostra no lugar de um segredo guardado. */
const MASCARA = '••••••••';

interface Settings {
  traccarUrl?: string | null;
  traccarUser?: string | null;
  traccarPasswordSet: boolean;
  traccarTokenSet: boolean;
  traccarConfigured: boolean;
  traccarOk?: boolean | null;
  traccarCheckedAt?: string | null;
  traccarLastError?: string | null;
  traccarPollEnabled: boolean;
  traccarLastPollAt?: string | null;
  traccarLastPositionAt?: string | null;
  traccarPollError?: string | null;
  traccarForwardSecretSet: boolean;

  smtpHost?: string | null;
  smtpPort?: number | null;
  smtpUsername?: string | null;
  smtpPasswordSet: boolean;
  smtpFrom?: string | null;
  smtpFromName?: string | null;
  smtpSecurity?: string | null;
  smtpConfigured: boolean;
  smtpOk?: boolean | null;
  smtpCheckedAt?: string | null;
  smtpLastError?: string | null;

  routingUrl?: string | null;
  routingConfigured: boolean;
  routingOk?: boolean | null;
  routingCheckedAt?: string | null;
  routingLastError?: string | null;
}

interface TestResult {
  ok: boolean;
  message: string;
  checkedAt: string;
}

export function CredenciaisCard() {
  const { data } = useQuery({
    queryKey: ['integrations'],
    queryFn: () => api<Settings>('/integrations'),
  });

  if (!data) {
    return (
      <Painel titulo="Integrações">
        <Text size="sm" c="dimmed">
          A carregar…
        </Text>
      </Painel>
    );
  }

  return (
    <Stack gap="sm">
      <TraccarPainel s={data} />
      <MotorRotasPainel s={data} />
      <EmailPainel s={data} />
    </Stack>
  );
}

// ==== Traccar ==============================================================

function TraccarPainel({ s }: { s: Settings }) {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState('');
  const [user, setUser] = useState('');
  const [password, setPassword] = useState('');
  const [token, setToken] = useState('');

  // A máscara só entra depois de se saber que há segredo guardado.
  useEffect(() => {
    setUrl(s.traccarUrl ?? '');
    setUser(s.traccarUser ?? '');
    setPassword(s.traccarPasswordSet ? MASCARA : '');
    setToken(s.traccarTokenSet ? MASCARA : '');
  }, [s.traccarUrl, s.traccarUser, s.traccarPasswordSet, s.traccarTokenSet]);

  const guardar = useMutation({
    mutationFn: () =>
      api<Settings>('/integrations/traccar', {
        method: 'PUT',
        body: { url, user, password, token },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Traccar guardado', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  const [segredo, setSegredo] = useState<{ secret: string; url: string } | null>(null);

  const sondagem = useMutation({
    mutationFn: (enabled: boolean) =>
      api<Settings>('/integrations/traccar/poll', { method: 'PUT', body: { enabled } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['integrations'] }),
    onError: (e: Error) => notifications.show({ title: 'Não foi possível', message: e.message, color: 'red' }),
  });

  const gerarSegredo = useMutation({
    mutationFn: () =>
      api<{ secret: string; url: string }>('/integrations/traccar/forward-secret', { method: 'POST' }),
    onSuccess: (r) => {
      setSegredo(r);
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível', message: e.message, color: 'red' }),
  });

  const testar = useMutation({
    mutationFn: () => api<TestResult>('/integrations/traccar/test', { method: 'POST' }),
    onSuccess: (r) => {
      notifications.show({
        title: r.ok ? 'Ligação confirmada' : 'A ligação falhou',
        message: r.message,
        color: r.ok ? 'green' : 'red',
        autoClose: r.ok ? 5000 : 12000,
      });
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível testar', message: e.message, color: 'red' }),
  });

  return (
    <Painel
      titulo="Servidor Traccar (GPS e bloqueio)"
      acoes={
        <>
          <EstadoLigacao
            configurado={s.traccarConfigured}
            ok={s.traccarOk}
            quando={s.traccarCheckedAt}
          />
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
            <Button
              size="xs"
              variant="default"
              leftSection={<IconPlugConnected size={13} />}
              onClick={() => testar.mutate()}
              loading={testar.isPending}
              disabled={!s.traccarConfigured}
            >
              Testar ligação
            </Button>
            <Button size="xs" onClick={() => guardar.mutate()} loading={guardar.isPending}>
              Guardar
            </Button>
          </div>
        </>
      }
    >
      <SeccaoForm
        titulo="Endereço e credenciais"
        descricao="É por aqui que as posições entram e os comandos de bloqueio saem. Sem isto o sistema não consegue bloquear viaturas — e di-lo, em vez de fingir que enviou."
      >
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput
              label="Endereço do servidor"
              placeholder="https://traccar.aminhaempresa.ao"
              description="Com http:// ou https://, sem barra no fim."
              value={url}
              onChange={(e) => setUrl(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput
              label="Utilizador"
              placeholder="admin@aminhaempresa.ao"
              value={user}
              onChange={(e) => setUser(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <PasswordInput
              label="Palavra-passe"
              placeholder="A palavra-passe desse utilizador"
              description={s.traccarPasswordSet ? 'Já guardada. Deixe como está para a manter.' : undefined}
              value={password}
              onChange={(e) => setPassword(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <PasswordInput
              label="Token de acesso (alternativa)"
              placeholder="Em vez de utilizador e palavra-passe"
              description={s.traccarTokenSet ? 'Já guardado.' : 'Se usar token, o utilizador é dispensável.'}
              value={token}
              onChange={(e) => setToken(e.currentTarget.value)}
            />
          </Grid.Col>
        </Grid>
      </SeccaoForm>

      <ResultadoUltimoTeste ok={s.traccarOk} erro={s.traccarLastError} quando={s.traccarCheckedAt} />

      <SeccaoForm
        titulo="Posições: como entram"
        descricao="Os rastreadores falam com o Traccar; o IMBONDEIRO OS vai lá buscar as posições. Sem isto o mapa, o odómetro e o combustível por sensor ficam vazios."
      >
        <Group justify="space-between" align="flex-start" wrap="wrap">
          <div>
            <Switch
              label="Sondar o Traccar de 20 em 20 segundos"
              description="Funciona com qualquer Traccar, sem mexer na configuração dele."
              checked={s.traccarPollEnabled}
              disabled={!s.traccarConfigured || sondagem.isPending}
              onChange={(e) => sondagem.mutate(e.currentTarget.checked)}
            />
            <Text size="xs" c="dimmed" mt={6}>
              Última sondagem: <b>{s.traccarLastPollAt ? fmtDateTime(s.traccarLastPollAt) : 'ainda nenhuma'}</b>
              {' · '}última posição recebida:{' '}
              <b>{s.traccarLastPositionAt ? fmtDateTime(s.traccarLastPositionAt) : 'ainda nenhuma'}</b>
            </Text>
            {s.traccarPollError && (
              <Text size="xs" c="red" mt={4}>
                {s.traccarPollError}
              </Text>
            )}
          </div>
        </Group>

        <Text size="sm" fw={600} mt="sm">
          Tempo real (opcional): o Traccar envia cada posição ao chegar
        </Text>
        <Text size="xs" c="dimmed">
          No servidor Traccar, em <code>conf/traccar.xml</code>, acrescente as linhas abaixo e reinicie-o.
          A sondagem continua ligada como rede de segurança.
        </Text>
        <Group gap="xs" mt={6}>
          <Button size="xs" variant="default" onClick={() => gerarSegredo.mutate()} loading={gerarSegredo.isPending}>
            {s.traccarForwardSecretSet ? 'Gerar um segredo novo' : 'Gerar o segredo de encaminhamento'}
          </Button>
          {s.traccarForwardSecretSet && !segredo && (
            <Text size="xs" c="dimmed">
              Há um segredo definido. Gerar outro invalida o anterior.
            </Text>
          )}
        </Group>
        {segredo && (
          <Alert color="yellow" variant="light" p="xs" mt="xs">
            <Text size="xs" fw={600}>
              Copie agora — não volta a ser mostrado.
            </Text>
            <pre style={{ fontSize: 11, margin: '6px 0 0', whiteSpace: 'pre-wrap', userSelect: 'all' }}>
{`<entry key='forward.enable'>true</entry>
<entry key='forward.url'>${segredo.url}</entry>`}
            </pre>
          </Alert>
        )}
      </SeccaoForm>

      <Alert color="gray" variant="light" p="xs" mt="xs">
        <Text size="xs">
          O teste liga-se mesmo ao servidor e valida as credenciais. Confirmar que o IMBONDEIRO OS fala
          com o Traccar <b>não</b> prova que o rastreador da viatura responde — isso só se sabe com
          uma viatura parada num sítio controlado.
        </Text>
      </Alert>
    </Painel>
  );
}

// ==== Email ================================================================

function EmailPainel({ s }: { s: Settings }) {
  const queryClient = useQueryClient();
  const [host, setHost] = useState('');
  const [port, setPort] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [from, setFrom] = useState('');
  const [fromName, setFromName] = useState('');
  const [security, setSecurity] = useState<string | null>('STARTTLS');
  const [destino, setDestino] = useState('');

  useEffect(() => {
    setHost(s.smtpHost ?? '');
    setPort(s.smtpPort ? String(s.smtpPort) : '');
    setUsername(s.smtpUsername ?? '');
    setPassword(s.smtpPasswordSet ? MASCARA : '');
    setFrom(s.smtpFrom ?? '');
    setFromName(s.smtpFromName ?? '');
    setSecurity(s.smtpSecurity ?? 'STARTTLS');
  }, [
    s.smtpHost,
    s.smtpPort,
    s.smtpUsername,
    s.smtpPasswordSet,
    s.smtpFrom,
    s.smtpFromName,
    s.smtpSecurity,
  ]);

  const guardar = useMutation({
    mutationFn: () =>
      api<Settings>('/integrations/email', {
        method: 'PUT',
        body: {
          host,
          port: port.trim() ? Number(port) : null,
          username,
          password,
          from,
          fromName,
          security,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Servidor de email guardado', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  const testar = useMutation({
    mutationFn: () =>
      api<TestResult>('/integrations/email/test', { method: 'POST', body: { to: destino } }),
    onSuccess: (r) => {
      notifications.show({
        title: r.ok ? 'Email enviado' : 'O envio falhou',
        message: r.message,
        color: r.ok ? 'green' : 'red',
        autoClose: r.ok ? 6000 : 12000,
      });
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível enviar', message: e.message, color: 'red' }),
  });

  return (
    <Painel
      titulo="Servidor de email"
      acoes={
        <>
          <EstadoLigacao configurado={s.smtpConfigured} ok={s.smtpOk} quando={s.smtpCheckedAt} />
          <div style={{ marginLeft: 'auto' }}>
            <Button size="xs" onClick={() => guardar.mutate()} loading={guardar.isPending}>
              Guardar
            </Button>
          </div>
        </>
      }
    >
      <SeccaoForm
        titulo="Ligação"
        descricao="Sem isto, os avisos de manutenção e de validades existem dentro da aplicação mas nenhum email sai."
      >
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 5 }}>
            <TextInput
              label="Servidor"
              placeholder="smtp.gmail.com"
              value={host}
              onChange={(e) => setHost(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <Select
              label="Segurança"
              data={[
                { value: 'STARTTLS', label: 'STARTTLS (587)' },
                { value: 'SSL', label: 'SSL/TLS (465)' },
                { value: 'NONE', label: 'Sem segurança' },
              ]}
              value={security}
              onChange={setSecurity}
              allowDeselect={false}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 4 }}>
            <TextInput
              label="Porto"
              placeholder="587"
              description="Vazio escolhe o porto habitual."
              inputMode="numeric"
              value={port}
              onChange={(e) => setPort(e.currentTarget.value.replace(/\D/g, ''))}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput
              label="Utilizador"
              placeholder="frota@aminhaempresa.ao"
              value={username}
              onChange={(e) => setUsername(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <PasswordInput
              label="Palavra-passe"
              description={
                s.smtpPasswordSet
                  ? 'Já guardada. Deixe como está para a manter.'
                  : 'No Gmail é uma palavra-passe de aplicação, não a da conta.'
              }
              value={password}
              onChange={(e) => setPassword(e.currentTarget.value)}
            />
          </Grid.Col>
        </Grid>
      </SeccaoForm>

      <SeccaoForm titulo="Remetente" descricao="O que aparece a quem recebe.">
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput
              label="Endereço remetente"
              placeholder="frota@aminhaempresa.ao"
              value={from}
              onChange={(e) => setFrom(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput
              label="Nome a mostrar"
              placeholder="IMBONDEIRO OS · A Minha Empresa"
              value={fromName}
              onChange={(e) => setFromName(e.currentTarget.value)}
            />
          </Grid.Col>
        </Grid>
      </SeccaoForm>

      <SeccaoForm
        titulo="Enviar um teste"
        descricao="Envia mesmo a mensagem. Validar só a ligação diria que está bom em casos em que o envio falha na mesma."
      >
        <Group align="flex-end" gap="xs">
          <TextInput
            label="Para"
            placeholder="o.seu.email@exemplo.ao"
            value={destino}
            onChange={(e) => setDestino(e.currentTarget.value)}
            style={{ flex: 1, minWidth: 220 }}
          />
          <Button
            variant="default"
            leftSection={<IconMail size={14} />}
            onClick={() => testar.mutate()}
            loading={testar.isPending}
            disabled={!s.smtpConfigured || !destino.includes('@')}
          >
            Enviar teste
          </Button>
        </Group>
      </SeccaoForm>

      <ResultadoUltimoTeste ok={s.smtpOk} erro={s.smtpLastError} quando={s.smtpCheckedAt} />
    </Painel>
  );
}

// ==== Peças comuns =========================================================

// ==== Motor de rotas =======================================================

/**
 * Onde vive o OSRM que calcula os percursos.
 *
 * <p>Sem isto, o sistema estima em linha reta com um fator de estrada — e
 * diz que o fez. Com isto, a distância de uma rota passa a ser medida pelas
 * estradas reais, e deixa de ser um número que alguém escreveu.
 *
 * <p>Fica no servidor da empresa de propósito: a rota de uma frota diz onde
 * estão os clientes e por onde andam as viaturas, e isso não se manda para
 * fora a cada consulta.
 */
function MotorRotasPainel({ s }: { s: Settings }) {
  const queryClient = useQueryClient();
  const [url, setUrl] = useState('');

  useEffect(() => {
    setUrl(s.routingUrl ?? '');
  }, [s.routingUrl]);

  const guardar = useMutation({
    mutationFn: () =>
      api<Settings>('/integrations/routing', {
        method: 'PUT',
        body: { url: url.trim() || null },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Guardado', message: 'Motor de rotas', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  const testar = useMutation({
    mutationFn: () => api<TestResult>('/integrations/routing/test', { method: 'POST' }),
    onSuccess: (r) => {
      notifications.show({
        title: r.ok ? 'Motor de rotas a responder' : 'O teste falhou',
        message: r.message,
        color: r.ok ? 'green' : 'red',
      });
      queryClient.invalidateQueries({ queryKey: ['integrations'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível testar', message: e.message, color: 'red' }),
  });

  return (
    <Painel
      titulo="Motor de rotas (cálculo de percursos)"
      acoes={
        <>
          <EstadoLigacao
            configurado={s.routingConfigured}
            ok={s.routingOk}
            quando={s.routingCheckedAt}
          />
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
            <Button
              size="xs"
              variant="default"
              leftSection={<IconPlugConnected size={13} />}
              onClick={() => testar.mutate()}
              loading={testar.isPending}
              disabled={!s.routingConfigured}
            >
              Testar
            </Button>
            <Button size="xs" onClick={() => guardar.mutate()} loading={guardar.isPending}>
              Guardar
            </Button>
          </div>
        </>
      }
    >
      <SeccaoForm
        titulo="Endereço do servidor OSRM"
        descricao="Calcula a distância e a duração de uma rota pelas estradas reais, em vez de as pedir escritas. Deixe vazio para o sistema estimar em linha reta — e dizer que foi isso que fez."
      >
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 8 }}>
            <TextInput
              label="Endereço"
              placeholder="http://osrm.aminhaempresa.ao:5000"
              description="Com http:// ou https://, sem barra no fim."
              value={url}
              onChange={(e) => setUrl(e.currentTarget.value)}
            />
          </Grid.Col>
        </Grid>
        <Alert color="gray" variant="light" p="xs" mt="xs">
          <Text size="xs">
            O teste pede um percurso a sério em Luanda. Um servidor de pé mas sem o mapa de
            Angola carregado responde — e é exatamente a avaria que um teste de «está vivo?»
            deixaria passar.
          </Text>
        </Alert>
        <ResultadoUltimoTeste
          ok={s.routingOk}
          erro={s.routingLastError}
          quando={s.routingCheckedAt}
        />
      </SeccaoForm>
    </Painel>
  );
}

function EstadoLigacao({
  configurado,
  ok,
  quando,
}: {
  configurado: boolean;
  ok?: boolean | null;
  quando?: string | null;
}) {
  if (!configurado) {
    return (
      <Badge color="gray" variant="light" size="sm" leftSection={<IconSatellite size={11} />}>
        Não configurado
      </Badge>
    );
  }
  // Configurado mas por testar não é o mesmo que a funcionar: dizer «ligado»
  // aqui daria ao cliente uma certeza que ninguém verificou.
  if (ok == null || !quando) {
    return (
      <Badge color="yellow" variant="light" size="sm">
        Configurado, por testar
      </Badge>
    );
  }
  return ok ? (
    <Badge color="green" variant="light" size="sm" leftSection={<IconCheck size={11} />}>
      Ligação confirmada
    </Badge>
  ) : (
    <Badge color="red" variant="light" size="sm" leftSection={<IconAlertTriangle size={11} />}>
      Último teste falhou
    </Badge>
  );
}

function ResultadoUltimoTeste({
  ok,
  erro,
  quando,
}: {
  ok?: boolean | null;
  erro?: string | null;
  quando?: string | null;
}) {
  if (ok == null || !quando) return null;
  return (
    <Alert color={ok ? 'green' : 'red'} variant="light" p="xs" mt="xs">
      <Text size="xs">
        <b>{ok ? 'Testado com sucesso' : 'Falhou'}</b> em {fmtDateTime(quando)}
        {erro ? ` — ${erro}` : ''}
      </Text>
    </Alert>
  );
}
