import { Alert, Badge, Table, Text } from '@mantine/core';
import { IconCrystalBall } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { Painel } from './erp';
import { fmtDate, fmtNumber } from '../lib/format';

export interface Previsao {
  assetId: string;
  assetTag: string;
  assetName: string;
  systemCode: string;
  systemLabel: string;
  failures: number;
  meanIntervalDays?: number | null;
  meanIntervalMeter?: number | null;
  meterUnit?: string | null;
  lastFailureAt: string;
  predictedAt: string;
  predictedMeter?: number | null;
  daysLeft?: number | null;
  confidence: 'LOW' | 'MEDIUM' | 'HIGH';
  risk: 'LOW' | 'MEDIUM' | 'HIGH';
  reasons: string[];
  suggestion: string;
}

const COR: Record<string, string> = { HIGH: 'red', MEDIUM: 'orange', LOW: 'gray' };
const CONF: Record<string, string> = { HIGH: 'alta', MEDIUM: 'média', LOW: 'baixa' };

/**
 * «Próximas avarias prováveis»: por ativo e sistema, a partir das avarias
 * repetidas, do ritmo de uso (GPS/leituras) e do consumo anómalo. Só aparece
 * o que os dados sustentam — com a confiança à vista.
 */
export function PrevisaoAvarias({ assetId }: { assetId?: string }) {
  const { data, isLoading } = useQuery({
    queryKey: ['predictive', 'forecast', assetId ?? 'org'],
    queryFn: () => api<Previsao[]>(assetId ? `/assets/${assetId}/predictive/forecast` : '/predictive/forecast'),
  });
  const lista = data ?? [];
  return (
    <Painel titulo="Próximas avarias prováveis" semPadding={lista.length > 0}>
      {!isLoading && lista.length === 0 && (
        <Alert color="gray" variant="light" icon={<IconCrystalBall size={18} />}>
          Ainda não há avarias repetidas no mesmo sistema {assetId ? 'nesta viatura' : 'na frota'} — é o que permite
          prever a próxima. Registe o sistema (motor, travões…) em cada avaria corretiva.
        </Alert>
      )}
      {lista.length > 0 && (
        <Table.ScrollContainer minWidth={820}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                {!assetId && <Table.Th>Ativo</Table.Th>}
                <Table.Th>Sistema</Table.Th>
                <Table.Th>Avarias</Table.Th>
                <Table.Th>Intervalo médio</Table.Th>
                <Table.Th>Previsão</Table.Th>
                <Table.Th>Risco</Table.Th>
                <Table.Th>Porquê e o que fazer</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {lista.map((p) => (
                <Table.Tr key={p.assetId + p.systemCode}>
                  {!assetId && (
                    <Table.Td>
                      <Text component={Link} to={`/ativos/${p.assetId}?tab=preditiva`} fw={700} size="sm" c="var(--erp-dourado-escuro)">
                        {p.assetTag}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {p.assetName}
                      </Text>
                    </Table.Td>
                  )}
                  <Table.Td fw={600}>{p.systemLabel}</Table.Td>
                  <Table.Td>
                    {p.failures}
                    <Text size="xs" c="dimmed">
                      última {fmtDate(p.lastFailureAt)}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    {p.meanIntervalDays} dias
                    {p.meanIntervalMeter != null && (
                      <Text size="xs" c="dimmed">
                        {fmtNumber(p.meanIntervalMeter, 0)} {p.meterUnit}
                      </Text>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Text fw={700} c={p.daysLeft != null && p.daysLeft <= 14 ? 'red' : undefined}>
                      {fmtDate(p.predictedAt)}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {p.daysLeft != null && p.daysLeft < 0
                        ? `há ${-p.daysLeft} dia(s)`
                        : `em ${p.daysLeft} dia(s)`}
                      {p.predictedMeter != null ? ` · aos ${fmtNumber(p.predictedMeter, 0)} ${p.meterUnit}` : ''}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Badge color={COR[p.risk]} variant="light">
                      {p.risk === 'HIGH' ? 'alto' : p.risk === 'MEDIUM' ? 'médio' : 'baixo'}
                    </Badge>
                    <Text size="xs" c="dimmed">
                      confiança {CONF[p.confidence]}
                    </Text>
                  </Table.Td>
                  <Table.Td style={{ maxWidth: 380 }}>
                    <Text size="xs">{p.reasons.join('; ')}.</Text>
                    <Text size="xs" fw={600} mt={2}>
                      {p.suggestion}
                    </Text>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
    </Painel>
  );
}
