import { Alert, Button, FileInput, Group, Modal, Table, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconDownload } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, checkUploadSize, downloadFile } from '../api/client';

export interface RelatorioImportacao {
  entity: string;
  dryRun: boolean;
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  errors: { line: number; value?: string | null; message: string }[];
}

/**
 * Importação de um CSV com verificação obrigatória antes de gravar.
 *
 * <p>Uma folha de trezentas linhas com uma coluna errada, gravada de uma vez,
 * dá trezentos registos por apagar à mão. Por isso «Verificar» é o primeiro
 * botão e «Importar» só acende depois do relatório.
 */
export function ImportarCsv({
  aberto,
  fechar,
  titulo,
  explicacao,
  rota,
  modelo,
  nomeDoModelo,
  invalidar,
  substantivo,
}: {
  aberto: boolean;
  fechar: () => void;
  titulo: string;
  explicacao: string;
  /** Rota da API, sem o dryRun: `/imports/work-orders`. */
  rota: string;
  /** Rota do modelo CSV: `/imports/work-orders/template`. */
  modelo: string;
  nomeDoModelo: string;
  /** Chaves de cache a invalidar depois de gravar. */
  invalidar: string[][];
  /** «ordem(ns)», «abastecimento(s)»… para a notificação. */
  substantivo: string;
}) {
  const queryClient = useQueryClient();
  const [ficheiro, setFicheiro] = useState<File | null>(null);
  const [relatorio, setRelatorio] = useState<RelatorioImportacao | null>(null);

  const enviar = useMutation({
    mutationFn: async (gravar: boolean) => {
      if (!ficheiro) throw new Error('Escolha um ficheiro CSV.');
      const recusa = checkUploadSize(ficheiro);
      if (recusa) throw new Error(recusa);
      const form = new FormData();
      form.append('file', ficheiro);
      return api<RelatorioImportacao>(`${rota}?dryRun=${!gravar}`, { method: 'POST', body: form });
    },
    onSuccess: (r) => {
      setRelatorio(r);
      if (!r.dryRun) {
        invalidar.forEach((k) => queryClient.invalidateQueries({ queryKey: k }));
        notifications.show({
          title: `${r.created} ${substantivo} importado(s)`,
          message: r.errors.length ? `${r.errors.length} linha(s) por corrigir.` : '',
          color: r.errors.length ? 'yellow' : 'green',
        });
      }
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível importar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={aberto} onClose={fechar} title={titulo} size="lg">
      <Text size="sm" c="dimmed" mb="sm">
        {explicacao}
      </Text>
      <Group align="flex-end" gap="xs" mb="sm">
        <FileInput
          label="Ficheiro CSV"
          placeholder="Escolher…"
          accept=".csv,text/csv"
          value={ficheiro}
          onChange={(f: File | null) => {
            setFicheiro(f);
            setRelatorio(null);
          }}
          style={{ flex: 1 }}
        />
        <Button variant="subtle" leftSection={<IconDownload size={14} />} onClick={() => downloadFile(modelo, nomeDoModelo).catch(() => undefined)}>
          Modelo
        </Button>
        <Button variant="default" onClick={() => enviar.mutate(false)} loading={enviar.isPending} disabled={!ficheiro}>
          Verificar
        </Button>
        <Button onClick={() => enviar.mutate(true)} loading={enviar.isPending} disabled={!ficheiro || !relatorio || !relatorio.dryRun}>
          Importar
        </Button>
      </Group>

      {relatorio && (
        <Alert color={relatorio.errors.length ? 'yellow' : 'green'} variant="light" p="xs" mb="xs">
          <Text size="sm" fw={600}>
            {relatorio.dryRun ? 'Verificação' : 'Importado'}: {relatorio.created} de {relatorio.totalRows} linha(s)
            {relatorio.errors.length ? `, ${relatorio.errors.length} por corrigir` : ''}
          </Text>
          {relatorio.dryRun && (
            <Text size="xs" c="dimmed">
              Nada foi gravado ainda. Carregue em «Importar» para confirmar as linhas que passaram.
            </Text>
          )}
        </Alert>
      )}

      {relatorio && relatorio.errors.length > 0 && (
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th style={{ width: 60 }}>Linha</Table.Th>
              <Table.Th style={{ width: 120 }}>Valor</Table.Th>
              <Table.Th>O que está mal</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {relatorio.errors.map((e, i) => (
              <Table.Tr key={i}>
                <Table.Td>{e.line}</Table.Td>
                <Table.Td>{e.value ?? '—'}</Table.Td>
                <Table.Td>{e.message}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
    </Modal>
  );
}
