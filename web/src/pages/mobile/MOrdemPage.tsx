import { Badge, Button, Card, Checkbox, FileButton, Group, Image, Loader, NumberInput, SimpleGrid, Stack, Text, Textarea, Title } from '@mantine/core';
import { notifications as toast } from '@mantine/notifications';
import { IconArrowLeft, IconCamera, IconPlayerPause, IconPlayerPlay } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { fmtDateTime } from '../../lib/format';
import { Erro, Feito, mensagemDe } from './comum';

interface Tarefa {
  id: string;
  title: string;
  systemName?: string | null;
  instructions?: string | null;
  done: boolean;
}

interface Ordem {
  id: string;
  number: string;
  assetId: string;
  assetTag: string;
  assetName: string;
  title: string;
  description?: string | null;
  status: string;
  statusLabel: string;
  priority: string;
  scheduledFor?: string | null;
  startedAt?: string | null;
  meterValue?: number | null;
  safetyNotes?: string | null;
  requiresShutdown?: boolean | null;
  tasks: Tarefa[];
  timers?: { userId: string; userName: string; startedAt: string; minutes: number }[];
}

interface Anexo {
  id: string;
  url: string;
  name: string;
  contentType: string;
  kindLabel: string;
  caption?: string | null;
}

const PRIORIDADE: Record<string, string> = { LOW: 'Baixa', NORMAL: 'Normal', HIGH: 'Alta', URGENT: 'Urgente' };
const PODE_INICIAR = new Set(['OPEN', 'PLANNED']);
const FECHADA = new Set(['DONE', 'VERIFIED', 'CLOSED', 'CANCELLED', 'REJECTED']);

/**
 * Uma ordem no telemóvel do mecânico: o que é, as tarefas para ir marcando,
 * fotografias do antes e do depois, iniciar e concluir. As peças, as horas
 * e os custos ficam para a versão completa — na oficina há um computador.
 */
export function MOrdemPage() {
  const { id = '' } = useParams();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [resolucao, setResolucao] = useState('');
  const [contador, setContador] = useState<number | string>('');
  const [aConcluir, setAConcluir] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const { data: o, isLoading } = useQuery({ queryKey: ['work-order', id], queryFn: () => api<Ordem>(`/work-orders/${id}`) });
  const { data: anexos } = useQuery({
    queryKey: ['work-order', id, 'anexos'],
    queryFn: () => api<Anexo[]>(`/work-orders/${id}/attachments`),
  });

  const invalidar = () => {
    queryClient.invalidateQueries({ queryKey: ['work-order', id] });
    queryClient.invalidateQueries({ queryKey: ['mobile'] });
  };

  const iniciar = useMutation({
    mutationFn: () => api(`/work-orders/${id}/start`, { method: 'POST', body: {} }),
    onSuccess: () => {
      toast.show({ message: 'Ordem iniciada.', color: 'green' });
      invalidar();
    },
    onError: (e) => setErro(mensagemDe(e)),
  });
  const marcar = useMutation({
    mutationFn: ({ taskId, done }: { taskId: string; done: boolean }) =>
      api(`/work-orders/${id}/tasks/${taskId}`, { method: 'POST', body: { done } }),
    onSuccess: invalidar,
    onError: (e) => setErro(mensagemDe(e)),
  });
  const foto = useMutation({
    mutationFn: ({ f, kind }: { f: File; kind: 'BEFORE' | 'AFTER' }) => {
      const form = new FormData();
      form.append('file', f);
      form.append('kind', kind);
      return api(`/work-orders/${id}/attachments`, { method: 'POST', body: form });
    },
    onSuccess: () => {
      toast.show({ message: 'Fotografia guardada.', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['work-order', id, 'anexos'] });
    },
    onError: (e) => setErro(mensagemDe(e)),
  });
  const cronometro = useMutation({
    mutationFn: (acao: 'start' | 'stop') => api(`/work-orders/${id}/timer/${acao}`, { method: 'POST' }),
    onSuccess: invalidar,
    onError: (e) => setErro(mensagemDe(e)),
  });
  const concluir = useMutation({
    mutationFn: () =>
      api<Ordem>(`/work-orders/${id}/complete`, {
        method: 'POST',
        body: { resolution: resolucao.trim() || undefined, closingMeterValue: contador === '' ? undefined : Number(contador) },
      }),
    onSuccess: invalidar,
    onError: (e) => setErro(mensagemDe(e)),
  });

  if (isLoading || !o) return <Loader />;

  if (concluir.isSuccess) {
    return <Feito titulo="Ordem concluída" texto={`${o.number} · ${o.assetTag}. Fica agora a aguardar verificação pelo responsável.`} />;
  }

  const fechada = FECHADA.has(o.status);
  const feitas = o.tasks.filter((t) => t.done).length;
  const meuCronometro = (o.timers ?? []).find((t) => t.userId === user?.id);
  const outros = (o.timers ?? []).filter((t) => t.userId !== user?.id);

  return (
    <Stack gap="md">
      <Group gap="xs">
        <Button component={Link} to="/m/ordens" variant="subtle" size="compact-sm" leftSection={<IconArrowLeft size={16} />}>
          Ordens
        </Button>
      </Group>
      <div>
        <Group gap={6} mb={4}>
          <Text size="xs" c="dimmed">
            {o.number}
          </Text>
          <Badge size="xs" color={o.priority === 'URGENT' ? 'red' : o.priority === 'HIGH' ? 'orange' : 'blue'} variant="light">
            {PRIORIDADE[o.priority] ?? o.priority}
          </Badge>
          <Badge size="xs" variant="outline" color={fechada ? 'green' : 'gray'}>
            {o.statusLabel}
          </Badge>
        </Group>
        <Title order={3} lh={1.2}>
          {o.title}
        </Title>
        <Text c="dimmed" size="sm">
          {o.assetTag} · {o.assetName}
          {o.scheduledFor ? ` · marcada para ${fmtDateTime(o.scheduledFor)}` : ''}
        </Text>
      </div>
      {o.description && (
        <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>
          {o.description}
        </Text>
      )}
      {(o.safetyNotes || o.requiresShutdown) && (
        <Card padding="sm" radius="md" withBorder style={{ borderColor: 'var(--mantine-color-red-4)' }}>
          <Text size="sm" fw={700} c="red">
            Segurança
          </Text>
          {o.requiresShutdown && <Text size="sm">Exige a viatura desligada e imobilizada.</Text>}
          {o.safetyNotes && <Text size="sm">{o.safetyNotes}</Text>}
        </Card>
      )}

      {PODE_INICIAR.has(o.status) && (
        <Button size="lg" leftSection={<IconPlayerPlay size={20} />} loading={iniciar.isPending} onClick={() => iniciar.mutate()}>
          Iniciar trabalho
        </Button>
      )}
      {!fechada && (
        meuCronometro ? (
          <Button size="lg" variant="light" color="orange" leftSection={<IconPlayerPause size={20} />} loading={cronometro.isPending} onClick={() => cronometro.mutate('stop')}>
            Parar o meu tempo · {meuCronometro.minutes} min
          </Button>
        ) : (
          <Button size="lg" variant="light" leftSection={<IconPlayerPlay size={20} />} loading={cronometro.isPending} onClick={() => cronometro.mutate('start')}>
            Iniciar o meu tempo
          </Button>
        )
      )}
      {outros.length > 0 && (
        <Text size="xs" c="dimmed">
          Também a trabalhar: {outros.map((t) => `${t.userName} (${t.minutes} min)`).join(', ')}
        </Text>
      )}

      {o.tasks.length > 0 && (
        <div>
          <Text fw={600} size="sm" mb={6}>
            Tarefas · {feitas}/{o.tasks.length}
          </Text>
          <Stack gap={6}>
            {o.tasks.map((t) => (
              <Card key={t.id} padding="sm" radius="md" withBorder>
                <Checkbox
                  size="md"
                  checked={t.done}
                  disabled={fechada || marcar.isPending}
                  onChange={(e) => marcar.mutate({ taskId: t.id, done: e.currentTarget.checked })}
                  label={
                    <div>
                      <Text fw={600} size="sm" td={t.done ? 'line-through' : undefined}>
                        {t.title}
                        {t.systemName ? ` (${t.systemName})` : ''}
                      </Text>
                      {t.instructions && (
                        <Text size="xs" c="dimmed">
                          {t.instructions}
                        </Text>
                      )}
                    </div>
                  }
                />
              </Card>
            ))}
          </Stack>
        </div>
      )}

      <div>
        <Text fw={600} size="sm" mb={6}>
          Fotografias
        </Text>
        {anexos && anexos.filter((a) => a.contentType.startsWith('image/')).length > 0 && (
          <SimpleGrid cols={3} spacing={6} mb={8}>
            {anexos
              .filter((a) => a.contentType.startsWith('image/'))
              .map((a) => (
                <div key={a.id}>
                  <Image src={a.url} radius="sm" h={90} fit="cover" />
                  <Text size="xs" c="dimmed" ta="center">
                    {a.kindLabel}
                  </Text>
                </div>
              ))}
          </SimpleGrid>
        )}
        {!fechada && (
          <Group grow>
            <FileButton onChange={(f) => f && foto.mutate({ f, kind: 'BEFORE' })} accept="image/*" capture="environment">
              {(p) => (
                <Button {...p} variant="light" leftSection={<IconCamera size={16} />} loading={foto.isPending}>
                  Antes
                </Button>
              )}
            </FileButton>
            <FileButton onChange={(f) => f && foto.mutate({ f, kind: 'AFTER' })} accept="image/*" capture="environment">
              {(p) => (
                <Button {...p} variant="light" leftSection={<IconCamera size={16} />} loading={foto.isPending}>
                  Depois
                </Button>
              )}
            </FileButton>
          </Group>
        )}
      </div>

      <Erro mensagem={erro} />

      {!fechada && !PODE_INICIAR.has(o.status) && !aConcluir && (
        <Button size="lg" color="green" variant="outline" onClick={() => setAConcluir(true)} disabled={feitas < o.tasks.length}>
          {feitas < o.tasks.length ? `Concluir (faltam ${o.tasks.length - feitas} tarefas)` : 'Concluir trabalho'}
        </Button>
      )}
      {!fechada && aConcluir && (
        <Card padding="md" radius="md" withBorder>
          <Stack gap="sm">
            <Text fw={700}>Concluir</Text>
            <Textarea
              label="O que foi feito"
              placeholder="Ex.: substituído o sensor do cárter e reposto o óleo"
              value={resolucao}
              onChange={(e) => setResolucao(e.currentTarget.value)}
              autosize
              minRows={2}
              size="md"
            />
            <NumberInput
              label="Contador ao terminar (km ou horas)"
              value={contador}
              onChange={setContador}
              min={0}
              thousandSeparator=" "
              size="md"
              inputMode="numeric"
            />
            <Group grow>
              <Button variant="default" onClick={() => setAConcluir(false)}>
                Ainda não
              </Button>
              <Button color="green" loading={concluir.isPending} onClick={() => {
                setErro(null);
                concluir.mutate();
              }}>
                Concluir
              </Button>
            </Group>
          </Stack>
        </Card>
      )}
    </Stack>
  );
}
