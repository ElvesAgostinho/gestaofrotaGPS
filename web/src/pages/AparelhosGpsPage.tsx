/**
 * Aparelhos GPS — registar e atribuir a cada viatura ou máquina.
 *
 * <p>Este ecrã não existia: a API já sabia registar aparelhos e associá-los a
 * um ativo, mas não havia forma de o fazer pela aplicação. Sem isto o mapa
 * nunca tem nada para mostrar, por mais bem configurado que o Traccar esteja.
 *
 * <p>A ordem de trabalho é: registar o aparelho com o IMEI → atribuí-lo à
 * viatura → configurar o Traccar em Definições → sincronizar. A sincronização
 * é que descobre o identificador do aparelho no Traccar e os comandos que ele
 * aceita; por isso não se escreve à mão.
 */
import { Alert, Badge, Button, Code, CopyButton, Grid, Group, Modal, Select, Stack, Text, Textarea, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCheck,
  IconDeviceCctv,
  IconKey,
  IconPlus,
  IconRefresh,
  IconTrash,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { BarraEstado, BotaoBarra, Painel, Ponto, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { fmtDateTime } from '../lib/format';

interface Device {
  fuelUnit?: string | null;
  id: string;
  externalId: string;
  name?: string | null;
  model?: string | null;
  simNumber?: string | null;
  provider: string;
  status: string;
  lastSeenAt?: string | null;
  batteryPercent?: number | null;
  assetId?: string | null;
  assetTag?: string | null;
  assetName?: string | null;
  notes?: string | null;
}

interface DeviceCreated extends Device {
  ingestKey: string;
}

interface AssetOpcao {
  id: string;
  tag: string;
  name: string;
}

interface DeviceSync {
  protocol?: string | null;
  supportedCommands?: string[] | null;
  message?: string | null;
  providerDeviceId?: string | null;
}

const ESTADO: Record<string, { label: string; cor: string }> = {
  ONLINE: { label: 'A comunicar', cor: '#16a34a' },
  IDLE: { label: 'Silencioso', cor: '#ca8a04' },
  OFFLINE: { label: 'Sem sinal', cor: '#dc2626' },
  NEVER_SEEN: { label: 'Nunca comunicou', cor: '#9ca3af' },
};

export function AparelhosGpsPage() {
  const queryClient = useQueryClient();
  const [editar, setEditar] = useState<Device | null>(null);
  const [novoAberto, setNovoAberto] = useState(false);
  const [chave, setChave] = useState<DeviceCreated | null>(null);

  const { data: aparelhos, isLoading } = useQuery({
    queryKey: ['gps-devices'],
    queryFn: () => api<Device[]>('/gps-devices'),
  });

  const sincronizar = useMutation({
    mutationFn: (id: string) => api<DeviceSync>(`/gps-devices/${id}/sync`, { method: 'POST' }),
    onSuccess: (r) => {
      notifications.show({
        title: 'Aparelho sincronizado',
        message:
          r.protocol
            ? `Protocolo ${r.protocol}. Comandos aceites: ${(r.supportedCommands ?? []).length}.`
            : (r.message ?? 'Sem informação do servidor.'),
        color: r.protocol ? 'green' : 'yellow',
        autoClose: 8000,
      });
      queryClient.invalidateQueries({ queryKey: ['gps-devices'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível sincronizar', message: e.message, color: 'red' }),
  });

  const remover = useMutation({
    mutationFn: (id: string) => api(`/gps-devices/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      notifications.show({ title: 'Aparelho removido', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['gps-devices'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível remover', message: e.message, color: 'red' }),
  });

  const novaChave = useMutation({
    mutationFn: (id: string) => api<DeviceCreated>(`/gps-devices/${id}/rotate-key`, { method: 'POST' }),
    onSuccess: (r) => {
      setChave(r);
      queryClient.invalidateQueries({ queryKey: ['gps-devices'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível gerar', message: e.message, color: 'red' }),
  });

  const lista = aparelhos ?? [];
  const semViatura = lista.filter((d) => !d.assetId).length;
  const aComunicar = lista.filter((d) => d.status === 'ONLINE').length;

  return (
    <Stack gap="sm">
      <FormAparelho
        aberto={novoAberto || editar != null}
        aparelho={editar}
        fechar={() => {
          setNovoAberto(false);
          setEditar(null);
        }}
        aoCriar={setChave}
      />
      <ChaveModal chave={chave} fechar={() => setChave(null)} />

      <Painel
        titulo="Aparelhos GPS"
        semPadding
        acoes={
          <>
            <BotaoBarra destaque icone={<IconPlus size={13} />} onClick={() => setNovoAberto(true)}>
              Registar aparelho
            </BotaoBarra>
            <SeparadorBarra />
            <Text size="xs" c="dimmed" style={{ paddingLeft: 4 }}>
              Registar → atribuir à viatura → configurar o Traccar → sincronizar
            </Text>
          </>
        }
        rodape={
          <BarraEstado
            itens={[
              { rotulo: 'Aparelhos', valor: lista.length },
              { rotulo: 'A comunicar', valor: aComunicar },
              { rotulo: 'Sem viatura', valor: semViatura },
            ]}
          />
        }
      >
        {semViatura > 0 && (
          <Alert color="yellow" variant="light" p="xs" m="sm" icon={<IconAlertTriangle size={15} />}>
            <Text size="sm">
              {semViatura} aparelho(s) sem viatura atribuída. Um aparelho por atribuir comunica
              posições que o sistema não consegue ligar a nada — não aparece no mapa nem conta
              quilómetros.
            </Text>
          </Alert>
        )}

        <Grelha
          id="rastreadores"
          linhas={lista}
          chave={(d) => d.id}
          carregando={isLoading}
          larguraMinima={1000}
          aoAbrir={(d) => setEditar(d)}
          vazio="Nenhum aparelho registado. Sem aparelhos o mapa não tem nada para mostrar."
          colunas={[
            {
              id: 'imei',
              titulo: 'Identificador (IMEI)',
              largura: 160,
              fixa: true,
              semQuebra: true,
              valor: (d) => d.externalId,
              render: (d) => <b>{d.externalId}</b>,
            },
            {
              id: 'viatura',
              titulo: 'Viatura',
              largura: 120,
              valor: (d) => d.assetTag ?? null,
              render: (d) =>
                d.assetId ? (
                  <Text component={Link} to={`/ativos/${d.assetId}`} size="xs" fw={600} c="var(--erp-dourado-escuro)">
                    {d.assetTag}
                  </Text>
                ) : (
                  <Badge size="xs" variant="light" color="yellow">
                    Por atribuir
                  </Badge>
                ),
            },
            {
              id: 'aparelho',
              titulo: 'Aparelho',
              valor: (d) => d.name ?? null,
              render: (d) => (
                <>
                  {d.name ?? '—'}
                  {d.model && (
                    <Text size="xs" c="dimmed">
                      {d.model}
                    </Text>
                  )}
                </>
              ),
            },
            { id: 'sim', titulo: 'SIM', largura: 120, valor: (d) => d.simNumber ?? null },
            { id: 'fornecedor', titulo: 'Fornecedor', largura: 100, valor: (d) => d.provider },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 150,
              valor: (d) => ESTADO[d.status]?.label ?? d.status,
              render: (d) => (
                <>
                  <Ponto cor={ESTADO[d.status]?.cor ?? '#6b7280'} />
                  {ESTADO[d.status]?.label ?? d.status}
                </>
              ),
            },
            {
              id: 'ultima',
              titulo: 'Última comunicação',
              largura: 150,
              valor: (d) => d.lastSeenAt ?? null,
              render: (d) => (
                <Text size="xs" c="dimmed">
                  {d.lastSeenAt ? fmtDateTime(d.lastSeenAt) : 'nunca'}
                </Text>
              ),
            },
            {
              id: 'acoes',
              titulo: '',
              largura: 220,
              render: (d) => (
                <Group gap={4} wrap="nowrap">
                  <Button size="compact-xs" variant="subtle" onClick={() => setEditar(d)}>
                    Editar
                  </Button>
                  <Button
                    size="compact-xs"
                    variant="subtle"
                    leftSection={<IconRefresh size={12} />}
                    onClick={() => sincronizar.mutate(d.id)}
                    loading={sincronizar.isPending}
                  >
                    Sincronizar
                  </Button>
                  <Button
                    size="compact-xs"
                    variant="subtle"
                    onClick={() => novaChave.mutate(d.id)}
                    title="Gerar uma chave de publicação nova"
                  >
                    <IconKey size={12} />
                  </Button>
                  <Button size="compact-xs" variant="subtle" color="red" onClick={() => remover.mutate(d.id)}>
                    <IconTrash size={12} />
                  </Button>
                </Group>
              ),
            },
          ]}
        />
      </Painel>

      <Alert color="gray" variant="light" p="sm" icon={<IconDeviceCctv size={16} />}>
        <Text size="sm" fw={600} mb={2}>
          A sincronização não envia nada ao aparelho
        </Text>
        <Text size="xs">
          Lê do Traccar o protocolo do rastreador e a lista de comandos que ele aceita. É o que
          permite saber se o aparelho suporta imobilização — e há protocolos que não suportam.
          Sem o Traccar configurado em Definições, a sincronização não tem a quem perguntar.
        </Text>
      </Alert>
    </Stack>
  );
}

// ==== Formulário ===========================================================

function FormAparelho({
  aberto,
  aparelho,
  fechar,
  aoCriar,
}: {
  aberto: boolean;
  aparelho: Device | null;
  fechar: () => void;
  aoCriar: (d: DeviceCreated) => void;
}) {
  const queryClient = useQueryClient();
  const editando = aparelho != null;

  const [externalId, setExternalId] = useState('');
  const [name, setName] = useState('');
  const [model, setModel] = useState('');
  const [simNumber, setSimNumber] = useState('');
  const [provider, setProvider] = useState<string | null>('TRACCAR');
  const [assetId, setAssetId] = useState<string | null>(null);
  const [notes, setNotes] = useState('');
  const [fuelUnit, setFuelUnit] = useState<string | null>('LITERS');
  const [carregado, setCarregado] = useState<string | null>(null);

  // Carrega os valores do aparelho em edição uma única vez por aparelho: usar
  // um efeito a cada render apagaria o que o utilizador está a escrever.
  if (aberto && aparelho && carregado !== aparelho.id) {
    setCarregado(aparelho.id);
    setExternalId(aparelho.externalId);
    setName(aparelho.name ?? '');
    setModel(aparelho.model ?? '');
    setSimNumber(aparelho.simNumber ?? '');
    setProvider(aparelho.provider);
    setAssetId(aparelho.assetId ?? null);
    setNotes(aparelho.notes ?? '');
    setFuelUnit(aparelho.fuelUnit ?? 'LITERS');
  }
  if (aberto && !aparelho && carregado !== null) {
    setCarregado(null);
    setExternalId('');
    setName('');
    setModel('');
    setSimNumber('');
    setProvider('TRACCAR');
    setAssetId(null);
    setNotes('');
    setFuelUnit('LITERS');
  }

  const { data: ativos } = useQuery({
    queryKey: ['assets', 'opcoes'],
    queryFn: () => api<{ content: AssetOpcao[] }>('/assets?size=300'),
    enabled: aberto,
  });

  const gravar = useMutation({
    mutationFn: () => {
      const body = {
        externalId: externalId.trim(),
        name: name.trim() || null,
        model: model.trim() || null,
        simNumber: simNumber.trim() || null,
        provider,
        // String vazia desatribui; null deixaria como está.
        assetId: assetId ?? '',
        notes: notes.trim() || null,
        fuelUnit,
      };
      return editando
        ? api<Device>(`/gps-devices/${aparelho!.id}`, { method: 'PATCH', body })
        : api<DeviceCreated>('/gps-devices', { method: 'POST', body });
    },
    onSuccess: (r) => {
      queryClient.invalidateQueries({ queryKey: ['gps-devices'] });
      if (!editando && (r as DeviceCreated).ingestKey) {
        aoCriar(r as DeviceCreated);
      } else {
        notifications.show({ title: 'Aparelho guardado', message: '', color: 'green' });
      }
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  return (
    <Modal
      opened={aberto}
      onClose={fechar}
      title={editando ? `Aparelho ${aparelho!.externalId}` : 'Registar aparelho GPS'}
      size="lg"
    >
      <Grid gutter="xs">
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Identificador (IMEI)"
            required
            placeholder="860123456789012"
            description="O mesmo que está registado no Traccar."
            value={externalId}
            onChange={(e) => setExternalId(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <Select
            label="Viatura ou máquina"
            placeholder="Escolher o ativo"
            description="Sem ativo, as posições não se ligam a nada."
            clearable
            searchable
            data={(ativos?.content ?? []).map((a) => ({
              value: a.id,
              label: `${a.tag} — ${a.name}`,
            }))}
            value={assetId}
            onChange={setAssetId}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Nome do aparelho"
            placeholder="Rastreador da cabine"
            value={name}
            onChange={(e) => setName(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Modelo"
            placeholder="Teltonika FMB920"
            value={model}
            onChange={(e) => setModel(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <Select
            label="Sensor de combustível"
            description="Em que unidade o aparelho manda o nível. Não se adivinha: 100 é válido nas duas."
            data={[
              { value: 'LITERS', label: 'Litros' },
              { value: 'PERCENT', label: 'Percentagem do depósito (precisa da capacidade no ativo)' },
            ]}
            value={fuelUnit}
            onChange={setFuelUnit}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Número do SIM"
            placeholder="+244 9xx xxx xxx"
            value={simNumber}
            onChange={(e) => setSimNumber(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <Select
            label="Como as posições chegam"
            data={[
              { value: 'TRACCAR', label: 'Traccar (servidor próprio)' },
              { value: 'GENERIC', label: 'Publica direto no IMBONDEIRO OS' },
              { value: 'DEMO', label: 'Demonstração' },
            ]}
            value={provider}
            onChange={setProvider}
            allowDeselect={false}
          />
        </Grid.Col>
        <Grid.Col span={12}>
          <Textarea
            label="Observações"
            placeholder="Onde está instalado, quem instalou, número de contrato do SIM."
            autosize
            minRows={2}
            value={notes}
            onChange={(e) => setNotes(e.currentTarget.value)}
          />
        </Grid.Col>
      </Grid>

      {provider === 'GENERIC' && !editando && (
        <Alert color="gray" variant="light" p="xs" mt="xs">
          <Text size="xs">
            Ao gravar recebe uma <b>chave de publicação</b>, mostrada uma única vez. É com ela
            que o aparelho envia posições diretamente para o IMBONDEIRO OS, sem Traccar pelo meio.
          </Text>
        </Alert>
      )}

      <Group justify="flex-end" gap="xs" mt="md">
        <Button variant="default" onClick={fechar}>
          Cancelar
        </Button>
        <Button
          onClick={() => gravar.mutate()}
          loading={gravar.isPending}
          disabled={!externalId.trim()}
        >
          {editando ? 'Guardar' : 'Registar'}
        </Button>
      </Group>
    </Modal>
  );
}

/** A chave só se vê uma vez. Não é guardada em claro do lado do servidor. */
function ChaveModal({ chave, fechar }: { chave: DeviceCreated | null; fechar: () => void }) {
  return (
    <Modal opened={chave != null} onClose={fechar} title="Chave de publicação" size="lg">
      {chave && (
        <>
          <Alert color="yellow" variant="light" p="xs" mb="sm" icon={<IconAlertTriangle size={15} />}>
            <Text size="sm">
              Esta chave aparece <b>uma única vez</b>. Copie-a agora — do lado do servidor fica
              só um resumo, e nem o IMBONDEIRO OS a consegue voltar a mostrar.
            </Text>
          </Alert>
          <Text size="xs" c="dimmed" mb={4}>
            Aparelho {chave.externalId}
          </Text>
          <Code block style={{ fontSize: 13, wordBreak: 'break-all' }}>
            {chave.ingestKey}
          </Code>
          <Group justify="flex-end" mt="md" gap="xs">
            <CopyButton value={chave.ingestKey}>
              {({ copied, copy }) => (
                <Button
                  variant={copied ? 'light' : 'filled'}
                  color={copied ? 'green' : undefined}
                  leftSection={copied ? <IconCheck size={14} /> : undefined}
                  onClick={copy}
                >
                  {copied ? 'Copiada' : 'Copiar chave'}
                </Button>
              )}
            </CopyButton>
            <Button variant="default" onClick={fechar}>
              Fechar
            </Button>
          </Group>
        </>
      )}
    </Modal>
  );
}
