import { Badge, Button, Group, Modal, NumberInput, Select, Stack, Text, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { notifications } from '@mantine/notifications';
import { IconClipboardPlus, IconPlus } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useDebouncedValue } from '@mantine/hooks';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { IconeAtivo } from '../components/Equipamento';
import { BarraEstado, BotaoBarra, Painel } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { CampoProcura, filtrar } from '../components/Procura';
import { NovaOrdemForm } from './workorders/NovaOrdemForm';
import type { AssetSummary, AssetTypeView, LocationView, Paged } from '../api/types';
import { CRITICALITY } from '../theme';
import { fmtNumber, statusLabel } from '../lib/format';
import { fraseRestante } from './assets/LimiteManutencao';

const POR_PAGINA = 50;

export function AssetsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [creating, setCreating] = useState(false);
  const [ordemPara, setOrdemPara] = useState<AssetSummary | null>(null);

  // Procura e paginação no servidor: uma frota de 500 viaturas não se carrega toda.
  const [pagina, setPagina] = useState(1);
  const [procuraServidor] = useDebouncedValue(search.trim(), 350);
  useEffect(() => setPagina(1), [procuraServidor]);
  const { data, isLoading } = useQuery({
    queryKey: ['assets', 'lista', procuraServidor, pagina],
    queryFn: () =>
      api<Paged<AssetSummary>>(
        `/assets?size=${POR_PAGINA}&page=${pagina - 1}${procuraServidor ? `&q=${encodeURIComponent(procuraServidor)}` : ''}`,
      ),
    placeholderData: (anterior) => anterior,
  });

  const { data: types } = useQuery({
    queryKey: ['asset-types'],
    queryFn: () => api<AssetTypeView[]>('/asset-types'),
  });

  const { data: locations } = useQuery({
    queryKey: ['locations'],
    queryFn: () => api<LocationView[]>('/locations'),
  });

  const form = useForm({
    initialValues: {
      tag: '',
      name: '',
      assetTypeId: '',
      locationId: '',
      manufacturer: '',
      model: '',
      serialNumber: '',
      modelYear: '' as number | '',
      initialMeterValue: '' as number | '',
    },
    validate: {
      tag: (v) => (v.trim() ? null : 'Indique a etiqueta (ex.: RE-001).'),
      name: (v) => (v.trim() ? null : 'Indique o nome do ativo.'),
      assetTypeId: (v) => (v ? null : 'Escolha o tipo de ativo.'),
    },
  });

  const create = useMutation({
    mutationFn: (values: typeof form.values) =>
      api<AssetSummary>('/assets', {
        method: 'POST',
        body: {
          ...values,
          locationId: values.locationId || undefined,
          modelYear: values.modelYear === '' ? undefined : values.modelYear,
          initialMeterValue:
            values.initialMeterValue === '' ? undefined : values.initialMeterValue,
        },
      }),
    onSuccess: () => {
      notifications.show({ message: 'Ativo criado.', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      setCreating(false);
      form.reset();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  // Procura que ignora acentos e aceita palavras por qualquer ordem:
  // O servidor já procurou por etiqueta, nome, matrícula e modelo; aqui só se
  // afina pelo que a página tem (tipo, filial), para «volvo luanda» continuar a resultar.
  const todos = useMemo(
    () =>
      filtrar(data?.content ?? [], search, (a) => [
        a.tag,
        a.name,
        a.assetTypeName,
        a.locationName,
        a.categoryLabel,
      ]),
    [data, search],
  );

  const familias = useMemo(() => new Set(todos.map((a) => a.category ?? 'OTHER')).size, [todos]);
  const rows = todos;

  return (
    <>
      <NovaOrdemForm
        aberto={ordemPara != null}
        fechar={() => setOrdemPara(null)}
        assetIdFixo={ordemPara?.id}
      />

      <Painel
        titulo="Parque de equipamento"
        semPadding
        acoes={
          <>
            <BotaoBarra destaque icone={<IconPlus size={13} />} onClick={() => setCreating(true)}>
              Novo ativo
            </BotaoBarra>
            <CampoProcura
              valor={search}
              aoMudar={setSearch}
              placeholder="Etiqueta, nome, tipo ou local…"
              largura={260}
            />
          </>
        }
        rodape={
          <BarraEstado
            itens={[
              { rotulo: 'Equipamentos', valor: data?.totalElements ?? todos.length },
              { rotulo: 'Famílias', valor: familias },
              {
                rotulo: 'Parados',
                valor: todos.filter((a) => a.status === 'DOWN').length,
              },
            ]}
          />
        }
      >
        <Grelha
          id="ativos"
          linhas={rows}
          chave={(a) => a.id}
          carregando={isLoading}
          larguraMinima={900}
          aoAbrir={(a) => navigate(`/ativos/${a.id}`)}
          grupo={(a) => a.categoryLabel ?? 'Outros'}
          ordemGrupo={(a) => a.categoryOrder ?? 99}
          vazio={search ? 'Nenhum ativo corresponde à procura.' : 'Ainda não há ativos. Crie o primeiro.'}
          porPagina={POR_PAGINA}
          servidor={{
            pagina,
            totalPaginas: data?.totalPages ?? 1,
            total: data?.totalElements ?? 0,
            aoMudarPagina: setPagina,
          }}
          colunas={[
            {
              id: 'silhueta',
              titulo: '',
              largura: 46,
              render: (a) => (
                /* A silhueta diz de relance se é uma retroescavadora ou um
                   gerador. Um ícone de linha genérico não diz. */
                <div
                  style={{
                    width: 34,
                    height: 26,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: 'var(--erp-grafite)',
                    color: 'var(--erp-dourado)',
                  }}
                >
                  <IconeAtivo tipo={a.assetTypeName} size={20} />
                </div>
              ),
            },
            {
              id: 'tag',
              titulo: 'Etiqueta',
              largura: 120,
              fixa: true,
              valor: (a) => a.tag,
              render: (a) => (
                <Text component={Link} to={`/ativos/${a.id}`} fw={700} size="sm" c="var(--erp-dourado-escuro)">
                  {a.tag}
                </Text>
              ),
            },
            { id: 'nome', titulo: 'Nome', valor: (a) => a.name },
            { id: 'tipo', titulo: 'Tipo', valor: (a) => a.assetTypeName ?? null },
            { id: 'local', titulo: 'Local', valor: (a) => a.locationName ?? null },
            {
              id: 'medidor',
              titulo: 'Medidor',
              largura: 110,
              alinhar: 'right',
              valor: (a) => a.meters?.[0]?.currentValue ?? null,
              render: (a) =>
                a.meters?.length
                  ? `${fmtNumber(a.meters[0].currentValue)} ${a.meters[0].kind === 'HOURMETER' ? 'h' : 'km'}`
                  : '—',
            },
            {
              id: 'manutencao',
              titulo: 'Próx. manutenção',
              largura: 170,
              valor: (a) =>
                a.nextMaintenance
                  ? (a.nextMaintenance.status === 'OVERDUE' ? -1 : a.nextMaintenance.status === 'DUE_SOON' ? 0 : 1)
                  : null,
              render: (a) => {
                const p = a.nextMaintenance;
                if (!p) {
                  return (
                    <Text size="xs" c="dimmed">
                      sem limite
                    </Text>
                  );
                }
                const cor = p.status === 'OVERDUE' ? 'red' : p.status === 'DUE_SOON' ? 'orange' : undefined;
                return (
                  <div>
                    <Text size="sm" fw={600} c={cor}>
                      {fraseRestante(p)}
                    </Text>
                    <Text size="xs" c="dimmed" lineClamp={1}>
                      {p.title}
                    </Text>
                  </div>
                );
              },
            },
            {
              id: 'criticidade',
              titulo: 'Criticidade',
              largura: 110,
              valor: (a) => a.criticality ?? null,
              render: (a) =>
                a.criticality ? (
                  <Badge color={CRITICALITY[a.criticality as keyof typeof CRITICALITY]?.color} variant="light">
                    {CRITICALITY[a.criticality as keyof typeof CRITICALITY]?.label}
                  </Badge>
                ) : (
                  '—'
                ),
            },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 110,
              valor: (a) => statusLabel[a.status] ?? a.status,
              render: (a) => (
                <Badge variant="light" color={a.status === 'DOWN' ? 'red' : 'gray'}>
                  {statusLabel[a.status] ?? a.status}
                </Badge>
              ),
            },
            { id: 'fotos', titulo: 'Fotos', largura: 70, alinhar: 'right', escondida: true, valor: (a) => a.photoCount ?? 0 },
            {
              id: 'acoes',
              titulo: '',
              largura: 150,
              render: (a) => (
                /* Abrir a ordem daqui poupa quatro ecrãs: é neste momento,
                   a olhar para a lista, que se decide mandar reparar. */
                <Group gap={4} wrap="nowrap" justify="flex-end">
                  <Button
                    size="compact-xs"
                    variant="light"
                    leftSection={<IconClipboardPlus size={13} />}
                    onClick={() => setOrdemPara(a)}
                  >
                    Ordem
                  </Button>
                  <Button size="compact-xs" variant="subtle" component={Link} to={`/ativos/${a.id}`}>
                    Abrir
                  </Button>
                </Group>
              ),
            },
          ]}
        />
      </Painel>

      <Modal opened={creating} onClose={() => setCreating(false)} title="Novo ativo" size="lg">
        <form onSubmit={form.onSubmit((v) => create.mutate(v))}>
          <Stack gap="sm">
            <Group grow>
              <TextInput
                label="Etiqueta"
                placeholder="RE-001"
                description="Código interno, único na empresa."
                {...form.getInputProps('tag')}
              />
              <TextInput
                label="Nome"
                placeholder="Retroescavadora Volvo BL71B"
                {...form.getInputProps('name')}
              />
            </Group>

            <Group grow>
              <Select
                label="Tipo de ativo"
                placeholder="Escolha"
                data={(types ?? []).map((t) => ({ value: t.id, label: t.name }))}
                searchable
                {...form.getInputProps('assetTypeId')}
              />
              <Select
                label="Local"
                placeholder="Opcional"
                data={(locations ?? []).map((l) => ({ value: l.id, label: l.name }))}
                searchable
                clearable
                {...form.getInputProps('locationId')}
              />
            </Group>

            <Group grow>
              <TextInput label="Fabricante" {...form.getInputProps('manufacturer')} />
              <TextInput label="Modelo" {...form.getInputProps('model')} />
            </Group>

            <Group grow>
              <TextInput label="Nº de série" {...form.getInputProps('serialNumber')} />
              <NumberInput label="Ano" min={1950} max={2100} {...form.getInputProps('modelYear')} />
              <NumberInput
                label="Leitura inicial"
                description="Horímetro ou hodómetro"
                min={0}
                {...form.getInputProps('initialMeterValue')}
              />
            </Group>

            <Group justify="flex-end" mt="sm">
              <Button variant="default" onClick={() => setCreating(false)}>
                Cancelar
              </Button>
              <Button type="submit" loading={create.isPending}>
                Criar ativo
              </Button>
            </Group>
          </Stack>
        </form>
      </Modal>
    </>
  );
}
