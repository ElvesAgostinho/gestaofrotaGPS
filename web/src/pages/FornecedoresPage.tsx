import { Badge, Group, Stack, Text } from '@mantine/core';
import { IconPlus, IconShip } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { BotaoBarra, Painel, SeparadorBarra } from '../components/erp';
import { Grelha } from '../components/Grelha';
import { CampoProcura } from '../components/Procura';
import { FormularioFornecedor } from './fornecedores/FormularioFornecedor';
import type { Fornecedor } from './fornecedores/tipos';

/**
 * Os fornecedores da empresa.
 *
 * <p>Até aqui só se registava um nome e um telefone, de dentro de uma ordem de
 * serviço — servia para dizer «a oficina X arranjou isto». Não servia para
 * comprar: para comprar é preciso saber a quem se manda a encomenda, se os
 * papéis dele estão em ordem, a que preço vende e em quantos dias entrega.
 *
 * <p>A coluna do prazo é a que mais conta, e por isso mostra o prazo
 * <b>total</b>: quem importa soma o despacho ao trânsito, porque uma peça só
 * serve quando sai da alfândega — não quando embarca.
 */
export function FornecedoresPage() {
  const navegar = useNavigate();
  const [procura, setProcura] = useState('');
  const [novo, setNovo] = useState(false);

  const { data } = useQuery({
    queryKey: ['fornecedores'],
    queryFn: () => api<Fornecedor[]>('/suppliers'),
  });

  const linhas = (data ?? []).filter((f) =>
    procura.trim()
      ? `${f.name} ${f.taxId ?? ''} ${f.city ?? ''} ${f.brands ?? ''}`
          .toLowerCase()
          .includes(procura.toLowerCase())
      : true,
  );

  return (
    <Stack gap="lg">
      <Painel
        titulo="Fornecedores"
        semPadding
        acoes={
          <>
            <BotaoBarra icone={<IconPlus size={15} />} onClick={() => setNovo(true)} destaque>
              Novo fornecedor
            </BotaoBarra>
            <SeparadorBarra />
            <CampoProcura
              valor={procura}
              aoMudar={setProcura}
              placeholder="Nome, NIF, cidade ou marca…"
              largura={260}
            />
          </>
        }
        rodape={
          <Text size="xs" c="dimmed">
            O prazo mostrado já soma o despacho aduaneiro ao trânsito — é com ele que o painel de
            compras decide quando encomendar.
          </Text>
        }
      >
        <Grelha
          id="fornecedores"
          linhas={linhas}
          chave={(f) => f.id}
          larguraMinima={940}
          aoAbrir={(f) => navegar(`/fornecedores/${f.id}`)}
          vazio={
            procura
              ? 'Nenhum fornecedor corresponde à procura.'
              : 'Ainda não há fornecedores. Sem eles, a lista de compras sabe o que falta mas não a quem pedir.'
          }
          colunas={[
            {
              id: 'nome',
              titulo: 'Fornecedor',
              fixa: true,
              valor: (f) => f.name,
              render: (f) => (
                <Group gap={6} wrap="nowrap">
                  <Text size="sm" fw={600}>
                    {f.name}
                  </Text>
                  {f.importer && (
                    <Badge size="xs" variant="light" color="blue" leftSection={<IconShip size={9} />}>
                      importa
                    </Badge>
                  )}
                  {!f.active && (
                    <Badge size="xs" variant="light" color="gray">
                      inactivo
                    </Badge>
                  )}
                </Group>
              ),
            },
            { id: 'nif', titulo: 'NIF', largura: 120, valor: (f) => f.taxId ?? null },
            { id: 'tipo', titulo: 'Tipo', largura: 120, valor: (f) => f.kindLabel },
            { id: 'cidade', titulo: 'Cidade', largura: 120, valor: (f) => f.city ?? null },
            {
              id: 'telefone',
              titulo: 'Telefone',
              largura: 130,
              valor: (f) => f.phone ?? null,
            },
            {
              id: 'email',
              titulo: 'Email',
              largura: 190,
              escondida: true,
              valor: (f) => f.email ?? null,
            },
            {
              id: 'prazo',
              titulo: 'Prazo',
              largura: 90,
              alinhar: 'right',
              valor: (f) => f.prazoTotalDias ?? null,
              render: (f) =>
                f.prazoTotalDias != null ? (
                  <Text size="sm" fw={600}>
                    {f.prazoTotalDias} d
                  </Text>
                ) : (
                  <Text size="xs" c="dimmed">
                    —
                  </Text>
                ),
            },
            {
              id: 'pagamento',
              titulo: 'Pagamento',
              largura: 150,
              valor: (f) => f.paymentTerms ?? null,
            },
            {
              id: 'marcas',
              titulo: 'Marcas que representa',
              largura: 200,
              valor: (f) => f.brands ?? null,
            },
          ]}
        />
      </Painel>

      <FormularioFornecedor aberto={novo} aoFechar={() => setNovo(false)} />
    </Stack>
  );
}
