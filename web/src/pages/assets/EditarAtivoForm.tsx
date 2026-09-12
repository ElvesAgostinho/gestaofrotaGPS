import { Button, Group, Modal, NumberInput, Select, Stack, Textarea, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { AssetTypeView, AssetView, LocationView } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { SeccaoForm } from '../../components/erp';
import { statusLabel } from '../../lib/format';

/**
 * Editar a ficha de um ativo.
 *
 * <p>Não existia: um ativo criava-se e nunca mais se corrigia pelo ecrã. Uma
 * matrícula mal escrita ficava mal escrita até alguém ir à base de dados.
 *
 * <p>O formulário devolve a versão que leu. Se outra pessoa gravou entretanto,
 * o servidor recusa com uma frase que diz o que fazer — e o trabalho dessa
 * pessoa fica, em vez de ser apagado em silêncio por este «guardar».
 */
export function EditarAtivoForm({
  ativo,
  aberto,
  fechar,
}: {
  ativo: AssetView & { version?: number | null; downtimeCostPerHour?: number | null; tankCapacityLiters?: number | null };
  aberto: boolean;
  fechar: () => void;
}) {
  const queryClient = useQueryClient();
  const { has } = useAuth();
  const podeVerCustos = has('COSTS_VIEW');

  const [v, setV] = useState(() => valores(ativo));
  useEffect(() => {
    if (aberto) setV(valores(ativo));
  }, [aberto, ativo]);

  const { data: tipos } = useQuery({
    queryKey: ['asset-types'],
    queryFn: () => api<AssetTypeView[]>('/asset-types'),
    enabled: aberto,
  });
  const { data: locais } = useQuery({
    queryKey: ['locations'],
    queryFn: () => api<LocationView[]>('/locations'),
    enabled: aberto,
  });

  const gravar = useMutation({
    mutationFn: () =>
      api(`/assets/${ativo.id}`, {
        method: 'PATCH',
        body: {
          tag: v.tag.trim(),
          name: v.name.trim(),
          assetTypeId: v.assetTypeId || null,
          locationId: v.locationId ?? '',
          manufacturer: v.manufacturer.trim() || null,
          model: v.model.trim() || null,
          modelYear: v.modelYear === '' ? null : Number(v.modelYear),
          serialNumber: v.serialNumber.trim() || null,
          plate: v.plate.trim() || null,
          responsibleLabel: v.responsibleLabel.trim() || null,
          status: v.status,
          speedLimitKph: v.speedLimitKph === '' ? null : Number(v.speedLimitKph),
          tankCapacityLiters: v.tankCapacityLiters === '' ? null : Number(v.tankCapacityLiters),
          // Os valores só vão se a pessoa os pode ver: um campo escondido que
          // enviasse «vazio» apagaria o valor que lá estava.
          ...(podeVerCustos
            ? {
                acquisitionValue: v.acquisitionValue === '' ? null : Number(v.acquisitionValue),
                downtimeCostPerHour: v.downtimeCostPerHour === '' ? null : Number(v.downtimeCostPerHour),
              }
            : {}),
          notes: v.notes.trim() || null,
          version: ativo.version ?? null,
        },
      }),
    onSuccess: () => {
      notifications.show({ title: 'Ficha guardada', message: v.tag, color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['asset', ativo.id] });
      queryClient.invalidateQueries({ queryKey: ['assets'] });
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red', autoClose: 9000 }),
  });

  const campo = <K extends keyof Valores>(k: K) => ({
    value: v[k] as never,
    onChange: (e: unknown) => {
      const valor = typeof e === 'object' && e !== null && 'currentTarget' in e
        ? (e as { currentTarget: HTMLInputElement }).currentTarget.value
        : e;
      setV((a) => ({ ...a, [k]: valor }));
    },
  });

  return (
    <Modal opened={aberto} onClose={fechar} title={`Editar ${ativo.tag}`} size="lg" centered>
      <Stack gap="sm">
        <SeccaoForm titulo="Identificação">
          <Group grow>
            <TextInput label="Etiqueta" required {...campo('tag')} />
            <TextInput label="Nome" required {...campo('name')} />
          </Group>
          <Group grow>
            <Select
              label="Tipo"
              data={(tipos ?? []).map((t) => ({ value: t.id, label: t.name }))}
              value={v.assetTypeId}
              onChange={(x) => setV((a) => ({ ...a, assetTypeId: x ?? '' }))}
              searchable
            />
            <Select
              label="Local"
              data={(locais ?? []).map((l) => ({ value: l.id, label: l.name }))}
              value={v.locationId}
              onChange={(x) => setV((a) => ({ ...a, locationId: x }))}
              searchable
              clearable
            />
            <Select
              label="Estado"
              data={Object.entries(statusLabel).map(([value, label]) => ({ value, label }))}
              value={v.status}
              onChange={(x) => x && setV((a) => ({ ...a, status: x }))}
            />
          </Group>
        </SeccaoForm>

        <SeccaoForm titulo="Máquina">
          <Group grow>
            <TextInput label="Fabricante" {...campo('manufacturer')} />
            <TextInput label="Modelo" {...campo('model')} />
            <NumberInput label="Ano" min={1950} max={2100} {...campo('modelYear')} />
          </Group>
          <Group grow>
            <TextInput label="Nº de série" {...campo('serialNumber')} />
            <TextInput label="Matrícula" {...campo('plate')} />
            <TextInput label="Responsável" {...campo('responsibleLabel')} />
          </Group>
          <Group grow>
            <NumberInput label="Limite de velocidade (km/h)" min={0} {...campo('speedLimitKph')} />
            <NumberInput label="Depósito (L)" min={0} {...campo('tankCapacityLiters')} />
          </Group>
        </SeccaoForm>

        {podeVerCustos && (
          <SeccaoForm titulo="Valores">
            <Group grow>
              <NumberInput label="Valor de aquisição (Kz)" min={0} decimalScale={2} {...campo('acquisitionValue')} />
              <NumberInput
                label="Custo de paragem por hora (Kz)"
                description="O que se perde por cada hora parada. Entra no custo real das ordens."
                min={0}
                decimalScale={2}
                {...campo('downtimeCostPerHour')}
              />
            </Group>
          </SeccaoForm>
        )}

        <Textarea label="Notas" autosize minRows={2} {...campo('notes')} />

        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button
            disabled={!v.tag.trim() || !v.name.trim()}
            loading={gravar.isPending}
            onClick={() => gravar.mutate()}
          >
            Guardar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

interface Valores {
  tag: string;
  name: string;
  assetTypeId: string;
  locationId: string | null;
  status: string;
  manufacturer: string;
  model: string;
  modelYear: number | string;
  serialNumber: string;
  plate: string;
  responsibleLabel: string;
  speedLimitKph: number | string;
  tankCapacityLiters: number | string;
  acquisitionValue: number | string;
  downtimeCostPerHour: number | string;
  notes: string;
}

function valores(a: AssetView & { downtimeCostPerHour?: number | null; tankCapacityLiters?: number | null }): Valores {
  return {
    tag: a.tag,
    name: a.name,
    assetTypeId: a.assetTypeId ?? '',
    locationId: a.locationId ?? null,
    status: a.status,
    manufacturer: a.manufacturer ?? '',
    model: a.model ?? '',
    modelYear: a.modelYear ?? '',
    serialNumber: a.serialNumber ?? '',
    plate: a.plate ?? '',
    responsibleLabel: a.responsibleLabel ?? '',
    speedLimitKph: a.speedLimitKph ?? '',
    tankCapacityLiters: a.tankCapacityLiters ?? '',
    acquisitionValue: a.acquisitionValue ?? '',
    downtimeCostPerHour: a.downtimeCostPerHour ?? '',
    notes: a.notes ?? '',
  };
}
