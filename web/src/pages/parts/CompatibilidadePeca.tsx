import {
  ActionIcon,
  Badge,
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPlus, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, ApiError } from '../../api/client';
import { SeccaoForm } from '../../components/erp';

/**
 * Onde é que esta peça se monta, e o que a substitui.
 *
 * <p>Duas listas, e nenhuma delas é decorativa. A de cima é o que transforma um
 * inventário em catálogo: enquanto ninguém disser que o filtro é de camião, o
 * sistema não pode avisar quando ele aparece na lista de uma pick-up. A de
 * baixo é o que permite aceitar o que o fornecedor tem: em Angola o original
 * não chega, chega o equivalente, e quem está ao balcão precisa de saber que
 * são o mesmo.
 *
 * <p>A regra das aplicações explica-se numa linha e está escrita no ecrã: cada
 * campo que se deixa vazio significa «qualquer». É de propósito que o caminho
 * mais curto — só a família — seja também o mais útil, porque é o que cobre as
 * peças comuns a uma classe inteira de viaturas.
 */

const FAMILIAS = [
  { value: 'TRUCK_HEAVY', label: 'Camiões pesados' },
  { value: 'BUS', label: 'Autocarros' },
  { value: 'PICKUP', label: 'Pick-ups' },
  { value: 'SUV', label: 'SUV / jipes' },
  { value: 'SEDAN', label: 'Ligeiros (sedan)' },
  { value: 'LIGHT_VEHICLE', label: 'Ligeiros' },
  { value: 'VAN', label: 'Carrinhas' },
  { value: 'RETROESCAVADORA', label: 'Retroescavadoras' },
  { value: 'FORKLIFT', label: 'Empilhadoras' },
  { value: 'IMPLEMENT', label: 'Alfaias e implementos' },
  { value: 'GENERATOR', label: 'Geradores' },
];

const TIPOS_REF = [
  { value: 'OEM', label: 'Original (OEM)' },
  { value: 'EQUIVALENT', label: 'Equivalente' },
  { value: 'SUPERSEDES', label: 'Substitui' },
  { value: 'SUPERSEDED_BY', label: 'Substituída por' },
];

interface Aplicacao {
  id: string;
  family?: string | null;
  make?: string | null;
  model?: string | null;
  engine?: string | null;
  yearFrom?: number | null;
  yearTo?: number | null;
  note?: string | null;
  resumo: string;
}

interface Referencia {
  id: string;
  number: string;
  brand?: string | null;
  kind: string;
  kindLabel: string;
  note?: string | null;
}

const CORES_REF: Record<string, string> = {
  OEM: 'blue',
  EQUIVALENT: 'green',
  SUPERSEDES: 'teal',
  SUPERSEDED_BY: 'orange',
};

export function CompatibilidadePeca({
  partId,
  partName,
  opened,
  onClose,
}: {
  partId: string;
  partName: string;
  opened: boolean;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const [ap, setAp] = useState({
    family: '',
    make: '',
    model: '',
    engine: '',
    yearFrom: '' as number | string,
    yearTo: '' as number | string,
  });
  const [ref, setRef] = useState({ number: '', brand: '', kind: 'EQUIVALENT' });

  const { data: aplicacoes } = useQuery({
    queryKey: ['parts', partId, 'applications'],
    queryFn: () => api<Aplicacao[]>(`/parts/${partId}/applications`),
    enabled: opened,
  });

  const { data: referencias } = useQuery({
    queryKey: ['parts', partId, 'cross-refs'],
    queryFn: () => api<Referencia[]>(`/parts/${partId}/cross-refs`),
    enabled: opened,
  });

  const recarregar = () => {
    void qc.invalidateQueries({ queryKey: ['parts', partId] });
    void qc.invalidateQueries({ queryKey: ['consumiveis'] });
  };

  const falhou = (e: unknown) =>
    notifications.show({
      color: 'red',
      message: e instanceof ApiError ? e.message : 'Não foi possível guardar.',
    });

  const criarAplicacao = useMutation({
    mutationFn: () =>
      api(`/parts/${partId}/applications`, {
        method: 'POST',
        body: {
          family: ap.family || null,
          make: ap.make.trim() || null,
          model: ap.model.trim() || null,
          engine: ap.engine.trim() || null,
          yearFrom: ap.yearFrom === '' ? null : Number(ap.yearFrom),
          yearTo: ap.yearTo === '' ? null : Number(ap.yearTo),
        },
      }),
    onSuccess: () => {
      setAp({ family: '', make: '', model: '', engine: '', yearFrom: '', yearTo: '' });
      recarregar();
    },
    onError: falhou,
  });

  const apagarAplicacao = useMutation({
    mutationFn: (id: string) => api(`/part-applications/${id}`, { method: 'DELETE' }),
    onSuccess: recarregar,
    onError: falhou,
  });

  const criarReferencia = useMutation({
    mutationFn: () =>
      api(`/parts/${partId}/cross-refs`, {
        method: 'POST',
        body: {
          number: ref.number.trim(),
          brand: ref.brand.trim() || null,
          kind: ref.kind,
        },
      }),
    onSuccess: () => {
      setRef({ number: '', brand: '', kind: 'EQUIVALENT' });
      recarregar();
    },
    onError: falhou,
  });

  const apagarReferencia = useMutation({
    mutationFn: (id: string) => api(`/part-cross-refs/${id}`, { method: 'DELETE' }),
    onSuccess: recarregar,
    onError: falhou,
  });

  return (
    <Modal opened={opened} onClose={onClose} title={`Compatibilidade · ${partName}`} size="xl">
      <Stack gap="lg">
        <SeccaoForm
          titulo="Onde se monta"
          descricao="Cada campo que ficar vazio significa «qualquer». Só a família serve a classe toda; marca, modelo, motor e anos servem exactamente uma viatura."
        >
          <Stack gap="xs">
            {(aplicacoes ?? []).length === 0 ? (
              <Text size="sm" c="dimmed">
                Ainda não há nenhuma. Enquanto isto estiver vazio, o sistema não pode avisar que
                esta peça foi pedida para o equipamento errado.
              </Text>
            ) : (
              <Table withTableBorder>
                <Table.Tbody>
                  {aplicacoes?.map((a) => (
                    <Table.Tr key={a.id}>
                      <Table.Td>
                        <Text size="sm">{a.resumo}</Text>
                        {a.note && (
                          <Text size="xs" c="dimmed">
                            {a.note}
                          </Text>
                        )}
                      </Table.Td>
                      <Table.Td w={44}>
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          aria-label="Apagar aplicação"
                          loading={apagarAplicacao.isPending}
                          onClick={() => apagarAplicacao.mutate(a.id)}
                        >
                          <IconTrash size={16} />
                        </ActionIcon>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            )}

            <Group grow align="flex-end">
              <Select
                label="Família"
                placeholder="Qualquer"
                data={FAMILIAS}
                clearable
                searchable
                value={ap.family || null}
                onChange={(v) => setAp((f) => ({ ...f, family: v ?? '' }))}
              />
              <TextInput
                label="Marca"
                placeholder="Qualquer"
                value={ap.make}
                onChange={(e) => {
                  const v = e.currentTarget.value;
                  setAp((f) => ({ ...f, make: v }));
                }}
              />
              <TextInput
                label="Modelo"
                placeholder="Qualquer"
                value={ap.model}
                onChange={(e) => {
                  const v = e.currentTarget.value;
                  setAp((f) => ({ ...f, model: v }));
                }}
              />
            </Group>
            <Group grow align="flex-end">
              <TextInput
                label="Motor"
                placeholder="Qualquer"
                value={ap.engine}
                onChange={(e) => {
                  const v = e.currentTarget.value;
                  setAp((f) => ({ ...f, engine: v }));
                }}
              />
              <NumberInput
                label="De (ano)"
                placeholder="Qualquer"
                min={1950}
                max={2100}
                value={ap.yearFrom}
                onChange={(v) => setAp((f) => ({ ...f, yearFrom: v }))}
              />
              <NumberInput
                label="Até (ano)"
                placeholder="Qualquer"
                min={1950}
                max={2100}
                value={ap.yearTo}
                onChange={(v) => setAp((f) => ({ ...f, yearTo: v }))}
              />
              <Button
                leftSection={<IconPlus size={15} />}
                loading={criarAplicacao.isPending}
                onClick={() => criarAplicacao.mutate()}
              >
                Acrescentar
              </Button>
            </Group>
          </Stack>
        </SeccaoForm>

        <SeccaoForm
          titulo="Referências equivalentes"
          descricao="O número do original e os das outras marcas que servem igual. É o que permite encontrar o artigo pelo número impresso na caixa — e aceitar o que o fornecedor tem."
        >
          <Stack gap="xs">
            {(referencias ?? []).length === 0 ? (
              <Text size="sm" c="dimmed">
                Nenhuma registada.
              </Text>
            ) : (
              <Table withTableBorder>
                <Table.Tbody>
                  {referencias?.map((r) => (
                    <Table.Tr key={r.id}>
                      <Table.Td>
                        <Group gap={8} wrap="nowrap">
                          <Text size="sm" fw={600} ff="monospace">
                            {r.number}
                          </Text>
                          {r.brand && <Text size="sm">{r.brand}</Text>}
                          <Badge size="xs" variant="light" color={CORES_REF[r.kind] ?? 'gray'}>
                            {r.kindLabel}
                          </Badge>
                        </Group>
                      </Table.Td>
                      <Table.Td w={44}>
                        <ActionIcon
                          variant="subtle"
                          color="red"
                          aria-label="Apagar referência"
                          loading={apagarReferencia.isPending}
                          onClick={() => apagarReferencia.mutate(r.id)}
                        >
                          <IconTrash size={16} />
                        </ActionIcon>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            )}

            <Group grow align="flex-end">
              <TextInput
                label="Referência"
                placeholder="WK 842/2"
                value={ref.number}
                onChange={(e) => {
                  const v = e.currentTarget.value;
                  setRef((f) => ({ ...f, number: v }));
                }}
              />
              <TextInput
                label="Marca"
                placeholder="Mann, Fleetguard…"
                value={ref.brand}
                onChange={(e) => {
                  const v = e.currentTarget.value;
                  setRef((f) => ({ ...f, brand: v }));
                }}
              />
              <Select
                label="Relação"
                data={TIPOS_REF}
                value={ref.kind}
                onChange={(v) => setRef((f) => ({ ...f, kind: v ?? 'EQUIVALENT' }))}
              />
              <Tooltip label="Indique a referência" disabled={!!ref.number.trim()} withArrow>
                <Button
                  leftSection={<IconPlus size={15} />}
                  disabled={!ref.number.trim()}
                  loading={criarReferencia.isPending}
                  onClick={() => criarReferencia.mutate()}
                >
                  Acrescentar
                </Button>
              </Tooltip>
            </Group>
          </Stack>
        </SeccaoForm>
      </Stack>
    </Modal>
  );
}
