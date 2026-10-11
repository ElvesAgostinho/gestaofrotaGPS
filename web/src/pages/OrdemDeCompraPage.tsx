import {
  Alert,
  Badge,
  Button,
  Group,
  Modal,
  Stack,
  Table,
  Text,
  Textarea,
  TextInput,
  Timeline,
  Tooltip,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconArrowLeft,
  IconBan,
  IconCheck,
  IconInfoCircle,
  IconMailForward,
  IconPrinter,
  IconSend,
  IconThumbUp,
  IconX,
} from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, ApiError, tokens } from '../api/client';
import { BarraEstado, Painel, SeccaoForm } from '../components/erp';
import { fmtDate, fmtDateTime, fmtMoney, fmtNumber } from '../lib/format';
import { COR_ESTADO, type Ordem } from './compras/tipos';

/**
 * A ficha de uma ordem de compra.
 *
 * <p>Os botões não são adivinhados aqui: o servidor diz, para esta pessoa e
 * esta ordem, o que se pode fazer agora. É deliberado — a regra de quem aprova
 * depende do valor e dos limites da empresa, e um ecrã que a replicasse ficava
 * desactualizado à primeira regra nova. Quando não se pode aprovar, mostra-se
 * <b>porquê</b> em vez de esconder o botão sem explicação.
 */
export function OrdemDeCompraPage() {
  const { id = '' } = useParams();
  const qc = useQueryClient();
  const [motivo, setMotivo] = useState('');
  const [aRejeitar, setARejeitar] = useState(false);
  const [aEnviar, setAEnviar] = useState(false);
  const [paraEnvio, setParaEnvio] = useState('');

  const { data: o } = useQuery({
    queryKey: ['compras', 'ordem', id],
    queryFn: () => api<Ordem>(`/purchase-orders/${id}`),
  });

  const recarregar = () => {
    void qc.invalidateQueries({ queryKey: ['compras'] });
    void qc.invalidateQueries({ queryKey: ['consumiveis'] });
  };

  const falhou = (e: unknown) =>
    notifications.show({
      color: 'red',
      title: 'Não foi possível',
      message: e instanceof ApiError ? e.message : 'Tente de novo.',
    });

  const accao = (caminho: string, corpo?: Record<string, unknown>) =>
    api(`/purchase-orders/${id}/${caminho}`, { method: 'POST', body: corpo ?? {} });

  const submeter = useMutation({
    mutationFn: () => accao('submit'),
    onSuccess: recarregar,
    onError: falhou,
  });
  const aprovar = useMutation({
    mutationFn: () => accao('approve'),
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'Ordem aprovada.' });
      recarregar();
    },
    onError: falhou,
  });
  const rejeitar = useMutation({
    mutationFn: () => accao('reject', { motivo }),
    onSuccess: () => {
      setARejeitar(false);
      setMotivo('');
      recarregar();
    },
    onError: falhou,
  });
  const confirmar = useMutation({
    mutationFn: () => accao('confirm'),
    onSuccess: recarregar,
    onError: falhou,
  });
  const anular = useMutation({
    mutationFn: () => accao('cancel', { motivo }),
    onSuccess: recarregar,
    onError: falhou,
  });
  const enviar = useMutation({
    mutationFn: () => accao('send', { para: paraEnvio || null }),
    onSuccess: () => {
      setAEnviar(false);
      notifications.show({ color: 'green', message: 'Ordem enviada ao fornecedor.' });
      recarregar();
    },
    onError: falhou,
  });

  /** O PDF vai por uma rota autenticada: um href directo não leva o token. */
  const imprimir = async () => {
    const r = await fetch(`/api/v1/purchase-orders/${id}/pdf`, {
      headers: { Authorization: `Bearer ${tokens.access ?? ''}` },
    });
    if (!r.ok) {
      notifications.show({ color: 'red', message: 'Não foi possível gerar o PDF.' });
      return;
    }
    const url = URL.createObjectURL(await r.blob());
    window.open(url, '_blank');
  };

  if (!o) {
    return (
      <Painel titulo="Ordem de compra">
        <Text size="sm" c="dimmed">
          A carregar…
        </Text>
      </Painel>
    );
  }

  return (
    <Stack gap="lg">
      <Painel
        titulo={`${o.number} · ${o.supplierName}`}
        acoes={
          <Group gap="xs" style={{ width: '100%' }}>
            <Button
              size="xs"
              variant="default"
              component={Link}
              to="/compras"
              leftSection={<IconArrowLeft size={14} />}
            >
              Compras
            </Button>
            <Badge size="lg" variant="light" color={COR_ESTADO[o.status]}>
              {o.statusLabel}
            </Badge>

            {o.podem.submeter && (
              <Button
                size="xs"
                leftSection={<IconSend size={14} />}
                loading={submeter.isPending}
                onClick={() => submeter.mutate()}
              >
                Submeter
              </Button>
            )}
            {o.podem.aprovar && (
              <Button
                size="xs"
                color="teal"
                leftSection={<IconThumbUp size={14} />}
                loading={aprovar.isPending}
                onClick={() => aprovar.mutate()}
              >
                Aprovar
              </Button>
            )}
            {o.podem.rejeitar && (
              <Button
                size="xs"
                color="red"
                variant="light"
                leftSection={<IconX size={14} />}
                onClick={() => setARejeitar(true)}
              >
                Rejeitar
              </Button>
            )}
            {o.podem.enviar && (
              <Button
                size="xs"
                leftSection={<IconMailForward size={14} />}
                onClick={() => {
                  setParaEnvio(o.supplierEmail ?? '');
                  setAEnviar(true);
                }}
              >
                Enviar ao fornecedor
              </Button>
            )}
            {o.podem.confirmar && (
              <Button
                size="xs"
                color="green"
                variant="light"
                leftSection={<IconCheck size={14} />}
                loading={confirmar.isPending}
                onClick={() => confirmar.mutate()}
              >
                Fornecedor confirmou
              </Button>
            )}
            <Button
              size="xs"
              variant="default"
              leftSection={<IconPrinter size={14} />}
              onClick={() => void imprimir()}
            >
              PDF
            </Button>
            {o.podem.anular && (
              <Button
                size="xs"
                variant="subtle"
                color="gray"
                leftSection={<IconBan size={14} />}
                onClick={() => anular.mutate()}
              >
                Anular
              </Button>
            )}
          </Group>
        }
      >
        <Stack gap="sm">
          {o.podem.porqueNaoAprova && (
            <Alert color="orange" variant="light" icon={<IconInfoCircle size={18} />}>
              {o.podem.porqueNaoAprova}
            </Alert>
          )}
          {o.impactoNoOrcamento && (
            <Alert color="blue" variant="light" icon={<IconInfoCircle size={18} />}>
              {o.impactoNoOrcamento}
            </Alert>
          )}
          {o.rejectedReason && (
            <Alert color="red" variant="light" icon={<IconX size={18} />}>
              <b>Rejeitada:</b> {o.rejectedReason}
            </Alert>
          )}

          <BarraEstado
            itens={[
              { rotulo: 'NIF', valor: o.supplierTaxId ?? '—' },
              { rotulo: 'Pagamento', valor: o.paymentTerms ?? '—' },
              { rotulo: 'IBAN', valor: o.supplierIban ?? '—' },
              { rotulo: 'Entrega até', valor: o.expectedAt ? fmtDate(o.expectedAt) : '—' },
              { rotulo: 'Criada por', valor: o.createdByLabel ?? '—' },
              { rotulo: 'Aprovada por', valor: o.approvedByLabel ?? '—' },
              { rotulo: 'Quem aprova', valor: o.quemAprova ?? '—' },
            ]}
          />
        </Stack>
      </Painel>

      <Painel titulo="Artigos" semPadding>
        <Table.ScrollContainer minWidth={820}>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Designação</Table.Th>
                <Table.Th>Ref. do fornecedor</Table.Th>
                <Table.Th>Para</Table.Th>
                <Table.Th ta="right">Qtd.</Table.Th>
                <Table.Th ta="right">Preço unit.</Table.Th>
                <Table.Th ta="right">Total</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {o.lines.map((l, i) => (
                <Table.Tr key={l.id ?? i}>
                  <Table.Td>
                    <Text size="sm" fw={600}>
                      {l.description}
                    </Text>
                    {l.specification && (
                      <Badge size="xs" variant="outline" color="gray">
                        {l.specification}
                      </Badge>
                    )}
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" ff="monospace">
                      {l.supplierPartNumber ?? '—'}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm">{l.assetTag ?? '—'}</Text>
                  </Table.Td>
                  <Table.Td ta="right">
                    <Text size="sm">
                      {fmtNumber(l.quantity)} {l.unit ?? ''}
                    </Text>
                  </Table.Td>
                  <Table.Td ta="right">
                    <Text size="sm">{fmtMoney(l.unitPrice)}</Text>
                  </Table.Td>
                  <Table.Td ta="right">
                    <Text size="sm" fw={600}>
                      {fmtMoney(l.lineTotal)}
                    </Text>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>

        <Stack gap={2} p="md" align="flex-end">
          <Linha rotulo="Subtotal" valor={fmtMoney(o.subtotal)} />
          {o.discountAmount > 0 && (
            <Linha rotulo="Desconto" valor={`− ${fmtMoney(o.discountAmount)}`} />
          )}
          {o.taxAmount > 0 && <Linha rotulo="IVA" valor={fmtMoney(o.taxAmount)} />}
          <Linha rotulo="Total" valor={fmtMoney(o.total)} forte />
          {o.withholdingAmount > 0 && (
            <>
              <Linha
                rotulo="Retenção na fonte"
                valor={`− ${fmtMoney(o.withholdingAmount)}`}
              />
              <Tooltip
                label="O que se transfere de facto. Transferir o total quando há retenção é pagar a mais e ir pedir o dinheiro de volta."
                withArrow
                multiline
                w={280}
              >
                <div>
                  <Linha rotulo="A transferir" valor={fmtMoney(o.payable)} forte />
                </div>
              </Tooltip>
            </>
          )}
        </Stack>
      </Painel>

      <Painel titulo="Histórico">
        <Timeline active={o.eventos.length} bulletSize={16} lineWidth={2}>
          {o.eventos.map((e, i) => (
            <Timeline.Item key={i} title={e.rotulo}>
              <Text size="xs" c="dimmed">
                {e.byLabel ? `${e.byLabel} · ` : ''}
                {fmtDateTime(e.at)}
              </Text>
              {e.note && <Text size="sm">{e.note}</Text>}
            </Timeline.Item>
          ))}
        </Timeline>
      </Painel>

      <Modal opened={aRejeitar} onClose={() => setARejeitar(false)} title="Rejeitar a ordem">
        <Stack gap="sm">
          <Textarea
            label="Porquê"
            description="Quem a fez tem de saber o que corrigir — senão ela volta igual."
            autosize
            minRows={3}
            value={motivo}
            onChange={(e) => setMotivo(e.currentTarget.value)}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setARejeitar(false)}>
              Cancelar
            </Button>
            <Button
              color="red"
              disabled={!motivo.trim()}
              loading={rejeitar.isPending}
              onClick={() => rejeitar.mutate()}
            >
              Rejeitar
            </Button>
          </Group>
        </Stack>
      </Modal>

      <Modal opened={aEnviar} onClose={() => setAEnviar(false)} title="Enviar ao fornecedor">
        <Stack gap="sm">
          <SeccaoForm
            titulo="Para onde vai"
            descricao="O PDF é gerado no servidor, com timbre e selo de verificação, e segue em anexo pela caixa de correio da empresa. Fica cópia em «Enviados»."
          >
            <TextInput
              label="Email"
              value={paraEnvio}
              onChange={(e) => setParaEnvio(e.currentTarget.value)}
              placeholder="vendas@fornecedor.ao"
            />
          </SeccaoForm>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setAEnviar(false)}>
              Cancelar
            </Button>
            <Button
              disabled={!paraEnvio.includes('@')}
              loading={enviar.isPending}
              onClick={() => enviar.mutate()}
            >
              Enviar
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

function Linha({ rotulo, valor, forte }: { rotulo: string; valor: string; forte?: boolean }) {
  return (
    <Group gap="lg" justify="space-between" style={{ minWidth: 260 }}>
      <Text size="sm" c="dimmed" fw={forte ? 700 : 400}>
        {rotulo}
      </Text>
      <Text size={forte ? 'md' : 'sm'} fw={forte ? 700 : 500}>
        {valor}
      </Text>
    </Group>
  );
}
