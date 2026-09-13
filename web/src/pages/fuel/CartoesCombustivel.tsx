import { Alert, Badge, Button, FileInput, Group, Modal, Stack, Table, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCreditCard, IconDownload } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, checkUploadSize, downloadFile } from '../../api/client';
import { Painel } from '../../components/erp';
import { fmtDateTime, fmtMoney, fmtNumber } from '../../lib/format';

interface Transacao {
  id: string;
  assetTag?: string | null;
  cardNumber?: string | null;
  transactedAt: string;
  liters?: number | null;
  amount?: number | null;
  station?: string | null;
  reference?: string | null;
  status: 'MATCHED' | 'UNMATCHED' | 'IGNORED' | string;
}

interface Resultado {
  report: { dryRun: boolean; totalRows: number; created: number; skipped: number; errors: { line: number; value?: string | null; message: string }[] };
  matched: number;
  unmatched: number;
  recordsWithoutCard: number;
}

const ESTADO: Record<string, { label: string; color: string }> = {
  MATCHED: { label: 'Bate com um registo', color: 'green' },
  UNMATCHED: { label: 'Pago sem registo', color: 'red' },
  IGNORED: { label: 'Tratado', color: 'gray' },
};

/**
 * O extrato do cartão de combustível (Sonangol, Pumangol, banco) cruzado
 * com o que os motoristas registaram. O que o cartão pagou e ninguém
 * registou é a pergunta mais barata que uma frota pode fazer.
 */
export function CartoesCombustivel() {
  const queryClient = useQueryClient();
  const [importar, setImportar] = useState(false);
  const { data } = useQuery({
    queryKey: ['fuel-cards'],
    queryFn: () => api<Transacao[]>('/fuel-cards/transactions'),
  });
  const todas = data ?? [];
  const semRegisto = todas.filter((t) => t.status === 'UNMATCHED');
  const tratar = useMutation({
    mutationFn: (t: Transacao) => api(`/fuel-cards/transactions/${t.id}/ignore`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['fuel-cards'] }),
  });

  return (
    <Painel
      titulo="Cartões de combustível"
      acoes={
        <Button size="xs" leftSection={<IconCreditCard size={13} />} onClick={() => setImportar(true)} style={{ marginLeft: 'auto' }}>
          Importar extrato do cartão
        </Button>
      }
    >
      {todas.length === 0 ? (
        <Text size="sm" c="dimmed">
          Importe o extrato mensal da gestora de cartões: cada linha paga é cruzada com os abastecimentos registados. O
          que o cartão pagou sem registo, e o que se registou sem o cartão pagar, abre uma anomalia com o valor em causa.
        </Text>
      ) : (
        <Stack gap="xs">
          <Group gap="lg">
            <Text size="sm">
              <b>{todas.filter((t) => t.status === 'MATCHED').length}</b> a bater
            </Text>
            <Text size="sm" c={semRegisto.length ? 'red' : undefined}>
              <b>{semRegisto.length}</b> pagas sem registo
              {semRegisto.length > 0 && ` · ${fmtMoney(semRegisto.reduce((s, t) => s + (t.amount ?? 0), 0))} por explicar`}
            </Text>
          </Group>
          {semRegisto.length > 0 && (
            <Table striped>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>Quando</Table.Th>
                  <Table.Th>Cartão</Table.Th>
                  <Table.Th>Viatura</Table.Th>
                  <Table.Th style={{ textAlign: 'right' }}>Litros</Table.Th>
                  <Table.Th style={{ textAlign: 'right' }}>Valor</Table.Th>
                  <Table.Th>Posto</Table.Th>
                  <Table.Th></Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {semRegisto.map((t) => (
                  <Table.Tr key={t.id}>
                    <Table.Td>{fmtDateTime(t.transactedAt)}</Table.Td>
                    <Table.Td>{t.cardNumber ?? '—'}</Table.Td>
                    <Table.Td>{t.assetTag ?? <Text span c="dimmed">desconhecida</Text>}</Table.Td>
                    <Table.Td style={{ textAlign: 'right' }}>{t.liters != null ? fmtNumber(t.liters, 0) : '—'}</Table.Td>
                    <Table.Td style={{ textAlign: 'right' }}>{t.amount != null ? fmtMoney(t.amount) : '—'}</Table.Td>
                    <Table.Td>{t.station ?? '—'}</Table.Td>
                    <Table.Td>
                      <Group gap={4} wrap="nowrap">
                        <Badge variant="light" color={ESTADO[t.status].color} size="sm">
                          {ESTADO[t.status].label}
                        </Badge>
                        <Button size="compact-xs" variant="subtle" onClick={() => tratar.mutate(t)}>
                          Tratado
                        </Button>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          )}
        </Stack>
      )}
      <ImportarExtrato aberto={importar} fechar={() => setImportar(false)} />
    </Painel>
  );
}

function ImportarExtrato({ aberto, fechar }: { aberto: boolean; fechar: () => void }) {
  const queryClient = useQueryClient();
  const [ficheiro, setFicheiro] = useState<File | null>(null);
  const [resultado, setResultado] = useState<Resultado | null>(null);
  const enviar = useMutation({
    mutationFn: async (gravar: boolean) => {
      if (!ficheiro) throw new Error('Escolha o ficheiro do extrato.');
      const recusa = checkUploadSize(ficheiro);
      if (recusa) throw new Error(recusa);
      const form = new FormData();
      form.append('file', ficheiro);
      return api<Resultado>(`/imports/fuel-cards?dryRun=${!gravar}`, { method: 'POST', body: form });
    },
    onSuccess: (r) => {
      setResultado(r);
      if (!r.report.dryRun) {
        queryClient.invalidateQueries({ queryKey: ['fuel-cards'] });
        queryClient.invalidateQueries({ queryKey: ['fuel'] });
        notifications.show({
          title: `${r.report.created} transação(ões) importada(s)`,
          message: `${r.matched} a bater · ${r.unmatched} pagas sem registo · ${r.recordsWithoutCard} registo(s) sem pagamento.`,
          color: r.unmatched + r.recordsWithoutCard > 0 ? 'orange' : 'green',
          autoClose: 10000,
        });
      }
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível importar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={aberto} onClose={fechar} title="Importar extrato do cartão de combustível" size="lg">
      <Text size="sm" c="dimmed" mb="sm">
        O ficheiro CSV da gestora de cartões: data e hora, número do cartão, matrícula (se vier), litros, valor, posto. As
        linhas são cruzadas com os abastecimentos registados nas 36 horas à volta (litros a ±5 % ou o mesmo valor).
      </Text>
      <Group align="flex-end" gap="xs" mb="sm">
        <FileInput label="Ficheiro CSV" placeholder="Escolher…" accept=".csv,text/csv" value={ficheiro} onChange={(f: File | null) => { setFicheiro(f); setResultado(null); }} style={{ flex: 1 }} />
        <Button variant="subtle" leftSection={<IconDownload size={14} />} onClick={() => downloadFile('/imports/fuel-cards/template', 'modelo-extrato-cartao.csv').catch(() => undefined)}>
          Modelo
        </Button>
        <Button variant="default" onClick={() => enviar.mutate(false)} loading={enviar.isPending} disabled={!ficheiro}>
          Verificar
        </Button>
        <Button onClick={() => enviar.mutate(true)} loading={enviar.isPending} disabled={!ficheiro || !resultado?.report.dryRun}>
          Importar
        </Button>
      </Group>
      {resultado && (
        <Alert color={resultado.unmatched + resultado.recordsWithoutCard > 0 ? 'orange' : 'green'} variant="light" p="xs">
          <Text size="sm" fw={600}>
            {resultado.report.dryRun ? 'Verificação' : 'Importado'}: {resultado.report.created} de {resultado.report.totalRows} linha(s)
            {resultado.report.skipped ? ` (${resultado.report.skipped} já importadas ou com erro)` : ''}
          </Text>
          <Text size="sm">
            {resultado.matched} a bater com registos · <b>{resultado.unmatched} pagas sem registo</b> · {resultado.recordsWithoutCard} registo(s) que o cartão não pagou
          </Text>
          {resultado.report.dryRun && (
            <Text size="xs" c="dimmed">
              Nada foi gravado. «Importar» grava as transações e abre as anomalias.
            </Text>
          )}
        </Alert>
      )}
      {resultado && resultado.report.errors.length > 0 && (
        <Table mt="xs">
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ width: 60 }}>Linha</Table.Th>
              <Table.Th>O que está mal</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {resultado.report.errors.map((e, i) => (
              <Table.Tr key={i}>
                <Table.Td>{e.line}</Table.Td>
                <Table.Td>{e.message}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Modal>
  );
}
