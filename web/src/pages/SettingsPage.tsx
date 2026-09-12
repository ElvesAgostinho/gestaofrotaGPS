import { Alert, Button, FileButton, FileInput, Group, NumberInput, Stack, Table, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconDownload, IconInfoCircle, IconUpload } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, checkUploadSize } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Painel, SeccaoForm } from '../components/erp';
import { CredenciaisCard } from './settings/CredenciaisCard';
import { IntegrationsCard } from './settings/IntegrationsCard';

interface ImportReport {
  entity: string;
  dryRun: boolean;
  totalRows: number;
  created: number;
  updated: number;
  skipped: number;
  createdReferences: string[];
  errors: { line: number; value?: string | null; message: string }[];
}

export function SettingsPage() {
  const { org, refresh } = useAuth();
  const queryClient = useQueryClient();
  const [name, setName] = useState(org?.name ?? '');
  const [speedLimit, setSpeedLimit] = useState<number | ''>(org?.defaultSpeedLimitKph ?? '');
  const [taxId, setTaxId] = useState(org?.taxId ?? '');
  const [address, setAddress] = useState(org?.address ?? '');
  const [city, setCity] = useState(org?.city ?? '');
  const [phone, setPhone] = useState(org?.phone ?? '');
  const [email, setEmail] = useState(org?.email ?? '');

  // O logótipo vai por multipart, como as fotografias dos ativos.
  const logo = useMutation({
    mutationFn: (ficheiro: File) => {
      const fd = new FormData();
      fd.append('file', ficheiro);
      return api('/organization/logo', { method: 'POST', body: fd });
    },
    onSuccess: async () => {
      notifications.show({ message: 'Logótipo guardado.', color: 'green' });
      await refresh();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });
  const tirarLogo = useMutation({
    mutationFn: () => api('/organization/logo', { method: 'DELETE' }),
    onSuccess: async () => {
      await refresh();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  const save = useMutation({
    mutationFn: () =>
      api('/organization', {
        method: 'PATCH',
        body: {
          name: name.trim() || undefined,
          defaultSpeedLimitKph: speedLimit === '' ? undefined : speedLimit,
          // Vazio apaga; é assim que se tira um NIF escrito por engano.
          taxId,
          address,
          city,
          phone,
          email,
        },
      }),
    onSuccess: async () => {
      notifications.show({ message: 'Definições guardadas.', color: 'green' });
      await refresh();
      queryClient.invalidateQueries();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  return (
    <Stack gap="lg">

      <Painel titulo="Empresa">
        <Stack gap="sm" maw={720}>
          <SeccaoForm
            titulo="Timbre dos impressos"
            descricao="O que vai no cabeçalho das ordens de serviço, guias de transporte, fichas e planos. É a sua empresa que assina, não o software."
          >
            <Group align="flex-start" wrap="nowrap" gap="md">
              <div
                style={{
                  width: 132,
                  height: 80,
                  flexShrink: 0,
                  border: '1px solid var(--erp-moldura)',
                  background: '#fff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  overflow: 'hidden',
                }}
              >
                {org?.logoUrl ? (
                  <img src={org.logoUrl} alt="Logótipo" style={{ maxWidth: 128, maxHeight: 76 }} />
                ) : (
                  <Text size="xs" c="dimmed" ta="center">
                    sem logótipo
                  </Text>
                )}
              </div>
              <Stack gap={6}>
                <FileButton onChange={(f) => f && logo.mutate(f)} accept="image/png,image/jpeg">
                  {(props) => (
                    <Button {...props} size="xs" variant="default" loading={logo.isPending}>
                      {org?.logoUrl ? 'Mudar logótipo' : 'Carregar logótipo'}
                    </Button>
                  )}
                </FileButton>
                {org?.logoUrl && (
                  <Button size="xs" variant="subtle" color="red" onClick={() => tirarLogo.mutate()}>
                    Remover
                  </Button>
                )}
                <Text size="xs" c="dimmed">
                  PNG ou JPEG até 2 MB. Fica com 86 px de largura no papel.
                </Text>
              </Stack>
            </Group>
            <Group grow>
              <TextInput label="Nome" value={name} onChange={(e) => setName(e.currentTarget.value)} />
              <TextInput
                label="NIF"
                placeholder="5401234567"
                value={taxId}
                onChange={(e) => setTaxId(e.currentTarget.value)}
              />
            </Group>
            <Group grow>
              <TextInput label="Morada" value={address} onChange={(e) => setAddress(e.currentTarget.value)} />
              <TextInput label="Cidade" value={city} onChange={(e) => setCity(e.currentTarget.value)} />
            </Group>
            <Group grow>
              <TextInput
                label="Telefone"
                placeholder="+244 9xx xxx xxx"
                value={phone}
                onChange={(e) => setPhone(e.currentTarget.value)}
              />
              <TextInput label="Email" value={email} onChange={(e) => setEmail(e.currentTarget.value)} />
            </Group>
          </SeccaoForm>
          <NumberInput
            label="Limite de velocidade da frota"
            description="Em km/h. Aplica-se aos ativos sem limite próprio. Vazio = sem vigilância."
            min={0}
            max={400}
            value={speedLimit}
            onChange={(v) => setSpeedLimit(typeof v === 'number' ? v : '')}
          />
          <Group>
            <Button onClick={() => save.mutate()} loading={save.isPending}>
              Guardar
            </Button>
          </Group>
        </Stack>
      </Painel>

      {/* Credenciais primeiro: sem elas o resto da integracao nao funciona. */}
      <CredenciaisCard />

      <IntegrationsCard />

      <ImportCard entity="assets" label="ativos" />
      <ImportCard entity="parts" label="peças" />
    </Stack>
  );
}

function ImportCard({ entity, label }: { entity: 'assets' | 'parts'; label: string }) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);

  const upload = useMutation({
    mutationFn: async (dryRun: boolean) => {
      if (!file) throw new Error('Escolha um ficheiro CSV.');
      const recusa = checkUploadSize(file);
      if (recusa) throw new Error(recusa);
      const form = new FormData();
      form.append('file', file);
      // Passa pelo cliente comum: ele deixa o browser escolher o boundary do
      // multipart, renova o token quando expira e traduz uma resposta que não
      // seja JSON (servidor em baixo) numa mensagem legível.
      return api<ImportReport>(`/imports/${entity}?dryRun=${dryRun}`, {
        method: 'POST',
        body: form,
      });
    },
    onSuccess: (result) => {
      setReport(result);
      if (!result.dryRun) {
        queryClient.invalidateQueries();
        notifications.show({
          message: `${result.created} criado(s), ${result.updated} atualizado(s).`,
          color: 'green',
        });
      }
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  return (
    <Painel titulo={`Importar ${label}`}>
      <Group justify="flex-end" mb="md">
        <Button
          component="a"
          href={`/api/v1/imports/${entity}/template`}
          variant="default"
          size="xs"
          leftSection={<IconDownload size={14} />}
        >
          Modelo CSV
        </Button>
      </Group>

      <Stack gap="sm">
        <FileInput
          label="Ficheiro"
          placeholder="Escolha o CSV"
          accept=".csv,text/csv"
          value={file}
          onChange={setFile}
          leftSection={<IconUpload size={16} />}
        />

        <Group>
          {/* Verificar antes de gravar não é um extra: numa folha de 300 linhas é
              a diferença entre corrigir três células e limpar a base de dados. */}
          <Button variant="default" onClick={() => upload.mutate(true)} loading={upload.isPending}>
            Verificar sem gravar
          </Button>
          <Button
            onClick={() => upload.mutate(false)}
            loading={upload.isPending}
            disabled={!!report && report.errors.length > 0 && report.dryRun}
          >
            Importar
          </Button>
        </Group>

        {report && (
          <Alert
            color={report.errors.length ? 'orange' : 'green'}
            variant="light"
            icon={<IconInfoCircle size={18} />}
          >
            <Stack gap="xs">
              <Text size="sm">
                {report.dryRun ? 'Verificação: ' : 'Importado: '}
                {report.totalRows} linha(s) — {report.created} a criar, {report.updated} a
                atualizar, {report.skipped} com erro.
              </Text>

              {report.createdReferences.length > 0 && (
                <Text size="xs">
                  Serão criados também: {report.createdReferences.join('; ')}
                </Text>
              )}

              {report.errors.length > 0 && (
                <Table>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th w={70}>Linha</Table.Th>
                      <Table.Th w={120}>Valor</Table.Th>
                      <Table.Th>Problema</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {report.errors.map((e, i) => (
                      <Table.Tr key={i}>
                        <Table.Td>{e.line}</Table.Td>
                        <Table.Td>{e.value ?? '—'}</Table.Td>
                        <Table.Td>{e.message}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              )}
            </Stack>
          </Alert>
        )}
      </Stack>
    </Painel>
  );
}
