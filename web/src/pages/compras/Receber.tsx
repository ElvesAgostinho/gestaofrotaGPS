import {
  Alert,
  Button,
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
import { IconInfoCircle } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, ApiError } from '../../api/client';
import { fmtNumber } from '../../lib/format';
import type { Ordem } from './tipos';

/**
 * Registar o que chegou.
 *
 * <p>Recebe-se em partes, porque é assim que chega: o fornecedor manda metade
 * e promete o resto. Por isso cada linha vem preenchida com o que ainda falta,
 * e não com o que foi encomendado — é o número que quem está no armazém tem à
 * frente quando confere o camião.
 *
 * <p>O que chega mal conta-se à parte: não entra no armazém, mas fica no
 * registo. É o que se devolve, e é o que distingue um fornecedor que entrega
 * de um que entrega bem.
 */
export function Receber({
  ordem,
  aberto,
  aoFechar,
}: {
  ordem: Ordem;
  aberto: boolean;
  aoFechar: () => void;
}) {
  const qc = useQueryClient();
  const [warehouseId, setWarehouseId] = useState<string | null>(null);
  const [deliveryNote, setDeliveryNote] = useState('');
  const [notes, setNotes] = useState('');
  const [valores, setValores] = useState<Record<string, { boa: string; ma: string; motivo: string }>>(
    () => {
      const inicial: Record<string, { boa: string; ma: string; motivo: string }> = {};
      ordem.lines.forEach((l) => {
        inicial[l.id ?? ''] = { boa: String(l.porReceber ?? l.quantity), ma: '', motivo: '' };
      });
      return inicial;
    },
  );

  const { data: armazens } = useQuery({
    queryKey: ['warehouses'],
    queryFn: () => api<{ id: string; name: string }[]>('/warehouses'),
    enabled: aberto,
  });

  const receber = useMutation({
    mutationFn: () =>
      api(`/purchase-orders/${ordem.id}/receipts`, {
        method: 'POST',
        body: {
          warehouseId,
          deliveryNote: deliveryNote.trim() || null,
          notes: notes.trim() || null,
          lines: ordem.lines
            .map((l) => {
              const v = valores[l.id ?? ''] ?? { boa: '0', ma: '', motivo: '' };
              return {
                orderLineId: l.id,
                quantity: Number(v.boa) || 0,
                rejectedQuantity: Number(v.ma) || 0,
                rejectionReason: v.motivo.trim() || null,
              };
            })
            .filter((l) => l.quantity > 0 || l.rejectedQuantity > 0),
        },
      }),
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'Entrega registada.' });
      void qc.invalidateQueries({ queryKey: ['compras'] });
      void qc.invalidateQueries({ queryKey: ['consumiveis'] });
      void qc.invalidateQueries({ queryKey: ['parts'] });
      aoFechar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        title: 'Não foi possível registar',
        message: e instanceof ApiError ? e.message : 'Tente de novo.',
      }),
  });

  const mudar = (id: string, campo: 'boa' | 'ma' | 'motivo', v: string) =>
    setValores((x) => ({ ...x, [id]: { ...x[id], [campo]: v } }));

  return (
    <Modal opened={aberto} onClose={aoFechar} title={`Receber · ${ordem.number}`} size="xl">
      <Stack gap="sm">
        <Group grow>
          <Select
            label="Entra no armazém"
            placeholder="Escolher"
            description="Sem armazém, a entrega fecha a encomenda mas não mexe no stock"
            searchable
            clearable
            value={warehouseId}
            onChange={setWarehouseId}
            data={(armazens ?? []).map((a) => ({ value: a.id, label: a.name }))}
          />
          <TextInput
            label="Guia de remessa"
            placeholder="GR-4411"
            value={deliveryNote}
            onChange={(e) => setDeliveryNote(e.currentTarget.value)}
          />
        </Group>

        <Table withTableBorder>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Artigo</Table.Th>
              <Table.Th style={{ width: '12%' }}>Falta</Table.Th>
              <Table.Th style={{ width: '14%' }}>Chegou bem</Table.Th>
              <Table.Th style={{ width: '14%' }}>Veio mal</Table.Th>
              <Table.Th style={{ width: '24%' }}>Porquê</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {ordem.lines.map((l) => {
              const id = l.id ?? '';
              const v = valores[id] ?? { boa: '', ma: '', motivo: '' };
              return (
                <Table.Tr key={id}>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {l.description}
                    </Text>
                    {l.supplierPartNumber && (
                      <Text size="xs" c="dimmed" ff="monospace">
                        {l.supplierPartNumber}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">
                      {fmtNumber(l.porReceber ?? l.quantity)} {l.unit ?? ''}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      type="number"
                      value={v.boa}
                      onChange={(e) => mudar(id, 'boa', e.currentTarget.value)}
                    />
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      type="number"
                      value={v.ma}
                      onChange={(e) => mudar(id, 'ma', e.currentTarget.value)}
                    />
                  </Table.Td>
                  <Table.Td>
                    <TextInput
                      size="xs"
                      placeholder="caixas amolgadas…"
                      disabled={!Number(v.ma)}
                      value={v.motivo}
                      onChange={(e) => mudar(id, 'motivo', e.currentTarget.value)}
                    />
                  </Table.Td>
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>

        <Alert color="blue" variant="light" icon={<IconInfoCircle size={16} />}>
          O que chegou bem entra no armazém com o preço desta encomenda e actualiza o custo médio.
          O que veio mal fica registado para devolução e conta na classificação do fornecedor.
        </Alert>

        <Textarea
          label="Observações"
          autosize
          minRows={2}
          value={notes}
          onChange={(e) => setNotes(e.currentTarget.value)}
        />

        <Group justify="flex-end">
          <Button variant="default" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button loading={receber.isPending} onClick={() => receber.mutate()}>
            Registar entrega
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
