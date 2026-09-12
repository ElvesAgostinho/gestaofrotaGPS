import {
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';

/**
 * Formulários do armazém.
 *
 * <p>O backend de peças, armazéns e movimentos existia há muito; o ecrã só
 * listava. Uma peça só entrava por importação de CSV nas Configurações — e
 * ninguém que abra «Peças e armazém» vai adivinhar isso. Um armazém que não
 * se consegue alimentar pelo próprio ecrã não é um armazém, é um relatório.
 */

export const CATEGORIAS_PECA = [
  { value: 'GENERAL', label: 'Geral' },
  { value: 'FILTER', label: 'Filtro' },
  { value: 'LUBRICANT', label: 'Lubrificante' },
  { value: 'WEAR', label: 'Desgaste' },
  { value: 'ELECTRICAL', label: 'Elétrico' },
  { value: 'HYDRAULIC', label: 'Hidráulico' },
  { value: 'ENGINE', label: 'Motor' },
  { value: 'TIRE', label: 'Pneu' },
  { value: 'BATTERY', label: 'Bateria' },
  { value: 'CONSUMABLE', label: 'Consumível' },
];

interface Armazem {
  id: string;
  name: string;
  locationName?: string | null;
  active: boolean;
}

interface Local {
  id: string;
  name: string;
}

interface PecaResumo {
  id: string;
  name: string;
  partNumber?: string | null;
  unit?: string | null;
}

function erro(titulo: string) {
  return (e: Error) => notifications.show({ title: titulo, message: e.message, color: 'red' });
}

// ==== Nova peça =============================================================

export function NovaPecaForm({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [partNumber, setPartNumber] = useState('');
  const [category, setCategory] = useState<string | null>('GENERAL');
  const [unit, setUnit] = useState('un');
  const [minQuantity, setMinQuantity] = useState<number | string>('');
  const [averageCost, setAverageCost] = useState<number | string>('');
  const [notes, setNotes] = useState('');

  const create = useMutation({
    mutationFn: () =>
      api('/parts', {
        method: 'POST',
        body: {
          name: name.trim(),
          partNumber: partNumber.trim() || null,
          category,
          unit: unit.trim() || null,
          minQuantity: minQuantity === '' ? null : Number(minQuantity),
          averageCost: averageCost === '' ? null : Number(averageCost),
          notes: notes.trim() || null,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Peça criada', message: name, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['parts'] });
      setName('');
      setPartNumber('');
      setCategory('GENERAL');
      setUnit('un');
      setMinQuantity('');
      setAverageCost('');
      setNotes('');
      onClose();
    },
    onError: erro('Não foi possível criar a peça'),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Nova peça" centered>
      <Stack gap="sm">
        <TextInput
          label="Nome"
          required
          placeholder="Filtro de óleo do motor"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
        />
        <Group grow>
          <TextInput
            label="Nº de peça"
            placeholder="1R-0739"
            value={partNumber}
            onChange={(e) => setPartNumber(e.currentTarget.value)}
          />
          <Select label="Categoria" data={CATEGORIAS_PECA} value={category} onChange={setCategory} />
        </Group>
        <Group grow>
          <TextInput
            label="Unidade"
            placeholder="un, L, kg"
            value={unit}
            onChange={(e) => setUnit(e.currentTarget.value)}
          />
          <NumberInput
            label="Stock mínimo"
            description="Abaixo disto o sistema avisa"
            value={minQuantity}
            onChange={setMinQuantity}
            min={0}
          />
        </Group>
        <NumberInput
          label="Custo médio (Kz)"
          description="Deixe vazio: o custo passa a ser calculado pelas entradas."
          value={averageCost}
          onChange={setAverageCost}
          min={0}
          decimalScale={2}
        />
        <Textarea
          label="Notas"
          autosize
          minRows={2}
          value={notes}
          onChange={(e) => setNotes(e.currentTarget.value)}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={!name.trim()} loading={create.isPending} onClick={() => create.mutate()}>
            Criar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

// ==== Novo armazém ==========================================================

export function NovoArmazemForm({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [locationId, setLocationId] = useState<string | null>(null);

  const { data: locais } = useQuery({
    queryKey: ['locations'],
    queryFn: () => api<Local[]>('/locations'),
    enabled: opened,
  });

  const create = useMutation({
    mutationFn: () =>
      api('/warehouses', {
        method: 'POST',
        body: { name: name.trim(), locationId: locationId || null },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Armazém criado', message: name, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['warehouses'] });
      setName('');
      setLocationId(null);
      onClose();
    },
    onError: erro('Não foi possível criar o armazém'),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Novo armazém" centered>
      <Stack gap="sm">
        <TextInput
          label="Nome"
          required
          placeholder="Armazém central"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
        />
        <Select
          label="Filial ou local"
          description="Onde fica fisicamente. Opcional."
          data={(locais ?? []).map((l) => ({ value: l.id, label: l.name }))}
          value={locationId}
          onChange={setLocationId}
          searchable
          clearable
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={!name.trim()} loading={create.isPending} onClick={() => create.mutate()}>
            Criar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

// ==== Movimento de stock ====================================================

const TIPOS_MOVIMENTO = [
  { value: 'IN', label: 'Entrada (compra, receção)' },
  { value: 'OUT_OTHER', label: 'Saída (sem ordem de serviço)' },
  { value: 'ADJUSTMENT', label: 'Acerto de inventário' },
];

/**
 * Entrada, saída ou acerto.
 *
 * <p>A saída para uma ordem de serviço não está aqui de propósito: faz-se na
 * própria ordem, para o custo da peça cair na máquina certa. Uma saída
 * «avulsa» que na verdade foi para uma reparação é custo que desaparece.
 */
export function MovimentoStockForm({
  opened,
  onClose,
  pecaInicial,
}: {
  opened: boolean;
  onClose: () => void;
  pecaInicial?: string | null;
}) {
  const queryClient = useQueryClient();
  const [partId, setPartId] = useState<string | null>(pecaInicial ?? null);
  const [warehouseId, setWarehouseId] = useState<string | null>(null);
  const [type, setType] = useState<string | null>('IN');
  const [quantity, setQuantity] = useState<number | string>('');
  const [unitCost, setUnitCost] = useState<number | string>('');
  const [reference, setReference] = useState('');

  const { data: pecas } = useQuery({
    queryKey: ['parts'],
    queryFn: () => api<PecaResumo[]>('/parts'),
    enabled: opened,
  });
  const { data: armazens } = useQuery({
    queryKey: ['warehouses'],
    queryFn: () => api<Armazem[]>('/warehouses'),
    enabled: opened,
  });

  const ativos = (armazens ?? []).filter((a) => a.active);

  const registar = useMutation({
    mutationFn: () =>
      api('/stock/movements', {
        method: 'POST',
        body: {
          partId,
          warehouseId,
          type,
          quantity: Number(quantity),
          unitCost: unitCost === '' ? null : Number(unitCost),
          reference: reference.trim() || null,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Movimento registado', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['parts'] });
      queryClient.invalidateQueries({ queryKey: ['stock'] });
      setQuantity('');
      setUnitCost('');
      setReference('');
      onClose();
    },
    onError: erro('Não foi possível registar'),
  });

  const pronto = !!partId && !!warehouseId && !!type && Number(quantity) > 0;

  return (
    <Modal opened={opened} onClose={onClose} title="Movimento de stock" centered>
      <Stack gap="sm">
        {ativos.length === 0 && (
          <Text size="sm" c="orange">
            Ainda não há armazéns. Crie um primeiro — o stock tem de estar em algum sítio.
          </Text>
        )}
        <Select
          label="Peça"
          required
          searchable
          data={(pecas ?? []).map((p) => ({
            value: p.id,
            label: p.partNumber ? `${p.name} — ${p.partNumber}` : p.name,
          }))}
          value={partId}
          onChange={setPartId}
        />
        <Group grow>
          <Select
            label="Armazém"
            required
            data={ativos.map((a) => ({
              value: a.id,
              label: a.locationName ? `${a.name} — ${a.locationName}` : a.name,
            }))}
            value={warehouseId}
            onChange={setWarehouseId}
          />
          <Select label="Tipo" required data={TIPOS_MOVIMENTO} value={type} onChange={setType} />
        </Group>
        <Group grow>
          <NumberInput
            label="Quantidade"
            required
            value={quantity}
            onChange={setQuantity}
            min={0}
            decimalScale={2}
          />
          <NumberInput
            label="Custo unitário (Kz)"
            description="Só nas entradas"
            value={unitCost}
            onChange={setUnitCost}
            min={0}
            decimalScale={2}
            disabled={type !== 'IN'}
          />
        </Group>
        <TextInput
          label="Referência"
          placeholder="Fatura, guia de remessa…"
          value={reference}
          onChange={(e) => setReference(e.currentTarget.value)}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancelar
          </Button>
          <Button disabled={!pronto} loading={registar.isPending} onClick={() => registar.mutate()}>
            Registar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
