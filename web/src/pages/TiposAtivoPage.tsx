/**
 * Tipos de equipamento — as famílias que a empresa gere.
 *
 * <p>Não existia ecrã nenhum: os tipos só se criavam por chamadas à API, e o
 * campo da família nunca chegava a ser preenchido. Resultado: tudo nascia como
 * «Máquinas», e um camião aparecia agrupado com uma retroescavadora.
 *
 * <p>A família decide como o equipamento é tratado em todo o sistema — o
 * agrupamento da lista, o desenho dos pontos de serviço, o medidor por
 * omissão. Por isso é a primeira coisa que se escolhe, e não uma opção
 * escondida.
 */
import { Alert, Badge, Button, Grid, Group, Modal, Select, Stack, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconPlus, IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import { BarraEstado, BotaoBarra, Painel, Ponto } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { IconeAtivo } from '../components/Equipamento';
import { CampoProcura, filtrar } from '../components/Procura';

interface Sistema {
  id: string;
  code: string;
  name: string;
}

interface TipoAtivo {
  /** Versão do registo, devolvida ao gravar para apanhar edições concorrentes. */
  version?: number | null;
  id: string;
  name: string;
  category: string;
  categoryLabel?: string | null;
  primaryMeter: string;
  secondaryMeter?: string | null;
  assetCount: number;
  systems: Sistema[];
}

const FAMILIAS = [
  { value: 'VEHICLE', label: 'Viaturas', nota: 'Camiões, ligeiros e reboques' },
  { value: 'MACHINE', label: 'Máquinas', nota: 'Movimentação de terras e obra' },
  { value: 'GENERATOR', label: 'Geradores', nota: 'Grupos electrogéneos e energia' },
  { value: 'IMPLEMENT', label: 'Alfaias e implementos', nota: 'Equipamento acoplável' },
  { value: 'OTHER', label: 'Outros', nota: 'O que não cabe nas famílias acima' },
];

const COR_FAMILIA: Record<string, string> = {
  VEHICLE: '#2563eb',
  MACHINE: '#ea580c',
  GENERATOR: '#7c3aed',
  IMPLEMENT: '#0891b2',
  OTHER: '#6b7280',
};

export function TiposAtivoPage() {
  const queryClient = useQueryClient();
  const [procura, setProcura] = useState('');
  const [novo, setNovo] = useState(false);
  const [editar, setEditar] = useState<TipoAtivo | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['asset-types'],
    queryFn: () => api<TipoAtivo[]>('/asset-types'),
  });

  const remover = useMutation({
    mutationFn: (id: string) => api(`/asset-types/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      notifications.show({ title: 'Tipo removido', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['asset-types'] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível remover', message: e.message, color: 'red' }),
  });

  const todos = data ?? [];
  const linhas = useMemo(
    () => filtrar(todos, procura, (t) => [t.name, t.categoryLabel, t.primaryMeter]),
    [todos, procura],
  );

  // Um tipo sem ativos foi criado e esquecido; um tipo em «Outros» quase sempre
  // é um que ninguém classificou.
  const semClassificar = todos.filter((t) => t.category === 'OTHER').length;

  return (
    <Stack gap="sm">
      <FormTipo
        aberto={novo || editar != null}
        tipo={editar}
        fechar={() => {
          setNovo(false);
          setEditar(null);
        }}
      />

      <Painel
        titulo="Tipos de equipamento"
        semPadding
        acoes={
          <>
            <BotaoBarra destaque icone={<IconPlus size={13} />} onClick={() => setNovo(true)}>
              Novo tipo
            </BotaoBarra>
            <CampoProcura
              valor={procura}
              aoMudar={setProcura}
              placeholder="Procurar tipo ou família…"
            />
          </>
        }
        rodape={
          <BarraEstado
            itens={[
              { rotulo: 'Tipos', valor: linhas.length },
              { rotulo: 'Equipamentos', valor: todos.reduce((s, t) => s + t.assetCount, 0) },
              { rotulo: 'Por classificar', valor: semClassificar },
            ]}
          />
        }
      >
        {semClassificar > 0 && (
          <Alert color="yellow" variant="light" p="xs" m="sm" icon={<IconAlertTriangle size={15} />}>
            <Text size="sm">
              {semClassificar} tipo(s) em «Outros». A família decide como o equipamento é
              agrupado e que pontos de serviço lhe aparecem — vale a pena classificá-los.
            </Text>
          </Alert>
        )}

        <Grelha
          id="tipos-equipamento"
          linhas={linhas}
          chave={(t) => t.id}
          carregando={isLoading}
          aoAbrir={(t) => setEditar(t)}
          vazio={
            procura ? 'Nada corresponde a essa procura.' : 'Nenhum tipo. Crie o primeiro antes de registar equipamento.'
          }
          colunas={[
            {
              id: 'silhueta',
              titulo: '',
              largura: 46,
              render: (t) => (
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
                  <IconeAtivo tipo={t.name} size={20} />
                </div>
              ),
            },
            { id: 'nome', titulo: 'Tipo', fixa: true, valor: (t) => t.name, render: (t) => <b>{t.name}</b> },
            {
              id: 'familia',
              titulo: 'Família',
              largura: 190,
              valor: (t) => t.categoryLabel ?? t.category,
              render: (t) => (
                <>
                  <Ponto cor={COR_FAMILIA[t.category] ?? '#6b7280'} />
                  {t.categoryLabel ?? t.category}
                </>
              ),
            },
            {
              id: 'medidor',
              titulo: 'Medidor',
              largura: 120,
              valor: (t) => (t.primaryMeter === 'HOURMETER' ? 'Horímetro' : 'Hodómetro'),
            },
            {
              id: 'sistemas',
              titulo: 'Sistemas',
              valor: (t) => t.systems.length,
              render: (t) =>
                t.systems.length === 0 ? (
                  <Text size="xs" c="dimmed">
                    sem sistemas
                  </Text>
                ) : (
                  <Group gap={3} wrap="wrap">
                    {t.systems.slice(0, 5).map((s) => (
                      <Badge key={s.id} size="xs" variant="light" color="gray">
                        {s.name}
                      </Badge>
                    ))}
                    {t.systems.length > 5 && (
                      <Text size="xs" c="dimmed">
                        +{t.systems.length - 5}
                      </Text>
                    )}
                  </Group>
                ),
            },
            {
              id: 'equipamentos',
              titulo: 'Equipamentos',
              largura: 110,
              alinhar: 'right',
              valor: (t) => t.assetCount,
              render: (t) => <b>{t.assetCount}</b>,
            },
            {
              id: 'acoes',
              titulo: '',
              largura: 130,
              render: (t) => (
                <Group gap={4} wrap="nowrap">
                  <Button size="compact-xs" variant="subtle" onClick={() => setEditar(t)}>
                    Editar
                  </Button>
                  <Button
                    size="compact-xs"
                    variant="subtle"
                    color="red"
                    disabled={t.assetCount > 0}
                    title={t.assetCount > 0 ? 'Tem equipamento associado — mova-o primeiro' : 'Remover'}
                    onClick={() => remover.mutate(t.id)}
                  >
                    <IconTrash size={12} />
                  </Button>
                </Group>
              ),
            },
          ]}
        />
      </Painel>
    </Stack>
  );
}

// ==== Formulário ===========================================================

function FormTipo({
  aberto,
  tipo,
  fechar,
}: {
  aberto: boolean;
  tipo: TipoAtivo | null;
  fechar: () => void;
}) {
  const queryClient = useQueryClient();
  const editando = tipo != null;

  const [name, setName] = useState('');
  const [category, setCategory] = useState<string | null>('VEHICLE');
  const [primaryMeter, setPrimaryMeter] = useState<string | null>('ODOMETER');
  const [secondaryMeter, setSecondaryMeter] = useState<string | null>(null);
  const [carregado, setCarregado] = useState<string | null>(null);

  // Carrega uma vez por tipo: um efeito a cada render apagava o que se escreve.
  if (aberto && tipo && carregado !== tipo.id) {
    setCarregado(tipo.id);
    setName(tipo.name);
    setCategory(tipo.category);
    setPrimaryMeter(tipo.primaryMeter);
    setSecondaryMeter(tipo.secondaryMeter ?? null);
  }
  if (aberto && !tipo && carregado !== null) {
    setCarregado(null);
    setName('');
    setCategory('VEHICLE');
    setPrimaryMeter('ODOMETER');
    setSecondaryMeter(null);
  }

  const gravar = useMutation({
    mutationFn: () => {
      const body = { name: name.trim(), category, primaryMeter, secondaryMeter };
      return editando
        ? api(`/asset-types/${tipo!.id}`, { method: 'PATCH', body: { ...body, version: tipo!.version ?? null } })
        : api('/asset-types', { method: 'POST', body: { ...body, useStandardSystems: true } });
    },
    onSuccess: () => {
      notifications.show({ title: 'Tipo guardado', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['asset-types'] });
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  const familia = FAMILIAS.find((f) => f.value === category);

  return (
    <Modal
      opened={aberto}
      onClose={fechar}
      title={editando ? `Tipo ${tipo!.name}` : 'Novo tipo de equipamento'}
      size="lg"
    >
      <Grid gutter="xs">
        <Grid.Col span={{ base: 12, sm: 7 }}>
          <TextInput
            label="Nome do tipo"
            required
            placeholder="Camião basculante"
            value={name}
            onChange={(e) => setName(e.currentTarget.value)}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 5 }}>
          <Select
            label="Família"
            required
            description={familia?.nota}
            data={FAMILIAS.map((f) => ({ value: f.value, label: f.label }))}
            value={category}
            onChange={(v) => {
              setCategory(v);
              // O medidor que faz sentido muda com a família: uma viatura
              // conta-se em quilómetros, uma máquina em horas.
              if (v === 'VEHICLE') setPrimaryMeter('ODOMETER');
              else if (v === 'MACHINE' || v === 'GENERATOR') setPrimaryMeter('HOURMETER');
            }}
            allowDeselect={false}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <Select
            label="Medidor principal"
            description="É por ele que os planos por intervalo vencem."
            data={[
              { value: 'ODOMETER', label: 'Hodómetro (km)' },
              { value: 'HOURMETER', label: 'Horímetro (h)' },
            ]}
            value={primaryMeter}
            onChange={setPrimaryMeter}
            allowDeselect={false}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 6 }}>
          <Select
            label="Medidor secundário"
            placeholder="Nenhum"
            description="Um camião com tomada de força tem os dois."
            clearable
            data={[
              { value: 'ODOMETER', label: 'Hodómetro (km)' },
              { value: 'HOURMETER', label: 'Horímetro (h)' },
            ]}
            value={secondaryMeter}
            onChange={setSecondaryMeter}
          />
        </Grid.Col>
      </Grid>

      <Alert color="gray" variant="light" p="xs" mt="sm">
        <Text size="xs">
          A família decide como o equipamento é agrupado na lista e que desenho de pontos de
          serviço lhe aparece.
          {!editando && ' Os oito sistemas padrão (motor, hidráulico, travagem…) são criados '
            + 'automaticamente e podem ser editados depois.'}
        </Text>
      </Alert>

      <Group justify="flex-end" gap="xs" mt="md">
        <Button variant="default" onClick={fechar}>
          Cancelar
        </Button>
        <Button onClick={() => gravar.mutate()} loading={gravar.isPending} disabled={!name.trim()}>
          {editando ? 'Guardar' : 'Criar'}
        </Button>
      </Group>
    </Modal>
  );
}
