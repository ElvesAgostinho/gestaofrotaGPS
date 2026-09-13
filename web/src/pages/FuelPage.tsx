import {
  Alert,
  Badge,
  Button,
  Card,
  FileInput,
  Group,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  Textarea,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCheck,
  IconDownload,
  IconFileExport,
  IconInfoCircle,
  IconPlus,
  IconUpload,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, checkUploadSize, downloadFile } from '../api/client';
import { BotaoBarra, SeparadorBarra } from '../components/erp';
import { Kpi, MagnitudeBar, PageHeader } from '../components/Kpi';
import { NovoAbastecimento } from './fuel/NovoAbastecimento';
import { fmtDateTime, fmtNumber } from '../lib/format';

interface Anomaly {
  id: string;
  kind: string;
  kindLabel: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  status: string;
  statusLabel: string;
  occurredAt: string;
  assetTag: string;
  driverName?: string | null;
  expectedValue?: number | null;
  observedValue?: number | null;
  unit?: string | null;
  litersAtRisk?: number | null;
  costAtRisk?: number | null;
  currency: string;
  title: string;
  detail?: string | null;
  resolution?: string | null;
}

interface AssetCost {
  assetId: string;
  assetTag: string;
  assetName: string;
  branchName?: string | null;
  refuels: number;
  liters: number;
  cost: number;
  currency: string;
  distanceKm: number;
  costPerKm?: number | null;
  litersPer100Km?: number | null;
  baseline?: number | null;
  deviationPercent?: number | null;
  litersAtRisk?: number | null;
  costAtRisk?: number | null;
}

interface Grouped {
  id: string;
  name: string;
  refuels: number;
  liters: number;
  cost: number;
  currency: string;
  litersAtRisk?: number | null;
  costAtRisk?: number | null;
}

interface Dashboard {
  periodStart: string;
  periodEnd: string;
  currency: string;
  refuels: number;
  totalLiters: number;
  totalCost: number;
  totalDistanceKm: number;
  costPerKm?: number | null;
  litersAtRisk: number;
  costAtRisk: number;
  percentAtRisk: number;
  openAnomalies: number;
  criticalAnomalies: number;
  byAsset: AssetCost[];
  byBranch: Grouped[];
  byDriver: Grouped[];
  worstAnomalies: Anomaly[];
  reading: string;
}

interface Baseline {
  assetId: string;
  assetTag: string;
  unit: string;
  baseline: number;
  stdDeviation?: number | null;
  sampleCount: number;
  best?: number | null;
  worst?: number | null;
  explanation: string;
}

/**
 * Controlo de combustível.
 *
 * <p>O ecrã é organizado à volta de uma pergunta: <b>quanto do que gastámos não
 * conseguimos explicar?</b> Custo total e litros totais a empresa já tem na
 * contabilidade; o que não tem é esse número — e é o único que muda
 * comportamentos.
 */
export function FuelPage() {
  const [toResolve, setToResolve] = useState<Anomaly | null>(null);
  const [novoAberto, setNovoAberto] = useState(false);
  const [importarAberto, setImportarAberto] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['fuel', 'dashboard'],
    queryFn: () => api<Dashboard>('/fuel/dashboard'),
  });

  const { data: anomalies } = useQuery({
    queryKey: ['fuel', 'anomalies'],
    queryFn: () => api<{ content: Anomaly[] }>('/fuel/anomalies?status=OPEN&size=100'),
  });

  const { data: baselines } = useQuery({
    queryKey: ['fuel', 'baselines'],
    queryFn: () => api<Baseline[]>('/fuel/baselines'),
  });

  const abertas = anomalies?.content ?? [];
  const maiorCusto = Math.max(1, ...(data?.byAsset ?? []).map((a) => a.cost));

  return (
    <Stack gap="lg">
      <NovoAbastecimento aberto={novoAberto} fechar={() => setNovoAberto(false)} />
      <ImportarAbastecimentos aberto={importarAberto} fechar={() => setImportarAberto(false)} />

      <PageHeader
        title="Combustível"
        subtitle="Quanto se gastou, em que, e quanto disso não tem explicação."
      />

      <Group gap={2} wrap="wrap">
        <BotaoBarra destaque icone={<IconPlus size={13} />} onClick={() => setNovoAberto(true)}>
          Lançar abastecimento
        </BotaoBarra>
        <SeparadorBarra />
        <BotaoBarra icone={<IconUpload size={13} />} onClick={() => setImportarAberto(true)}>
          Importar do posto
        </BotaoBarra>
        <BotaoBarra
          icone={<IconFileExport size={13} />}
          onClick={() => descarregarFicheiro('/reports/fuel.xlsx', 'abastecimentos.xlsx')}
        >
          Exportar
        </BotaoBarra>
        <BotaoBarra
          icone={<IconDownload size={13} />}
          onClick={() => descarregarFicheiro('/imports/fuel/template', 'modelo-abastecimentos.csv')}
          titulo="Modelo CSV com as colunas certas"
        >
          Modelo
        </BotaoBarra>
      </Group>

      {/* O número que interessa vem primeiro e sozinho na leitura. */}
      <Group gap="sm" wrap="wrap">
        <Kpi
          label="Por explicar"
          value={fmtNumber(data?.costAtRisk ?? 0, 0)}
          unit={data?.currency}
          tone={(data?.costAtRisk ?? 0) > 0 ? 'critical' : 'good'}
          hint="Soma do dinheiro das anomalias por analisar e confirmadas. As dispensadas tinham explicação e não contam."
          footnote={
            data ? `${fmtNumber(data.percentAtRisk, 1)}% do gasto do período` : undefined
          }
        />
        <Kpi
          label="Litros em risco"
          value={fmtNumber(data?.litersAtRisk ?? 0, 0)}
          unit="L"
          tone={(data?.litersAtRisk ?? 0) > 0 ? 'warning' : 'neutral'}
        />
        <Kpi
          label="Gasto total"
          value={fmtNumber(data?.totalCost ?? 0, 0)}
          unit={data?.currency}
          footnote={data ? `${data.refuels} abastecimento(s)` : undefined}
        />
        <Kpi
          label="Litros"
          value={fmtNumber(data?.totalLiters ?? 0, 0)}
          unit="L"
        />
        <Kpi
          label="Custo por km"
          value={data?.costPerKm != null ? fmtNumber(data.costPerKm, 1) : '—'}
          unit={data?.costPerKm != null ? data.currency : undefined}
          hint="Só conta os abastecimentos com leitura de hodómetro. Sem medidor não há custo por km que se calcule."
        />
        <Kpi
          label="Anomalias abertas"
          value={data?.openAnomalies ?? 0}
          tone={(data?.criticalAnomalies ?? 0) > 0 ? 'critical' : 'neutral'}
          footnote={
            data && data.criticalAnomalies > 0
              ? `${data.criticalAnomalies} crítica(s) no período`
              : undefined
          }
        />
      </Group>

      {data && (
        <Alert
          variant="light"
          color={data.costAtRisk > 0 ? 'red' : 'blue'}
          icon={data.costAtRisk > 0 ? <IconAlertTriangle size={18} /> : <IconInfoCircle size={18} />}
        >
          {data.reading}
        </Alert>
      )}

      {/* Anomalias antes dos totais: é o que exige ação. */}
      <Card p={0}>
        <Group justify="space-between" p="md" pb="xs">
          <Text fw={700}>Por analisar</Text>
          <Text size="sm" c="dimmed">
            {abertas.length} anomalia(s)
          </Text>
        </Group>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ width: 150 }}>Quando</Table.Th>
              <Table.Th style={{ width: 110 }}>Ativo</Table.Th>
              <Table.Th>O que está mal</Table.Th>
              <Table.Th style={{ width: 150 }}>Em risco</Table.Th>
              <Table.Th style={{ width: 120 }} />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {abertas.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed" ta="center" py="lg" size="sm">
                    {isLoading
                      ? 'A carregar…'
                      : 'Nada por analisar. Isto só cobre o que foi lançado no sistema.'}
                  </Text>
                </Table.Td>
              </Table.Tr>
            )}
            {abertas.map((a) => (
              <Table.Tr key={a.id}>
                <Table.Td>
                  <Text size="sm">{fmtDateTime(a.occurredAt)}</Text>
                  {a.driverName && (
                    <Text size="xs" c="dimmed">
                      {a.driverName}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Text size="sm" fw={600}>
                    {a.assetTag}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Group gap={6} wrap="nowrap" align="center">
                    <Badge variant="light" color={severityColour(a.severity)} size="sm">
                      {a.kindLabel}
                    </Badge>
                  </Group>
                  <Text size="sm" fw={500} mt={2}>
                    {a.title}
                  </Text>
                  {a.detail && (
                    <Text size="xs" c="dimmed" lh={1.4} mt={2}>
                      {a.detail}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  {a.costAtRisk != null ? (
                    <>
                      <Text size="sm" fw={700} c="#b42318" style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {fmtNumber(a.costAtRisk, 0)} {a.currency}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {fmtNumber(a.litersAtRisk ?? 0, 1)} L
                      </Text>
                    </>
                  ) : (
                    <Text size="xs" c="dimmed">
                      sem valor apurado
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Button size="xs" variant="default" onClick={() => setToResolve(a)}>
                    Analisar
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Card>

      <Group align="flex-start" gap="md" wrap="wrap">
        <CostTable
          title="Por ativo"
          rows={(data?.byAsset ?? []).map((a) => ({
            id: a.assetId,
            name: a.assetTag,
            sub: a.assetName,
            refuels: a.refuels,
            liters: a.liters,
            cost: a.cost,
            currency: a.currency,
            costAtRisk: a.costAtRisk,
            extra:
              a.litersPer100Km != null
                ? `${fmtNumber(a.litersPer100Km, 1)} L/100km${
                    a.deviationPercent != null
                      ? ` (${a.deviationPercent > 0 ? '+' : ''}${fmtNumber(
                          a.deviationPercent,
                          0,
                        )}% da base)`
                      : ''
                  }`
                : null,
          }))}
          max={maiorCusto}
        />
        <CostTable
          title="Por filial"
          rows={(data?.byBranch ?? []).map((g) => ({
            id: g.id,
            name: g.name,
            refuels: g.refuels,
            liters: g.liters,
            cost: g.cost,
            currency: g.currency,
            costAtRisk: g.costAtRisk,
          }))}
          max={Math.max(1, ...(data?.byBranch ?? []).map((g) => g.cost))}
          empty="Nenhum abastecimento tem filial atribuída."
        />
        <CostTable
          title="Por motorista"
          rows={(data?.byDriver ?? []).map((g) => ({
            id: g.id,
            name: g.name,
            refuels: g.refuels,
            liters: g.liters,
            cost: g.cost,
            currency: g.currency,
            costAtRisk: g.costAtRisk,
          }))}
          max={Math.max(1, ...(data?.byDriver ?? []).map((g) => g.cost))}
          empty="Nenhum abastecimento tem motorista atribuído."
        />
      </Group>

      <Card p={0}>
        <Group justify="space-between" p="md" pb="xs">
          <Text fw={700}>Consumo normal de cada ativo</Text>
          <Tooltip
            label="Cada ativo é comparado consigo próprio. Um camião de obra gasta o dobro de um ligeiro e isso não é anomalia nenhuma."
            multiline
            w={280}
            withArrow
          >
            <Text size="xs" c="dimmed" style={{ cursor: 'help' }}>
              como é calculado?
            </Text>
          </Tooltip>
        </Group>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Ativo</Table.Th>
              <Table.Th>Base</Table.Th>
              <Table.Th>Melhor</Table.Th>
              <Table.Th>Pior</Table.Th>
              <Table.Th>Depósitos</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {(baselines ?? []).length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={5}>
                  <Text c="dimmed" ta="center" py="md" size="sm">
                    Ainda não há depósitos cheios que cheguem. São precisos quatro consumos
                    calculados para a base valer alguma coisa.
                  </Text>
                </Table.Td>
              </Table.Tr>
            )}
            {(baselines ?? []).map((b) => (
              <Table.Tr key={b.assetId}>
                <Table.Td>
                  <Text size="sm" fw={600}>
                    {b.assetTag}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" fw={700} style={{ fontVariantNumeric: 'tabular-nums' }}>
                    {fmtNumber(b.baseline, 1)} {b.unit}
                  </Text>
                  {b.stdDeviation != null && (
                    <Text size="xs" c="dimmed">
                      ± {fmtNumber(b.stdDeviation, 2)}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    {b.best != null ? fmtNumber(b.best, 1) : '—'}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    {b.worst != null ? fmtNumber(b.worst, 1) : '—'}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="sm" c="dimmed">
                    {b.sampleCount}
                  </Text>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Card>

      <ResolveDialog anomaly={toResolve} onClose={() => setToResolve(null)} />
    </Stack>
  );
}

interface CostRow {
  id: string;
  name: string;
  sub?: string;
  refuels: number;
  liters: number;
  cost: number;
  currency: string;
  costAtRisk?: number | null;
  extra?: string | null;
}

function CostTable({
  title,
  rows,
  max,
  empty,
}: {
  title: string;
  rows: CostRow[];
  max: number;
  empty?: string;
}) {
  return (
    <Card p={0} style={{ flex: '1 1 320px', minWidth: 300 }}>
      <Text fw={700} p="md" pb="xs">
        {title}
      </Text>
      <Table>
        <Table.Tbody>
          {rows.length === 0 && (
            <Table.Tr>
              <Table.Td>
                <Text c="dimmed" ta="center" py="md" size="sm">
                  {empty ?? 'Sem dados no período.'}
                </Text>
              </Table.Td>
            </Table.Tr>
          )}
          {rows.map((r) => (
            <Table.Tr key={r.id}>
              <Table.Td>
                <Group justify="space-between" gap="sm" wrap="nowrap" align="flex-start">
                  <div style={{ minWidth: 0 }}>
                    <Text size="sm" fw={600} truncate>
                      {r.name}
                    </Text>
                    {r.sub && (
                      <Text size="xs" c="dimmed" truncate>
                        {r.sub}
                      </Text>
                    )}
                    {r.extra && (
                      <Text size="xs" c="dimmed">
                        {r.extra}
                      </Text>
                    )}
                  </div>
                  <div style={{ textAlign: 'right', flexShrink: 0 }}>
                    <Text size="sm" fw={700} style={{ fontVariantNumeric: 'tabular-nums' }}>
                      {fmtNumber(r.cost, 0)} {r.currency}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {fmtNumber(r.liters, 0)} L · {r.refuels}×
                    </Text>
                    {!!r.costAtRisk && (
                      <Text size="xs" c="#b42318" fw={600}>
                        {fmtNumber(r.costAtRisk, 0)} por explicar
                      </Text>
                    )}
                  </div>
                </Group>
                <MagnitudeBar value={r.cost} max={max} />
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Card>
  );
}

/** Fechar uma anomalia exige explicação — em branco seria escondê-la. */
function ResolveDialog({ anomaly, onClose }: { anomaly: Anomaly | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<string | null>('CONFIRMED');
  const [resolution, setResolution] = useState('');

  const resolve = useMutation({
    mutationFn: () =>
      api(`/fuel/anomalies/${anomaly!.id}/resolve`, {
        method: 'POST',
        body: { status, resolution },
      }),
    onSuccess: () => {
      notifications.show({
        title: 'Anomalia fechada',
        message: 'Fica registada a explicação e quem a deu.',
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['fuel'] });
      setResolution('');
      onClose();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível fechar', message: e.message, color: 'red' }),
  });

  return (
    <Modal
      opened={!!anomaly}
      onClose={onClose}
      title={anomaly?.title ?? ''}
      size="lg"
      centered
    >
      {anomaly && (
        <Stack gap="md">
          <Alert variant="light" color={severityColour(anomaly.severity)}>
            {anomaly.detail}
          </Alert>

          <Group gap="xl">
            <Field label="Ativo" value={anomaly.assetTag} />
            <Field label="Quando" value={fmtDateTime(anomaly.occurredAt)} />
            {anomaly.driverName && <Field label="Motorista" value={anomaly.driverName} />}
            {anomaly.expectedValue != null && (
              <Field
                label="Esperado"
                value={`${fmtNumber(anomaly.expectedValue, 2)} ${anomaly.unit ?? ''}`}
              />
            )}
            {anomaly.observedValue != null && (
              <Field
                label="Observado"
                value={`${fmtNumber(anomaly.observedValue, 2)} ${anomaly.unit ?? ''}`}
              />
            )}
            {anomaly.costAtRisk != null && (
              <Field
                label="Em risco"
                value={`${fmtNumber(anomaly.costAtRisk, 0)} ${anomaly.currency}`}
              />
            )}
          </Group>

          <Select
            label="Desfecho"
            data={[
              { value: 'CONFIRMED', label: 'Confirmada — havia mesmo problema' },
              { value: 'DISMISSED', label: 'Sem fundamento — o sistema enganou-se' },
              { value: 'RESOLVED', label: 'Resolvida — já foi tratada' },
            ]}
            value={status}
            onChange={setStatus}
            allowDeselect={false}
          />

          <Textarea
            label="O que se apurou"
            description="Obrigatório. Uma anomalia fechada em branco é indistinguível de uma anomalia escondida."
            placeholder="Ex.: confirmado com a bomba — encheram também dois bidões para o gerador da obra"
            minRows={3}
            autosize
            value={resolution}
            onChange={(e) => setResolution(e.currentTarget.value)}
          />

          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancelar
            </Button>
            <Button
              leftSection={<IconCheck size={16} />}
              disabled={resolution.trim().length < 5}
              loading={resolve.isPending}
              onClick={() => resolve.mutate()}
            >
              Fechar anomalia
            </Button>
          </Group>
        </Stack>
      )}
    </Modal>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <Text size="10px" c="dimmed" tt="uppercase" fw={700} style={{ letterSpacing: '0.05em' }}>
        {label}
      </Text>
      <Text size="sm" fw={600}>
        {value}
      </Text>
    </div>
  );
}

function severityColour(severity: string) {
  if (severity === 'CRITICAL') return 'red';
  if (severity === 'WARNING') return 'orange';
  return 'gray';
}


async function descarregarFicheiro(rota: string, nome: string) {
  try {
    await downloadFile(rota, nome);
  } catch {
    // O cliente já mostra a razão ao utilizador.
  }
}

/**
 * Importação do ficheiro que o posto ou a gestora de cartões manda por mês.
 *
 * <p>Faz sempre a verificação antes de gravar: uma folha de trezentas linhas
 * com a coluna errada, gravada de uma vez, dá trezentos registos por apagar à
 * mão. O relatório mostra linha a linha o que não passou.
 */
function ImportarAbastecimentos({ aberto, fechar }: { aberto: boolean; fechar: () => void }) {
  const queryClient = useQueryClient();
  const [ficheiro, setFicheiro] = useState<File | null>(null);
  const [relatorio, setRelatorio] = useState<ImportReport | null>(null);

  const enviar = useMutation({
    mutationFn: async (gravar: boolean) => {
      if (!ficheiro) throw new Error('Escolha um ficheiro CSV.');
      const recusa = checkUploadSize(ficheiro);
      if (recusa) throw new Error(recusa);
      const form = new FormData();
      form.append('file', ficheiro);
      return api<ImportReport>(`/imports/fuel?dryRun=${!gravar}`, {
        method: 'POST',
        body: form,
      });
    },
    onSuccess: (r) => {
      setRelatorio(r);
      if (!r.dryRun) {
        queryClient.invalidateQueries({ queryKey: ['fuel'] });
        notifications.show({
          title: `${r.created} abastecimento(s) lançado(s)`,
          message: r.errors.length ? `${r.errors.length} linha(s) por corrigir.` : '',
          color: r.errors.length ? 'yellow' : 'green',
        });
      }
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível importar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={aberto} onClose={fechar} title="Importar abastecimentos" size="lg">
      <Text size="sm" c="dimmed" mb="sm">
        Aceita a etiqueta ou a matrícula da viatura, datas em dd/mm/aaaa, e «sim/não» no depósito
        cheio. Descarregue o modelo se quiser as colunas certas.
      </Text>

      <Group align="flex-end" gap="xs" mb="sm">
        <FileInput
          label="Ficheiro CSV"
          placeholder="Escolher…"
          accept=".csv,text/csv"
          value={ficheiro}
          onChange={(f: File | null) => {
            setFicheiro(f);
            setRelatorio(null);
          }}
          style={{ flex: 1 }}
        />
        <Button
          variant="default"
          onClick={() => enviar.mutate(false)}
          loading={enviar.isPending}
          disabled={!ficheiro}
        >
          Verificar
        </Button>
        <Button
          onClick={() => enviar.mutate(true)}
          loading={enviar.isPending}
          disabled={!ficheiro || !relatorio}
        >
          Importar
        </Button>
      </Group>

      {relatorio && (
        <Alert
          color={relatorio.errors.length ? 'yellow' : 'green'}
          variant="light"
          p="xs"
          mb="xs"
        >
          <Text size="sm" fw={600}>
            {relatorio.dryRun ? 'Verificação' : 'Importado'}: {relatorio.created} de{' '}
            {relatorio.totalRows} linha(s)
            {relatorio.errors.length ? `, ${relatorio.errors.length} por corrigir` : ''}
          </Text>
          {relatorio.dryRun && !relatorio.errors.length && (
            <Text size="xs" c="dimmed">
              Nada foi gravado ainda. Carregue em «Importar» para confirmar.
            </Text>
          )}
        </Alert>
      )}

      {relatorio && relatorio.errors.length > 0 && (
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ width: 60 }}>Linha</Table.Th>
              <Table.Th style={{ width: 120 }}>Valor</Table.Th>
              <Table.Th>O que está mal</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {relatorio.errors.map((e, i) => (
              <Table.Tr key={i}>
                <Table.Td>{e.line}</Table.Td>
                <Table.Td>{e.value ?? '—'}</Table.Td>
                <Table.Td>{e.message}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Modal>
  );
}

interface ImportReport {
  dryRun: boolean;
  totalRows: number;
  created: number;
  errors: { line: number; value?: string | null; message: string }[];
}
