import {
  Badge,
  Button,
  Group,
  Loader,
  Modal,
  NumberInput,
  SegmentedControl,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconClipboardCheck, IconClockHour4, IconPrinter, IconTool } from '@tabler/icons-react';
import { useState } from 'react';
import { NovaOrdemForm } from '../workorders/NovaOrdemForm';
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
  const [aFazer, setAFazer] = useState(false);
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
      <ExecutarInspecao assetId={assetId} ficha={data} aberto={aFazer} fechar={() => setAFazer(false)} />
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
        <Group justify="space-between" mt="xs" wrap="wrap" gap="xs">
          <Text size="xs" c="dimmed" style={{ flex: '1 1 340px' }}>
            Qualquer ponto <b style={{ color: '#b91c1c' }}>crítico</b> não conforme impede a máquina de sair até ser
            resolvido. O operador também a pode fazer no telemóvel (Modo telemóvel → Inspeção); o resultado fica sempre
            no separador «Inspeções».
          </Text>
          {can('TECHNICIAN') && (
            <Button size="compact-sm" leftSection={<IconClipboardCheck size={15} />} onClick={() => setAFazer(true)}>
              Fazer inspeção agora
            </Button>
          )}
        </Group>
      </CaixaFicha>
    </div>
  );
}

type Resposta = 'OK' | 'NOT_OK' | 'NA';

interface Registada {
  id: string;
  outcome: 'OK' | 'ISSUES';
  itemsNotOk: number;
}

/**
 * Fazer a inspeção aqui, no ecrã, sem passar pelo telemóvel.
 *
 * <p>Faltava o passo mais simples de todos: a folha estava à vista e não havia
 * onde carregar para a dar por feita. Quem está na oficina, ao computador, com
 * a máquina à frente, tem de poder percorrer os pontos e fechar — e, se algum
 * ponto reprovar, abrir a ordem de serviço no clique seguinte, que é o que
 * qualquer pessoa vai querer fazer a seguir.
 */
function ExecutarInspecao({
  assetId,
  ficha,
  aberto,
  fechar,
}: {
  assetId: string;
  ficha: Ficha;
  aberto: boolean;
  fechar: () => void;
}) {
  const queryClient = useQueryClient();
  const [respostas, setRespostas] = useState<Record<string, Resposta>>({});
  const [notas, setNotas] = useState<Record<string, string>>({});
  const [contador, setContador] = useState<number | string>('');
  const [observacoes, setObservacoes] = useState('');
  const [feita, setFeita] = useState<Registada | null>(null);
  const [ordemAberta, setOrdemAberta] = useState(false);

  const resposta = (id: string) => respostas[id] ?? 'OK';
  const reprovados = ficha.items.filter((i) => resposta(i.id) === 'NOT_OK');
  const criticosReprovados = reprovados.filter((i) => i.critical);

  const gravar = useMutation({
    mutationFn: () =>
      api<Registada>(`/assets/${assetId}/checklist-executions`, {
        method: 'POST',
        body: {
          templateId: ficha.templateId ?? undefined,
          templateName: ficha.name,
          meterValue: contador === '' ? undefined : Number(contador),
          notes: observacoes.trim() || undefined,
          items: ficha.items.map((i) => ({
            text: i.text,
            verification: i.verification,
            critical: i.critical,
            result: resposta(i.id),
            note: (notas[i.id] ?? '').trim() || undefined,
          })),
        },
      }),
    onSuccess: (r) => {
      setFeita(r);
      queryClient.invalidateQueries({ queryKey: ['asset', assetId, 'inspecoes'] });
      queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });

  // O que se escreve na ordem não é «ver a máquina»: são os pontos que
  // reprovaram, com a nota de quem os viu. Quem vai reparar já sabe ao que vai.
  const descricaoDaOrdem = [
    `Aberta a partir de ${ficha.name.toLowerCase()} de ${new Date().toLocaleDateString('pt-PT')}.`,
    '',
    ...reprovados.map(
      (i) =>
        `• ${i.text}${i.critical ? ' (crítico)' : ''}${(notas[i.id] ?? '').trim() ? ` — ${notas[i.id].trim()}` : ''}`,
    ),
    ...(observacoes.trim() ? ['', `Observações: ${observacoes.trim()}`] : []),
  ].join('\n');

  const sair = () => {
    setFeita(null);
    setRespostas({});
    setNotas({});
    setObservacoes('');
    setContador('');
    fechar();
  };

  return (
    <Modal opened={aberto} onClose={sair} title={ficha.name} size="lg">
      {feita ? (
        <Stack gap="sm">
          <Text fw={700} size="lg">
            {feita.outcome === 'OK'
              ? 'Inspeção registada — sem problemas.'
              : `Inspeção registada com ${feita.itemsNotOk} ponto(s) reprovado(s).`}
          </Text>
          <Text size="sm" c="dimmed">
            {feita.outcome === 'OK'
              ? 'A máquina pode sair. Fica no separador «Inspeções», com a hora e quem a fez.'
              : 'Quem gere a frota foi avisado dos pontos críticos. O passo seguinte é abrir a ordem para os resolver.'}
          </Text>
          <Group>
            {feita.outcome !== 'OK' && <Button onClick={() => setOrdemAberta(true)}>Abrir ordem de serviço</Button>}
            <Button variant="default" onClick={sair}>
              Fechar
            </Button>
          </Group>
          <NovaOrdemForm
            aberto={ordemAberta}
            fechar={() => {
              setOrdemAberta(false);
              sair();
            }}
            assetIdFixo={assetId}
            tituloInicial={`Corrigir pontos reprovados na ${ficha.name.toLowerCase()}`}
            descricaoInicial={descricaoDaOrdem}
          />
        </Stack>
      ) : (
        <Stack gap="sm">
          <NumberInput
            label="Contador no painel (km ou horas)"
            placeholder="Ex.: 125430"
            value={contador}
            onChange={setContador}
            min={0}
            thousandSeparator=" "
          />
          <Stack gap={6}>
            {ficha.items.map((i) => (
              <div key={i.id} style={{ borderBottom: '1px solid #e4e4e7', paddingBottom: 6 }}>
                <Group justify="space-between" wrap="nowrap" align="flex-start" gap="sm">
                  <Text component="div" size="sm" fw={500} style={{ flex: 1 }}>
                    {i.text}
                    {i.critical && (
                      <Badge size="xs" color="red" variant="light" ml={6}>
                        crítico
                      </Badge>
                    )}
                  </Text>
                  <SegmentedControl
                    size="xs"
                    value={resposta(i.id)}
                    onChange={(v) => setRespostas((r) => ({ ...r, [i.id]: v as Resposta }))}
                    data={[
                      { value: 'OK', label: 'Conforme' },
                      { value: 'NOT_OK', label: 'Não conforme' },
                      { value: 'NA', label: 'N/A' },
                    ]}
                  />
                </Group>
                {resposta(i.id) === 'NOT_OK' && (
                  <TextInput
                    mt={4}
                    size="xs"
                    placeholder="O que está mal? (fica na ordem de serviço)"
                    value={notas[i.id] ?? ''}
                    onChange={(e) => setNotas((n) => ({ ...n, [i.id]: e.currentTarget.value }))}
                  />
                )}
              </div>
            ))}
          </Stack>
          <Textarea
            label="Observações"
            placeholder="O que mais houver a dizer sobre a máquina hoje."
            value={observacoes}
            onChange={(e) => setObservacoes(e.currentTarget.value)}
            autosize
            minRows={2}
          />
          {criticosReprovados.length > 0 && (
            <Text size="sm" c="red">
              {criticosReprovados.length} ponto(s) crítico(s) não conforme(s): a máquina não deve sair até serem
              resolvidos.
            </Text>
          )}
          <Group justify="flex-end">
            <Button variant="default" onClick={sair}>
              Cancelar
            </Button>
            <Button loading={gravar.isPending} onClick={() => gravar.mutate()}>
              Registar inspeção
            </Button>
          </Group>
        </Stack>
      )}
    </Modal>
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
