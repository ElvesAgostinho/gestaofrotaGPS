import { Badge, Button, Group, Loader, Table, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconClockHour4, IconPrinter, IconTool } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, openFile } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';

const AMBAR = '#FFC62F';
const PRETO = '#141416';

/**
 * A barra preta com o título em âmbar, como no manual do fabricante.
 *
 * <p>Não é enfeite: é o que faz uma folha de oficina ler-se a um metro de
 * distância, com luvas e contraluz. O sistema por dentro pode ser moderno; a
 * folha tem de ser a que o mecânico já sabe ler.
 */
export function BarraFicha({ titulo, direita }: { titulo: string; direita?: React.ReactNode }) {
  return (
    <div
      style={{
        background: PRETO,
        color: AMBAR,
        padding: '6px 10px',
        fontFamily: '"Barlow Condensed", Barlow, sans-serif',
        fontWeight: 700,
        fontSize: 15,
        letterSpacing: '0.04em',
        textTransform: 'uppercase',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        gap: 8,
      }}
    >
      <span>{titulo}</span>
      {direita && (
        <span
          style={{
            color: '#fff',
            fontWeight: 500,
            fontSize: 12,
            textTransform: 'none',
            letterSpacing: 0,
          }}
        >
          {direita}
        </span>
      )}
    </div>
  );
}

/** O corpo da ficha: caixa de traço grosso, agarrada à barra. */
export function CaixaFicha({ children }: { children: React.ReactNode }) {
  return (
    <div
      style={{
        border: `1.5px solid ${PRETO}`,
        borderTop: 'none',
        padding: '8px 10px',
        background: '#fff',
      }}
    >
      {children}
    </div>
  );
}

interface ItemInspecao {
  id: string;
  text: string;
  verification: 'VERIFY' | 'INSPECT' | 'TEST' | string;
  critical: boolean;
}

interface Ficha {
  /** OWN = modelo desta empresa; SUGGESTED = do catálogo, ainda por criar. */
  source: 'OWN' | 'SUGGESTED' | string;
  templateId?: string | null;
  name: string;
  description?: string | null;
  estimatedMinutes?: number | null;
  items: ItemInspecao[];
}

const VERIFICACAO: Record<string, string> = {
  VERIFY: 'Verificar',
  INSPECT: 'Inspecionar',
  TEST: 'Testar',
};

/**
 * A inspeção diária desta máquina, com o aspeto da folha que fica na cabina.
 *
 * <p>Quando a empresa ainda não tem a sua, mostra-se a do catálogo para a
 * família — dita como sugestão, não como facto — e cria-se num clique. É a
 * diferença entre um sistema que exige que alguém escreva onze linhas à mão e
 * um que já sabe o que se verifica numa retroescavadora antes do arranque.
 */
export function InspecaoDiariaFicha({ assetId }: { assetId: string }) {
  const { can } = useAuth();
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['asset', assetId, 'daily-inspection'],
    queryFn: () => api<Ficha | null>(`/assets/${assetId}/daily-inspection`),
  });

  const adotar = useMutation({
    mutationFn: () => api(`/assets/${assetId}/daily-inspection`, { method: 'POST' }),
    onSuccess: () => {
      notifications.show({
        message: 'Inspeção diária criada para esta família.',
        color: 'green',
      });
      queryClient.invalidateQueries({
        queryKey: ['asset', assetId, 'daily-inspection'],
      });
      queryClient.invalidateQueries({ queryKey: ['checklist-templates'] });
    },
    onError: (e: Error) =>
      notifications.show({
        title: 'Não foi possível criar',
        message: e.message,
        color: 'red',
      }),
  });

  if (isLoading) return <Loader size="sm" />;
  if (!data) {
    return (
      <div>
        <BarraFicha titulo="Inspeção diária (antes do arranque)" />
        <CaixaFicha>
          <Text size="sm" c="dimmed">
            Ainda não há inspeção diária para esta família de equipamento. Crie um modelo em Planos → Inspeções e ele
            passa a aparecer aqui e no telemóvel do operador.
          </Text>
        </CaixaFicha>
      </div>
    );
  }

  const sugestao = data.source === 'SUGGESTED';
  return (
    <div>
      <BarraFicha
        titulo={data.name}
        direita={
          data.estimatedMinutes != null ? (
            <Group gap={4} wrap="nowrap">
              <IconClockHour4 size={13} />
              Tempo estimado: {data.estimatedMinutes} minutos
            </Group>
          ) : undefined
        }
      />
      <CaixaFicha>
        {sugestao && (
          <Group justify="space-between" mb="xs" wrap="wrap">
            <Text size="xs" c="dimmed">
              <b>Sugestão do catálogo</b> para esta família — ainda não é um modelo seu. Crie-o para o operador o poder
              executar no telemóvel.
            </Text>
            {can('MANAGER') && (
              <Button size="compact-xs" loading={adotar.isPending} onClick={() => adotar.mutate()}>
                Criar esta inspeção
              </Button>
            )}
          </Group>
        )}
        {data.description && (
          <Text size="xs" c="dimmed" mb={6}>
            {data.description}
          </Text>
        )}
        <Table fz="sm" withRowBorders>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Item</Table.Th>
              <Table.Th w={140}>Verificação</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data.items.map((i) => (
              <Table.Tr key={i.id}>
                <Table.Td>
                  {i.text}
                  {i.critical && (
                    <Badge size="xs" color="red" variant="light" ml={6}>
                      crítico
                    </Badge>
                  )}
                </Table.Td>
                <Table.Td>{VERIFICACAO[i.verification] ?? 'Verificar'}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
        <Text size="xs" c="dimmed" mt={6}>
          Qualquer ponto <b style={{ color: '#b91c1c' }}>crítico</b> não conforme impede a máquina de sair até ser
          resolvido. O operador executa esta inspeção no telemóvel (Modo telemóvel → Inspeção) e o resultado fica no
          separador «Inspeções».
        </Text>
      </CaixaFicha>
    </div>
  );
}

/** Botão que abre a folha para imprimir e pendurar na cabina. */
export function BotaoFichaDoPosto({ assetId }: { assetId: string }) {
  return (
    <Button
      size="compact-xs"
      variant="default"
      leftSection={<IconPrinter size={14} />}
      onClick={async () => {
        try {
          await openFile(`/assets/${assetId}/operator-sheet.pdf`);
        } catch (e) {
          notifications.show({
            title: 'Não foi possível abrir',
            message: (e as Error).message,
            color: 'red',
          });
        }
      }}
    >
      Ficha do posto (PDF)
    </Button>
  );
}

/** Rodapé de materiais, como no manual: o que é preciso ter à mão. */
export function FerramentasEMateriais({ itens }: { itens: string[] }) {
  if (itens.length === 0) return null;
  return (
    <div>
      <BarraFicha titulo="Ferramentas e materiais" />
      <CaixaFicha>
        <Group gap="lg" wrap="wrap">
          {itens.map((m) => (
            <Group key={m} gap={6} wrap="nowrap">
              <IconTool size={14} style={{ color: '#a1a1aa' }} />
              <Text size="sm">{m}</Text>
            </Group>
          ))}
        </Group>
      </CaixaFicha>
    </div>
  );
}

export { AMBAR, PRETO };
