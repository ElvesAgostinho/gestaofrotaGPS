/**
 * O acesso do motorista à aplicação do telemóvel.
 *
 * <p>O motorista não se regista: o gestor carrega aqui e o sistema devolve um
 * identificador curto e uma palavra-passe. Esse par aparece <b>uma única vez</b>,
 * em letras grandes, prontas a imprimir ou a fotografar — depois fica cifrado e
 * nem o gestor lhe volta a chegar. Se se perder, repõe-se.
 */
import { Alert, Badge, Button, CopyButton, Group, Loader, Modal, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconCheck,
  IconCopy,
  IconDeviceMobile,
  IconInfoCircle,
  IconKey,
  IconLock,
  IconLockOpen,
  IconPrinter,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { fmtDateTime } from '../../lib/format';

const AMBAR = '#FFC62F';
const PRETO = '#141416';

interface Acesso {
  exists: boolean;
  userId?: string | null;
  loginId?: string | null;
  active: boolean;
  mustChangePassword: boolean;
  lastLoginAt?: string | null;
}

interface Credenciais {
  name: string;
  loginId: string;
  password: string;
  message: string;
}

/** O endereço que o motorista escreve no telemóvel para instalar a aplicação. */
const ENDERECO = typeof window !== 'undefined' ? window.location.origin : '';

/**
 * O cartão que se entrega em mão.
 *
 * <p>Letras grandes e monoespaçadas porque isto vai ser lido dentro de uma
 * cabina, copiado à mão para um telemóvel, e provavelmente fotografado. Um
 * «O» confundido com um zero é uma chamada ao escritório.
 */
function CartaoDeAcesso({ c }: { c: Credenciais }) {
  const linha = (rotulo: string, valor: string) => (
    <div style={{ marginBottom: 10 }}>
      <Text size="xs" c="dimmed" tt="uppercase" style={{ letterSpacing: '0.06em' }}>
        {rotulo}
      </Text>
      <Group gap="xs" wrap="nowrap">
        <Text
          style={{
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            fontSize: 26,
            fontWeight: 700,
            letterSpacing: '0.08em',
          }}
        >
          {valor}
        </Text>
        <CopyButton value={valor}>
          {({ copied, copy }) => (
            <Button size="compact-xs" variant="subtle" onClick={copy}
              leftSection={copied ? <IconCheck size={13} /> : <IconCopy size={13} />}>
              {copied ? 'copiado' : 'copiar'}
            </Button>
          )}
        </CopyButton>
      </Group>
    </div>
  );

  return (
    <div id="cartao-acesso" style={{ border: `1.5px solid ${PRETO}`, background: '#fff' }}>
      <div
        style={{
          background: PRETO,
          color: AMBAR,
          padding: '6px 10px',
          fontFamily: '"Barlow Condensed", Barlow, sans-serif',
          fontWeight: 700,
          fontSize: 15,
          letterSpacing: '0.04em',
          textTransform: 'uppercase',
        }}
      >
        Acesso à aplicação · {c.name}
      </div>
      <div style={{ padding: '12px 14px' }}>
        {linha('Identificador', c.loginId)}
        {linha('Palavra-passe', c.password)}
        <Text size="xs" c="dimmed" mt={4}>
          Abrir <b>{ENDERECO}</b> no telemóvel, entrar com estes dados e instalar a aplicação
          («Adicionar ao ecrã principal»). No primeiro acesso é pedida uma palavra-passe nova, que
          só o motorista conhece.
        </Text>
      </div>
    </div>
  );
}

export function AcessoApp({ motoristaId, nome }: { motoristaId: string; nome: string }) {
  const { has } = useAuth();
  const podeGerir = has('DRIVERS_MANAGE');
  const queryClient = useQueryClient();
  const [credenciais, setCredenciais] = useState<Credenciais | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['drivers', motoristaId, 'access'],
    queryFn: () => api<Acesso>(`/drivers/${motoristaId}/access`),
  });

  const recarregar = () => {
    queryClient.invalidateQueries({ queryKey: ['drivers', motoristaId, 'access'] });
    queryClient.invalidateQueries({ queryKey: ['drivers'] });
  };

  const criar = useMutation({
    mutationFn: () => api<Credenciais>(`/drivers/${motoristaId}/access`, { method: 'POST' }),
    onSuccess: (c) => {
      setCredenciais(c);
      recarregar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível criar o acesso', message: e.message, color: 'red' }),
  });

  const repor = useMutation({
    mutationFn: () => api<Credenciais>(`/drivers/${motoristaId}/access/password`, { method: 'POST' }),
    onSuccess: (c) => {
      setCredenciais(c);
      recarregar();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  const alternar = useMutation({
    mutationFn: (bloquear: boolean) =>
      api<Acesso>(`/drivers/${motoristaId}/access/${bloquear ? 'block' : 'unblock'}`, { method: 'POST' }),
    onSuccess: (a) => {
      notifications.show({
        message: a.active ? 'Acesso reactivado.' : 'Acesso bloqueado. A sessão no telemóvel terminou.',
        color: a.active ? 'green' : 'orange',
      });
      recarregar();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  if (isLoading) return <Loader size="sm" />;

  return (
    <Stack gap="sm">
      {/* O cartão só aparece no momento em que é gerado. */}
      <Modal
        opened={credenciais !== null}
        onClose={() => setCredenciais(null)}
        title="Dados de acesso"
        centered
        size="md"
      >
        {credenciais && (
          <Stack gap="sm">
            <CartaoDeAcesso c={credenciais} />
            <Alert color="yellow" variant="light" p="xs" icon={<IconInfoCircle size={16} />}>
              <Text size="sm">{credenciais.message}</Text>
            </Alert>
            <Group justify="flex-end">
              <Button
                variant="default"
                leftSection={<IconPrinter size={15} />}
                onClick={() => window.print()}
              >
                Imprimir
              </Button>
              <Button onClick={() => setCredenciais(null)}>Já anotei</Button>
            </Group>
          </Stack>
        )}
      </Modal>

      {!data?.exists ? (
        <Stack gap="xs" align="flex-start">
          <Group gap={6} wrap="nowrap">
            <IconDeviceMobile size={18} style={{ color: '#a1a1aa' }} />
            <Text size="sm" c="dimmed">
              {nome} ainda não tem acesso à aplicação. Ao criar, o sistema gera um identificador
              (por exemplo <b>MOT-0412</b>) e uma palavra-passe para lhe entregar — sem precisar de
              email.
            </Text>
          </Group>
          {podeGerir && (
            <Button loading={criar.isPending} onClick={() => criar.mutate()} leftSection={<IconKey size={16} />}>
              Criar acesso
            </Button>
          )}
        </Stack>
      ) : (
        <Stack gap="xs">
          <Group gap="xs" wrap="wrap">
            <Text
              style={{
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                fontSize: 18,
                fontWeight: 700,
                letterSpacing: '0.06em',
              }}
            >
              {data.loginId}
            </Text>
            <Badge variant="light" color={data.active ? 'green' : 'red'}>
              {data.active ? 'activo' : 'bloqueado'}
            </Badge>
            {data.mustChangePassword && (
              <Badge variant="light" color="yellow">
                por trocar a palavra-passe
              </Badge>
            )}
          </Group>
          <Text size="xs" c="dimmed">
            {data.lastLoginAt
              ? `Última entrada: ${fmtDateTime(data.lastLoginAt)}`
              : 'Ainda não entrou na aplicação.'}
          </Text>
          {podeGerir && (
            <Group gap="xs">
              <Button
                size="compact-sm"
                variant="default"
                loading={repor.isPending}
                onClick={() => repor.mutate()}
                leftSection={<IconKey size={15} />}
              >
                Repor palavra-passe
              </Button>
              <Button
                size="compact-sm"
                variant="default"
                color={data.active ? 'red' : 'green'}
                loading={alternar.isPending}
                onClick={() => alternar.mutate(data.active)}
                leftSection={data.active ? <IconLock size={15} /> : <IconLockOpen size={15} />}
              >
                {data.active ? 'Bloquear acesso' : 'Reactivar acesso'}
              </Button>
            </Group>
          )}
          <Text size="xs" c="dimmed">
            Bloquear termina a sessão aberta no telemóvel — é o que se faz quando alguém sai da
            empresa ou perde o aparelho. A conta fica guardada, com o histórico do que ele fez.
          </Text>
        </Stack>
      )}
    </Stack>
  );
}
