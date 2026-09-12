/**
 * O chão de oficina de uma ordem: o que o mecânico mede, despeja, lê e assina.
 *
 * <p>Estes separadores são o lado técnico da ficha. O resto da ordem trata do
 * percurso administrativo — aprovações, orçamentos, custos; aqui trata-se do
 * que acontece com a viatura em cima da vala.
 *
 * <p>A peça central são as <b>medições</b>. Um checklist só diz «OK» ou «não
 * OK»; não diz que a pastilha tem 4 mm. Sem o número não há forma de saber que
 * ela estava a 9 mm há três meses — e portanto quando chega ao limite.
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
import {
  IconAlertTriangle,
  IconPlus,
  IconRuler,
  IconSignature,
  IconTrash,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../../api/client';
import { Painel, Ponto, SeccaoForm } from '../../components/erp';
import { fmtDateTime, fmtNumber } from '../../lib/format';

// ==== Tipos ================================================================

export interface Measurement {
  id: string;
  groupName: string;
  name: string;
  position?: string | null;
  valueNum?: number | null;
  valueText?: string | null;
  unit?: string | null;
  minValue?: number | null;
  maxValue?: number | null;
  verdict: string;
  verdictLabel: string;
  needsAction: boolean;
  note?: string | null;
  recordedByLabel?: string | null;
  recordedAt: string;
}

export interface Fluid {
  id: string;
  kind: string;
  kindLabel: string;
  spec?: string | null;
  brand?: string | null;
  action: string;
  actionLabel: string;
  quantity?: number | null;
  unit: string;
  filterChanged: boolean;
  filterPartNumber?: string | null;
  batch?: string | null;
  unitCost?: number | null;
  totalCost?: number | null;
  note?: string | null;
}

export interface FaultCode {
  id: string;
  source: string;
  sourceLabel: string;
  code: string;
  description?: string | null;
  occurrences?: number | null;
  status: string;
  statusLabel: string;
  clearedAt?: string | null;
  note?: string | null;
}

export interface Signature {
  id: string;
  role: string;
  roleLabel: string;
  personName: string;
  personDocument?: string | null;
  accepted: boolean;
  note?: string | null;
  signedAt: string;
}

interface Template {
  groupName: string;
  name: string;
  position?: string | null;
  unit?: string | null;
  minValue?: number | null;
  maxValue?: number | null;
}

/** Cor do veredicto. Nunca o dourado da marca — ver theme.ts. */
const COR_VEREDICTO: Record<string, string> = {
  OK: '#16a34a',
  ATTENTION: '#ea580c',
  REPLACE: '#dc2626',
  FAIL: '#991b1b',
};

// ==== Medições =============================================================

export function MedicoesPainel({
  id,
  medicoes,
  editavel,
}: {
  id: string;
  medicoes: Measurement[];
  editavel: boolean;
}) {
  const queryClient = useQueryClient();
  const [modeloAberto, setModeloAberto] = useState(false);

  const remover = useMutation({
    mutationFn: (mid: string) =>
      api(`/work-orders/${id}/measurements/${mid}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-order', id] }),
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível remover', message: e.message, color: 'red' }),
  });

  // Agrupadas como na folha de oficina: travagem junto de travagem.
  const porGrupo = useMemo(() => {
    const m = new Map<string, Measurement[]>();
    for (const x of medicoes) {
      if (!m.has(x.groupName)) m.set(x.groupName, []);
      m.get(x.groupName)!.push(x);
    }
    return [...m.entries()];
  }, [medicoes]);

  const aAgir = medicoes.filter((m) => m.needsAction);

  return (
    <Stack gap="sm">
      {editavel && (
        <FormMedicao id={id} aberto={modeloAberto} fechar={() => setModeloAberto(false)} />
      )}

      <Painel
        titulo="Medições"
        acoes={
          editavel ? (
            <Button
              size="xs"
              leftSection={<IconRuler size={13} />}
              onClick={() => setModeloAberto(true)}
            >
              Registar medição
            </Button>
          ) : undefined
        }
        rodape={
          <Text size="xs" c="dimmed">
            {medicoes.length} medição(ões) · {aAgir.length} fora do limite
          </Text>
        }
      >
        {aAgir.length > 0 && (
          <Alert color="red" variant="light" p="xs" mb="sm" icon={<IconAlertTriangle size={15} />}>
            <Text size="sm" fw={600}>
              {aAgir.length} medição(ões) fora do limite de serviço
            </Text>
            <Text size="xs">
              {aAgir
                .map(
                  (m) =>
                    `${m.name}${m.position ? ` (${m.position})` : ''}: ${fmtNumber(
                      m.valueNum ?? 0,
                      1,
                    )} ${m.unit ?? ''}`,
                )
                .join(' · ')}
            </Text>
          </Alert>
        )}

        {medicoes.length === 0 ? (
          <Text size="sm" c="dimmed" py="sm">
            Sem medições. É o número — e não o «OK» — que permite prever quando a peça chega ao
            limite.
          </Text>
        ) : (
          porGrupo.map(([grupo, itens]) => (
            <div key={grupo} style={{ marginBottom: 12 }}>
              <Text size="xs" fw={700} tt="uppercase" mb={4} c="dimmed">
                {grupo}
              </Text>
              <Table>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>O que</Table.Th>
                    <Table.Th style={{ width: 140 }}>Onde</Table.Th>
                    <Table.Th style={{ width: 90, textAlign: 'right' }}>Valor</Table.Th>
                    <Table.Th style={{ width: 110, textAlign: 'right' }}>Limite</Table.Th>
                    <Table.Th style={{ width: 170 }}>Veredicto</Table.Th>
                    <Table.Th style={{ width: 130 }}>Quem</Table.Th>
                    {editavel && <Table.Th style={{ width: 40 }} />}
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {itens.map((m) => (
                    <Table.Tr key={m.id}>
                      <Table.Td>{m.name}</Table.Td>
                      <Table.Td>{m.position ?? '—'}</Table.Td>
                      <Table.Td style={{ textAlign: 'right', fontWeight: 700 }}>
                        {m.valueNum != null
                          ? `${fmtNumber(m.valueNum, 2)} ${m.unit ?? ''}`
                          : (m.valueText ?? '—')}
                      </Table.Td>
                      <Table.Td style={{ textAlign: 'right' }}>
                        <Text size="xs" c="dimmed">
                          {m.minValue != null && `min ${fmtNumber(m.minValue, 1)}`}
                          {m.minValue != null && m.maxValue != null && ' · '}
                          {m.maxValue != null && `máx ${fmtNumber(m.maxValue, 1)}`}
                          {m.minValue == null && m.maxValue == null && '—'}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Ponto cor={COR_VEREDICTO[m.verdict] ?? '#6b7280'} />
                        {m.verdictLabel}
                      </Table.Td>
                      <Table.Td>
                        <Text size="xs" c="dimmed">
                          {m.recordedByLabel ?? '—'}
                        </Text>
                      </Table.Td>
                      {editavel && (
                        <Table.Td>
                          <Button
                            size="compact-xs"
                            variant="subtle"
                            color="red"
                            onClick={() => remover.mutate(m.id)}
                          >
                            <IconTrash size={13} />
                          </Button>
                        </Table.Td>
                      )}
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </div>
          ))
        )}
      </Painel>
    </Stack>
  );
}

/**
 * Formulário de medição, com modelos por tipo de equipamento.
 *
 * <p>Escrever trinta medições à mão em cada ordem é o que faz um técnico
 * desistir do sistema e voltar ao papel. Os modelos trazem os limites de
 * serviço habituais já preenchidos — e são editáveis, porque o manual do
 * fabricante manda sempre.
 */
function FormMedicao({
  id,
  aberto,
  fechar,
}: {
  id: string;
  aberto: boolean;
  fechar: () => void;
}) {
  const queryClient = useQueryClient();
  const [tipo, setTipo] = useState<string | null>('HEAVY');
  const [escolhido, setEscolhido] = useState<Template | null>(null);
  const [groupName, setGroupName] = useState('');
  const [name, setName] = useState('');
  const [position, setPosition] = useState('');
  const [valueNum, setValueNum] = useState('');
  const [unit, setUnit] = useState('');
  const [minValue, setMinValue] = useState('');
  const [maxValue, setMaxValue] = useState('');
  const [note, setNote] = useState('');

  const { data: modelos } = useQuery({
    queryKey: ['measurement-templates', tipo],
    queryFn: () => api<Template[]>(`/work-orders/measurement-templates?kind=${tipo}`),
    enabled: aberto,
  });

  function aplicar(t: Template) {
    setEscolhido(t);
    setGroupName(t.groupName);
    setName(t.name);
    setPosition(t.position ?? '');
    setUnit(t.unit ?? '');
    setMinValue(t.minValue != null ? String(t.minValue) : '');
    setMaxValue(t.maxValue != null ? String(t.maxValue) : '');
    setValueNum('');
  }

  const gravar = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/measurements`, {
        method: 'POST',
        body: {
          groupName,
          name,
          position: position.trim() || null,
          valueNum: valueNum.trim() ? Number(valueNum) : null,
          unit: unit.trim() || null,
          minValue: minValue.trim() ? Number(minValue) : null,
          maxValue: maxValue.trim() ? Number(maxValue) : null,
          note: note.trim() || null,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      notifications.show({ title: 'Medição registada', message: '', color: 'green' });
      setValueNum('');
      setPosition('');
      setNote('');
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });

  const falta = !groupName.trim() || !name.trim() || !valueNum.trim();

  return (
    <Modal opened={aberto} onClose={fechar} title="Registar medição" size="xl">
      <SeccaoForm
        titulo="O que se mede neste equipamento"
        descricao="Escolha da lista para trazer os limites já preenchidos, ou escreva à mão em baixo."
      >
        <Select
          label="Tipo de equipamento"
          data={[
            { value: 'HEAVY', label: 'Pesado (camião, máquina)' },
            { value: 'LIGHT', label: 'Ligeiro' },
            { value: 'GENERATOR', label: 'Gerador' },
          ]}
          value={tipo}
          onChange={(v) => {
            setTipo(v);
            setEscolhido(null);
          }}
          allowDeselect={false}
          mb="xs"
          style={{ maxWidth: 280 }}
        />
        <div style={{ maxHeight: 190, overflowY: 'auto', border: '1px solid var(--erp-moldura)' }}>
          <Table>
            <Table.Tbody>
              {(modelos ?? []).map((t, i) => {
                const activo =
                  escolhido?.name === t.name && escolhido?.position === t.position;
                return (
                  <Table.Tr
                    key={i}
                    onClick={() => aplicar(t)}
                    style={{
                      cursor: 'pointer',
                      background: activo ? 'var(--erp-realce)' : undefined,
                    }}
                  >
                    <Table.Td style={{ width: 110 }}>
                      <Text size="xs" c="dimmed">
                        {t.groupName}
                      </Text>
                    </Table.Td>
                    <Table.Td>{t.name}</Table.Td>
                    <Table.Td style={{ width: 150 }}>
                      <Text size="xs" c="dimmed">
                        {t.position ?? ''}
                      </Text>
                    </Table.Td>
                    <Table.Td style={{ width: 150, textAlign: 'right' }}>
                      <Text size="xs" c="dimmed">
                        {t.minValue != null && `min ${t.minValue}`}
                        {t.minValue != null && t.maxValue != null && ' · '}
                        {t.maxValue != null && `máx ${t.maxValue}`}
                        {t.unit ? ` ${t.unit}` : ''}
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                );
              })}
            </Table.Tbody>
          </Table>
        </div>
      </SeccaoForm>

      <SeccaoForm titulo="O valor lido">
        <Grid gutter="xs">
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Grupo"
              required
              value={groupName}
              onChange={(e) => setGroupName(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 4 }}>
            <TextInput
              label="O que"
              required
              value={name}
              onChange={(e) => setName(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 5 }}>
            <TextInput
              label="Onde"
              placeholder="Dianteiro esquerdo"
              value={position}
              onChange={(e) => setPosition(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Valor lido"
              required
              placeholder="4.5"
              inputMode="decimal"
              value={valueNum}
              onChange={(e) => setValueNum(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Unidade"
              placeholder="mm"
              value={unit}
              onChange={(e) => setUnit(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Mínimo"
              inputMode="decimal"
              value={minValue}
              onChange={(e) => setMinValue(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Máximo"
              inputMode="decimal"
              value={maxValue}
              onChange={(e) => setMaxValue(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>
          <Grid.Col span={12}>
            <TextInput
              label="Observação"
              value={note}
              onChange={(e) => setNote(e.currentTarget.value)}
            />
          </Grid.Col>
        </Grid>

        <Alert color="gray" variant="light" p="xs" mt="xs">
          <Text size="xs">
            O veredicto é decidido pelo servidor a partir dos limites. Não se consegue marcar como
            «OK» um valor que está fora.
          </Text>
        </Alert>
      </SeccaoForm>

      <Group justify="flex-end" gap="xs">
        <Button variant="default" onClick={fechar}>
          Fechar
        </Button>
        <Button onClick={() => gravar.mutate()} loading={gravar.isPending} disabled={falta}>
          Registar e continuar
        </Button>
      </Group>
    </Modal>
  );
}

// ==== Fluidos ==============================================================

export function FluidosPainel({
  id,
  fluidos,
  editavel,
  podeVerCustos,
}: {
  id: string;
  fluidos: Fluid[];
  editavel: boolean;
  podeVerCustos: boolean;
}) {
  const queryClient = useQueryClient();
  const [aberto, setAberto] = useState(false);

  const remover = useMutation({
    mutationFn: (fid: string) => api(`/work-orders/${id}/fluids/${fid}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-order', id] }),
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível remover', message: e.message, color: 'red' }),
  });

  const total = fluidos.reduce((s, f) => s + (f.totalCost ?? 0), 0);

  return (
    <>
      {editavel && <FormFluido id={id} aberto={aberto} fechar={() => setAberto(false)} />}
      <Painel
        titulo="Fluidos e filtros"
        acoes={
          editavel ? (
            <Button size="xs" leftSection={<IconPlus size={13} />} onClick={() => setAberto(true)}>
              Registar fluido
            </Button>
          ) : undefined
        }
        rodape={
          podeVerCustos && total > 0 ? (
            <Text size="xs" c="dimmed">
              Total em fluidos: <b>{fmtNumber(total, 2)} Kz</b>
            </Text>
          ) : undefined
        }
      >
        {fluidos.length === 0 ? (
          <Text size="sm" c="dimmed" py="sm">
            Sem fluidos registados. Uma peça diz «filtro de óleo»; não diz que se meteram 38 L de
            15W-40.
          </Text>
        ) : (
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ width: 170 }}>Fluido</Table.Th>
                <Table.Th style={{ width: 140 }}>Especificação</Table.Th>
                <Table.Th style={{ width: 110 }}>Ação</Table.Th>
                <Table.Th style={{ width: 90, textAlign: 'right' }}>Quantidade</Table.Th>
                <Table.Th>Filtro</Table.Th>
                {podeVerCustos && <Table.Th style={{ width: 110, textAlign: 'right' }}>Custo</Table.Th>}
                {editavel && <Table.Th style={{ width: 40 }} />}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {fluidos.map((f) => (
                <Table.Tr key={f.id}>
                  <Table.Td>{f.kindLabel}</Table.Td>
                  <Table.Td>
                    {f.spec ?? '—'}
                    {f.brand && (
                      <Text size="xs" c="dimmed">
                        {f.brand}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>{f.actionLabel}</Table.Td>
                  <Table.Td style={{ textAlign: 'right' }}>
                    {f.quantity != null ? `${fmtNumber(f.quantity, 2)} ${f.unit}` : '—'}
                  </Table.Td>
                  <Table.Td>
                    {f.filterChanged ? (
                      <>
                        <Badge size="xs" variant="light" color="gray">
                          Trocado
                        </Badge>
                        {f.filterPartNumber && (
                          <Text size="xs" c="dimmed">
                            {f.filterPartNumber}
                          </Text>
                        )}
                      </>
                    ) : (
                      '—'
                    )}
                  </Table.Td>
                  {podeVerCustos && (
                    <Table.Td style={{ textAlign: 'right' }}>
                      {f.totalCost != null ? fmtNumber(f.totalCost, 2) : '—'}
                    </Table.Td>
                  )}
                  {editavel && (
                    <Table.Td>
                      <Button
                        size="compact-xs"
                        variant="subtle"
                        color="red"
                        onClick={() => remover.mutate(f.id)}
                      >
                        <IconTrash size={13} />
                      </Button>
                    </Table.Td>
                  )}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Painel>
    </>
  );
}

function FormFluido({ id, aberto, fechar }: { id: string; aberto: boolean; fechar: () => void }) {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<string | null>('ENGINE_OIL');
  const [action, setAction] = useState<string | null>('REPLACED');
  const [spec, setSpec] = useState('');
  const [brand, setBrand] = useState('');
  const [quantity, setQuantity] = useState('');
  const [unit, setUnit] = useState('L');
  const [filterChanged, setFilterChanged] = useState(false);
  const [filterPartNumber, setFilterPartNumber] = useState('');
  const [batch, setBatch] = useState('');
  const [unitCost, setUnitCost] = useState('');
  const [note, setNote] = useState('');

  const gravar = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/fluids`, {
        method: 'POST',
        body: {
          kind,
          action,
          spec: spec.trim() || null,
          brand: brand.trim() || null,
          quantity: quantity.trim() ? Number(quantity) : null,
          unit,
          filterChanged,
          filterPartNumber: filterPartNumber.trim() || null,
          batch: batch.trim() || null,
          unitCost: unitCost.trim() ? Number(unitCost) : null,
          note: note.trim() || null,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      notifications.show({ title: 'Fluido registado', message: '', color: 'green' });
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={aberto} onClose={fechar} title="Registar fluido" size="lg">
      <Grid gutter="xs">
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <Select
            label="Fluido"
            required
            data={[
              { value: 'ENGINE_OIL', label: 'Óleo do motor' },
              { value: 'TRANSMISSION', label: 'Óleo da transmissão' },
              { value: 'HYDRAULIC', label: 'Óleo hidráulico' },
              { value: 'DIFFERENTIAL', label: 'Óleo do diferencial' },
              { value: 'COOLANT', label: 'Líquido de refrigeração' },
              { value: 'BRAKE_FLUID', label: 'Líquido dos travões' },
              { value: 'GREASE', label: 'Massa lubrificante' },
              { value: 'FUEL', label: 'Combustível' },
              { value: 'ADBLUE', label: 'AdBlue' },
              { value: 'OTHER', label: 'Outro' },
            ]}
            value={kind}
            onChange={setKind}
            allowDeselect={false}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <Select
            label="O que se fez"
            data={[
              { value: 'REPLACED', label: 'Substituído' },
              { value: 'ADDED', label: 'Acrescentado' },
              { value: 'TOPPED_UP', label: 'Atestado' },
              { value: 'DRAINED', label: 'Drenado' },
              { value: 'SAMPLED', label: 'Recolhida amostra' },
            ]}
            value={action}
            onChange={setAction}
            allowDeselect={false}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Especificação"
            placeholder="15W-40 CH-4"
            description="A especificação errada estraga o motor que se queria proteger."
            value={spec}
            onChange={(e) => setSpec(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <TextInput
            label="Marca"
            placeholder="Cat DEO"
            value={brand}
            onChange={(e) => setBrand(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, sm: 3 }}>
          <TextInput
            label="Quantidade"
            placeholder="38"
            inputMode="decimal"
            value={quantity}
            onChange={(e) => setQuantity(e.currentTarget.value.replace(',', '.'))}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, sm: 3 }}>
          <Select
            label="Unidade"
            data={['L', 'kg', 'ml', 'g']}
            value={unit}
            onChange={(v) => setUnit(v ?? 'L')}
            allowDeselect={false}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, sm: 3 }}>
          <TextInput
            label="Preço unitário"
            placeholder="1850"
            inputMode="decimal"
            value={unitCost}
            onChange={(e) => setUnitCost(e.currentTarget.value.replace(',', '.'))}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, sm: 3 }}>
          <TextInput
            label="Lote"
            value={batch}
            onChange={(e) => setBatch(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 5 }}>
          <Checkbox
            label="Filtro trocado ao mesmo tempo"
            checked={filterChanged}
            onChange={(e) => setFilterChanged(e.currentTarget.checked)}
            mt={22}
          />
        </Grid.Col>
        {filterChanged && (
          <Grid.Col span={{ base: 12, sm: 7 }}>
            <TextInput
              label="Número de peça do filtro"
              placeholder="1R-0716"
              description="É o que permite repetir a compra sem enganos."
              value={filterPartNumber}
              onChange={(e) => setFilterPartNumber(e.currentTarget.value)}
            />
          </Grid.Col>
        )}
        <Grid.Col span={12}>
          <TextInput label="Observação" value={note} onChange={(e) => setNote(e.currentTarget.value)} />
        </Grid.Col>
      </Grid>

      <Group justify="flex-end" gap="xs" mt="md">
        <Button variant="default" onClick={fechar}>
          Cancelar
        </Button>
        <Button onClick={() => gravar.mutate()} loading={gravar.isPending}>
          Registar
        </Button>
      </Group>
    </Modal>
  );
}

// ==== Códigos de avaria ====================================================

export function CodigosPainel({
  id,
  codigos,
  editavel,
}: {
  id: string;
  codigos: FaultCode[];
  editavel: boolean;
}) {
  const queryClient = useQueryClient();
  const [aberto, setAberto] = useState(false);
  const [source, setSource] = useState<string | null>('J1939');
  const [code, setCode] = useState('');
  const [description, setDescription] = useState('');
  const [occurrences, setOccurrences] = useState('');

  const gravar = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/fault-codes`, {
        method: 'POST',
        body: {
          source,
          code,
          description: description.trim() || null,
          occurrences: occurrences.trim() ? Number(occurrences) : null,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      setCode('');
      setDescription('');
      setOccurrences('');
      setAberto(false);
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });

  const apagar = useMutation({
    mutationFn: (cid: string) =>
      api(`/work-orders/${id}/fault-codes/${cid}/clear`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-order', id] }),
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível marcar', message: e.message, color: 'red' }),
  });

  const remover = useMutation({
    mutationFn: (cid: string) => api(`/work-orders/${id}/fault-codes/${cid}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-order', id] }),
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível remover', message: e.message, color: 'red' }),
  });

  return (
    <>
      <Modal opened={aberto} onClose={() => setAberto(false)} title="Registar código de avaria">
        <Grid gutter="xs">
          <Grid.Col span={12}>
            <Select
              label="Origem"
              data={[
                { value: 'J1939', label: 'J1939 — pesados (SPN/FMI)' },
                { value: 'OBD2', label: 'OBD-II — ligeiros' },
                { value: 'PANEL', label: 'Painel do equipamento' },
                { value: 'MANUFACTURER', label: 'Diagnóstico do fabricante' },
                { value: 'OTHER', label: 'Outra origem' },
              ]}
              value={source}
              onChange={setSource}
              allowDeselect={false}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 7 }}>
            <TextInput
              label="Código"
              required
              placeholder="SPN 100 FMI 1"
              value={code}
              onChange={(e) => setCode(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 5 }}>
            <TextInput
              label="Ocorrências"
              placeholder="3"
              inputMode="numeric"
              value={occurrences}
              onChange={(e) => setOccurrences(e.currentTarget.value.replace(/\D/g, ''))}
            />
          </Grid.Col>
          <Grid.Col span={12}>
            <Textarea
              label="Descrição"
              placeholder="Pressão de óleo do motor abaixo do mínimo"
              autosize
              minRows={2}
              value={description}
              onChange={(e) => setDescription(e.currentTarget.value)}
            />
          </Grid.Col>
        </Grid>
        <Group justify="flex-end" gap="xs" mt="md">
          <Button variant="default" onClick={() => setAberto(false)}>
            Cancelar
          </Button>
          <Button onClick={() => gravar.mutate()} loading={gravar.isPending} disabled={!code.trim()}>
            Registar
          </Button>
        </Group>
      </Modal>

      <Painel
        titulo="Códigos de avaria lidos"
        acoes={
          editavel ? (
            <Button size="xs" leftSection={<IconPlus size={13} />} onClick={() => setAberto(true)}>
              Registar código
            </Button>
          ) : undefined
        }
      >
        {codigos.length === 0 ? (
          <Text size="sm" c="dimmed" py="sm">
            Sem códigos. Guardá-los é o que permite ver, um ano depois, que o mesmo código voltou
            três vezes — e que o que se andou a fazer foi apagar o sintoma.
          </Text>
        ) : (
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ width: 150 }}>Origem</Table.Th>
                <Table.Th style={{ width: 140 }}>Código</Table.Th>
                <Table.Th>Descrição</Table.Th>
                <Table.Th style={{ width: 80, textAlign: 'right' }}>Vezes</Table.Th>
                <Table.Th style={{ width: 110 }}>Estado</Table.Th>
                {editavel && <Table.Th style={{ width: 140 }} />}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {codigos.map((c) => (
                <Table.Tr key={c.id}>
                  <Table.Td>
                    <Text size="xs" c="dimmed">
                      {c.sourceLabel}
                    </Text>
                  </Table.Td>
                  <Table.Td style={{ fontWeight: 700 }}>{c.code}</Table.Td>
                  <Table.Td>{c.description ?? '—'}</Table.Td>
                  <Table.Td style={{ textAlign: 'right' }}>{c.occurrences ?? '—'}</Table.Td>
                  <Table.Td>
                    <Badge
                      size="xs"
                      variant="light"
                      color={c.status === 'CLEARED' ? 'gray' : c.status === 'ACTIVE' ? 'red' : 'yellow'}
                    >
                      {c.statusLabel}
                    </Badge>
                  </Table.Td>
                  {editavel && (
                    <Table.Td>
                      <Group gap={4}>
                        {c.status !== 'CLEARED' && (
                          <Button
                            size="compact-xs"
                            variant="subtle"
                            onClick={() => apagar.mutate(c.id)}
                          >
                            Apagado
                          </Button>
                        )}
                        <Button
                          size="compact-xs"
                          variant="subtle"
                          color="red"
                          onClick={() => remover.mutate(c.id)}
                        >
                          <IconTrash size={13} />
                        </Button>
                      </Group>
                    </Table.Td>
                  )}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Painel>
    </>
  );
}

// ==== Assinaturas ==========================================================

export function AssinaturasPainel({
  id,
  assinaturas,
  editavel,
}: {
  id: string;
  assinaturas: Signature[];
  editavel: boolean;
}) {
  const queryClient = useQueryClient();
  const [aberto, setAberto] = useState(false);
  const [role, setRole] = useState<string | null>('TECHNICIAN');
  const [personName, setPersonName] = useState('');
  const [personDocument, setPersonDocument] = useState('');
  const [accepted, setAccepted] = useState(true);
  const [note, setNote] = useState('');

  const assinar = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/signatures`, {
        method: 'POST',
        body: {
          role,
          personName,
          personDocument: personDocument.trim() || null,
          accepted,
          note: note.trim() || null,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      notifications.show({ title: 'Assinatura registada', message: '', color: 'green' });
      setPersonName('');
      setPersonDocument('');
      setNote('');
      setAccepted(true);
      setAberto(false);
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível assinar', message: e.message, color: 'red' }),
  });

  return (
    <>
      <Modal opened={aberto} onClose={() => setAberto(false)} title="Assinar a ordem">
        <Grid gutter="xs">
          <Grid.Col span={12}>
            <Select
              label="Em que papel"
              data={[
                { value: 'TECHNICIAN', label: 'Técnico executante' },
                { value: 'SUPERVISOR', label: 'Responsável da oficina' },
                { value: 'QUALITY', label: 'Controlo de qualidade' },
                { value: 'OPERATOR', label: 'Operador ou motorista' },
                { value: 'CLIENT', label: 'Cliente' },
              ]}
              value={role}
              onChange={setRole}
              allowDeselect={false}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 7 }}>
            <TextInput
              label="Nome"
              required
              value={personName}
              onChange={(e) => setPersonName(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 5 }}>
            <TextInput
              label="Documento"
              placeholder="BI ou número interno"
              value={personDocument}
              onChange={(e) => setPersonDocument(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={12}>
            <Checkbox
              label="Aceita o trabalho sem reservas"
              checked={accepted}
              onChange={(e) => setAccepted(e.currentTarget.checked)}
            />
          </Grid.Col>
          {!accepted && (
            <Grid.Col span={12}>
              <Textarea
                label="Que reservas"
                required
                description="Uma reserva que não se escreve não vale nada quando a discussão aparecer."
                autosize
                minRows={2}
                value={note}
                onChange={(e) => setNote(e.currentTarget.value)}
              />
            </Grid.Col>
          )}
        </Grid>
        <Group justify="flex-end" gap="xs" mt="md">
          <Button variant="default" onClick={() => setAberto(false)}>
            Cancelar
          </Button>
          <Button
            onClick={() => assinar.mutate()}
            loading={assinar.isPending}
            disabled={!personName.trim() || (!accepted && !note.trim())}
          >
            Assinar
          </Button>
        </Group>
      </Modal>

      <Painel
        titulo="Assinaturas"
        acoes={
          editavel ? (
            <Button
              size="xs"
              leftSection={<IconSignature size={13} />}
              onClick={() => setAberto(true)}
            >
              Assinar
            </Button>
          ) : undefined
        }
      >
        {assinaturas.length === 0 ? (
          <Text size="sm" c="dimmed" py="sm">
            Sem assinaturas. Sem a aceitação do operador, «a viatura já vinha assim» e
            «estragaram-na na oficina» continuam duas afirmações igualmente indemonstráveis.
          </Text>
        ) : (
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th style={{ width: 190 }}>Papel</Table.Th>
                <Table.Th>Nome</Table.Th>
                <Table.Th style={{ width: 130 }}>Documento</Table.Th>
                <Table.Th style={{ width: 150 }}>Quando</Table.Th>
                <Table.Th style={{ width: 170 }}>Aceitação</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {assinaturas.map((a) => (
                <Table.Tr key={a.id}>
                  <Table.Td>{a.roleLabel}</Table.Td>
                  <Table.Td style={{ fontWeight: 600 }}>{a.personName}</Table.Td>
                  <Table.Td>{a.personDocument ?? '—'}</Table.Td>
                  <Table.Td>
                    <Text size="xs" c="dimmed">
                      {fmtDateTime(a.signedAt)}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    {a.accepted ? (
                      <Badge size="xs" variant="light" color="green">
                        Sem reservas
                      </Badge>
                    ) : (
                      <>
                        <Badge size="xs" variant="light" color="orange">
                          Com reservas
                        </Badge>
                        <Text size="xs" c="dimmed">
                          {a.note}
                        </Text>
                      </>
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Painel>
    </>
  );
}
