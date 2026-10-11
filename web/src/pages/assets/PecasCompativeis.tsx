import {
  Badge,
  Button,
  Group,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  Tooltip,
} from '@mantine/core';
import { IconAlertTriangle, IconCircleCheck, IconHelpCircle, IconPlus } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { Painel } from '../../components/erp';
import { fmtNumber } from '../../lib/format';

/**
 * O catálogo filtrado por este equipamento.
 *
 * <p>É o ecrã que o mecânico quer antes de abrir a caixa de ferramentas: «o que
 * é que serve este camião, e há em armazém?». Um armazém que só conhece nomes
 * não responde a isto — «filtro de óleo» é o nome de cinquenta peças, e a que
 * serve o Hino parte o Hilux.
 *
 * <p>As peças que ninguém classificou ficam de fora por omissão: mostrá-las
 * seria mostrar o armazém inteiro e não responder a nada. Mas diz-se quantas
 * são, porque é trabalho que falta fazer — e dá-se o caminho mais curto para o
 * fazer, que é o botão «serve este equipamento» ao lado de cada uma.
 */

interface Referencia {
  id: string;
  number: string;
  brand?: string | null;
  kindLabel: string;
}

interface PecaCompativel {
  partId: string;
  name: string;
  partNumber?: string | null;
  specification?: string | null;
  category?: string | null;
  systemCode?: string | null;
  unit?: string | null;
  stock: number;
  minQuantity?: number | null;
  abaixoDoMinimo: boolean;
  leadTimeDays?: number | null;
  veredicto: string;
  porque?: string | null;
  referencias: Referencia[];
}

interface Resultado {
  equipamento: {
    tag?: string | null;
    family?: string | null;
    make?: string | null;
    model?: string | null;
    engine?: string | null;
    year?: number | null;
  };
  pecas: PecaCompativel[];
  semCompatibilidade: number;
}

const CATEGORIAS = [
  { value: 'FILTER', label: 'Filtros' },
  { value: 'LUBRICANT', label: 'Lubrificantes' },
  { value: 'WEAR', label: 'Desgaste' },
  { value: 'ENGINE', label: 'Motor' },
  { value: 'HYDRAULIC', label: 'Hidráulico' },
  { value: 'ELECTRICAL', label: 'Elétrico' },
  { value: 'TIRE', label: 'Pneus' },
  { value: 'BATTERY', label: 'Baterias' },
  { value: 'CONSUMABLE', label: 'Consumíveis' },
  { value: 'GENERAL', label: 'Geral' },
];

function Veredicto({ peca }: { peca: PecaCompativel }) {
  if (peca.veredicto === 'SERVE') {
    return (
      <Tooltip label={peca.porque ?? ''} withArrow multiline w={260}>
        <Badge color="green" variant="light" size="sm" leftSection={<IconCircleCheck size={11} />}>
          Compatível
        </Badge>
      </Tooltip>
    );
  }
  if (peca.veredicto === 'INCERTO') {
    return (
      <Tooltip label={peca.porque ?? ''} withArrow multiline w={260}>
        <Badge color="yellow" variant="light" size="sm" leftSection={<IconHelpCircle size={11} />}>
          Por confirmar
        </Badge>
      </Tooltip>
    );
  }
  return (
    <Tooltip label={peca.porque ?? ''} withArrow multiline w={260}>
      <Badge color="gray" variant="light" size="sm">
        Sem informação
      </Badge>
    </Tooltip>
  );
}

export function PecasCompativeis({ assetId }: { assetId: string }) {
  const [categoria, setCategoria] = useState<string | null>(null);
  const [incluirDesconhecidas, setIncluirDesconhecidas] = useState(false);
  const { has } = useAuth();
  const podeDeclarar = has('PARTS_MANAGE');

  const { data, refetch } = useQuery({
    queryKey: ['assets', assetId, 'compatible-parts', categoria, incluirDesconhecidas],
    queryFn: () =>
      api<Resultado>(
        `/assets/${assetId}/compatible-parts?includeUnknown=${incluirDesconhecidas}` +
          (categoria ? `&category=${categoria}` : ''),
      ),
  });

  const marcar = async (partId: string, familyOnly: boolean) => {
    await api(`/parts/${partId}/applications/from-asset/${assetId}?familyOnly=${familyOnly}`, {
      method: 'POST',
    });
    void refetch();
  };

  const eq = data?.equipamento;
  const identificacao = [eq?.make, eq?.model, eq?.engine, eq?.year].filter(Boolean).join(' · ');

  return (
    <Painel
      titulo="Peças compatíveis"
      semPadding
      acoes={
        <Group gap="xs">
          <Select
            size="xs"
            placeholder="Todas as categorias"
            data={CATEGORIAS}
            clearable
            value={categoria}
            onChange={setCategoria}
            w={190}
          />
          <Switch
            size="xs"
            label="Mostrar as não classificadas"
            checked={incluirDesconhecidas}
            onChange={(e) => setIncluirDesconhecidas(e.currentTarget.checked)}
          />
        </Group>
      }
      rodape={
        <Text size="xs" c="dimmed">
          Identificação usada: {identificacao || 'nenhuma — preencha marca, modelo, motor e ano na ficha'}
          {eq?.family ? ` (família ${eq.family})` : ''}.
          {(data?.semCompatibilidade ?? 0) > 0 &&
            ` Há ${data?.semCompatibilidade} artigo(s) no armazém sem compatibilidade declarada.`}
        </Text>
      }
    >
      {(data?.pecas.length ?? 0) === 0 ? (
        <Stack gap={6} p="md">
          <Group gap={8}>
            <IconAlertTriangle size={18} color="var(--mantine-color-yellow-6)" />
            <Text size="sm" c="dimmed">
              Nenhum artigo do armazém está declarado como compatível com este equipamento.
            </Text>
          </Group>
          <Text size="xs" c="dimmed">
            Ligue o botão «Mostrar as não classificadas» e, em cada peça que sirva, use «Serve este
            equipamento» — ou aplique um plano do catálogo, que classifica as peças por família
            automaticamente.
          </Text>
        </Stack>
      ) : (
        <Table.ScrollContainer minWidth={900}>
          <Table highlightOnHover>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Peça</Table.Th>
                <Table.Th>Sistema</Table.Th>
                <Table.Th>Em armazém</Table.Th>
                <Table.Th>Prazo</Table.Th>
                <Table.Th>Compatibilidade</Table.Th>
                {podeDeclarar && <Table.Th />}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {data?.pecas.map((p) => (
                <Table.Tr key={p.partId}>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {p.name}
                    </Text>
                    <Group gap={6}>
                      {p.partNumber && (
                        <Text size="xs" c="dimmed">
                          ref. {p.partNumber}
                        </Text>
                      )}
                      {p.specification && (
                        <Badge size="xs" variant="outline" color="gray">
                          {p.specification}
                        </Badge>
                      )}
                      {p.referencias.length > 0 && (
                        <Tooltip
                          label={p.referencias
                            .map((r) => `${r.number} ${r.brand ?? ''} (${r.kindLabel})`)
                            .join(' · ')}
                          withArrow
                          multiline
                          w={280}
                        >
                          <Badge size="xs" variant="light" color="blue">
                            {p.referencias.length} equivalente(s)
                          </Badge>
                        </Tooltip>
                      )}
                    </Group>
                  </Table.Td>
                  <Table.Td>
                    <Text size="xs" c="dimmed">
                      {p.systemCode ?? p.category ?? '—'}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" fw={600} c={p.abaixoDoMinimo ? 'orange.7' : undefined}>
                      {fmtNumber(p.stock)} {p.unit ?? ''}
                    </Text>
                    {p.abaixoDoMinimo && (
                      <Text size="xs" c="orange.7">
                        abaixo do mínimo
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Text size="xs">{p.leadTimeDays != null ? `${p.leadTimeDays} d` : '—'}</Text>
                  </Table.Td>
                  <Table.Td>
                    <Veredicto peca={p} />
                  </Table.Td>
                  {podeDeclarar && (
                    <Table.Td>
                      {p.veredicto !== 'SERVE' && (
                        <Group gap={4} wrap="nowrap">
                          <Tooltip
                            label="Copia marca, modelo, motor e ano deste equipamento"
                            withArrow
                          >
                            <Button
                              size="compact-xs"
                              variant="light"
                              leftSection={<IconPlus size={12} />}
                              onClick={() => void marcar(p.partId, false)}
                            >
                              Serve este
                            </Button>
                          </Tooltip>
                          <Tooltip
                            label={`Serve toda a família ${eq?.family ?? ''} — para peças comuns à classe`}
                            withArrow
                          >
                            <Button
                              size="compact-xs"
                              variant="subtle"
                              onClick={() => void marcar(p.partId, true)}
                            >
                              e a família
                            </Button>
                          </Tooltip>
                        </Group>
                      )}
                    </Table.Td>
                  )}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
    </Painel>
  );
}
