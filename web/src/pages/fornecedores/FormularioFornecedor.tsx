import {
  Button,
  Grid,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Switch,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api, ApiError } from '../../api/client';
import { SeccaoForm } from '../../components/erp';
import { REGIMES_IVA, TIPOS_FORNECEDOR, type Fornecedor } from './tipos';

/**
 * Registar ou alterar um fornecedor.
 *
 * <p>Os campos estão agrupados pela pergunta a que respondem, e não pela ordem
 * em que foram acrescentados à base de dados: quem é, como se fala com ele,
 * como se paga, e em quantos dias entrega. O último grupo é o que decide
 * compras, e por isso tem explicação por baixo de cada campo — o prazo de
 * despacho é a diferença entre uma peça em cinco dias e a mesma peça em três
 * semanas, e quem preenche isto raramente sabe que o sistema usa esse número.
 */
export function FormularioFornecedor({
  aberto,
  aoFechar,
  existente,
}: {
  aberto: boolean;
  aoFechar: () => void;
  existente?: Fornecedor;
}) {
  const qc = useQueryClient();
  const vazio = {
    name: '',
    taxId: '',
    kind: 'PARTS',
    phone: '',
    email: '',
    address: '',
    city: '',
    contactPerson: '',
    paymentTerms: '',
    defaultWarrantyMonths: '' as number | string,
    vatRegime: '' as string | null,
    withholdingTax: '' as number | string,
    iban: '',
    bankName: '',
    currency: 'AOA',
    avgLeadTimeDays: '' as number | string,
    importer: false,
    incoterm: '',
    customsDays: '' as number | string,
    minOrderValue: '' as number | string,
    brands: '',
    families: '',
    notes: '',
    active: true,
  };
  const [v, setV] = useState(vazio);

  useEffect(() => {
    if (existente) {
      setV({
        name: existente.name,
        taxId: existente.taxId ?? '',
        kind: existente.kind,
        phone: existente.phone ?? '',
        email: existente.email ?? '',
        address: existente.address ?? '',
        city: existente.city ?? '',
        contactPerson: existente.contactPerson ?? '',
        paymentTerms: existente.paymentTerms ?? '',
        defaultWarrantyMonths: existente.defaultWarrantyMonths ?? '',
        vatRegime: existente.vatRegime ?? null,
        withholdingTax: existente.withholdingTax ?? '',
        iban: existente.iban ?? '',
        bankName: existente.bankName ?? '',
        currency: existente.currency ?? 'AOA',
        avgLeadTimeDays: existente.avgLeadTimeDays ?? '',
        importer: existente.importer,
        incoterm: existente.incoterm ?? '',
        customsDays: existente.customsDays ?? '',
        minOrderValue: existente.minOrderValue ?? '',
        brands: existente.brands ?? '',
        families: existente.families ?? '',
        notes: existente.notes ?? '',
        active: existente.active,
      });
    } else {
      setV(vazio);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [existente, aberto]);

  const campo = (k: keyof typeof v) => ({
    value: v[k] as string,
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      // O valor lê-se antes de actualizar o estado: o evento é reaproveitado
      // pelo React e já não existe quando a função de actualização corre.
      const valor = e.currentTarget.value;
      setV((f) => ({ ...f, [k]: valor }));
    },
  });

  const numero = (k: keyof typeof v) => ({
    value: v[k] as number | string,
    onChange: (valor: number | string) => setV((f) => ({ ...f, [k]: valor })),
  });

  const corpo = () => ({
    name: v.name.trim(),
    taxId: v.taxId.trim() || null,
    kind: v.kind,
    phone: v.phone.trim() || null,
    email: v.email.trim() || null,
    address: v.address.trim() || null,
    city: v.city.trim() || null,
    contactPerson: v.contactPerson.trim() || null,
    paymentTerms: v.paymentTerms.trim() || null,
    defaultWarrantyMonths:
      v.defaultWarrantyMonths === '' ? null : Number(v.defaultWarrantyMonths),
    notes: v.notes.trim() || null,
    active: v.active,
    vatRegime: v.vatRegime || null,
    withholdingTax: v.withholdingTax === '' ? null : Number(v.withholdingTax),
    iban: v.iban.trim() || null,
    bankName: v.bankName.trim() || null,
    currency: v.currency.trim() || null,
    avgLeadTimeDays: v.avgLeadTimeDays === '' ? null : Number(v.avgLeadTimeDays),
    importer: v.importer,
    incoterm: v.incoterm.trim() || null,
    customsDays: v.customsDays === '' ? null : Number(v.customsDays),
    minOrderValue: v.minOrderValue === '' ? null : Number(v.minOrderValue),
    brands: v.brands.trim() || null,
    families: v.families.trim() || null,
  });

  const guardar = useMutation({
    mutationFn: () =>
      existente
        ? api(`/suppliers/${existente.id}`, { method: 'PUT', body: corpo() })
        : api('/suppliers', { method: 'POST', body: corpo() }),
    onSuccess: () => {
      notifications.show({
        color: 'green',
        message: existente ? 'Fornecedor actualizado.' : 'Fornecedor registado.',
      });
      void qc.invalidateQueries({ queryKey: ['fornecedores'] });
      void qc.invalidateQueries({ queryKey: ['correio', 'directorio'] });
      aoFechar();
    },
    onError: (e: unknown) =>
      notifications.show({
        color: 'red',
        title: 'Não foi possível guardar',
        message: e instanceof ApiError ? e.message : 'Tente de novo.',
      }),
  });

  return (
    <Modal
      opened={aberto}
      onClose={aoFechar}
      title={existente ? `Fornecedor · ${existente.name}` : 'Novo fornecedor'}
      size="xl"
    >
      <Stack gap="xs">
        <SeccaoForm
          titulo="Quem é"
          descricao="O NIF não é opcional: sem ele, a factura deste fornecedor não se reconcilia com a contabilidade da empresa."
        >
          <Grid gutter="xs">
            <Grid.Col span={{ base: 12, sm: 6 }}>
              <TextInput label="Nome" required {...campo('name')} />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 3 }}>
              <TextInput label="NIF" placeholder="5417…" {...campo('taxId')} />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 3 }}>
              <Select
                label="Tipo"
                data={TIPOS_FORNECEDOR}
                value={v.kind}
                onChange={(x) => setV((f) => ({ ...f, kind: x ?? 'PARTS' }))}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 4 }}>
              <Select
                label="Regime de IVA"
                placeholder="Não indicado"
                data={REGIMES_IVA}
                clearable
                value={v.vatRegime}
                onChange={(x) => setV((f) => ({ ...f, vatRegime: x }))}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 4 }}>
              <NumberInput
                label="Retenção na fonte (%)"
                description="Em Angola, 6,5 % nos serviços"
                min={0}
                max={100}
                decimalScale={2}
                {...numero('withholdingTax')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 4 }}>
              <TextInput label="Moeda em que factura" placeholder="AOA" {...campo('currency')} />
            </Grid.Col>
          </Grid>
        </SeccaoForm>

        <SeccaoForm titulo="Como se fala com ele">
          <Grid gutter="xs">
            <Grid.Col span={{ base: 12, sm: 4 }}>
              <TextInput label="Telefone" {...campo('phone')} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 4 }}>
              <TextInput
                label="Email"
                description="É por aqui que a ordem de compra sai"
                {...campo('email')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 4 }}>
              <TextInput label="Pessoa de contacto" {...campo('contactPerson')} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 8 }}>
              <TextInput label="Morada" {...campo('address')} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 4 }}>
              <TextInput label="Cidade" {...campo('city')} />
            </Grid.Col>
          </Grid>
        </SeccaoForm>

        <SeccaoForm
          titulo="Como se paga"
          descricao="O IBAN vai na ordem de compra: sem ele, o financeiro tem de ir perguntar."
        >
          <Grid gutter="xs">
            <Grid.Col span={{ base: 12, sm: 5 }}>
              <TextInput label="IBAN" placeholder="AO06 …" {...campo('iban')} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 3 }}>
              <TextInput label="Banco" {...campo('bankName')} />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 4 }}>
              <TextInput
                label="Condições de pagamento"
                placeholder="30 dias, pronto pagamento…"
                {...campo('paymentTerms')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 4 }}>
              <NumberInput
                label="Valor mínimo de encomenda"
                description="Abaixo disto não vale a pena pedir"
                min={0}
                {...numero('minOrderValue')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 6, sm: 4 }}>
              <NumberInput
                label="Garantia habitual (meses)"
                min={0}
                {...numero('defaultWarrantyMonths')}
              />
            </Grid.Col>
          </Grid>
        </SeccaoForm>

        <SeccaoForm
          titulo="Em quantos dias entrega"
          descricao="É o grupo que decide compras: o sistema usa estes números para saber até quando se pode esperar antes de encomendar."
        >
          <Grid gutter="xs">
            <Grid.Col span={{ base: 6, sm: 4 }}>
              <NumberInput
                label="Prazo médio (dias)"
                description="O real, não o prometido"
                min={0}
                {...numero('avgLeadTimeDays')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 8 }}>
              <Switch
                mt={26}
                label="Importa (não tem prateleira em Luanda)"
                description="Uma peça só serve quando sai da alfândega, não quando embarca"
                checked={v.importer}
                onChange={(e) => setV((f) => ({ ...f, importer: e.currentTarget.checked }))}
              />
            </Grid.Col>
            {v.importer && (
              <>
                <Grid.Col span={{ base: 6, sm: 4 }}>
                  <TextInput label="Incoterm" placeholder="FOB, CIF, EXW…" {...campo('incoterm')} />
                </Grid.Col>
                <Grid.Col span={{ base: 6, sm: 4 }}>
                  <NumberInput
                    label="Dias de despacho"
                    description="Somados ao trânsito"
                    min={0}
                    {...numero('customsDays')}
                  />
                </Grid.Col>
              </>
            )}
          </Grid>
        </SeccaoForm>

        <SeccaoForm
          titulo="O que fornece"
          descricao="Serve para a lista de compras saber a quem pedir cada linha."
        >
          <Grid gutter="xs">
            <Grid.Col span={{ base: 12, sm: 6 }}>
              <TextInput
                label="Marcas que representa"
                placeholder="Mann, Fleetguard, Bridgestone…"
                {...campo('brands')}
              />
            </Grid.Col>
            <Grid.Col span={{ base: 12, sm: 6 }}>
              <TextInput
                label="Famílias que fornece"
                placeholder="TRUCK_HEAVY, GENERATOR…"
                {...campo('families')}
              />
            </Grid.Col>
            <Grid.Col span={12}>
              <Textarea label="Observações" autosize minRows={2} {...campo('notes')} />
            </Grid.Col>
          </Grid>
          {existente && (
            <Switch
              mt="sm"
              label="Fornecedor activo"
              description="Um fornecedor inactivo deixa de aparecer nas compras, mas o histórico fica"
              checked={v.active}
              onChange={(e) => setV((f) => ({ ...f, active: e.currentTarget.checked }))}
            />
          )}
        </SeccaoForm>

        <Group justify="flex-end">
          <Button variant="default" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button
            loading={guardar.isPending}
            disabled={!v.name.trim()}
            onClick={() => guardar.mutate()}
          >
            Guardar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
