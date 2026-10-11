import {
  ActionIcon,
  Alert,
  Avatar,
  Badge,
  Box,
  Button,
  Group,
  Loader,
  ScrollArea,
  Select,
  Stack,
  Text,
  TextInput,
  Tooltip,
  UnstyledButton,
} from '@mantine/core';
import {
  IconAlertTriangle,
  IconBuildingWarehouse,
  IconDownload,
  IconInbox,
  IconMail,
  IconMailOpened,
  IconPaperclip,
  IconPencil,
  IconRefresh,
  IconSearch,
  IconSend,
  IconTrash,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, tokens } from '../api/client';
import { Painel } from '../components/erp';
import { Escrever } from './correio/Escrever';
import { PropostaDoFornecedor } from './correio/PropostaDoFornecedor';
import {
  corDoAvatar,
  COR_ENTIDADE,
  iniciais,
  quando,
  ROTULO_ENTIDADE,
  tamanhoLegivel,
  type Entidade,
  type EstadoCaixa,
  type Mensagem,
  type Pagina,
  type Pasta,
  type Pessoa,
} from './correio/tipos';

/**
 * O correio da empresa, dentro do sistema.
 *
 * <p>Três colunas, como qualquer cliente de correio sério: as pastas e os
 * filtros à esquerda, a lista ao meio, a mensagem à direita. A disposição não
 * é uma homenagem — é a que permite percorrer cinquenta mensagens sem perder o
 * sítio, e toda a gente já a sabe usar sem explicação.
 *
 * <p>O que aqui há e num webmail não: <b>o sistema sabe quem está do outro
 * lado</b>. Uma mensagem de um fornecedor registado aparece com o nome da
 * empresa e um atalho para a ficha dele, e há um filtro que mostra só o
 * correio de quem está no sistema — que é a diferença entre procurar uma
 * mensagem durante cinco minutos e abri-la logo.
 *
 * <p>O corpo da mensagem é mostrado dentro de uma moldura isolada, sem
 * permissões para correr nada. O servidor já lhe tirou o que podia executar;
 * isto é a segunda defesa, e é a que conta — uma mensagem de email é conteúdo
 * de um estranho.
 */

const TAMANHO_PAGINA = 30;

export function CorreioPage() {
  const qc = useQueryClient();
  const [pasta, setPasta] = useState('INBOX');
  const [seleccionada, setSeleccionada] = useState<number | null>(null);
  const [procura, setProcura] = useState('');
  const [filtro, setFiltro] = useState<'TODAS' | 'POR_LER' | 'ANEXOS' | 'CONHECIDOS'>('TODAS');
  const [entidade, setEntidade] = useState<string | null>(null);
  const [escrever, setEscrever] = useState(false);
  const [responderA, setResponderA] = useState<{ para: string; assunto: string } | null>(null);

  const { data: estado } = useQuery({
    queryKey: ['correio', 'estado'],
    queryFn: () => api<EstadoCaixa>('/mailbox/status'),
  });

  const activa = !!estado?.configurada;

  const { data: pastas } = useQuery({
    queryKey: ['correio', 'pastas'],
    queryFn: () => api<Pasta[]>('/mailbox/folders'),
    enabled: activa,
  });

  const { data: directorio } = useQuery({
    queryKey: ['correio', 'directorio'],
    queryFn: () => api<Entidade[]>('/mailbox/directory'),
    enabled: activa,
  });

  const parametros = new URLSearchParams({
    folder: pasta,
    size: String(TAMANHO_PAGINA),
    unread: String(filtro === 'POR_LER'),
    withAttachments: String(filtro === 'ANEXOS'),
    knownOnly: String(filtro === 'CONHECIDOS'),
  });
  if (entidade) {
    parametros.set('entity', entidade);
  }
  if (procura.trim()) {
    parametros.set('q', procura.trim());
  }

  const lista = useQuery({
    queryKey: ['correio', 'lista', parametros.toString()],
    queryFn: () => api<Pagina>(`/mailbox/messages?${parametros.toString()}`),
    enabled: activa,
  });

  const mensagem = useQuery({
    queryKey: ['correio', 'mensagem', pasta, seleccionada],
    queryFn: () =>
      api<Mensagem>(
        `/mailbox/messages/${seleccionada}?folder=${encodeURIComponent(pasta)}&markRead=true`,
      ),
    enabled: activa && seleccionada != null,
  });

  const recarregar = () => {
    void qc.invalidateQueries({ queryKey: ['correio', 'lista'] });
    void qc.invalidateQueries({ queryKey: ['correio', 'pastas'] });
  };

  const apagar = useMutation({
    mutationFn: (uid: number) =>
      api(`/mailbox/messages/${uid}/delete?folder=${encodeURIComponent(pasta)}`, {
        method: 'POST',
      }),
    onSuccess: () => {
      setSeleccionada(null);
      recarregar();
    },
  });

  const marcar = useMutation({
    mutationFn: ({ uid, lida }: { uid: number; lida: boolean }) =>
      api(
        `/mailbox/messages/${uid}/read?folder=${encodeURIComponent(pasta)}&read=${lida}`,
        { method: 'POST' },
      ),
    onSuccess: recarregar,
  });

  if (estado && !estado.configurada) {
    return (
      <Painel titulo="Correio">
        <Stack gap="sm">
          <Alert color="yellow" variant="light" icon={<IconAlertTriangle size={18} />}>
            {estado.mensagem}
          </Alert>
          <Text size="sm" c="dimmed">
            O servidor de receção costuma ser <b>imap.</b> seguido do seu domínio, no porto{' '}
            <b>993 com SSL</b>, com as mesmas credenciais do envio.
          </Text>
          <Group>
            <Button component={Link} to="/definicoes" variant="default">
              Ir às configurações
            </Button>
          </Group>
        </Stack>
      </Painel>
    );
  }

  return (
    // A altura disponível é a do ecrã menos o cabeçalho (60), o rodapé (26) e
    // o espaçamento da área de trabalho (20 em cima e 20 em baixo).
    <Box style={{ height: 'calc(100dvh - 126px)', minHeight: 440 }}>
      <Painel
        titulo="Correio"
        semPadding
        preencher
        acoes={
          <Group gap="xs" wrap="nowrap" style={{ width: '100%' }}>
            <Button size="xs" leftSection={<IconPencil size={14} />} onClick={() => setEscrever(true)}>
              Escrever
            </Button>
            <Tooltip label="Ir buscar o que chegou" withArrow>
              <ActionIcon variant="default" onClick={recarregar} aria-label="Actualizar">
                <IconRefresh size={16} />
              </ActionIcon>
            </Tooltip>
            <TextInput
              size="xs"
              placeholder="Assunto ou remetente…"
              leftSection={<IconSearch size={13} />}
              value={procura}
              onChange={(e) => setProcura(e.currentTarget.value)}
              style={{ flex: 1, maxWidth: 280 }}
            />
            <Select
              size="xs"
              w={180}
              data={[
                { value: 'TODAS', label: 'Todas' },
                { value: 'POR_LER', label: 'Por ler' },
                { value: 'ANEXOS', label: 'Com anexos' },
                { value: 'CONHECIDOS', label: 'De quem está no sistema' },
              ]}
              value={filtro}
              onChange={(v) => setFiltro((v as typeof filtro) ?? 'TODAS')}
            />
            <Select
              size="xs"
              w={220}
              placeholder="Qualquer entidade"
              clearable
              searchable
              value={entidade}
              onChange={setEntidade}
              data={(directorio ?? []).map((e) => ({
                value: e.email,
                label: `${e.nome} · ${ROTULO_ENTIDADE[e.tipo]}`,
              }))}
            />
            {estado?.endereco && (
              <Text size="xs" c="dimmed" style={{ marginLeft: 'auto', whiteSpace: 'nowrap' }}>
                {estado.endereco}
              </Text>
            )}
          </Group>
        }
      >
        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'minmax(170px, 200px) minmax(260px, 380px) 1fr',
            height: '100%',
            minHeight: 0,
            overflow: 'hidden',
          }}
        >
          {/* ---- pastas ---- */}
          <Box
            style={{
              borderRight: '1px solid var(--mantine-color-default-border)',
              minHeight: 0,
              overflow: 'hidden',
            }}
          >
            <ScrollArea h="100%" type="hover">
              <Stack gap={2} p={6}>
                {(pastas ?? []).map((p) => (
                  <UnstyledButton
                    key={p.caminho}
                    onClick={() => {
                      setPasta(p.caminho);
                      setSeleccionada(null);
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      padding: '7px 9px',
                      borderRadius: 2,
                      background:
                        pasta === p.caminho ? 'var(--mantine-color-default-hover)' : undefined,
                      fontWeight: p.porLer > 0 ? 700 : 500,
                    }}
                  >
                    {p.papel === 'SENT' ? <IconSend size={15} /> : <IconInbox size={15} />}
                    <Text size="sm" style={{ flex: 1 }}>
                      {p.nome}
                    </Text>
                    {p.porLer > 0 && (
                      <Badge size="xs" variant="filled">
                        {p.porLer}
                      </Badge>
                    )}
                  </UnstyledButton>
                ))}
              </Stack>
            </ScrollArea>
          </Box>

          {/* ---- lista ---- */}
          <Box
            style={{
              borderRight: '1px solid var(--mantine-color-default-border)',
              minHeight: 0,
              overflow: 'hidden',
            }}
          >
            <ScrollArea h="100%" type="auto">
              {lista.isPending ? (
                <Group justify="center" p="xl">
                  <Loader size="sm" />
                </Group>
              ) : (lista.data?.mensagens.length ?? 0) === 0 ? (
                <Text size="sm" c="dimmed" p="md">
                  Nada aqui {procura || entidade || filtro !== 'TODAS' ? 'com estes filtros' : ''}.
                </Text>
              ) : (
                <Stack gap={0}>
                  {lista.data?.mensagens.map((m) => {
                    const activaLinha = seleccionada === m.uid;
                    const ent = m.de?.entidade;
                    return (
                      <UnstyledButton
                        key={m.uid}
                        onClick={() => setSeleccionada(m.uid)}
                        style={{
                          display: 'block',
                          padding: '9px 11px',
                          borderBottom: '1px solid var(--mantine-color-default-border)',
                          borderLeft: `3px solid ${
                            activaLinha ? 'var(--erp-dourado)' : 'transparent'
                          }`,
                          background: activaLinha
                            ? 'var(--mantine-color-default-hover)'
                            : undefined,
                        }}
                      >
                        <Group gap={8} wrap="nowrap" align="flex-start">
                          <Avatar
                            size={30}
                            radius="xl"
                            color={corDoAvatar(m.de?.email)}
                            variant="light"
                          >
                            <Text size="xs" fw={700}>
                              {iniciais(m.de?.nome, m.de?.email)}
                            </Text>
                          </Avatar>
                          <div style={{ minWidth: 0, flex: 1 }}>
                            <Group gap={6} wrap="nowrap" justify="space-between">
                              <Text
                                size="sm"
                                fw={m.lida ? 500 : 700}
                                truncate
                                style={{ flex: 1 }}
                              >
                                {m.de?.nome ?? m.de?.email ?? '(sem remetente)'}
                              </Text>
                              <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
                                {quando(m.data)}
                              </Text>
                            </Group>
                            <Text size="sm" fw={m.lida ? 400 : 600} truncate>
                              {m.assunto || '(sem assunto)'}
                            </Text>
                            <Group gap={5} mt={2} wrap="nowrap">
                              {ent && (
                                <Badge
                                  size="xs"
                                  variant="light"
                                  color={COR_ENTIDADE[ent.tipo]}
                                  leftSection={<IconBuildingWarehouse size={9} />}
                                >
                                  {ROTULO_ENTIDADE[ent.tipo]}
                                </Badge>
                              )}
                              {m.temAnexos && (
                                <Badge
                                  size="xs"
                                  variant="outline"
                                  color="gray"
                                  leftSection={<IconPaperclip size={9} />}
                                >
                                  {m.anexos}
                                </Badge>
                              )}
                              {!m.lida && (
                                <Badge size="xs" variant="filled" color="blue">
                                  nova
                                </Badge>
                              )}
                            </Group>
                          </div>
                        </Group>
                      </UnstyledButton>
                    );
                  })}
                  {(lista.data?.total ?? 0) > (lista.data?.mensagens.length ?? 0) && (
                    <Text size="xs" c="dimmed" p="sm" ta="center">
                      a mostrar {lista.data?.mensagens.length} de {lista.data?.total}
                    </Text>
                  )}
                </Stack>
              )}
            </ScrollArea>
          </Box>

          {/* ---- mensagem ---- */}
          <Box style={{ minHeight: 0, overflow: 'hidden' }}>
            {seleccionada == null ? (
              <Group justify="center" align="center" h="100%" p="xl">
                <Stack align="center" gap={6}>
                  <IconMail size={34} color="var(--mantine-color-dimmed)" />
                  <Text size="sm" c="dimmed">
                    Escolha uma mensagem para a ler.
                  </Text>
                </Stack>
              </Group>
            ) : mensagem.isPending ? (
              <Group justify="center" p="xl">
                <Loader size="sm" />
              </Group>
            ) : mensagem.data ? (
              <LeitorDeMensagem
                m={mensagem.data}
                pasta={pasta}
                aoApagar={() => apagar.mutate(mensagem.data.uid)}
                aoMarcarPorLer={() =>
                  marcar.mutate({ uid: mensagem.data.uid, lida: false })
                }
                aoResponder={() =>
                  setResponderA({
                    para: mensagem.data.de?.email ?? '',
                    assunto: `Re: ${mensagem.data.assunto ?? ''}`,
                  })
                }
              />
            ) : null}
          </Box>
        </Box>
      </Painel>

      <Escrever aberto={escrever} aoFechar={() => setEscrever(false)} />
      <Escrever
        aberto={!!responderA}
        aoFechar={() => setResponderA(null)}
        paraInicial={responderA?.para}
        assuntoInicial={responderA?.assunto}
      />
    </Box>
  );
}

/** Quem escreveu, com a ficha dele a um clique quando está no sistema. */
function Remetente({ p }: { p?: Pessoa | null }) {
  if (!p) {
    return <Text size="sm">(sem remetente)</Text>;
  }
  const ent = p.entidade;
  return (
    <Group gap={8} wrap="nowrap">
      <Avatar size={38} radius="xl" color={corDoAvatar(p.email)} variant="light">
        <Text size="sm" fw={700}>
          {iniciais(p.nome, p.email)}
        </Text>
      </Avatar>
      <div style={{ minWidth: 0 }}>
        <Group gap={6} wrap="nowrap">
          <Text size="sm" fw={700}>
            {p.nome ?? p.email}
          </Text>
          {ent && (
            <Badge size="xs" variant="light" color={COR_ENTIDADE[ent.tipo]}>
              {ROTULO_ENTIDADE[ent.tipo]}
              {ent.detalhe ? ` · ${ent.detalhe}` : ''}
            </Badge>
          )}
        </Group>
        <Group gap={8}>
          <Text size="xs" c="dimmed">
            {p.email}
          </Text>
          {ent?.link && (
            <Text component={Link} to={ent.link} size="xs" td="underline" c="dimmed">
              ver ficha
            </Text>
          )}
        </Group>
      </div>
    </Group>
  );
}

function LeitorDeMensagem({
  m,
  pasta,
  aoApagar,
  aoMarcarPorLer,
  aoResponder,
}: {
  m: Mensagem;
  pasta: string;
  aoApagar: () => void;
  aoMarcarPorLer: () => void;
  aoResponder: () => void;
}) {
  const base = `/api/v1/mailbox/messages/${m.uid}/attachments`;
  const sufixo = `folder=${encodeURIComponent(pasta)}`;

  /**
   * Os anexos vão por uma rota autenticada: um `href` directo não leva o
   * token, portanto vai-se buscar o ficheiro e entrega-se ao browser.
   */
  const descarregar = async (anexoId: string, nome: string) => {
    const r = await fetch(`${base}/${anexoId}?${sufixo}`, {
      headers: { Authorization: `Bearer ${tokens.access ?? ''}` },
    });
    if (!r.ok) {
      return;
    }
    const blob = await r.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = nome;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <Stack gap={0} h="100%">
      <Box p="md" style={{ borderBottom: '1px solid var(--mantine-color-default-border)' }}>
        <Group justify="space-between" align="flex-start" wrap="nowrap" mb="xs">
          <Text fw={700} size="lg" style={{ lineHeight: 1.25 }}>
            {m.assunto || '(sem assunto)'}
          </Text>
          <Group gap={4} wrap="nowrap">
            <Tooltip label="Responder" withArrow>
              <ActionIcon variant="default" onClick={aoResponder} aria-label="Responder">
                <IconPencil size={16} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label="Marcar como por ler" withArrow>
              <ActionIcon variant="default" onClick={aoMarcarPorLer} aria-label="Por ler">
                <IconMailOpened size={16} />
              </ActionIcon>
            </Tooltip>
            <Tooltip label="Mover para o lixo" withArrow>
              <ActionIcon variant="default" color="red" onClick={aoApagar} aria-label="Apagar">
                <IconTrash size={16} />
              </ActionIcon>
            </Tooltip>
          </Group>
        </Group>

        <Group justify="space-between" align="flex-end" wrap="nowrap">
          <Remetente p={m.de} />
          <Text size="xs" c="dimmed" style={{ whiteSpace: 'nowrap' }}>
            {m.data ? new Date(m.data).toLocaleString('pt-AO') : ''}
          </Text>
        </Group>

        {m.para.length > 0 && (
          <Text size="xs" c="dimmed" mt={6}>
            Para: {m.para.map((p) => p.nome ?? p.email).join(', ')}
            {m.cc.length > 0 && ` · Cc: ${m.cc.map((p) => p.nome ?? p.email).join(', ')}`}
          </Text>
        )}

        <PropostaDoFornecedor
          entidade={m.de?.entidade}
          pasta={pasta}
          uid={m.uid}
          anexos={m.anexos}
        />

        {m.anexos.length > 0 && (
          <Group gap={6} mt="xs">
            {m.anexos.map((a) => (
              <Button
                key={a.id}
                size="compact-xs"
                variant="default"
                leftSection={<IconDownload size={12} />}
                onClick={() => void descarregar(a.id, a.nome)}
              >
                {a.nome}
                <Text span size="xs" c="dimmed" ml={5}>
                  {tamanhoLegivel(a.tamanho)}
                </Text>
              </Button>
            ))}
          </Group>
        )}
      </Box>

      <Box style={{ flex: 1, minHeight: 0 }}>
        {m.html ? (
          // Moldura isolada e sem permissões: o corpo de um email é conteúdo de
          // um estranho. O servidor já lhe tirou o que podia correr; esta é a
          // defesa que conta.
          <iframe
            title="Mensagem"
            sandbox=""
            srcDoc={`<!doctype html><meta charset="utf-8"><style>body{font-family:system-ui,sans-serif;font-size:14px;line-height:1.5;padding:14px;margin:0;color:#18181b}img{max-width:100%;height:auto}</style>${m.html}`}
            style={{ width: '100%', height: '100%', border: 0, background: '#fff' }}
          />
        ) : (
          <ScrollArea h="100%">
            <Text
              size="sm"
              p="md"
              style={{ whiteSpace: 'pre-wrap', fontFamily: 'ui-monospace, monospace' }}
            >
              {m.texto ?? '(sem conteúdo)'}
            </Text>
          </ScrollArea>
        )}
      </Box>
    </Stack>
  );
}
