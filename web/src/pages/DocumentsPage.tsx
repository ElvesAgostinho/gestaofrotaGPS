import {
  Alert,
  Anchor,
  Badge,
  SegmentedControl,
  Stack,
  Table,
  Text,
} from '@mantine/core';
import { IconCircleCheck } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { Painel } from '../components/erp';
import { fmtDate } from '../lib/format';

interface DocumentView {
  id: string;
  assetId: string;
  assetTag: string;
  kindLabel: string;
  title: string;
  reference?: string | null;
  issuer?: string | null;
  expiresAt?: string | null;
  state: string;
  expiryLabel?: string | null;
  daysRemaining?: number | null;
  fileUrl?: string | null;
}

const STATE: Record<string, { label: string; color: string }> = {
  CADUCADO: { label: 'Caducado', color: 'red' },
  A_CADUCAR: { label: 'A caducar', color: 'orange' },
  VALIDO: { label: 'Válido', color: 'green' },
  SEM_VALIDADE: { label: 'Sem validade', color: 'gray' },
};

export function DocumentsPage() {
  const [days, setDays] = useState('60');

  const { data } = useQuery({
    queryKey: ['documents', 'expiring', days],
    queryFn: () => api<DocumentView[]>(`/documents/expiring?withinDays=${days}`),
  });

  const expired = (data ?? []).filter((d) => d.state === 'CADUCADO');

  return (
    <Stack gap="lg">

      {expired.length > 0 && (
        <Alert color="red" variant="light">
          <b>{expired.length} documento(s) caducado(s).</b> Um seguro fora de prazo imobiliza a
          viatura e uma inspeção caducada é uma multa à espera de acontecer.
        </Alert>
      )}

      <Painel
        titulo="Documentos e validades"
        semPadding
        acoes={
          <>
            <SegmentedControl
            size="xs"
            value={days}
            onChange={setDays}
            data={[
            { value: '30', label: '30 dias' },
            { value: '60', label: '60 dias' },
            { value: '180', label: '6 meses' },
            { value: '3650', label: 'Todos' },
            ]}
            />
          </>
        }
      >
        <Table.ScrollContainer minWidth={800}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Ativo</Table.Th>
                <Table.Th>Tipo</Table.Th>
                <Table.Th>Título</Table.Th>
                <Table.Th>Referência</Table.Th>
                <Table.Th>Emissor</Table.Th>
                <Table.Th>Validade</Table.Th>
                <Table.Th>Estado</Table.Th>
                <Table.Th>Ficheiro</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {(data ?? []).length === 0 && (
                <Table.Tr>
                  <Table.Td colSpan={8}>
                    <Alert
                      color="green"
                      variant="light"
                      icon={<IconCircleCheck size={18} />}
                      m="sm"
                    >
                      Nada a caducar nesta janela.
                    </Alert>
                  </Table.Td>
                </Table.Tr>
              )}
              {(data ?? []).map((d) => (
                <Table.Tr key={d.id}>
                  <Table.Td>
                    <Text component={Link} to={`/ativos/${d.assetId}`} fw={600} size="sm">
                      {d.assetTag}
                    </Text>
                  </Table.Td>
                  <Table.Td>{d.kindLabel}</Table.Td>
                  <Table.Td>{d.title}</Table.Td>
                  <Table.Td>{d.reference ?? '—'}</Table.Td>
                  <Table.Td>{d.issuer ?? '—'}</Table.Td>
                  <Table.Td>{fmtDate(d.expiresAt)}</Table.Td>
                  <Table.Td>
                    <Badge variant="light" color={STATE[d.state]?.color}>
                      {d.expiryLabel ?? STATE[d.state]?.label}
                    </Badge>
                  </Table.Td>
                  <Table.Td>
                    {d.fileUrl ? (
                      <Anchor href={d.fileUrl} target="_blank" size="sm">
                        Abrir
                      </Anchor>
                    ) : (
                      '—'
                    )}
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
            </Painel>
    </Stack>
  );
}
