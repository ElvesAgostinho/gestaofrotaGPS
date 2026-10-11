import { Alert, Badge, Button, Group, Select, Stack, Text } from '@mantine/core';
import { IconFileInvoice, IconPaperclip } from '@tabler/icons-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { RegistarResposta, type AnexoDoCorreio } from '../compras/RegistarResposta';
import type { Pedido } from '../compras/tiposCotacao';
import type { Anexo, Entidade } from './tipos';

/**
 * A ponte entre o correio e as cotações.
 *
 * <p>É o passo que se perdia: a proposta chegava por email, os preços
 * copiavam-se à mão para o pedido e o PDF ficava na caixa de entrada. Daí a um
 * mês, quando alguém pergunta «mas ele cotou mesmo esse preço?», a resposta
 * está enterrada em quatrocentas mensagens.
 *
 * <p>A barra só aparece quando há de facto um pedido à espera deste
 * fornecedor. Um aviso que aparecesse sempre deixava de ser lido à terceira
 * mensagem.
 */
export function PropostaDoFornecedor({
  entidade,
  pasta,
  uid,
  anexos,
}: {
  entidade?: Entidade | null;
  pasta: string;
  uid: number;
  anexos: Anexo[];
}) {
  const qc = useQueryClient();
  const [pedidoId, setPedidoId] = useState<string | null>(null);
  const [anexoId, setAnexoId] = useState<string | null>(anexos[0]?.id ?? null);
  const [aRegistar, setARegistar] = useState(false);

  // Só um fornecedor tem pedidos de cotação. Um motorista ou alguém da equipa
  // não, e perguntar por eles seria um pedido ao servidor por cada mensagem.
  const eFornecedor =
    entidade?.tipo === 'SUPPLIER' || entidade?.tipo === 'SUPPLIER_CONTACT';

  const { data: abertos } = useQuery({
    queryKey: ['cotacoes', 'abertos', entidade?.id],
    queryFn: () => api<Pedido[]>(`/rfqs/open-for/${entidade?.id}`),
    enabled: eFornecedor && !!entidade?.id,
  });

  if (!eFornecedor || !abertos || abertos.length === 0) {
    return null;
  }

  const escolhido = pedidoId ?? abertos[0].id;
  const anexoEscolhido = anexos.find((a) => a.id === anexoId) ?? anexos[0];

  return (
    <>
      <Alert color="blue" variant="light" icon={<IconFileInvoice size={18} />} mt="xs">
        <Stack gap={8}>
          <Text size="sm">
            <b>{entidade.nome}</b> tem {abertos.length === 1 ? 'um pedido' : `${abertos.length} pedidos`}{' '}
            de cotação à espera de resposta.
          </Text>

          <Group gap="xs" align="flex-end" wrap="wrap">
            {abertos.length > 1 && (
              <Select
                size="xs"
                w={280}
                label="Pedido"
                value={escolhido}
                onChange={setPedidoId}
                data={abertos.map((p) => ({
                  value: p.id,
                  label: `${p.number} · ${p.title}`,
                }))}
              />
            )}
            {anexos.length > 1 && (
              <Select
                size="xs"
                w={240}
                label="Proposta em anexo"
                value={anexoEscolhido?.id ?? null}
                onChange={setAnexoId}
                data={anexos.map((a) => ({ value: a.id, label: a.nome }))}
              />
            )}
            <Button size="xs" onClick={() => setARegistar(true)}>
              Registar a proposta
            </Button>
          </Group>

          {anexos.length === 1 && (
            <Group gap={5}>
              <IconPaperclip size={13} />
              <Text size="xs" c="dimmed">
                {anexos[0].nome} fica guardado com o pedido
              </Text>
            </Group>
          )}
          {abertos.length === 1 && (
            <Badge size="xs" variant="light" w="fit-content">
              {abertos[0].number} · {abertos[0].title}
            </Badge>
          )}
        </Stack>
      </Alert>

      {aRegistar && (
        <RegistarResposta
          pedidoId={escolhido}
          supplierId={entidade.id}
          supplierName={entidade.nome}
          anexo={
            anexoEscolhido
              ? ({
                  folder: pasta,
                  uid,
                  attachmentId: anexoEscolhido.id,
                  nome: anexoEscolhido.nome,
                } satisfies AnexoDoCorreio)
              : null
          }
          aoFechar={() => {
            setARegistar(false);
            void qc.invalidateQueries({ queryKey: ['cotacoes'] });
          }}
        />
      )}
    </>
  );
}
