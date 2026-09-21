/**
 * Abater um ativo: quando ele sai da frota.
 *
 * <p>Vender um camião e deixá-lo na lista a fingir que existe estraga todos
 * os números; apagá-lo estraga o histórico. Aqui faz-se a terceira coisa: sai
 * das listas e dos indicadores, e fica guardado com a data, o motivo, o
 * contador final e o que rendeu — para se poder responder à pergunta que vem
 * a seguir, que é sempre a mesma: valeu a pena?
 */
import { Alert, Button, Group, Modal, NumberInput, Select, Stack, Text, Textarea } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconArchiveOff, IconInfoCircle, IconTrashOff } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { fmtDate, fmtMoney, fmtNumber } from '../../lib/format';

const MOTIVOS = [
  { value: 'SOLD', label: 'Vendido' },
  { value: 'SCRAPPED', label: 'Sucata' },
  { value: 'ACCIDENT', label: 'Perda total por acidente' },
  { value: 'THEFT', label: 'Roubo' },
  { value: 'END_OF_LIFE', label: 'Fim de vida útil' },
  { value: 'RETURNED', label: 'Devolvido ao locador' },
  { value: 'OTHER', label: 'Outro' },
];

export interface Abate {
  assetId: string;
  tag: string;
  retiredAt?: string | null;
  reason?: string | null;
  reasonLabel?: string | null;
  notes?: string | null;
  finalMeter?: number | null;
  meterUnit?: string | null;
  residualValue?: number | null;
  currency?: string | null;
  lifetimeMaintenanceCost?: number | null;
  costPerUnit?: number | null;
  workOrders: number;
}

export function AbaterAtivoModal({
  assetId,
  tag,
  contadorActual,
  unidade,
  aberto,
  fechar,
}: {
  assetId: string;
  tag: string;
  contadorActual?: number | null;
  unidade?: string | null;
  aberto: boolean;
  fechar: () => void;
}) {
  const queryClient = useQueryClient();
  const [motivo, setMotivo] = useState<string | null>('SOLD');
  const [contador, setContador] = useState<number | string>(contadorActual ?? '');
  const [valor, setValor] = useState<number | string>('');
  const [notas, setNotas] = useState('');
  const [feito, setFeito] = useState<Abate | null>(null);

  const abater = useMutation({
    mutationFn: () =>
      api<Abate>(`/assets/${assetId}/retire`, {
        method: 'POST',
        body: {
          reason: motivo,
          finalMeter: contador === '' ? undefined : Number(contador),
          residualValue: valor === '' ? undefined : Number(valor),
          notes: notas.trim() || undefined,
        },
      }),
    onSuccess: (a) => {
      setFeito(a);
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível abater', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={aberto} onClose={fechar} title={`Abater ${tag}`} centered size="md">
      {feito ? (
        <Stack gap="sm">
          <Text fw={700}>
            {tag} foi abatido em {fmtDate(feito.retiredAt)}.
          </Text>
          <Alert variant="light" color="gray" p="sm">
            <Stack gap={4}>
              <Text size="sm">
                <b>Motivo:</b> {feito.reasonLabel}
              </Text>
              <Text size="sm">
                <b>Contador final:</b> {fmtNumber(feito.finalMeter)} {feito.meterUnit}
              </Text>
              <Text size="sm">
                <b>Custou em manutenção:</b> {fmtMoney(feito.lifetimeMaintenanceCost)}
                {feito.costPerUnit != null && feito.meterUnit
                  ? ` · ${fmtMoney(feito.costPerUnit)} por ${feito.meterUnit}`
                  : ''}
              </Text>
              <Text size="sm">
                <b>Ordens na vida:</b> {feito.workOrders}
              </Text>
            </Stack>
          </Alert>
          <Text size="xs" c="dimmed">
            Sai das listas e dos indicadores. O histórico fica, em «Ativos abatidos».
          </Text>
          <Group justify="flex-end">
            <Button onClick={fechar}>Fechar</Button>
          </Group>
        </Stack>
      ) : (
        <Stack gap="sm">
          <Alert variant="light" color="yellow" p="xs" icon={<IconInfoCircle size={16} />}>
            <Text size="xs">
              O ativo sai das listas, dos indicadores e do painel, mas nada é apagado: as ordens, os
              custos e as inspeções ficam guardados. Pode reverter-se.
            </Text>
          </Alert>
          <Select label="Motivo" data={MOTIVOS} value={motivo} onChange={setMotivo} allowDeselect={false} />
          <NumberInput
            label={`Contador final${unidade ? ` (${unidade})` : ''}`}
            description="O que a viatura fez na vida. Vazio usa a última leitura."
            value={contador}
            onChange={setContador}
            min={0}
            thousandSeparator=" "
          />
          <NumberInput
            label="Valor recebido"
            description="Quanto rendeu a venda ou a sucata. Vazio se não rendeu nada."
            value={valor}
            onChange={setValor}
            min={0}
            thousandSeparator=" "
          />
          <Textarea
            label="Observações"
            placeholder="A quem foi vendido, número de contrato, o que aconteceu…"
            value={notas}
            onChange={(e) => setNotas(e.currentTarget.value)}
            autosize
            minRows={2}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={fechar}>
              Cancelar
            </Button>
            <Button color="red" loading={abater.isPending} onClick={() => abater.mutate()}
              leftSection={<IconTrashOff size={16} />}>
              Abater
            </Button>
          </Group>
        </Stack>
      )}
    </Modal>
  );
}

/** O botão que devolve um ativo abatido à frota. */
export function ReverterAbate({ assetId, tag }: { assetId: string; tag: string }) {
  const queryClient = useQueryClient();
  const reverter = useMutation({
    mutationFn: () => api(`/assets/${assetId}/unretire`, { method: 'POST' }),
    onSuccess: () => {
      notifications.show({ message: `${tag} voltou à frota.`, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });
  return (
    <Button
      size="compact-sm"
      variant="default"
      leftSection={<IconArchiveOff size={15} />}
      loading={reverter.isPending}
      onClick={() => reverter.mutate()}
    >
      Reverter abate
    </Button>
  );
}
