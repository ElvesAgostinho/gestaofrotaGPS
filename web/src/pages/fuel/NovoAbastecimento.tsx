/**
 * Lançamento manual de um abastecimento.
 *
 * <p>Não existia: só se conseguia lançar combustível por chamadas à API. Numa
 * frota angolana a maior parte dos abastecimentos ainda entra à mão, com o
 * talão do posto na mesa.
 *
 * <p>O campo que decide tudo é a <b>leitura do contador</b>: sem ela não há
 * distância percorrida, e sem distância não há consumo — e sem consumo o
 * sistema não consegue dizer o que está a mais. Por isso avisa quando falta,
 * em vez de aceitar em silêncio e produzir um painel vazio.
 */
import {
  Alert,
  Button,
  Checkbox,
  Grid,
  Group,
  Modal,
  Select,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { SeccaoForm } from '../../components/erp';
import { fmtNumber } from '../../lib/format';

interface AssetOpcao {
  id: string;
  tag: string;
  name: string;
}

interface Motorista {
  id: string;
  name: string;
}

interface Local {
  id: string;
  name: string;
  kind: string;
}

const COMBUSTIVEIS = [
  { value: 'DIESEL', label: 'Gasóleo' },
  { value: 'PETROL', label: 'Gasolina' },
  { value: 'LPG', label: 'GPL' },
  { value: 'ELECTRIC', label: 'Elétrico' },
];

export function NovoAbastecimento({
  aberto,
  fechar,
  assetIdFixo,
}: {
  aberto: boolean;
  fechar: () => void;
  /** Quando aberto a partir da ficha de uma viatura, já se sabe qual é. */
  assetIdFixo?: string;
}) {
  const queryClient = useQueryClient();

  const [assetId, setAssetId] = useState<string | null>(assetIdFixo ?? null);
  const [filledAt, setFilledAt] = useState<Date | null>(new Date());
  const [liters, setLiters] = useState('');
  const [pricePerLiter, setPricePerLiter] = useState('');
  const [totalCost, setTotalCost] = useState('');
  const [meterValue, setMeterValue] = useState('');
  const [fullTank, setFullTank] = useState(true);
  const [station, setStation] = useState('');
  const [driverId, setDriverId] = useState<string | null>(null);
  const [branchId, setBranchId] = useState<string | null>(null);
  const [cardNumber, setCardNumber] = useState('');
  const [invoiceNumber, setInvoiceNumber] = useState('');
  const [fuelType, setFuelType] = useState<string | null>('DIESEL');
  const [notes, setNotes] = useState('');

  const { data: ativos } = useQuery({
    queryKey: ['assets', 'opcoes'],
    queryFn: () => api<{ content: AssetOpcao[] }>('/assets?size=300'),
    enabled: aberto && !assetIdFixo,
  });
  const { data: motoristas } = useQuery({
    queryKey: ['drivers', 'opcoes'],
    queryFn: () => api<{ content: Motorista[] }>('/drivers?size=200'),
    enabled: aberto,
  });
  const { data: locais } = useQuery({
    queryKey: ['locations', 'opcoes'],
    queryFn: () => api<Local[]>('/locations'),
    enabled: aberto,
  });

  const criar = useMutation({
    mutationFn: () =>
      api(`/assets/${assetId}/fuel`, {
        method: 'POST',
        body: {
          liters: Number(liters),
          filledAt: filledAt ? filledAt.toISOString() : null,
          pricePerLiter: pricePerLiter.trim() ? Number(pricePerLiter) : null,
          // Vazio deixa o servidor calcular a partir do preço por litro.
          totalCost: totalCost.trim() ? Number(totalCost) : null,
          meterValue: meterValue.trim() ? Number(meterValue) : null,
          fullTank,
          station: station.trim() || null,
          driverId,
          branchId,
          cardNumber: cardNumber.trim() || null,
          invoiceNumber: invoiceNumber.trim() || null,
          fuelType,
          notes: notes.trim() || null,
        },
      }),
    onSuccess: () => {
      notifications.show({
        title: 'Abastecimento lançado',
        message: meterValue.trim()
          ? 'O consumo é recalculado a partir do último depósito cheio.'
          : 'Sem leitura do contador não há consumo para este abastecimento.',
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['fuel'] });
      queryClient.invalidateQueries({ queryKey: ['asset'] });
      limpar();
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível lançar', message: e.message, color: 'red' }),
  });

  function limpar() {
    if (!assetIdFixo) setAssetId(null);
    setLiters('');
    setPricePerLiter('');
    setTotalCost('');
    setMeterValue('');
    setStation('');
    setCardNumber('');
    setInvoiceNumber('');
    setNotes('');
    setFilledAt(new Date());
  }

  const litrosNum = Number(liters);
  const precoNum = Number(pricePerLiter);
  const totalCalculado =
    liters.trim() && pricePerLiter.trim() && !Number.isNaN(litrosNum) && !Number.isNaN(precoNum)
      ? litrosNum * precoNum
      : null;

  const faltaAlgo = !assetId || !liters.trim() || Number.isNaN(litrosNum) || litrosNum <= 0;
  const filiais = (locais ?? []).filter((l) => l.kind === 'BRANCH');

  return (
    <Modal
      opened={aberto}
      onClose={fechar}
      title="Lançar abastecimento"
      size="lg"
      closeOnClickOutside={false}
    >
      <SeccaoForm titulo="Abastecimento">
        <Grid gutter="xs">
          {!assetIdFixo && (
            <Grid.Col span={{ base: 12, sm: 7 }}>
              <Select
                label="Viatura ou equipamento"
                placeholder="Escolha o ativo"
                required
                searchable
                data={(ativos?.content ?? []).map((a) => ({
                  value: a.id,
                  label: `${a.tag} — ${a.name}`,
                }))}
                value={assetId}
                onChange={setAssetId}
              />
            </Grid.Col>
          )}
          <Grid.Col span={{ base: 12, sm: assetIdFixo ? 6 : 5 }}>
            <DateTimePicker
              label="Data e hora"
              valueFormat="DD/MM/YYYY HH:mm"
              value={filledAt}
              onChange={(v) => setFilledAt(v as Date | null)}
            />
          </Grid.Col>

          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Litros"
              required
              placeholder="150"
              inputMode="decimal"
              value={liters}
              onChange={(e) => setLiters(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Preço por litro"
              placeholder="325"
              inputMode="decimal"
              value={pricePerLiter}
              onChange={(e) => setPricePerLiter(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput
              label="Total pago (Kz)"
              placeholder={totalCalculado != null ? fmtNumber(totalCalculado, 2) : 'Calculado'}
              description={
                totalCalculado != null
                  ? `Vazio usa ${fmtNumber(totalCalculado, 2)} Kz.`
                  : 'Vazio calcula a partir do preço por litro.'
              }
              inputMode="decimal"
              value={totalCost}
              onChange={(e) => setTotalCost(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>

          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput
              label="Leitura do contador"
              placeholder="185600"
              description="Quilómetros ou horas, conforme o equipamento."
              inputMode="decimal"
              value={meterValue}
              onChange={(e) => setMeterValue(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <Checkbox
              label="Depósito cheio"
              description="Só entre enchimentos completos há consumo fiável."
              checked={fullTank}
              onChange={(e) => setFullTank(e.currentTarget.checked)}
              mt={22}
            />
          </Grid.Col>
        </Grid>

        {!meterValue.trim() && (
          <Alert color="yellow" variant="light" p="xs" mt="xs">
            <Text size="xs">
              Sem a leitura do contador este abastecimento entra no custo mas fica de fora do
              consumo — e é o consumo que mostra o que está a mais.
            </Text>
          </Alert>
        )}
      </SeccaoForm>

      <SeccaoForm
        titulo="Onde, quem e com quê"
        descricao="O que permite depois cruzar com a posição da viatura e imputar o custo."
      >
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <TextInput
              label="Posto"
              placeholder="Sonangol Viana"
              value={station}
              onChange={(e) => setStation(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <Select
              label="Motorista"
              placeholder="Quem abasteceu"
              clearable
              searchable
              data={(motoristas?.content ?? []).map((m) => ({ value: m.id, label: m.name }))}
              value={driverId}
              onChange={setDriverId}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <Select
              label="Filial"
              placeholder="Quem suporta o custo"
              clearable
              data={filiais.map((f) => ({ value: f.id, label: f.name }))}
              value={branchId}
              onChange={setBranchId}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 4 }}>
            <TextInput
              label="Cartão"
              placeholder="CARD-4412"
              value={cardNumber}
              onChange={(e) => setCardNumber(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 4 }}>
            <TextInput
              label="Fatura"
              placeholder="FT 2026/9912"
              value={invoiceNumber}
              onChange={(e) => setInvoiceNumber(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <Select
              label="Combustível"
              data={COMBUSTIVEIS}
              value={fuelType}
              onChange={setFuelType}
              clearable
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 8 }}>
            <Textarea
              label="Observações"
              placeholder="Algo que explique este abastecimento."
              autosize
              minRows={1}
              value={notes}
              onChange={(e) => setNotes(e.currentTarget.value)}
            />
          </Grid.Col>
        </Grid>
      </SeccaoForm>

      <Group justify="flex-end" gap="xs">
        <Button variant="default" onClick={fechar}>
          Cancelar
        </Button>
        <Button onClick={() => criar.mutate()} loading={criar.isPending} disabled={faltaAlgo}>
          Lançar
        </Button>
      </Group>
    </Modal>
  );
}
