/**
 * Guias de transporte — o documento que acompanha a carga.
 *
 * <p>É o que a autoridade pede na estrada e o que o cliente assina na entrega.
 * O percurso é o de um documento com valor fora da empresa: rascunho →
 * emitida → em trânsito → entregue.
 *
 * <p>A partir de emitida a carga deixa de se poder alterar, porque o papel já
 * saiu com o condutor. O botão de editar desaparece nessa altura em vez de
 * falhar quando se carrega nele.
 */
import {
  Alert,
  Badge,
  Button,
  Checkbox,
  Grid,
  Group,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconFileInvoice, IconPlus, IconPrinter, IconTrash, IconTruckDelivery } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api, openFile } from '../api/client';
import { BarraEstado, BotaoBarra, Painel, Ponto, SeparadorBarra } from '../components/erp';
import { CampoConsulta } from '../components/Consulta';
import { Grelha } from '../components/Grelha';
import { CampoProcura, filtrar } from '../components/Procura';
import { fmtDateTime, fmtNumber } from '../lib/format';

interface Linha {
  id?: string;
  description: string;
  reference?: string | null;
  quantity?: number | null;
  unit?: string | null;
  weightKg?: number | null;
  packages?: number | null;
}

interface Guia {
  id: string;
  number: string;
  status: string;
  statusLabel: string;
  assetId?: string | null;
  assetTag?: string | null;
  driverId?: string | null;
  driverName?: string | null;
  originLabel: string;
  destinationLabel: string;
  customerName?: string | null;
  customerTaxId?: string | null;
  totalWeightKg?: number | null;
  totalPackages?: number | null;
  distanceKm?: number | null;
  issuedAt?: string | null;
  deliveredAt?: string | null;
  receivedByName?: string | null;
  deliveryAccepted?: boolean | null;
  deliveryNotes?: string | null;
  cancellationReason?: string | null;
  items?: Linha[];
  nextActions?: string[];
}

const COR_ESTADO: Record<string, string> = {
  DRAFT: '#6b7280',
  ISSUED: '#2563eb',
  IN_TRANSIT: '#ca8a04',
  DELIVERED: '#16a34a',
  CANCELLED: '#9ca3af',
};

export function GuiasTransportePage() {
  const queryClient = useQueryClient();
  const [filtro, setFiltro] = useState<string | null>(null);
  const [nova, setNova] = useState(false);
  const [aberta, setAberta] = useState<Guia | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['transport-notes', filtro],
    queryFn: () =>
      api<{ content: Guia[] }>(
        `/transport-notes?size=200${filtro ? `&status=${filtro}` : ''}`,
      ),
  });

  const [procura, setProcura] = useState('');
  const todasLinhas = data?.content ?? [];
  // Procura que ignora acentos e aceita as palavras por qualquer ordem.
  const linhas = useMemo(
    () => filtrar(todasLinhas, procura, (g) => [g.number, g.assetTag, g.driverName, g.originLabel, g.destinationLabel, g.customerName, g.statusLabel]),
    [todasLinhas, procura],
  );
  const aCaminho = linhas.filter((g) => g.status === 'IN_TRANSIT').length;
  const porEmitir = linhas.filter((g) => g.status === 'DRAFT').length;

  function recarregar() {
    queryClient.invalidateQueries({ queryKey: ['transport-notes'] });
  }

  return (
    <Stack gap="sm">
      <FormGuia aberto={nova} fechar={() => setNova(false)} />
      <FichaGuia
        id={aberta?.id ?? null}
        fechar={() => setAberta(null)}
        aoMudar={recarregar}
      />

      <Painel
        titulo="Guias de transporte"
        semPadding
        acoes={
          <>
            <CampoProcura valor={procura} aoMudar={setProcura} placeholder="Número, viatura, cliente…" />
            <BotaoBarra destaque icone={<IconPlus size={13} />} onClick={() => setNova(true)}>
              Nova guia
            </BotaoBarra>
            <SeparadorBarra />
            <Select
              size="xs"
              placeholder="Todos os estados"
              clearable
              data={[
                { value: 'DRAFT', label: 'Rascunho' },
                { value: 'ISSUED', label: 'Emitida' },
                { value: 'IN_TRANSIT', label: 'Em trânsito' },
                { value: 'DELIVERED', label: 'Entregue' },
                { value: 'CANCELLED', label: 'Anulada' },
              ]}
              value={filtro}
              onChange={setFiltro}
              style={{ width: 170 }}
            />
          </>
        }
        rodape={
          <BarraEstado
            itens={[
              { rotulo: 'Guias', valor: linhas.length },
              { rotulo: 'A caminho', valor: aCaminho },
              { rotulo: 'Por emitir', valor: porEmitir },
            ]}
          />
        }
      >
        <Grelha
          id="guias"
          linhas={linhas}
          chave={(g) => g.id}
          carregando={isLoading}
          larguraMinima={960}
          aoAbrir={(g) => setAberta(g)}
          vazio="Nenhuma guia. Use «Nova guia» para a primeira."
          colunas={[
            {
              id: 'numero',
              titulo: 'Número',
              largura: 130,
              fixa: true,
              semQuebra: true,
              valor: (g) => g.number,
              render: (g) => (
                <span style={{ fontWeight: 700, color: 'var(--erp-dourado-escuro)' }}>{g.number}</span>
              ),
            },
            { id: 'viatura', titulo: 'Viatura', largura: 100, valor: (g) => g.assetTag ?? null },
            { id: 'motorista', titulo: 'Motorista', largura: 150, valor: (g) => g.driverName ?? null },
            {
              id: 'percurso',
              titulo: 'Percurso',
              valor: (g) => `${g.originLabel} → ${g.destinationLabel}`,
              render: (g) => (
                <>
                  {g.originLabel} <span style={{ opacity: 0.5 }}>→</span> {g.destinationLabel}
                </>
              ),
            },
            { id: 'cliente', titulo: 'Cliente', largura: 170, valor: (g) => g.customerName ?? null },
            {
              id: 'peso',
              titulo: 'Peso',
              largura: 90,
              alinhar: 'right',
              valor: (g) => g.totalWeightKg ?? null,
              render: (g) => (g.totalWeightKg != null ? `${fmtNumber(g.totalWeightKg, 0)} kg` : '—'),
            },
            { id: 'volumes', titulo: 'Volumes', largura: 80, alinhar: 'right', valor: (g) => g.totalPackages ?? null },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 130,
              valor: (g) => g.statusLabel,
              render: (g) => (
                <>
                  <Ponto cor={COR_ESTADO[g.status] ?? '#6b7280'} />
                  {g.statusLabel}
                </>
              ),
            },
          ]}
        />
      </Painel>
    </Stack>
  );
}

// ==== Nova guia ============================================================

function FormGuia({ aberto, fechar }: { aberto: boolean; fechar: () => void }) {
  const queryClient = useQueryClient();
  const [assetId, setAssetId] = useState<string | null>(null);
  const [driverId, setDriverId] = useState<string | null>(null);
  const [originLabel, setOriginLabel] = useState('');
  const [destinationLabel, setDestinationLabel] = useState('');
  const [originAddress, setOriginAddress] = useState('');
  const [destinationAddress, setDestinationAddress] = useState('');
  const [customerName, setCustomerName] = useState('');
  const [customerTaxId, setCustomerTaxId] = useState('');
  const [hazardClass, setHazardClass] = useState('');
  const [items, setItems] = useState<Linha[]>([
    { description: '', quantity: null, unit: 'un', weightKg: null, packages: null },
  ]);

  const { data: ativos } = useQuery({
    queryKey: ['assets', 'opcoes'],
    queryFn: () => api<{ content: { id: string; tag: string; name: string }[] }>('/assets?size=300'),
    enabled: aberto,
  });
  const { data: motoristas } = useQuery({
    queryKey: ['drivers', 'opcoes'],
    queryFn: () => api<{ content: { id: string; name: string }[] }>('/drivers?size=200'),
    enabled: aberto,
  });

  const criar = useMutation({
    mutationFn: () =>
      api<Guia>('/transport-notes', {
        method: 'POST',
        body: {
          assetId,
          driverId,
          originLabel,
          originAddress: originAddress.trim() || null,
          destinationLabel,
          destinationAddress: destinationAddress.trim() || null,
          customerName: customerName.trim() || null,
          customerTaxId: customerTaxId.trim() || null,
          hazardClass: hazardClass.trim() || null,
          items: items.filter((i) => i.description.trim()),
        },
      }),
    onSuccess: (g) => {
      notifications.show({
        title: `Guia ${g.number} criada`,
        message: 'Em rascunho. Emita-a quando a carga estiver conferida.',
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['transport-notes'] });
      limpar();
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível criar', message: e.message, color: 'red' }),
  });

  function limpar() {
    setAssetId(null);
    setDriverId(null);
    setOriginLabel('');
    setDestinationLabel('');
    setOriginAddress('');
    setDestinationAddress('');
    setCustomerName('');
    setCustomerTaxId('');
    setHazardClass('');
    setItems([{ description: '', quantity: null, unit: 'un', weightKg: null, packages: null }]);
  }

  function mudarLinha(i: number, campo: keyof Linha, valor: string) {
    setItems((atual) =>
      atual.map((l, k) =>
        k === i
          ? {
              ...l,
              [campo]:
                campo === 'description' || campo === 'unit' || campo === 'reference'
                  ? valor
                  : valor.trim()
                    ? Number(valor.replace(',', '.'))
                    : null,
            }
          : l,
      ),
    );
  }

  // Os totais são calculados pelo servidor a partir das linhas; mostram-se
  // aqui para quem preenche ver o que vai declarar.
  const pesoTotal = items.reduce((s, l) => s + (l.weightKg ?? 0), 0);
  const volumesTotal = items.reduce((s, l) => s + (l.packages ?? 0), 0);
  const falta = !originLabel.trim() || !destinationLabel.trim();

  return (
    <Modal opened={aberto} onClose={fechar} title="Nova guia de transporte" size="xl">
      <Grid gutter="xs" mb="sm">
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <CampoConsulta
            label="Viatura"
            placeholder="Quem transporta"
            clearable
            linhas={ativos?.content ?? []}
            valor={assetId}
            aoMudar={setAssetId}
            chave={(a) => a.id}
            rotulo={(a) => `${a.tag} — ${a.name}`}
            camposProcura={(a) => [a.tag, a.name]}
            tituloJanela="Escolher a viatura"
            idGrelha="consulta-guia-ativos"
            colunas={[
              { id: 'tag', titulo: 'Etiqueta', largura: 120, fixa: true, valor: (a) => a.tag, render: (a) => <b>{a.tag}</b> },
              { id: 'nome', titulo: 'Nome', valor: (a) => a.name },
            ]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <CampoConsulta
            label="Motorista"
            placeholder="Quem conduz"
            clearable
            linhas={motoristas?.content ?? []}
            valor={driverId}
            aoMudar={setDriverId}
            chave={(m) => m.id}
            rotulo={(m) => m.name}
            camposProcura={(m) => [m.name]}
            tituloJanela="Escolher o motorista"
            idGrelha="consulta-guia-motoristas"
            colunas={[{ id: 'nome', titulo: 'Nome', fixa: true, valor: (m) => m.name }]}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Origem"
            required
            placeholder="Armazém de Viana"
            value={originLabel}
            onChange={(e) => setOriginLabel(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Destino"
            required
            placeholder="Obra do Lobito"
            value={destinationLabel}
            onChange={(e) => setDestinationLabel(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Morada de origem"
            value={originAddress}
            onChange={(e) => setOriginAddress(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Morada de destino"
            value={destinationAddress}
            onChange={(e) => setDestinationAddress(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 5 }}>
          <TextInput
            label="Cliente"
            placeholder="Construções Kwanza, Lda."
            value={customerName}
            onChange={(e) => setCustomerName(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, sm: 4 }}>
          <TextInput
            label="NIF do cliente"
            value={customerTaxId}
            onChange={(e) => setCustomerTaxId(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, sm: 3 }}>
          <TextInput
            label="Classe de perigo"
            placeholder="ONU 1203"
            description="Só se for mercadoria perigosa."
            value={hazardClass}
            onChange={(e) => setHazardClass(e.currentTarget.value)}
          />
        </Grid.Col>
      </Grid>

      <Text
        size="xs"
        fw={700}
        tt="uppercase"
        mb={4}
        style={{ borderBottom: '2px solid var(--erp-dourado)', paddingBottom: 3 }}
      >
        Carga
      </Text>
      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Descrição</Table.Th>
            <Table.Th style={{ width: 90 }}>Referência</Table.Th>
            <Table.Th style={{ width: 80 }}>Qtd.</Table.Th>
            <Table.Th style={{ width: 70 }}>Unidade</Table.Th>
            <Table.Th style={{ width: 90 }}>Peso (kg)</Table.Th>
            <Table.Th style={{ width: 80 }}>Volumes</Table.Th>
            <Table.Th style={{ width: 36 }} />
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {items.map((l, i) => (
            <Table.Tr key={i}>
              <Table.Td>
                <TextInput
                  size="xs"
                  placeholder="Cimento Portland 42,5"
                  value={l.description}
                  onChange={(e) => mudarLinha(i, 'description', e.currentTarget.value)}
                />
              </Table.Td>
              <Table.Td>
                <TextInput
                  size="xs"
                  value={l.reference ?? ''}
                  onChange={(e) => mudarLinha(i, 'reference', e.currentTarget.value)}
                />
              </Table.Td>
              <Table.Td>
                <TextInput
                  size="xs"
                  value={l.quantity ?? ''}
                  onChange={(e) => mudarLinha(i, 'quantity', e.currentTarget.value)}
                />
              </Table.Td>
              <Table.Td>
                <TextInput
                  size="xs"
                  value={l.unit ?? ''}
                  onChange={(e) => mudarLinha(i, 'unit', e.currentTarget.value)}
                />
              </Table.Td>
              <Table.Td>
                <TextInput
                  size="xs"
                  value={l.weightKg ?? ''}
                  onChange={(e) => mudarLinha(i, 'weightKg', e.currentTarget.value)}
                />
              </Table.Td>
              <Table.Td>
                <TextInput
                  size="xs"
                  value={l.packages ?? ''}
                  onChange={(e) => mudarLinha(i, 'packages', e.currentTarget.value)}
                />
              </Table.Td>
              <Table.Td>
                {items.length > 1 && (
                  <Button
                    size="compact-xs"
                    variant="subtle"
                    color="red"
                    onClick={() => setItems((a) => a.filter((_, k) => k !== i))}
                  >
                    <IconTrash size={12} />
                  </Button>
                )}
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>

      <Group justify="space-between" mt="xs">
        <Button
          size="xs"
          variant="default"
          leftSection={<IconPlus size={13} />}
          onClick={() =>
            setItems((a) => [
              ...a,
              { description: '', quantity: null, unit: 'un', weightKg: null, packages: null },
            ])
          }
        >
          Acrescentar linha
        </Button>
        <Text size="sm">
          Total: <b>{fmtNumber(pesoTotal, 0)} kg</b> · <b>{volumesTotal}</b> volume(s)
        </Text>
      </Group>

      <Alert color="gray" variant="light" p="xs" mt="sm">
        <Text size="xs">
          Os totais vão ser recalculados pelo servidor a partir das linhas. Uma guia que declare
          doze toneladas com oito na caixa é uma discussão à espera, na estrada ou na entrega.
        </Text>
      </Alert>

      <Group justify="flex-end" gap="xs" mt="md">
        <Button variant="default" onClick={fechar}>
          Cancelar
        </Button>
        <Button onClick={() => criar.mutate()} loading={criar.isPending} disabled={falta}>
          Criar rascunho
        </Button>
      </Group>
    </Modal>
  );
}

// ==== Ficha e percurso =====================================================

function FichaGuia({
  id,
  fechar,
  aoMudar,
}: {
  id: string | null;
  fechar: () => void;
  aoMudar: () => void;
}) {
  const queryClient = useQueryClient();
  const [entregaAberta, setEntregaAberta] = useState(false);

  const { data: g } = useQuery({
    queryKey: ['transport-note', id],
    queryFn: () => api<Guia>(`/transport-notes/${id}`),
    enabled: id != null,
  });

  const agir = useMutation({
    mutationFn: ({ accao, corpo }: { accao: string; corpo?: unknown }) =>
      api<Guia>(`/transport-notes/${id}/${accao}`, {
        method: 'POST',
        body: (corpo ?? {}) as Record<string, unknown>,
      }),
    onSuccess: (novo) => {
      notifications.show({ title: novo.statusLabel, message: novo.number, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['transport-note', id] });
      aoMudar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível', message: e.message, color: 'red' }),
  });

  const podeFazer = (a: string) => (g?.nextActions ?? []).includes(a);

  return (
    <Modal
      opened={id != null}
      onClose={fechar}
      title={g ? `Guia ${g.number}` : 'Guia'}
      size="xl"
    >
      {g && (
        <>
          <Group justify="space-between" mb="sm" wrap="wrap">
            <Group gap="xs">
              <Badge variant="light" color={g.status === 'DELIVERED' ? 'green' : 'gray'}>
                {g.statusLabel}
              </Badge>
              <Text size="sm">
                {g.originLabel} <span style={{ opacity: 0.5 }}>→</span> {g.destinationLabel}
              </Text>
            </Group>
            <Group gap="xs">
              <Button
                size="xs"
                variant="default"
                leftSection={<IconPrinter size={13} />}
                onClick={() =>
                  openFile(`/transport-notes/${g.id}/print.pdf`).catch((e: Error) =>
                    notifications.show({ title: 'Não foi possível abrir', message: e.message, color: 'red' }),
                  )
                }
              >
                Imprimir
              </Button>
              {podeFazer('ISSUE') && (
                <Button
                  size="xs"
                  leftSection={<IconFileInvoice size={13} />}
                  onClick={() => agir.mutate({ accao: 'issue' })}
                  loading={agir.isPending}
                >
                  Emitir
                </Button>
              )}
              {podeFazer('DEPART') && (
                <Button
                  size="xs"
                  leftSection={<IconTruckDelivery size={13} />}
                  onClick={() => agir.mutate({ accao: 'depart', corpo: {} })}
                  loading={agir.isPending}
                >
                  Registar saída
                </Button>
              )}
              {podeFazer('DELIVER') && (
                <Button
                  size="xs"
                  leftSection={<IconCheck size={13} />}
                  onClick={() => setEntregaAberta(true)}
                >
                  Registar entrega
                </Button>
              )}
            </Group>
          </Group>

          <Grid gutter="xs" mb="sm">
            <Campo rotulo="Viatura" valor={g.assetTag} />
            <Campo rotulo="Motorista" valor={g.driverName} />
            <Campo rotulo="Cliente" valor={g.customerName} />
            <Campo rotulo="NIF" valor={g.customerTaxId} />
            <Campo
              rotulo="Peso declarado"
              valor={g.totalWeightKg != null ? `${fmtNumber(g.totalWeightKg, 0)} kg` : null}
            />
            <Campo rotulo="Volumes" valor={g.totalPackages?.toString()} />
            <Campo
              rotulo="Distância"
              valor={g.distanceKm != null ? `${fmtNumber(g.distanceKm, 0)} km` : null}
            />
            <Campo rotulo="Emitida" valor={g.issuedAt ? fmtDateTime(g.issuedAt) : null} />
          </Grid>

          {(g.items ?? []).length > 0 && (
            <Table mb="sm">
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Carga</Table.Th>
                  <Table.Th style={{ width: 100 }}>Referência</Table.Th>
                  <Table.Th style={{ width: 90, textAlign: 'right' }}>Qtd.</Table.Th>
                  <Table.Th style={{ width: 100, textAlign: 'right' }}>Peso</Table.Th>
                  <Table.Th style={{ width: 80, textAlign: 'right' }}>Volumes</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {(g.items ?? []).map((l, i) => (
                  <Table.Tr key={l.id ?? i}>
                    <Table.Td>{l.description}</Table.Td>
                    <Table.Td>{l.reference ?? '—'}</Table.Td>
                    <Table.Td style={{ textAlign: 'right' }}>
                      {l.quantity != null ? `${fmtNumber(l.quantity, 0)} ${l.unit ?? ''}` : '—'}
                    </Table.Td>
                    <Table.Td style={{ textAlign: 'right' }}>
                      {l.weightKg != null ? `${fmtNumber(l.weightKg, 0)} kg` : '—'}
                    </Table.Td>
                    <Table.Td style={{ textAlign: 'right' }}>{l.packages ?? '—'}</Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}

          {g.receivedByName && (
            <Alert color={g.deliveryAccepted ? 'green' : 'orange'} variant="light" p="xs">
              <Text size="sm" fw={600}>
                Recebida por {g.receivedByName}
                {g.deliveryAccepted ? ' — sem reservas' : ' — com reservas'}
              </Text>
              {g.deliveryNotes && <Text size="xs">{g.deliveryNotes}</Text>}
            </Alert>
          )}

          {g.cancellationReason && (
            <Alert color="gray" variant="light" p="xs">
              <Text size="sm">Anulada: {g.cancellationReason}</Text>
            </Alert>
          )}

          <ModalEntrega
            aberto={entregaAberta}
            fechar={() => setEntregaAberta(false)}
            entregar={(corpo) => {
              agir.mutate({ accao: 'deliver', corpo });
              setEntregaAberta(false);
            }}
          />
        </>
      )}
    </Modal>
  );
}

function ModalEntrega({
  aberto,
  fechar,
  entregar,
}: {
  aberto: boolean;
  fechar: () => void;
  entregar: (corpo: Record<string, unknown>) => void;
}) {
  const [receivedByName, setNome] = useState('');
  const [receivedByDocument, setDocumento] = useState('');
  const [accepted, setAceite] = useState(true);
  const [notes, setNotas] = useState('');
  const [arrivalMeter, setContador] = useState('');

  return (
    <Modal opened={aberto} onClose={fechar} title="Registar entrega">
      <TextInput
        label="Quem recebeu"
        required
        value={receivedByName}
        onChange={(e) => setNome(e.currentTarget.value)}
        mb="xs"
      />
      <TextInput
        label="Documento"
        placeholder="BI ou número interno"
        value={receivedByDocument}
        onChange={(e) => setDocumento(e.currentTarget.value)}
        mb="xs"
      />
      <TextInput
        label="Leitura do contador à chegada"
        placeholder="185480"
        description="Serve para calcular a distância percorrida."
        value={arrivalMeter}
        onChange={(e) => setContador(e.currentTarget.value.replace(',', '.'))}
        mb="xs"
      />
      <Checkbox
        label="Cliente recebeu sem reservas"
        checked={accepted}
        onChange={(e) => setAceite(e.currentTarget.checked)}
        mb="xs"
      />
      {!accepted && (
        <Textarea
          label="Que reservas"
          required
          description="Uma reserva que não se escreve não vale nada quando a discussão aparecer."
          autosize
          minRows={2}
          value={notes}
          onChange={(e) => setNotas(e.currentTarget.value)}
        />
      )}
      <Group justify="flex-end" gap="xs" mt="md">
        <Button variant="default" onClick={fechar}>
          Cancelar
        </Button>
        <Button
          disabled={!receivedByName.trim() || (!accepted && !notes.trim())}
          onClick={() =>
            entregar({
              receivedByName,
              receivedByDocument: receivedByDocument.trim() || null,
              accepted,
              notes: notes.trim() || null,
              arrivalMeter: arrivalMeter.trim() ? Number(arrivalMeter) : null,
            })
          }
        >
          Confirmar entrega
        </Button>
      </Group>
    </Modal>
  );
}

function Campo({ rotulo, valor }: { rotulo: string; valor?: string | null }) {
  return (
    <Grid.Col span={{ base: 6, sm: 3 }}>
      <Text size="xs" c="dimmed" tt="uppercase" style={{ letterSpacing: '0.05em' }}>
        {rotulo}
      </Text>
      <Text size="sm" fw={600}>
        {valor ?? '—'}
      </Text>
    </Grid.Col>
  );
}
