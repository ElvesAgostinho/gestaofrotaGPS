import {
  Alert,
  Badge,
  Button,
  Code,
  CopyButton,
  Group,
  Modal,
  PasswordInput,
  Stack,
  Switch,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { notifications } from '@mantine/notifications';
import {
  IconBuildingSkyscraper,
  IconCopy,
  IconKey,
  IconLockOpen,
  IconLock,
  IconPencil,
  IconPlus,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { api } from '../api/client';
import { BotaoBarra, Painel, SeccaoForm, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { Kpi } from '../components/Kpi';
import { CampoProcura, filtrar } from '../components/Procura';

type Estado = 'ACTIVE' | 'EXPIRING' | 'EXPIRED' | 'SUSPENDED';

interface Empresa {
  id: string;
  name: string;
  taxId?: string | null;
  city?: string | null;
  createdAt: string;
  status: Estado;
  licenseUntil?: string | null;
  suspendedAt?: string | null;
  suspendedReason?: string | null;
  platformNotes?: string | null;
  ownerName?: string | null;
  ownerEmail?: string | null;
  memberCount: number;
  assetCount: number;
  workOrderCount: number;
  lastActivityAt?: string | null;
}

interface Resumo {
  organizations: number;
  active: number;
  expiring: number;
  expired: number;
  suspended: number;
  users: number;
  assets: number;
}

interface Criada {
  organization: Empresa;
  ownerEmail: string;
  temporaryPassword?: string | null;
  ownerExisted: boolean;
}

const ESTADO: Record<Estado, { label: string; color: string }> = {
  ACTIVE: { label: 'Ativa', color: 'green' },
  EXPIRING: { label: 'Licença a vencer', color: 'yellow' },
  EXPIRED: { label: 'Licença vencida', color: 'red' },
  SUSPENDED: { label: 'Suspensa', color: 'gray' },
};

function data(iso?: string | null) {
  if (!iso) return null;
  const d = new Date(iso);
  return isNaN(d.getTime()) ? iso : d.toLocaleDateString('pt-PT');
}

function paraIso(d: Date | null): string | null {
  if (!d) return null;
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${dia}`;
}

function deIso(s?: string | null): Date | null {
  if (!s) return null;
  const [a, m, d] = s.split('-').map(Number);
  return new Date(a, m - 1, d);
}

/**
 * O ecrã do dono do sistema: as empresas clientes.
 *
 * <p>Só o administrador da plataforma chega aqui (o servidor exige o papel
 * ADMIN em /api/v1/admin/**). É aqui que se vende o sistema: cria-se a
 * empresa, o Dono dela recebe uma palavra-passe temporária, e a licença tem
 * um prazo. Nada aqui apaga dados — suspender só trava os utilizadores.
 */
export function PlataformaPage() {
  const [nova, setNova] = useState(false);
  const [editar, setEditar] = useState<Empresa | null>(null);
  const [suspender, setSuspender] = useState<Empresa | null>(null);
  const [credenciais, setCredenciais] = useState<{ email: string; password: string; titulo: string } | null>(null);
  const [procura, setProcura] = useState('');
  const queryClient = useQueryClient();

  const { data: lista, isLoading } = useQuery({
    queryKey: ['plataforma', 'organizations'],
    queryFn: () => api<{ items: Empresa[] }>('/admin/platform/organizations'),
  });
  const { data: resumo } = useQuery({
    queryKey: ['plataforma', 'summary'],
    queryFn: () => api<Resumo>('/admin/platform/summary'),
  });

  const todas = lista?.items ?? [];
  const rows = useMemo(
    () => filtrar(todas, procura, (e) => [e.name, e.taxId, e.city, e.ownerName, e.ownerEmail, ESTADO[e.status].label]),
    [todas, procura],
  );

  const invalidar = () => queryClient.invalidateQueries({ queryKey: ['plataforma'] });

  const reativar = useMutation({
    mutationFn: (e: Empresa) => api(`/admin/platform/organizations/${e.id}/activate`, { method: 'POST' }),
    onSuccess: (_r, e) => {
      notifications.show({ title: 'Empresa reativada', message: e.name, color: 'green' });
      invalidar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível reativar', message: e.message, color: 'red' }),
  });

  const reporPassword = useMutation({
    mutationFn: (e: Empresa) =>
      api<{ ownerEmail: string; temporaryPassword: string }>(
        `/admin/platform/organizations/${e.id}/owner-password`,
        { method: 'POST' },
      ),
    onSuccess: (r, e) => {
      setCredenciais({ email: r.ownerEmail, password: r.temporaryPassword, titulo: `Nova palavra-passe do Dono — ${e.name}` });
      invalidar();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível repor', message: e.message, color: 'red' }),
  });

  return (
    <Stack gap="lg">
      <Group gap="sm" wrap="wrap">
        <Kpi label="Empresas" value={resumo?.organizations ?? '—'} tone="brand" />
        <Kpi label="Ativas" value={resumo?.active ?? '—'} tone="good" />
        <Kpi
          label="Licença a vencer"
          value={resumo?.expiring ?? '—'}
          tone={resumo?.expiring ? 'warning' : 'neutral'}
          hint="Termina dentro de 30 dias. Renove antes, ou os utilizadores ficam travados no dia seguinte."
        />
        <Kpi
          label="Vencidas ou suspensas"
          value={(resumo?.expired ?? 0) + (resumo?.suspended ?? 0)}
          tone={(resumo?.expired ?? 0) + (resumo?.suspended ?? 0) > 0 ? 'critical' : 'neutral'}
        />
        <Kpi label="Utilizadores" value={resumo?.users ?? '—'} />
        <Kpi label="Ativos geridos" value={resumo?.assets ?? '—'} />
      </Group>

      <Painel
        titulo="Empresas clientes"
        semPadding
        acoes={
          <>
            <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNova(true)} destaque>
              Nova empresa
            </BotaoBarra>
            <SeparadorBarra />
            <CampoProcura valor={procura} aoMudar={setProcura} placeholder="Empresa, NIF, dono, estado…" />
          </>
        }
      >
        <Grelha
          id="plataforma-empresas"
          linhas={rows}
          chave={(e) => e.id}
          carregando={isLoading}
          aoAbrir={(e) => setEditar(e)}
          vazio="Ainda não há empresas. Crie a primeira com «Nova empresa»."
          larguraMinima={1100}
          colunas={[
            {
              id: 'nome',
              titulo: 'Empresa',
              fixa: true,
              valor: (e) => e.name,
              render: (e) => (
                <Group gap={6} wrap="nowrap">
                  <IconBuildingSkyscraper size={15} style={{ color: '#B08D3C', flexShrink: 0 }} />
                  <div>
                    <Text size="sm" fw={600}>
                      {e.name}
                    </Text>
                    <Text size="xs" c="dimmed">
                      {[e.taxId && `NIF ${e.taxId}`, e.city].filter(Boolean).join(' · ') || 'sem NIF'}
                    </Text>
                  </div>
                </Group>
              ),
            },
            {
              id: 'estado',
              titulo: 'Estado',
              largura: 140,
              valor: (e) => ESTADO[e.status].label,
              render: (e) => (
                <Badge variant="light" color={ESTADO[e.status].color} size="sm">
                  {ESTADO[e.status].label}
                </Badge>
              ),
            },
            {
              id: 'licenca',
              titulo: 'Licença até',
              largura: 110,
              valor: (e) => e.licenseUntil ?? null,
              render: (e) => (
                <Text size="sm" c={e.status === 'EXPIRED' ? 'red' : e.status === 'EXPIRING' ? 'orange' : undefined}>
                  {data(e.licenseUntil) ?? <Text span c="dimmed">sem prazo</Text>}
                </Text>
              ),
            },
            {
              id: 'dono',
              titulo: 'Dono',
              valor: (e) => e.ownerEmail ?? null,
              render: (e) =>
                e.ownerEmail ? (
                  <div>
                    <Text size="sm">{e.ownerName}</Text>
                    <Text size="xs" c="dimmed">
                      {e.ownerEmail}
                    </Text>
                  </div>
                ) : (
                  <Text size="xs" c="orange">
                    sem dono ativo
                  </Text>
                ),
            },
            { id: 'utilizadores', titulo: 'Utiliz.', largura: 80, alinhar: 'right', valor: (e) => e.memberCount },
            { id: 'ativos', titulo: 'Ativos', largura: 80, alinhar: 'right', valor: (e) => e.assetCount },
            { id: 'ordens', titulo: 'Ordens', largura: 80, alinhar: 'right', valor: (e) => e.workOrderCount },
            {
              id: 'atividade',
              titulo: 'Última atividade',
              largura: 130,
              valor: (e) => e.lastActivityAt ?? null,
              render: (e) => (
                <Text size="sm" c={e.lastActivityAt ? undefined : 'dimmed'}>
                  {data(e.lastActivityAt) ?? 'nunca'}
                </Text>
              ),
            },
            {
              id: 'criada',
              titulo: 'Cliente desde',
              largura: 110,
              valor: (e) => e.createdAt,
              render: (e) => <Text size="sm">{data(e.createdAt)}</Text>,
            },
            {
              id: 'notas',
              titulo: 'Notas',
              escondida: true,
              valor: (e) => e.platformNotes ?? null,
            },
            {
              id: 'acoes',
              titulo: '',
              largura: 250,
              semQuebra: true,
              render: (e) => (
                <Group gap={4} wrap="nowrap">
                  <Button size="compact-xs" variant="subtle" leftSection={<IconPencil size={13} />} onClick={() => setEditar(e)}>
                    Licença
                  </Button>
                  {e.status === 'SUSPENDED' ? (
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      color="green"
                      leftSection={<IconLockOpen size={13} />}
                      loading={reativar.isPending && reativar.variables?.id === e.id}
                      onClick={() => reativar.mutate(e)}
                    >
                      Reativar
                    </Button>
                  ) : (
                    <Button size="compact-xs" variant="subtle" color="red" leftSection={<IconLock size={13} />} onClick={() => setSuspender(e)}>
                      Suspender
                    </Button>
                  )}
                  <Button
                    size="compact-xs"
                    variant="subtle"
                    color="gray"
                    leftSection={<IconKey size={13} />}
                    disabled={!e.ownerEmail}
                    loading={reporPassword.isPending && reporPassword.variables?.id === e.id}
                    onClick={() => {
                      if (window.confirm(`Gerar uma nova palavra-passe para ${e.ownerEmail}? A atual deixa de funcionar.`)) {
                        reporPassword.mutate(e);
                      }
                    }}
                  >
                    Palavra-passe
                  </Button>
                </Group>
              ),
            },
          ]}
        />
      </Painel>

      <NovaEmpresaModal
        opened={nova}
        onClose={() => setNova(false)}
        aoCriar={(c) => {
          invalidar();
          if (c.temporaryPassword) {
            setCredenciais({ email: c.ownerEmail, password: c.temporaryPassword, titulo: `Acesso do Dono — ${c.organization.name}` });
          } else {
            notifications.show({
              title: 'Empresa criada',
              message: c.ownerExisted
                ? `${c.ownerEmail} já tinha conta e ficou Dono da nova empresa com a palavra-passe que já usa.`
                : `${c.ownerEmail} entra com a palavra-passe que escolheu.`,
              color: 'green',
            });
          }
        }}
      />

      {editar && (
        <EditarLicencaModal empresa={editar} onClose={() => setEditar(null)} aoGuardar={invalidar} />
      )}

      {suspender && (
        <SuspenderModal empresa={suspender} onClose={() => setSuspender(null)} aoSuspender={invalidar} />
      )}

      <Modal opened={!!credenciais} onClose={() => setCredenciais(null)} title={credenciais?.titulo} centered>
        {credenciais && (
          <Stack gap="sm">
            <Alert color="yellow" variant="light">
              Esta palavra-passe <b>só aparece agora</b>: não fica guardada em lado nenhum. Copie-a e
              entregue-a ao Dono da empresa; ele deve mudá-la no Perfil ao entrar.
            </Alert>
            <Group grow>
              <TextInput label="Email" value={credenciais.email} readOnly />
            </Group>
            <Group align="flex-end" gap="xs">
              <TextInput label="Palavra-passe temporária" value={credenciais.password} readOnly style={{ flex: 1 }} styles={{ input: { fontFamily: 'monospace', letterSpacing: '0.08em' } }} />
              <CopyButton value={`${credenciais.email}\n${credenciais.password}`}>
                {({ copied, copy }) => (
                  <Button variant="light" leftSection={<IconCopy size={14} />} onClick={copy} color={copied ? 'green' : undefined}>
                    {copied ? 'Copiado' : 'Copiar'}
                  </Button>
                )}
              </CopyButton>
            </Group>
            <Text size="xs" c="dimmed">
              Endereço para entrar: <Code>{window.location.origin}/entrar</Code>
            </Text>
          </Stack>
        )}
      </Modal>
    </Stack>
  );
}

function NovaEmpresaModal({
  opened,
  onClose,
  aoCriar,
}: {
  opened: boolean;
  onClose: () => void;
  aoCriar: (c: Criada) => void;
}) {
  const [name, setName] = useState('');
  const [taxId, setTaxId] = useState('');
  const [city, setCity] = useState('');
  const [ownerName, setOwnerName] = useState('');
  const [ownerEmail, setOwnerEmail] = useState('');
  const [escolherPassword, setEscolherPassword] = useState(false);
  const [ownerPassword, setOwnerPassword] = useState('');
  const [licenseUntil, setLicenseUntil] = useState<Date | null>(() => {
    const d = new Date();
    d.setFullYear(d.getFullYear() + 1);
    return d;
  });
  const [notas, setNotas] = useState('');

  const limpar = () => {
    setName('');
    setTaxId('');
    setCity('');
    setOwnerName('');
    setOwnerEmail('');
    setOwnerPassword('');
    setEscolherPassword(false);
    setNotas('');
  };

  const criar = useMutation({
    mutationFn: () =>
      api<Criada>('/admin/platform/organizations', {
        method: 'POST',
        body: {
          name: name.trim(),
          taxId: taxId.trim() || null,
          city: city.trim() || null,
          ownerName: ownerName.trim(),
          ownerEmail: ownerEmail.trim(),
          ownerPassword: escolherPassword && ownerPassword ? ownerPassword : null,
          licenseUntil: paraIso(licenseUntil),
          platformNotes: notas.trim() || null,
        },
      }),
    onSuccess: (c) => {
      limpar();
      onClose();
      aoCriar(c);
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível criar a empresa', message: e.message, color: 'red' }),
  });

  const valido =
    name.trim().length >= 2 &&
    ownerName.trim().length >= 2 &&
    /\S+@\S+\.\S+/.test(ownerEmail.trim()) &&
    (!escolherPassword || ownerPassword.length >= 8);

  return (
    <Modal opened={opened} onClose={onClose} title="Nova empresa cliente" centered size="lg">
      <Stack gap="xs">
        <SeccaoForm titulo="Empresa" descricao="O que sai nos impressos pode ser completado depois pela própria empresa, em Configurações.">
          <TextInput label="Nome da empresa" required placeholder="Transportes do Sul, Lda." value={name} onChange={(e) => setName(e.currentTarget.value)} data-autofocus />
          <Group grow mt="xs">
            <TextInput label="NIF" placeholder="5417000000" value={taxId} onChange={(e) => setTaxId(e.currentTarget.value)} />
            <TextInput label="Cidade" placeholder="Luanda" value={city} onChange={(e) => setCity(e.currentTarget.value)} />
          </Group>
        </SeccaoForm>

        <SeccaoForm titulo="Dono da empresa" descricao="É quem manda na empresa dentro do sistema: convida a equipa, configura o Traccar e o timbre.">
          <Group grow>
            <TextInput label="Nome" required placeholder="Maria Chipenda" value={ownerName} onChange={(e) => setOwnerName(e.currentTarget.value)} />
            <TextInput label="Email" required placeholder="maria@transsul.ao" value={ownerEmail} onChange={(e) => setOwnerEmail(e.currentTarget.value)} />
          </Group>
          <Switch
            mt="sm"
            label="Escolher eu a palavra-passe"
            description="Desligado: o sistema gera uma palavra-passe temporária e mostra-a uma vez."
            checked={escolherPassword}
            onChange={(e) => setEscolherPassword(e.currentTarget.checked)}
          />
          {escolherPassword && (
            <PasswordInput mt="xs" label="Palavra-passe inicial" description="Pelo menos 8 caracteres." value={ownerPassword} onChange={(e) => setOwnerPassword(e.currentTarget.value)} />
          )}
        </SeccaoForm>

        <SeccaoForm titulo="Licença" descricao="No dia seguinte ao prazo, os utilizadores da empresa ficam travados até renovar. Sem prazo = sem fim.">
          <DateInput label="Válida até" value={licenseUntil} onChange={setLicenseUntil} valueFormat="DD/MM/YYYY" clearable placeholder="sem prazo" />
          <Textarea mt="xs" label="Notas internas" description="Contrato, contacto comercial, valor. A empresa nunca vê isto." value={notas} onChange={(e) => setNotas(e.currentTarget.value)} autosize minRows={2} />
        </SeccaoForm>

        <Group justify="flex-end" mt="xs">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => criar.mutate()} loading={criar.isPending} disabled={!valido}>
            Criar empresa
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function EditarLicencaModal({
  empresa,
  onClose,
  aoGuardar,
}: {
  empresa: Empresa;
  onClose: () => void;
  aoGuardar: () => void;
}) {
  const [name, setName] = useState(empresa.name);
  const [licenseUntil, setLicenseUntil] = useState<Date | null>(deIso(empresa.licenseUntil));
  const [notas, setNotas] = useState(empresa.platformNotes ?? '');

  const guardar = useMutation({
    mutationFn: () =>
      api(`/admin/platform/organizations/${empresa.id}`, {
        method: 'PUT',
        body: {
          name: name.trim() || null,
          licenseUntil: paraIso(licenseUntil),
          clearLicense: licenseUntil === null,
          platformNotes: notas,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Guardado', message: empresa.name, color: 'green' });
      aoGuardar();
      onClose();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened onClose={onClose} title={`Licença — ${empresa.name}`} centered>
      <Stack gap="sm">
        {empresa.status === 'SUSPENDED' && (
          <Alert color="gray" variant="light">
            Suspensa desde {data(empresa.suspendedAt)}
            {empresa.suspendedReason ? `: ${empresa.suspendedReason}` : ''}.
          </Alert>
        )}
        <TextInput label="Nome da empresa" value={name} onChange={(e) => setName(e.currentTarget.value)} />
        <DateInput label="Licença válida até" value={licenseUntil} onChange={setLicenseUntil} valueFormat="DD/MM/YYYY" clearable placeholder="sem prazo" description="Limpar o campo deixa a licença sem fim." />
        <Textarea label="Notas internas" value={notas} onChange={(e) => setNotas(e.currentTarget.value)} autosize minRows={2} />
        <Group gap="xl">
          <Text size="xs" c="dimmed">
            Dono: {empresa.ownerEmail ?? '—'}
          </Text>
          <Text size="xs" c="dimmed">
            Cliente desde {data(empresa.createdAt)}
          </Text>
        </Group>
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => guardar.mutate()} loading={guardar.isPending}>
            Guardar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function SuspenderModal({
  empresa,
  onClose,
  aoSuspender,
}: {
  empresa: Empresa;
  onClose: () => void;
  aoSuspender: () => void;
}) {
  const [reason, setReason] = useState('');
  const suspender = useMutation({
    mutationFn: () =>
      api(`/admin/platform/organizations/${empresa.id}/suspend`, {
        method: 'POST',
        body: { reason: reason.trim() || null },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Empresa suspensa', message: empresa.name, color: 'orange' });
      aoSuspender();
      onClose();
    },
    onError: (e: Error) => notifications.show({ title: 'Não foi possível suspender', message: e.message, color: 'red' }),
  });

  return (
    <Modal opened onClose={onClose} title={`Suspender ${empresa.name}`} centered>
      <Stack gap="sm">
        <Alert color="orange" variant="light">
          Os {empresa.memberCount} utilizador(es) desta empresa deixam de poder trabalhar até a
          reativar. Os dados ficam intactos. O motivo é mostrado a eles quando tentam entrar.
        </Alert>
        <TextInput label="Motivo" placeholder="Fatura de Agosto em atraso" value={reason} onChange={(e) => setReason(e.currentTarget.value)} data-autofocus />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button color="red" onClick={() => suspender.mutate()} loading={suspender.isPending}>
            Suspender
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
