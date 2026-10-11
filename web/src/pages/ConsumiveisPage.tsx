import {
  Badge,
  Box,
  Button,
  Group,
  Progress,
  SegmentedControl,
  Stack,
  Table,
  Tabs,
  Text,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconDroplet,
  IconEye,
  IconFilter,
  IconLink,
  IconShoppingCart,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Painel } from '../components/erp';
import { fmtDate, fmtMoney, fmtNumber } from '../lib/format';

/**
 * Pneus, óleos e filtros — o controlo que decide se uma frota dá lucro.
 *
 * <p>Depois do combustível, é aqui que o dinheiro sai: um pneu trocado cedo é
 * borracha deitada fora, trocado tarde é um rebentamento na estrada; um óleo
 * fora de intervalo é um motor aberto a meio da obra; um filtro em falta é uma
 * máquina parada três semanas à espera de uma encomenda.
 *
 * <p>Por isso este ecrã não é uma lista de registos: responde a uma pergunta
 * de cada vez que se abre — <b>o que tenho de trocar no próximo mês, em que
 * equipamento, com que peça, e tenho-a em armazém?</b> As contas vêm do
 * servidor (taxa de desgaste medida no sulco, quilómetros convertidos em datas
 * pela utilização real, prazo de entrega descontado da data em que a peça é
 * precisa); aqui mostra-se o resultado e o <i>porquê</i> ao lado, sempre.
 *
 * <p>O que o sistema não sabe aparece como «sem dados» e nunca como «em
 * ordem». Um painel que chama em ordem a um pneu que ninguém mediu faz mais
 * estragos do que painel nenhum.
 */

type Estado = 'CRITICO' | 'AVISO' | 'OK' | 'SEM_DADOS';

interface PecaLinha {
  partId?: string | null;
  nome: string;
  partNumber?: string | null;
  especificacao?: string | null;
  categoria?: string | null;
  quantidade?: number | null;
  unidade?: string | null;
  stock: number;
  noCatalogo: boolean;
  suficiente: boolean;
  prazoDias?: number | null;
}

interface PneuLinha {
  tyreId: string;
  assetId: string;
  assetTag?: string | null;
  assetName?: string | null;
  position?: string | null;
  brand?: string | null;
  model?: string | null;
  size?: string | null;
  serialNumber?: string | null;
  treadMm?: number | null;
  minTreadMm?: number | null;
  pressure?: number | null;
  targetPressure?: number | null;
  pressaoPct?: number | null;
  kmRodados?: number | null;
  desgastePor1000Km?: number | null;
  kmRestantes?: number | null;
  diasRestantes?: number | null;
  dataPrevista?: string | null;
  vidaUsadaPct?: number | null;
  custoPorKm?: number | null;
  medidoHaDias?: number | null;
  base?: string | null;
  estado: Estado;
  motivo?: string | null;
  acao?: string | null;
}

interface ServicoLinha {
  taskId: string;
  assetId: string;
  assetTag?: string | null;
  assetName?: string | null;
  titulo: string;
  sistema?: string | null;
  tipo?: string | null;
  medidor?: string | null;
  unidadeMedidor?: string | null;
  ultimoMedidor?: number | null;
  ultimaData?: string | null;
  proximoMedidor?: number | null;
  proximaData?: string | null;
  restanteMedidor?: number | null;
  restanteDias?: number | null;
  diasProjetados?: number | null;
  dataPrevista?: string | null;
  vidaUsadaPct?: number | null;
  litros?: number | null;
  consumoAnormal?: string | null;
  pecas: PecaLinha[];
  estado: Estado;
  motivo?: string | null;
}

interface CompraLinha {
  partId?: string | null;
  nome: string;
  partNumber?: string | null;
  especificacao?: string | null;
  categoria?: string | null;
  unidade?: string | null;
  necessario: number;
  stock: number;
  aComprar: number;
  custoEstimado?: number | null;
  moeda?: string | null;
  prazoDias?: number | null;
  precisaEm?: string | null;
  encomendarAte?: string | null;
  urgente: boolean;
  noCatalogo: boolean;
  equipamentos: string[];
}

interface Resumo {
  pneusCriticos: number;
  pneusAviso: number;
  pneusMontados: number;
  pneusSemMedicao: number;
  oleosVencidos: number;
  oleosAVencer: number;
  filtrosVencidos: number;
  filtrosAVencer: number;
  pecasEmFalta: number;
  encomendasUrgentes: number;
  equipamentosSemPlano: number;
  custoEstimado?: number | null;
  moeda?: string | null;
}

interface PainelConsumiveis {
  horizonteDias: number;
  resumo: Resumo;
  pneus: PneuLinha[];
  oleos: ServicoLinha[];
  filtros: ServicoLinha[];
  compras: CompraLinha[];
}

const CORES: Record<Estado, string> = {
  CRITICO: 'red',
  AVISO: 'orange',
  OK: 'green',
  SEM_DADOS: 'gray',
};

const ROTULOS: Record<Estado, string> = {
  CRITICO: 'Urgente',
  AVISO: 'A vencer',
  OK: 'Em ordem',
  SEM_DADOS: 'Sem dados',
};

/** O estado vem sempre com palavra e com o motivo — nunca só com cor. */
function EstadoBadge({ estado, motivo }: { estado: Estado; motivo?: string | null }) {
  const badge = (
    <Badge color={CORES[estado]} variant="light" size="sm" style={{ whiteSpace: 'nowrap' }}>
      {ROTULOS[estado]}
    </Badge>
  );
  return motivo ? (
    <Tooltip label={motivo} multiline w={280} withArrow>
      {badge}
    </Tooltip>
  ) : (
    badge
  );
}

/**
 * Um número grande com o que ele significa debaixo.
 *
 * <p>Seis destes em cima do ecrã dizem em dois segundos se a semana está
 * tranquila. O número sozinho não chega: ao lado vai sempre a frase que diz o
 * que fazer com ele.
 */
function Ficha({
  valor,
  rotulo,
  nota,
  cor,
  icone: Icone,
}: {
  valor: number | string;
  rotulo: string;
  nota?: string;
  cor: string;
  icone: typeof IconDroplet;
}) {
  return (
    <Box
      style={{
        flex: '1 1 150px',
        minWidth: 150,
        padding: '10px 12px',
        border: '1px solid var(--mantine-color-default-border)',
        borderLeft: `3px solid var(--mantine-color-${cor}-6)`,
        borderRadius: 2,
        background: 'var(--mantine-color-body)',
      }}
    >
      <Group gap={6} wrap="nowrap" align="center">
        <Icone size={16} color={`var(--mantine-color-${cor}-6)`} />
        <Text fw={700} style={{ fontSize: 26, lineHeight: 1.1, fontFamily: 'var(--erp-condensada)' }}>
          {valor}
        </Text>
      </Group>
      <Text size="xs" fw={600} style={{ textTransform: 'uppercase', letterSpacing: '0.04em' }}>
        {rotulo}
      </Text>
      {nota && (
        <Text size="xs" c="dimmed" style={{ lineHeight: 1.25 }}>
          {nota}
        </Text>
      )}
    </Box>
  );
}

/** Quanto da vida já foi: a barra diz num relance o que a percentagem diz em número. */
function Vida({ pct }: { pct?: number | null }) {
  if (pct == null) {
    return <Text size="xs" c="dimmed">—</Text>;
  }
  const limitado = Math.min(pct, 100);
  const cor = pct >= 100 ? 'red' : pct >= 85 ? 'orange' : 'green';
  return (
    <Group gap={6} wrap="nowrap">
      <Progress value={limitado} color={cor} size="sm" w={56} />
      <Text size="xs" style={{ minWidth: 34 }}>
        {pct}%
      </Text>
    </Group>
  );
}

/** As peças de um serviço, com o armazém ao lado de cada uma. */
function Pecas({ pecas }: { pecas: PecaLinha[] }) {
  if (pecas.length === 0) {
    return (
      <Text size="xs" c="dimmed">
        Sem peças indicadas no plano
      </Text>
    );
  }
  return (
    <Stack gap={2}>
      {pecas.map((p, i) => (
        <Group key={`${p.partId ?? p.nome}-${i}`} gap={6} wrap="nowrap">
          <Text size="xs">
            {fmtNumber(p.quantidade)} {p.unidade ?? 'un'} · {p.nome}
          </Text>
          {p.especificacao && (
            <Badge size="xs" variant="outline" color="gray">
              {p.especificacao}
            </Badge>
          )}
          {!p.noCatalogo ? (
            <Tooltip label="Esta peça não existe no armazém — não é possível saber se há stock" withArrow>
              <Badge size="xs" color="gray" variant="light">
                fora do armazém
              </Badge>
            </Tooltip>
          ) : (
            <Badge size="xs" color={p.suficiente ? 'green' : 'red'} variant="light">
              {p.suficiente ? `${fmtNumber(p.stock)} em stock` : `só ${fmtNumber(p.stock)} em stock`}
            </Badge>
          )}
        </Group>
      ))}
    </Stack>
  );
}

function LinhaDeServico({ s, unidade }: { s: ServicoLinha; unidade: string }) {
  return (
    <Table.Tr>
      <Table.Td>
        <Text size="sm" fw={600} component={Link} to={`/ativos/${s.assetId}`} style={{ color: 'inherit' }}>
          {s.assetTag}
        </Text>
        <Text size="xs" c="dimmed">
          {s.assetName}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{s.titulo}</Text>
        <Group gap={6}>
          {s.tipo && (
            <Badge size="xs" variant="light" color="blue">
              {s.tipo}
            </Badge>
          )}
          {s.sistema && (
            <Text size="xs" c="dimmed">
              {s.sistema}
            </Text>
          )}
          {s.litros != null && (
            <Text size="xs" c="dimmed">
              {fmtNumber(s.litros)} L
            </Text>
          )}
        </Group>
        {s.consumoAnormal && (
          <Group gap={4} mt={2} wrap="nowrap">
            <IconAlertTriangle size={13} color="var(--mantine-color-orange-6)" />
            <Text size="xs" c="orange.7" style={{ lineHeight: 1.2 }}>
              {s.consumoAnormal}
            </Text>
          </Group>
        )}
      </Table.Td>
      <Table.Td>
        <Text size="xs">
          {s.ultimoMedidor != null ? `${fmtNumber(s.ultimoMedidor)} ${unidade}` : '—'}
        </Text>
        <Text size="xs" c="dimmed">
          {fmtDate(s.ultimaData)}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="xs">
          {s.proximoMedidor != null ? `${fmtNumber(s.proximoMedidor)} ${unidade}` : '—'}
        </Text>
        <Text size="xs" c="dimmed">
          {s.motivo}
        </Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm" fw={600}>
          {s.dataPrevista ? fmtDate(s.dataPrevista) : '—'}
        </Text>
        {s.diasProjetados != null && (
          <Text size="xs" c="dimmed">
            {s.diasProjetados} dias ao ritmo actual
          </Text>
        )}
      </Table.Td>
      <Table.Td>
        <Vida pct={s.vidaUsadaPct} />
      </Table.Td>
      <Table.Td>
        <Pecas pecas={s.pecas} />
      </Table.Td>
      <Table.Td>
        <EstadoBadge estado={s.estado} motivo={s.motivo} />
      </Table.Td>
    </Table.Tr>
  );
}

function TabelaDeServicos({ linhas, vazio }: { linhas: ServicoLinha[]; vazio: string }) {
  if (linhas.length === 0) {
    return (
      <Group gap={8} p="md">
        <IconCircleCheck size={18} color="var(--mantine-color-green-6)" />
        <Text size="sm" c="dimmed">
          {vazio}
        </Text>
      </Group>
    );
  }
  return (
    <Table.ScrollContainer minWidth={1100}>
      <Table highlightOnHover>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Equipamento</Table.Th>
            <Table.Th>Serviço</Table.Th>
            <Table.Th>Última</Table.Th>
            <Table.Th>Próxima</Table.Th>
            <Table.Th>Previsão</Table.Th>
            <Table.Th>Intervalo usado</Table.Th>
            <Table.Th>Peças e armazém</Table.Th>
            <Table.Th>Estado</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {linhas.map((s) => (
            <LinhaDeServico key={`${s.taskId}-${s.tipo}`} s={s} unidade={s.unidadeMedidor ?? 'km'} />
          ))}
        </Table.Tbody>
      </Table>
    </Table.ScrollContainer>
  );
}

export function ConsumiveisPage() {
  const [dias, setDias] = useState('30');
  const { has } = useAuth();
  const custos = has('COSTS_VIEW');
  const qc = useQueryClient();

  const { data } = useQuery({
    queryKey: ['consumiveis', dias],
    queryFn: () => api<PainelConsumiveis>(`/consumables?dias=${dias}`),
  });

  const ligar = useMutation({
    mutationFn: () => api<{ linked: number }>('/consumables/link-parts', { method: 'POST' }),
    onSuccess: (r) => {
      notifications.show({
        color: r.linked > 0 ? 'green' : 'blue',
        message:
          r.linked > 0
            ? `${r.linked} peça(s) dos planos ficaram ligadas ao armazém.`
            : 'Não havia peças por ligar com nome igual ao do armazém.',
      });
      void qc.invalidateQueries({ queryKey: ['consumiveis'] });
    },
  });

  const r = data?.resumo;

  return (
    <Stack gap="lg">
      <Painel
        titulo="Pneus, óleos e filtros"
        acoes={
          <Group gap="xs">
            <SegmentedControl
              size="xs"
              value={dias}
              onChange={setDias}
              data={[
                { value: '7', label: '7 dias' },
                { value: '30', label: '30 dias' },
                { value: '90', label: '90 dias' },
                { value: '365', label: '1 ano' },
              ]}
            />
            <Tooltip
              label="Faz corresponder, pelo nome, as peças escritas nos planos aos artigos do armazém"
              withArrow
              multiline
              w={260}
            >
              <Button
                size="xs"
                variant="default"
                leftSection={<IconLink size={14} />}
                loading={ligar.isPending}
                onClick={() => ligar.mutate()}
              >
                Ligar peças ao armazém
              </Button>
            </Tooltip>
          </Group>
        }
        rodape={
          <Text size="xs" c="dimmed">
            As previsões usam o desgaste medido e a utilização real de cada equipamento. O que não
            tem medições aparece como «sem dados» — e não como «em ordem».
          </Text>
        }
      >
        <Group gap="xs" align="stretch">
          <Ficha
            valor={r?.pneusCriticos ?? 0}
            rotulo="Pneus urgentes"
            nota="Sulco abaixo do mínimo ou pressão perigosa"
            cor={(r?.pneusCriticos ?? 0) > 0 ? 'red' : 'green'}
            icone={IconAlertTriangle}
          />
          <Ficha
            valor={r?.pneusAviso ?? 0}
            rotulo="Pneus a vencer"
            nota={`de ${r?.pneusMontados ?? 0} montados na frota`}
            cor={(r?.pneusAviso ?? 0) > 0 ? 'orange' : 'green'}
            icone={IconEye}
          />
          <Ficha
            valor={r?.oleosVencidos ?? 0}
            rotulo="Óleos vencidos"
            nota={`${r?.oleosAVencer ?? 0} a vencer no período`}
            cor={(r?.oleosVencidos ?? 0) > 0 ? 'red' : 'green'}
            icone={IconDroplet}
          />
          <Ficha
            valor={r?.filtrosVencidos ?? 0}
            rotulo="Filtros vencidos"
            nota={`${r?.filtrosAVencer ?? 0} a vencer no período`}
            cor={(r?.filtrosVencidos ?? 0) > 0 ? 'red' : 'green'}
            icone={IconFilter}
          />
          <Ficha
            valor={r?.pecasEmFalta ?? 0}
            rotulo="Peças a comprar"
            nota={
              custos && r?.custoEstimado != null
                ? `estimativa ${fmtMoney(r.custoEstimado)}`
                : 'para cumprir o que vence'
            }
            cor={(r?.pecasEmFalta ?? 0) > 0 ? 'orange' : 'green'}
            icone={IconShoppingCart}
          />
          <Ficha
            valor={r?.encomendasUrgentes ?? 0}
            rotulo="Encomendar hoje"
            nota="o prazo de entrega já não chega"
            cor={(r?.encomendasUrgentes ?? 0) > 0 ? 'red' : 'green'}
            icone={IconAlertTriangle}
          />
        </Group>

        {((r?.pneusSemMedicao ?? 0) > 0 || (r?.equipamentosSemPlano ?? 0) > 0) && (
          <Group
            gap={8}
            mt="xs"
            p="xs"
            wrap="nowrap"
            style={{
              border: '1px solid var(--mantine-color-yellow-4)',
              background: 'var(--mantine-color-yellow-light)',
              borderRadius: 2,
            }}
          >
            <IconEye size={16} color="var(--mantine-color-yellow-8)" />
            <Text size="xs">
              {(r?.pneusSemMedicao ?? 0) > 0 && (
                <>
                  <b>{r?.pneusSemMedicao} pneu(s) sem nenhuma medição de sulco</b> — sem isso não há
                  previsão de troca nem custo por quilómetro.{' '}
                </>
              )}
              {(r?.equipamentosSemPlano ?? 0) > 0 && (
                <>
                  <b>{r?.equipamentosSemPlano} equipamento(s) sem plano de manutenção</b> — os óleos
                  e os filtros desse equipamento não estão a ser controlados por ninguém.{' '}
                  <Text component={Link} to="/planos" size="xs" td="underline">
                    atribuir um plano
                  </Text>
                </>
              )}
            </Text>
          </Group>
        )}
      </Painel>

      <Painel titulo="O que tem de ser trocado" semPadding>
        <Tabs defaultValue="pneus" keepMounted={false}>
          <Tabs.List>
            <Tabs.Tab value="pneus" leftSection={<IconEye size={14} />}>
              Pneus ({data?.pneus.length ?? 0})
            </Tabs.Tab>
            <Tabs.Tab value="oleos" leftSection={<IconDroplet size={14} />}>
              Óleos ({data?.oleos.length ?? 0})
            </Tabs.Tab>
            <Tabs.Tab value="filtros" leftSection={<IconFilter size={14} />}>
              Filtros ({data?.filtros.length ?? 0})
            </Tabs.Tab>
            <Tabs.Tab value="compras" leftSection={<IconShoppingCart size={14} />}>
              Lista de compras ({data?.compras.length ?? 0})
            </Tabs.Tab>
          </Tabs.List>

          <Tabs.Panel value="pneus">
            {(data?.pneus.length ?? 0) === 0 ? (
              <Group gap={8} p="md">
                <IconCircleCheck size={18} color="var(--mantine-color-green-6)" />
                <Text size="sm" c="dimmed">
                  Nenhum pneu montado registado. Monte os pneus na ficha de cada viatura para passar
                  a saber o custo por quilómetro de cada marca.
                </Text>
              </Group>
            ) : (
              <Table.ScrollContainer minWidth={1180}>
                <Table highlightOnHover>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Equipamento</Table.Th>
                      <Table.Th>Pos.</Table.Th>
                      <Table.Th>Pneu</Table.Th>
                      <Table.Th>Sulco</Table.Th>
                      <Table.Th>Pressão</Table.Th>
                      <Table.Th>Rodou</Table.Th>
                      <Table.Th>Desgaste</Table.Th>
                      <Table.Th>Faltam</Table.Th>
                      <Table.Th>Previsão</Table.Th>
                      <Table.Th>Vida</Table.Th>
                      {custos && <Table.Th>Kz/km</Table.Th>}
                      <Table.Th>Estado</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {data?.pneus.map((t) => (
                      <Table.Tr key={t.tyreId}>
                        <Table.Td>
                          <Text
                            size="sm"
                            fw={600}
                            component={Link}
                            to={`/ativos/${t.assetId}`}
                            style={{ color: 'inherit' }}
                          >
                            {t.assetTag}
                          </Text>
                          <Text size="xs" c="dimmed">
                            {t.assetName}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Badge size="sm" variant="outline" color="gray">
                            {t.position ?? '—'}
                          </Badge>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">
                            {[t.brand, t.model].filter(Boolean).join(' ') || 'Marca não indicada'}
                          </Text>
                          <Text size="xs" c="dimmed">
                            {t.size ?? 'medida não indicada'}
                            {t.serialNumber ? ` · ${t.serialNumber}` : ''}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" fw={600}>
                            {t.treadMm != null ? `${fmtNumber(t.treadMm, 1)} mm` : '—'}
                          </Text>
                          <Text size="xs" c="dimmed">
                            mín. {fmtNumber(t.minTreadMm, 1)} mm
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">
                            {t.pressure != null ? `${fmtNumber(t.pressure, 1)} bar` : '—'}
                          </Text>
                          {t.pressaoPct != null && (
                            <Text size="xs" c={t.pressaoPct < 90 ? 'orange.7' : 'dimmed'}>
                              {t.pressaoPct}% do alvo
                            </Text>
                          )}
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">{t.kmRodados != null ? fmtNumber(t.kmRodados) : '—'}</Text>
                          {t.medidoHaDias != null && (
                            <Text size="xs" c={t.medidoHaDias > 45 ? 'orange.7' : 'dimmed'}>
                              medido há {t.medidoHaDias} d
                            </Text>
                          )}
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">
                            {t.desgastePor1000Km != null
                              ? `${fmtNumber(t.desgastePor1000Km, 2)} mm/1000 km`
                              : '—'}
                          </Text>
                          {t.base && (
                            <Text size="xs" c="dimmed">
                              {t.base}
                            </Text>
                          )}
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" fw={600}>
                            {t.kmRestantes != null ? `${fmtNumber(t.kmRestantes)} km` : '—'}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">{t.dataPrevista ? fmtDate(t.dataPrevista) : '—'}</Text>
                          {t.diasRestantes != null && (
                            <Text size="xs" c="dimmed">
                              {t.diasRestantes} dias
                            </Text>
                          )}
                        </Table.Td>
                        <Table.Td>
                          <Vida pct={t.vidaUsadaPct} />
                        </Table.Td>
                        {custos && (
                          <Table.Td>
                            <Text size="sm">{t.custoPorKm != null ? fmtNumber(t.custoPorKm, 2) : '—'}</Text>
                          </Table.Td>
                        )}
                        <Table.Td>
                          <EstadoBadge estado={t.estado} motivo={t.motivo} />
                          {t.acao && (
                            <Text size="xs" c="dimmed" style={{ lineHeight: 1.2, maxWidth: 190 }}>
                              {t.acao}
                            </Text>
                          )}
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>
            )}
          </Tabs.Panel>

          <Tabs.Panel value="oleos">
            <TabelaDeServicos
              linhas={data?.oleos ?? []}
              vazio={`Nenhuma mudança de óleo vence nos próximos ${dias} dias.`}
            />
          </Tabs.Panel>

          <Tabs.Panel value="filtros">
            <TabelaDeServicos
              linhas={data?.filtros ?? []}
              vazio={`Nenhum filtro vence nos próximos ${dias} dias.`}
            />
          </Tabs.Panel>

          <Tabs.Panel value="compras">
            {(data?.compras.length ?? 0) === 0 ? (
              <Group gap={8} p="md">
                <IconCircleCheck size={18} color="var(--mantine-color-green-6)" />
                <Text size="sm" c="dimmed">
                  Não há nada a comprar para o que vence neste período.
                </Text>
              </Group>
            ) : (
              <Table.ScrollContainer minWidth={1080}>
                <Table highlightOnHover>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Artigo</Table.Th>
                      <Table.Th>Necessário</Table.Th>
                      <Table.Th>Em armazém</Table.Th>
                      <Table.Th>A comprar</Table.Th>
                      {custos && <Table.Th>Estimativa</Table.Th>}
                      <Table.Th>Prazo</Table.Th>
                      <Table.Th>Precisa em</Table.Th>
                      <Table.Th>Encomendar até</Table.Th>
                      <Table.Th>Para</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {data?.compras.map((c, i) => (
                      <Table.Tr key={`${c.partId ?? c.nome}-${i}`}>
                        <Table.Td>
                          <Text size="sm" fw={600}>
                            {c.nome}
                          </Text>
                          <Group gap={6}>
                            {c.partNumber && (
                              <Text size="xs" c="dimmed">
                                ref. {c.partNumber}
                              </Text>
                            )}
                            {c.especificacao && (
                              <Badge size="xs" variant="outline" color="gray">
                                {c.especificacao}
                              </Badge>
                            )}
                            {!c.noCatalogo && (
                              <Badge size="xs" color="gray" variant="light">
                                não está no armazém
                              </Badge>
                            )}
                          </Group>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">
                            {fmtNumber(c.necessario)} {c.unidade ?? 'un'}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" c={c.stock > 0 ? undefined : 'dimmed'}>
                            {fmtNumber(c.stock)}
                          </Text>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm" fw={700} c={c.aComprar > 0 ? 'red.7' : 'green.7'}>
                            {fmtNumber(c.aComprar)}
                          </Text>
                        </Table.Td>
                        {custos && (
                          <Table.Td>
                            <Text size="sm">
                              {c.custoEstimado != null ? fmtMoney(c.custoEstimado) : '—'}
                            </Text>
                          </Table.Td>
                        )}
                        <Table.Td>
                          <Text size="sm">{c.prazoDias != null ? `${c.prazoDias} d` : '—'}</Text>
                        </Table.Td>
                        <Table.Td>
                          <Text size="sm">{c.precisaEm ? fmtDate(c.precisaEm) : '—'}</Text>
                        </Table.Td>
                        <Table.Td>
                          <Group gap={6} wrap="nowrap">
                            <Text size="sm" fw={600}>
                              {c.encomendarAte ? fmtDate(c.encomendarAte) : '—'}
                            </Text>
                            {c.urgente && (
                              <Tooltip
                                label="O prazo de entrega já não chega para a data em que a peça é precisa"
                                withArrow
                                multiline
                                w={240}
                              >
                                <Badge size="xs" color="red" variant="filled">
                                  hoje
                                </Badge>
                              </Tooltip>
                            )}
                          </Group>
                        </Table.Td>
                        <Table.Td>
                          <Text size="xs" c="dimmed" style={{ maxWidth: 180 }}>
                            {c.equipamentos.join(', ') || '—'}
                          </Text>
                        </Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>
            )}
          </Tabs.Panel>
        </Tabs>
      </Painel>
    </Stack>
  );
}
