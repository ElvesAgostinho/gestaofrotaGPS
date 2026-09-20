import { Badge, Card, Collapse, Group, Loader, Stack, Table, Text, UnstyledButton } from '@mantine/core';
import { IconChevronDown, IconChevronRight } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { fmtDateTime, fmtNumber } from '../../lib/format';
import { BotaoFichaDoPosto, InspecaoDiariaFicha } from './FichaTecnica';

interface Resultado {
  text: string;
  critical: boolean;
  result: 'OK' | 'NOT_OK' | 'NA';
  note?: string | null;
}

interface Execucao {
  id: string;
  templateName: string;
  performedByLabel?: string | null;
  performedAt: string;
  meterValue?: number | null;
  outcome: 'OK' | 'ISSUES';
  notes?: string | null;
  itemsOk: number;
  itemsNotOk: number;
  items: Resultado[];
}

/**
 * As inspeções feitas a este equipamento — sobretudo as diárias, que o
 * motorista faz no telemóvel antes de sair. Quem gere vê aqui o que foi
 * verificado, por quem, e o que estava mal.
 */
export function InspecoesTab({ assetId }: { assetId: string }) {
  const [aberta, setAberta] = useState<string | null>(null);
  const { data, isLoading } = useQuery({
    queryKey: ['asset', assetId, 'inspecoes'],
    queryFn: () => api<{ content: Execucao[] }>(`/assets/${assetId}/checklist-executions?size=60`),
  });
  const lista = data?.content ?? [];

  if (isLoading) return <Loader />;
  return (
    <Stack gap="lg">
      <Group justify="flex-end">
        <BotaoFichaDoPosto assetId={assetId} />
      </Group>
      <InspecaoDiariaFicha assetId={assetId} />

      <Stack gap={6}>
        <Text fw={700} size="sm" tt="uppercase" c="dimmed">
          Inspeções feitas
        </Text>
        {lista.length === 0 && (
          <Text c="dimmed" size="sm">
            Ainda nenhuma foi executada. O motorista faz a inspeção diária na app do telemóvel (Modo telemóvel →
            Inspeção) e o resultado aparece aqui.
          </Text>
        )}
        {lista.map((e) => (
          <Card key={e.id} padding="sm" radius="md" withBorder>
            <UnstyledButton onClick={() => setAberta(aberta === e.id ? null : e.id)} style={{ width: '100%' }}>
              <Group justify="space-between" wrap="nowrap">
                <Group gap="sm" wrap="nowrap">
                  {aberta === e.id ? <IconChevronDown size={16} /> : <IconChevronRight size={16} />}
                  <div>
                    <Group gap={6}>
                      <Text fw={600} size="sm">
                        {e.templateName}
                      </Text>
                      <Badge size="xs" color={e.outcome === 'OK' ? 'green' : 'red'} variant="light">
                        {e.outcome === 'OK' ? 'sem problemas' : `${e.itemsNotOk} reprovado(s)`}
                      </Badge>
                    </Group>
                    <Text size="xs" c="dimmed">
                      {fmtDateTime(e.performedAt)}
                      {e.performedByLabel ? ` · ${e.performedByLabel}` : ''}
                      {e.meterValue != null ? ` · contador ${fmtNumber(e.meterValue, 0)}` : ''}
                    </Text>
                  </div>
                </Group>
                <Text size="xs" c="dimmed">
                  {e.itemsOk} OK
                </Text>
              </Group>
            </UnstyledButton>
            <Collapse in={aberta === e.id}>
              <Table mt="sm" fz="sm">
                <Table.Tbody>
                  {e.items.map((i, idx) => (
                    <Table.Tr key={idx}>
                      <Table.Td>
                        {i.text}
                        {i.critical && (
                          <Badge size="xs" ml={6} color="red" variant="outline">
                            crítico
                          </Badge>
                        )}
                      </Table.Td>
                      <Table.Td w={90}>
                        <Badge
                          size="xs"
                          color={i.result === 'OK' ? 'green' : i.result === 'NOT_OK' ? 'red' : 'gray'}
                          variant="light"
                        >
                          {i.result === 'OK' ? 'OK' : i.result === 'NOT_OK' ? 'Não OK' : 'N/A'}
                        </Badge>
                      </Table.Td>
                      <Table.Td c="dimmed">{i.note ?? ''}</Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
              {e.notes && (
                <Text size="sm" mt="xs" c="dimmed">
                  {e.notes}
                </Text>
              )}
            </Collapse>
          </Card>
        ))}
      </Stack>
    </Stack>
  );
}
