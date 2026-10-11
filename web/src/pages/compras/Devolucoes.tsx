import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Select,
  Stack,
  Switch,
  Table,
  Text,
  Textarea,
  TextInput,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconAlertTriangle, IconArrowBackUp } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../../api/client';
import { Painel } from '../../components/erp';
import { fmtDate, fmtMoney, fmtNumber } from '../../lib/format';

/**
 * O que veio mal, e o que lhe aconteceu a seguir.
 *
 * <p>A quantidade recusada já ficava registada e já contava na qualidade do
 * fornecedor. Mas não gerava nada: não havia lista, ninguém sabia quanto é que
 * ele devia, e a caixa com os filtros molhados ficava a um canto do armazém
 * até alguém tropeçar nela.
 *
 * <p>São quatro desfechos e não dois, porque nem tudo se devolve: por uma
 * unidade raramente compensa o frete de volta, e obrigar a escolher entre
 * «devolvida» e «não devolvida» faria metade dos casos ficar por registar.
 */

export interface Devolucao {
  id: string;
  orderId: string;
  orderNumber: string;
  supplierId?: string | null;
  supplierName: string;
  receiptNumber: string;
  receivedAt?: string | null;
  description: string;
  quantity: number;
  unit?: string | null;
  reason?: string | null;
  resolution: 'PENDING' | 'RETURNED' | 'REPLACED' | 'CREDITED' | 'ACCEPTED';
  resolutionLabel: string;
  resolvedAt?: string | null;
  resolutionNote?: string | null;
  creditNote?: string | null;
  creditAmount?: number | null;
  valor?: number | null;
  currency: string;
  diasPorResolver: number;
  aArrastar: boolean;
}

const DESFECHOS = [
  { value: 'RETURNED', label: 'Devolvida ao fornecedor' },
  { value: 'REPLACED', label: 'Ele substituiu' },
  { value: 'CREDITED', label: 'Ele creditou' },
  { value: 'ACCEPTED', label: 'Ficámos com ela assim mesmo' },
];

const COR: Record<Devolucao['resolution'], string> = {
  PENDING: 'orange',
  RETURNED: 'blue',
  REPLACED: 'teal',
  CREDITED: 'green',
  ACCEPTED: 'gray',
};

export function Devolucoes({ supplierId }: { supplierId?: string }) {
  const qc = useQueryClient();
  const [tudo, setTudo] = useState(false);
  const [aResolver, setAResolver] = useState<Devolucao | null>(null);

  const caminho = supplierId
    ? `/suppliers/${supplierId}/returns`
    : `/purchase-returns?pendingOnly=${!tudo}`;

  const { data } = useQuery({
    queryKey: ['devolucoes', supplierId ?? 'todas', tudo],
    queryFn: () => api<Devolucao[]>(caminho),
  });

  const linhas = supplierId && !tudo
    ? (data ?? []).filter((d) => d.resolution === 'PENDING')
    : (data ?? []);

  const porResolver = (data ?? []).filter((d) => d.resolution === 'PENDING');
  const aArrastar = porResolver.filter((d) => d.aArrastar);
  const emDivida = porResolver.reduce((s, d) => s + (d.valor ?? 0), 0);

  if (!supplierId && porResolver.length === 0 && !tudo) {
    return null;
  }

  return (
    <Painel
      titulo="Devoluções"
      acoes={
        <>
          <Switch
            size="xs"
            label="Mostrar as já resolvidas"
            checked={tudo}
            onChange={(e) => setTudo(e.currentTarget.checked)}
          />
          {porResolver.length > 0 && (
            <div style={{ marginLeft: 'auto' }}>
              <Text size="xs" c="dimmed">
                {porResolver.length} por resolver · {fmtMoney(emDivida)} em causa
              </Text>
            </div>
          )}
        </>
      }
    >
      <Stack gap="sm">
        {aArrastar.length > 0 && (
          <Alert color="orange" variant="light" icon={<IconAlertTriangle size={18} />}>
            <b>{aArrastar.length} devolução(ões) por resolver há mais de duas semanas.</b> Uma
            devolução esquecida é dinheiro perdido em silêncio: ninguém a reclama e o fornecedor
            não a credita.
          </Alert>
        )}

        {linhas.length === 0 ? (
          <Text size="sm" c="dimmed">
            {tudo ? 'Nunca houve devoluções.' : 'Nada por resolver.'}
          </Text>
        ) : (
          <Table.ScrollContainer minWidth={900}>
            <Table highlightOnHover>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>O que veio mal</Table.Th>
                  {!supplierId && <Table.Th>Fornecedor</Table.Th>}
                  <Table.Th>Encomenda</Table.Th>
                  <Table.Th>Quando</Table.Th>
                  <Table.Th ta="right">Vale</Table.Th>
                  <Table.Th>Desfecho</Table.Th>
                  <Table.Th w={110} />
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {linhas.map((d) => (
                  <Table.Tr key={d.id}>
                    <Table.Td>
                      <Text size="sm" fw={600}>
                        {fmtNumber(d.quantity)} {d.unit ?? ''} · {d.description}
                      </Text>
                      {d.reason && (
                        <Text size="xs" c="dimmed">
                          {d.reason}
                        </Text>
                      )}
                    </Table.Td>
                    {!supplierId && (
                      <Table.Td>
                        {d.supplierId ? (
                          <Text
                            size="sm"
                            component={Link}
                            to={`/fornecedores/${d.supplierId}`}
                            style={{ color: 'inherit' }}
                          >
                            {d.supplierName}
                          </Text>
                        ) : (
                          <Text size="sm">{d.supplierName}</Text>
                        )}
                      </Table.Td>
                    )}
                    <Table.Td>
                      <Text
                        size="sm"
                        ff="monospace"
                        component={Link}
                        to={`/compras/${d.orderId}`}
                        style={{ color: 'inherit' }}
                      >
                        {d.orderNumber}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {d.receiptNumber}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{d.receivedAt ? fmtDate(d.receivedAt) : '—'}</Text>
                      {d.resolution === 'PENDING' && d.diasPorResolver > 0 && (
                        <Text size="xs" c={d.aArrastar ? 'orange.7' : 'dimmed'}>
                          há {d.diasPorResolver} dias
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td ta="right">
                      <Text size="sm">{d.valor != null ? fmtMoney(d.valor) : '—'}</Text>
                    </Table.Td>
                    <Table.Td>
                      <Badge size="sm" variant="light" color={COR[d.resolution]}>
                        {d.resolutionLabel}
                      </Badge>
                      {d.creditNote && (
                        <Text size="xs" c="dimmed">
                          nota {d.creditNote}
                        </Text>
                      )}
                      {d.resolutionNote && (
                        <Tooltip label={d.resolutionNote} withArrow multiline w={240}>
                          <Text size="xs" c="dimmed" truncate style={{ maxWidth: 140 }}>
                            {d.resolutionNote}
                          </Text>
                        </Tooltip>
                      )}
                    </Table.Td>
                    <Table.Td>
                      {d.resolution === 'PENDING' && (
                        <Button
                          size="compact-xs"
                          variant="light"
                          leftSection={<IconArrowBackUp size={12} />}
                          onClick={() => setAResolver(d)}
                        >
                          Resolver
                        </Button>
                      )}
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        )}
      </Stack>

      {aResolver && (
        <Resolver
          devolucao={aResolver}
          aoFechar={() => {
            setAResolver(null);
            void qc.invalidateQueries({ queryKey: ['devolucoes'] });
            void qc.invalidateQueries({ queryKey: ['fornecedores'] });
          }}
        />
      )}
    </Painel>
  );
}

function Resolver({
  devolucao,
  aoFechar,
}: {
  devolucao: Devolucao;
  aoFechar: () => void;
}) {
  const [resolution, setResolution] = useState<string | null>('RETURNED');
  const [note, setNote] = useState('');
  const [creditNote, setCreditNote] = useState('');
  const [creditAmount, setCreditAmount] = useState(
    devolucao.valor != null ? String(devolucao.valor) : '',
  );

  const guardar = useMutation({
    mutationFn: () =>
      api(`/purchase-returns/${devolucao.id}/resolve`, {
        method: 'POST',
        body: {
          resolution,
          note: note.trim() || null,
          creditNote: creditNote.trim() || null,
          creditAmount: creditAmount ? Number(creditAmount) : null,
        },
      }),
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'Devolução resolvida.' });
      aoFechar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        title: 'Não foi possível',
        message: e instanceof ApiError ? e.message : 'Tente de novo.',
      }),
  });

  return (
    <Modal opened onClose={aoFechar} title="Resolver a devolução" size="md">
      <Stack gap="sm">
        <Text size="sm">
          <b>
            {fmtNumber(devolucao.quantity)} {devolucao.unit ?? ''} · {devolucao.description}
          </b>
          <br />
          {devolucao.supplierName} · {devolucao.orderNumber}
          {devolucao.reason ? ` · ${devolucao.reason}` : ''}
        </Text>

        <Select
          label="Como acabou"
          data={DESFECHOS}
          value={resolution}
          onChange={setResolution}
        />

        {resolution === 'CREDITED' && (
          <Group grow>
            <TextInput
              label="Nota de crédito"
              required
              description="Sem o número não há como a conciliar"
              placeholder="NC 2026/88"
              value={creditNote}
              onChange={(e) => setCreditNote(e.currentTarget.value)}
            />
            <TextInput
              label="Valor creditado"
              type="number"
              value={creditAmount}
              onChange={(e) => setCreditAmount(e.currentTarget.value)}
            />
          </Group>
        )}

        <Textarea
          label="Nota"
          description={
            resolution === 'ACCEPTED'
              ? 'Porque é que se ficou com ela — daqui a seis meses ninguém se lembra'
              : 'Opcional'
          }
          autosize
          minRows={2}
          value={note}
          onChange={(e) => setNote(e.currentTarget.value)}
        />

        <Group justify="flex-end">
          <Button variant="default" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button
            disabled={resolution === 'CREDITED' && !creditNote.trim()}
            loading={guardar.isPending}
            onClick={() => guardar.mutate()}
          >
            Guardar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
