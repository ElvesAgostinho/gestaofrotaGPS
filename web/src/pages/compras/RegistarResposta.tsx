import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Select,
  Stack,
  Table,
  Text,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPaperclip } from '@tabler/icons-react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, ApiError } from '../../api/client';
import { fmtNumber } from '../../lib/format';
import type { Pedido, Proposta } from './tiposCotacao';

/**
 * Registar o que um fornecedor respondeu.
 *
 * <p>Vive aqui, e não dentro do ecrã das cotações, porque se usa dos dois
 * lados: a partir do pedido, quando a resposta vem por telefone, e a partir do
 * <b>correio</b>, quando vem por email — que é o caso normal. Nesse segundo
 * caso o pedido já vem escolhido e o anexo da proposta segue com ele, do
 * servidor de email para o pedido, sem passar pelo computador de ninguém.
 */

/** O anexo de uma mensagem que se pode guardar como proposta. */
export interface AnexoDoCorreio {
  folder: string;
  uid: number;
  attachmentId: string;
  nome: string;
}

export function RegistarResposta({
  pedidoId,
  supplierId,
  supplierName,
  anexo,
  aoFechar,
}: {
  pedidoId: string;
  supplierId: string;
  supplierName: string;
  /** Quando vem do correio: o anexo a guardar e a mensagem de origem. */
  anexo?: AnexoDoCorreio | null;
  aoFechar: () => void;
}) {
  const { data: pedido } = useQuery({
    queryKey: ['cotacoes', 'pedido', pedidoId],
    queryFn: () => api<Pedido>(`/rfqs/${pedidoId}`),
  });

  if (!pedido) {
    return null;
  }
  const proposta = pedido.propostas.find((p) => p.supplierId === supplierId);
  if (!proposta) {
    return null;
  }

  return (
    <Formulario
      pedido={pedido}
      proposta={proposta}
      supplierName={supplierName}
      anexo={anexo}
      aoFechar={aoFechar}
    />
  );
}

function Formulario({
  pedido,
  proposta,
  supplierName,
  anexo,
  aoFechar,
}: {
  pedido: Pedido;
  proposta: Proposta;
  supplierName: string;
  anexo?: AnexoDoCorreio | null;
  aoFechar: () => void;
}) {
  const [precos, setPrecos] = useState<Record<string, string>>(() => {
    const inicial: Record<string, string> = {};
    proposta.precos.forEach((q) => {
      inicial[q.lineId] = q.unitPrice != null ? String(q.unitPrice) : '';
    });
    return inicial;
  });
  const [semStock, setSemStock] = useState<Record<string, boolean>>(() => {
    const inicial: Record<string, boolean> = {};
    proposta.precos.forEach((q) => {
      inicial[q.lineId] = q.unavailable;
    });
    return inicial;
  });
  const [prazo, setPrazo] = useState(
    proposta.leadTimeDays != null ? String(proposta.leadTimeDays) : '',
  );
  const [validade, setValidade] = useState(proposta.validUntil ?? '');
  const [declined, setDeclined] = useState(proposta.declined);
  const [guardarAnexo, setGuardarAnexo] = useState(true);

  const total = pedido.lines.reduce((soma, l) => {
    if (semStock[l.id]) {
      return soma;
    }
    return soma + (Number(precos[l.id]) || 0) * l.quantity;
  }, 0);

  const guardar = useMutation({
    mutationFn: async () => {
      await api(`/rfqs/${pedido.id}/suppliers/${proposta.supplierId}/response`, {
        method: 'POST',
        body: {
          leadTimeDays: prazo ? Number(prazo) : null,
          validUntil: validade || null,
          declined,
          sourceFolder: anexo?.folder ?? null,
          sourceUid: anexo?.uid ?? null,
          precos: pedido.lines.map((l) => ({
            lineId: l.id,
            unitPrice: precos[l.id] ? Number(precos[l.id]) : null,
            unavailable: !!semStock[l.id],
          })),
        },
      });
      // O anexo vai do servidor de email para o pedido: os bytes nunca passam
      // por aqui, que é o passo que se perdia a copiar à mão.
      if (anexo && guardarAnexo) {
        const q = new URLSearchParams({
          folder: anexo.folder,
          uid: String(anexo.uid),
          attachmentId: anexo.attachmentId,
        });
        await api(
          `/rfqs/${pedido.id}/suppliers/${proposta.supplierId}/proposal/from-mail?${q}`,
          { method: 'POST' },
        );
      }
    },
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'Resposta registada.' });
      aoFechar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        title: 'Não foi possível guardar',
        message: e instanceof ApiError ? e.message : 'Tente de novo.',
      }),
  });

  return (
    <Modal opened onClose={aoFechar} title={`Resposta de ${supplierName}`} size="lg">
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          {pedido.number} · {pedido.title}
        </Text>

        {anexo && (
          <Alert color="blue" variant="light" icon={<IconPaperclip size={18} />}>
            <Group justify="space-between" wrap="nowrap">
              <Text size="sm">
                A proposta <b>{anexo.nome}</b> fica guardada com este pedido.
              </Text>
              <Button
                size="compact-xs"
                variant={guardarAnexo ? 'filled' : 'default'}
                onClick={() => setGuardarAnexo((g) => !g)}
              >
                {guardarAnexo ? 'guardar' : 'não guardar'}
              </Button>
            </Group>
          </Alert>
        )}

        <Group grow>
          <TextInput
            label="Prazo de entrega (dias)"
            type="number"
            value={prazo}
            onChange={(e) => setPrazo(e.currentTarget.value)}
          />
          <TextInput
            label="Cotação válida até"
            type="date"
            value={validade}
            onChange={(e) => setValidade(e.currentTarget.value)}
          />
        </Group>

        <Table withTableBorder>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Linha</Table.Th>
              <Table.Th style={{ width: '28%' }}>Preço unitário</Table.Th>
              <Table.Th style={{ width: '18%' }}>Não tem</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {pedido.lines.map((l) => (
              <Table.Tr key={l.id}>
                <Table.Td>
                  <Text size="sm">{l.description}</Text>
                  <Group gap={6}>
                    <Text size="xs" c="dimmed">
                      {fmtNumber(l.quantity)} {l.unit ?? ''}
                    </Text>
                    {l.specification && (
                      <Badge size="xs" variant="outline" color="gray">
                        {l.specification}
                      </Badge>
                    )}
                  </Group>
                </Table.Td>
                <Table.Td>
                  <TextInput
                    size="xs"
                    type="number"
                    disabled={!!semStock[l.id] || declined}
                    value={precos[l.id] ?? ''}
                    onChange={(e) => {
                      const v = e.currentTarget.value;
                      setPrecos((x) => ({ ...x, [l.id]: v }));
                    }}
                  />
                </Table.Td>
                <Table.Td>
                  <Button
                    size="compact-xs"
                    variant={semStock[l.id] ? 'filled' : 'default'}
                    color="gray"
                    disabled={declined}
                    onClick={() => setSemStock((x) => ({ ...x, [l.id]: !x[l.id] }))}
                  >
                    {semStock[l.id] ? 'não tem' : 'tem'}
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>

        <Group justify="space-between">
          <Button
            size="xs"
            variant={declined ? 'filled' : 'default'}
            color="gray"
            onClick={() => setDeclined((d) => !d)}
          >
            {declined ? 'Respondeu que não fornece' : 'Marcar «não fornece»'}
          </Button>
          {!declined && total > 0 && (
            <Text size="sm" fw={700}>
              Total: {total.toLocaleString('pt-AO')} {proposta.currency}
            </Text>
          )}
        </Group>

        <Group justify="flex-end">
          <Button variant="default" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button loading={guardar.isPending} onClick={() => guardar.mutate()}>
            Guardar resposta
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

/** Escolher a que pedido pertence a resposta, quando há mais do que um aberto. */
export function EscolherPedido({
  pedidos,
  valor,
  aoMudar,
}: {
  pedidos: Pedido[];
  valor: string | null;
  aoMudar: (v: string | null) => void;
}) {
  return (
    <Select
      label="Pedido de cotação"
      description="Este fornecedor tem mais do que um à espera"
      data={pedidos.map((p) => ({ value: p.id, label: `${p.number} · ${p.title}` }))}
      value={valor}
      onChange={aoMudar}
    />
  );
}
