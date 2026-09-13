import {
  Alert,
  Badge,
  Button,
  Card,
  Chip,
  FileButton,
  Group,
  Select,
  SimpleGrid,
  Stack,
  Stepper,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCheck, IconDownload, IconPhoto, IconSatellite, IconUpload } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, downloadFile } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { ImportarCsv } from '../components/ImportarCsv';

interface TipoAtivo {
  id: string;
  name: string;
  category?: string | null;
}

interface AssetLite {
  id: string;
  tag: string;
  name: string;
}

/** Tipos que quase toda a frota angolana tem; cada um cria-se com um toque. */
const SUGESTOES: { name: string; category: string; primaryMeter: 'ODOMETER' | 'HOURMETER' }[] = [
  { name: 'Camião', category: 'VEHICLE', primaryMeter: 'ODOMETER' },
  { name: 'Ligeiro', category: 'VEHICLE', primaryMeter: 'ODOMETER' },
  { name: 'Carrinha de caixa aberta', category: 'VEHICLE', primaryMeter: 'ODOMETER' },
  { name: 'Autocarro', category: 'VEHICLE', primaryMeter: 'ODOMETER' },
  { name: 'Reboque', category: 'VEHICLE', primaryMeter: 'ODOMETER' },
  { name: 'Escavadora', category: 'MACHINE', primaryMeter: 'HOURMETER' },
  { name: 'Retroescavadora', category: 'MACHINE', primaryMeter: 'HOURMETER' },
  { name: 'Pá carregadora', category: 'MACHINE', primaryMeter: 'HOURMETER' },
  { name: 'Cilindro', category: 'MACHINE', primaryMeter: 'HOURMETER' },
  { name: 'Gerador', category: 'GENERATOR', primaryMeter: 'HOURMETER' },
];

/**
 * Assistente de primeira utilização: cinco passos que deixam a empresa a
 * trabalhar — empresa, timbre, tipos, viaturas, primeiro rastreador. Cada
 * passo grava no mesmo sítio onde as Definições gravam; não há nada aqui
 * que não se possa refazer depois nos menus. Pode-se saltar tudo.
 */
export function PrimeirosPassosPage() {
  const { org, refresh } = useAuth();
  const navigate = useNavigate();
  const [passo, setPasso] = useState(0);

  const concluir = useMutation({
    mutationFn: () => api('/organization/onboarding/done', { method: 'POST' }),
    onSuccess: async () => {
      await refresh();
      navigate('/');
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  return (
    <Stack gap="lg" maw={980} mx="auto">
      <div>
        <Title order={1} size="h2">
          Bem-vindo ao IMBONDEIRO OS
        </Title>
        <Text c="dimmed">
          Cinco passos e a frota fica a trabalhar. Tudo o que fizer aqui pode mudar depois nas Definições.
        </Text>
      </div>

      <Stepper active={passo} onStepClick={setPasso} allowNextStepsSelect size="sm">
        <Stepper.Step label="Empresa" description="Nome e NIF">
          <PassoEmpresa avancar={() => setPasso(1)} />
        </Stepper.Step>
        <Stepper.Step label="Timbre" description="Logótipo nos impressos">
          <PassoTimbre avancar={() => setPasso(2)} />
        </Stepper.Step>
        <Stepper.Step label="Tipos" description="Camiões, máquinas…">
          <PassoTipos avancar={() => setPasso(3)} />
        </Stepper.Step>
        <Stepper.Step label="Viaturas" description="Importar ou criar">
          <PassoAtivos avancar={() => setPasso(4)} />
        </Stepper.Step>
        <Stepper.Step label="Rastreador" description="O primeiro GPS">
          <PassoRastreador />
        </Stepper.Step>
        <Stepper.Completed>
          <Card withBorder p="lg">
            <Stack align="center">
              <IconCheck size={40} style={{ color: 'var(--mantine-color-green-6)' }} />
              <Text fw={700}>Pronto.</Text>
            </Stack>
          </Card>
        </Stepper.Completed>
      </Stepper>

      <Group justify="space-between">
        <Button variant="subtle" color="gray" onClick={() => concluir.mutate()} loading={concluir.isPending}>
          Saltar o assistente
        </Button>
        <Group>
          {passo > 0 && (
            <Button variant="default" onClick={() => setPasso(passo - 1)}>
              Anterior
            </Button>
          )}
          {passo >= 4 ? (
            <Button onClick={() => concluir.mutate()} loading={concluir.isPending} leftSection={<IconCheck size={16} />}>
              Concluir e ir para o painel
            </Button>
          ) : (
            <Button variant="default" onClick={() => setPasso(passo + 1)}>
              Saltar este passo
            </Button>
          )}
        </Group>
      </Group>
      {org && (
        <Text size="xs" c="dimmed" ta="right">
          {org.assetTypeCount ?? 0} tipo(s) · {org.assetCount} viatura(s) registada(s)
        </Text>
      )}
    </Stack>
  );
}

function PassoEmpresa({ avancar }: { avancar: () => void }) {
  const { org, refresh } = useAuth();
  const [name, setName] = useState(org?.name ?? '');
  const [taxId, setTaxId] = useState(org?.taxId ?? '');
  const [address, setAddress] = useState(org?.address ?? '');
  const [city, setCity] = useState(org?.city ?? '');
  const [phone, setPhone] = useState(org?.phone ?? '');
  const [email, setEmail] = useState(org?.email ?? '');
  const guardar = useMutation({
    mutationFn: () => api('/organization', { method: 'PATCH', body: { name, taxId, address, city, phone, email } }),
    onSuccess: async () => {
      await refresh();
      avancar();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });
  return (
    <Card withBorder p="lg" mt="md">
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          O que sai nos impressos (ordens, guias, relatórios) e o que identifica a empresa nas faturas dos fornecedores.
        </Text>
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          <TextInput label="Nome da empresa" value={name} onChange={(e) => setName(e.currentTarget.value)} required />
          <TextInput label="NIF" value={taxId} onChange={(e) => setTaxId(e.currentTarget.value)} placeholder="5000000000" />
          <TextInput label="Morada" value={address} onChange={(e) => setAddress(e.currentTarget.value)} />
          <TextInput label="Cidade" value={city} onChange={(e) => setCity(e.currentTarget.value)} placeholder="Luanda" />
          <TextInput label="Telefone" value={phone} onChange={(e) => setPhone(e.currentTarget.value)} placeholder="+244 …" />
          <TextInput label="Email" value={email} onChange={(e) => setEmail(e.currentTarget.value)} />
        </SimpleGrid>
        <Group justify="flex-end">
          <Button onClick={() => guardar.mutate()} loading={guardar.isPending} disabled={name.trim().length < 2}>
            Guardar e continuar
          </Button>
        </Group>
      </Stack>
    </Card>
  );
}

function PassoTimbre({ avancar }: { avancar: () => void }) {
  const { org, refresh } = useAuth();
  const logo = useMutation({
    mutationFn: (f: File) => {
      const fd = new FormData();
      fd.append('file', f);
      return api('/organization/logo', { method: 'POST', body: fd });
    },
    onSuccess: async () => {
      notifications.show({ message: 'Logótipo guardado.', color: 'green' });
      await refresh();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });
  return (
    <Card withBorder p="lg" mt="md">
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          O logótipo aparece no cabeçalho de todos os documentos impressos, ao lado do nome, NIF e morada. PNG ou JPG,
          de preferência com fundo branco.
        </Text>
        <Group align="center">
          {org?.logoUrl ? (
            <img src={org.logoUrl} alt="Logótipo" style={{ maxWidth: 160, maxHeight: 90, border: '1px solid #ddd', padding: 4 }} />
          ) : (
            <Text size="sm" c="dimmed">
              Ainda sem logótipo.
            </Text>
          )}
          <FileButton onChange={(f) => f && logo.mutate(f)} accept="image/png,image/jpeg,image/webp">
            {(props) => (
              <Button {...props} variant="light" leftSection={<IconPhoto size={16} />} loading={logo.isPending}>
                {org?.logoUrl ? 'Trocar logótipo' : 'Carregar logótipo'}
              </Button>
            )}
          </FileButton>
        </Group>
        <Group justify="flex-end">
          <Button onClick={avancar}>Continuar</Button>
        </Group>
      </Stack>
    </Card>
  );
}

function PassoTipos({ avancar }: { avancar: () => void }) {
  const queryClient = useQueryClient();
  const { refresh } = useAuth();
  const { data: tipos } = useQuery({ queryKey: ['asset-types'], queryFn: () => api<TipoAtivo[]>('/asset-types') });
  const [outro, setOutro] = useState('');
  const [contadorOutro, setContadorOutro] = useState<string | null>('ODOMETER');
  const existentes = new Set((tipos ?? []).map((t) => t.name.toLowerCase()));
  const criar = useMutation({
    mutationFn: (s: { name: string; category: string; primaryMeter: string }) => api('/asset-types', { method: 'POST', body: s }),
    onSuccess: async () => {
      queryClient.invalidateQueries({ queryKey: ['asset-types'] });
      await refresh();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });
  return (
    <Card withBorder p="lg" mt="md">
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          Os tipos definem o contador (km ou horas) e agrupam os planos de manutenção. Toque nos que a sua frota tem.
        </Text>
        <Group gap="xs">
          {SUGESTOES.map((s) => {
            const tem = existentes.has(s.name.toLowerCase());
            return (
              <Chip key={s.name} checked={tem} disabled={tem || criar.isPending} onChange={() => criar.mutate(s)} variant="light">
                {s.name} <Text span size="xs" c="dimmed">({s.primaryMeter === 'HOURMETER' ? 'horas' : 'km'})</Text>
              </Chip>
            );
          })}
        </Group>
        <Group align="flex-end">
          <TextInput label="Outro tipo" placeholder="Ex.: Empilhador" value={outro} onChange={(e) => setOutro(e.currentTarget.value)} />
          <Select label="Contador" data={[{ value: 'ODOMETER', label: 'km' }, { value: 'HOURMETER', label: 'horas' }]} value={contadorOutro} onChange={setContadorOutro} allowDeselect={false} w={110} />
          <Button
            variant="default"
            disabled={!outro.trim()}
            onClick={() => {
              criar.mutate({ name: outro.trim(), category: contadorOutro === 'HOURMETER' ? 'MACHINE' : 'VEHICLE', primaryMeter: contadorOutro ?? 'ODOMETER' });
              setOutro('');
            }}
          >
            Adicionar
          </Button>
        </Group>
        {tipos && tipos.length > 0 && (
          <Group gap={6}>
            <Text size="sm">Já tem:</Text>
            {tipos.map((t) => (
              <Badge key={t.id} variant="outline">
                {t.name}
              </Badge>
            ))}
          </Group>
        )}
        <Group justify="flex-end">
          <Button onClick={avancar} disabled={!tipos || tipos.length === 0}>
            Continuar
          </Button>
        </Group>
      </Stack>
    </Card>
  );
}

function PassoAtivos({ avancar }: { avancar: () => void }) {
  const queryClient = useQueryClient();
  const { org, refresh } = useAuth();
  const { data: tipos } = useQuery({ queryKey: ['asset-types'], queryFn: () => api<TipoAtivo[]>('/asset-types') });
  const [importar, setImportar] = useState(false);
  const [tag, setTag] = useState('');
  const [nome, setNome] = useState('');
  const [tipo, setTipo] = useState<string | null>(null);
  const [plate, setPlate] = useState('');
  useEffect(() => {
    if (!tipo && tipos && tipos.length > 0) setTipo(tipos[0].id);
  }, [tipos, tipo]);
  const criar = useMutation({
    mutationFn: () => api('/assets', { method: 'POST', body: { tag: tag.trim(), name: nome.trim(), assetTypeId: tipo, plate: plate.trim() || undefined } }),
    onSuccess: async () => {
      notifications.show({ message: `${tag} registada.`, color: 'green' });
      setTag('');
      setNome('');
      setPlate('');
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      await refresh();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });
  return (
    <Card withBorder p="lg" mt="md">
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          Tem a frota numa folha de Excel? Descarregue o modelo, preencha e importe. Ou registe as primeiras viaturas à mão.
        </Text>
        <Group>
          <Button variant="light" leftSection={<IconDownload size={16} />} onClick={() => void downloadFile('/imports/assets/template', 'modelo-ativos.csv')}>
            Descarregar modelo CSV
          </Button>
          <Button variant="light" leftSection={<IconUpload size={16} />} onClick={() => setImportar(true)}>
            Importar ficheiro
          </Button>
        </Group>
        <ImportarCsv
          aberto={importar}
          fechar={() => {
            setImportar(false);
            void refresh();
          }}
          titulo="Importar viaturas"
          explicacao="Uma linha por viatura: etiqueta, nome, tipo, matrícula, contador. Primeiro faz-se uma simulação — nada é gravado sem confirmar."
          rota="/imports/assets"
          modelo="/imports/assets/template"
          nomeDoModelo="modelo-ativos.csv"
          invalidar={[['assets'], ['organization']]}
          substantivo="viatura(s)"
        />
        <Text size="sm" fw={600} mt="xs">
          Ou registar uma agora
        </Text>
        <SimpleGrid cols={{ base: 1, sm: 4 }}>
          <TextInput label="Etiqueta" placeholder="CAM-001" value={tag} onChange={(e) => setTag(e.currentTarget.value)} />
          <TextInput label="Nome" placeholder="Camião basculante Volvo" value={nome} onChange={(e) => setNome(e.currentTarget.value)} />
          <Select label="Tipo" data={(tipos ?? []).map((t) => ({ value: t.id, label: t.name }))} value={tipo} onChange={setTipo} />
          <TextInput label="Matrícula" placeholder="LD-11-22-AA" value={plate} onChange={(e) => setPlate(e.currentTarget.value)} />
        </SimpleGrid>
        <Group justify="space-between">
          <Button variant="default" onClick={() => criar.mutate()} loading={criar.isPending} disabled={!tag.trim() || !nome.trim() || !tipo}>
            Registar viatura
          </Button>
          <Group gap="sm">
            <Text size="sm" c="dimmed">
              {org?.assetCount ?? 0} registada(s)
            </Text>
            <Button onClick={avancar}>Continuar</Button>
          </Group>
        </Group>
      </Stack>
    </Card>
  );
}

function PassoRastreador() {
  const { data: ativos } = useQuery({
    queryKey: ['assets', 'lite'],
    queryFn: () => api<{ content: AssetLite[] }>('/assets?size=200'),
  });
  const [imei, setImei] = useState('');
  const [ativo, setAtivo] = useState<string | null>(null);
  const [resultado, setResultado] = useState<{ traccarNote?: string | null } | null>(null);
  const registar = useMutation({
    mutationFn: () => api<{ traccarNote?: string | null }>('/gps-devices', { method: 'POST', body: { externalId: imei.trim(), assetId: ativo, provider: 'TRACCAR' } }),
    onSuccess: (r) => {
      setResultado(r);
      setImei('');
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });
  return (
    <Card withBorder p="lg" mt="md">
      <Stack gap="sm">
        <Text size="sm" c="dimmed">
          O IMEI está na etiqueta do aparelho (15 dígitos). Registe-o aqui e associe-o à viatura; o instalador
          configura o aparelho para o servidor indicado em Definições → Rastreadores GPS. Sem GPS, o sistema funciona
          na mesma com os contadores lidos à mão.
        </Text>
        <Group align="flex-end">
          <TextInput label="IMEI do aparelho" placeholder="86xxxxxxxxxxxxx" value={imei} onChange={(e) => setImei(e.currentTarget.value)} w={220} />
          <Select
            label="Viatura"
            data={(ativos?.content ?? []).map((a) => ({ value: a.id, label: `${a.tag} — ${a.name}` }))}
            value={ativo}
            onChange={setAtivo}
            searchable
            w={300}
          />
          <Button leftSection={<IconSatellite size={16} />} onClick={() => registar.mutate()} loading={registar.isPending} disabled={imei.trim().length < 5 || !ativo}>
            Registar rastreador
          </Button>
        </Group>
        {resultado && (
          <Alert color="green" variant="light" icon={<IconCheck size={16} />}>
            Rastreador registado.{resultado.traccarNote ? ' ' + resultado.traccarNote : ''}
          </Alert>
        )}
      </Stack>
    </Card>
  );
}
