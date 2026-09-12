/**
 * Segurança, ensaio final e próxima intervenção.
 *
 * <p>O bloqueio e etiquetagem (LOTO) existia antes só como texto livre. Numa
 * auditoria de segurança, «bloqueámos a máquina» escrito num campo de notas não
 * prova nada: é preciso saber quem bloqueou, com que etiqueta e a que horas —
 * e a hora tem de vir do servidor, não de quem a escreve.
 */
import {
  Alert,
  Badge,
  Button,
  Grid,
  Group,
  Select,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { notifications } from '@mantine/notifications';
import { IconLock, IconLockOpen } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Painel, SeccaoForm } from '../../components/erp';
import { fmtDateTime, fmtNumber } from '../../lib/format';

export interface ChaoOficinaCampos {
  lotoApplied?: boolean | null;
  lotoTagNumber?: string | null;
  lotoAppliedByLabel?: string | null;
  lotoAppliedAt?: string | null;
  lotoRemovedByLabel?: string | null;
  lotoRemovedAt?: string | null;
  workPermitNumber?: string | null;
  riskLevel?: string | null;
  riskLevelLabel?: string | null;
  ppeRequired?: string | null;
  safetyNotes?: string | null;
  requiresShutdown?: boolean | null;

  testPerformed?: boolean | null;
  testKind?: string | null;
  testKindLabel?: string | null;
  testDistanceKm?: number | null;
  testDurationMinutes?: number | null;
  testResult?: string | null;
  testResultLabel?: string | null;
  testNotes?: string | null;

  nextServiceMeter?: number | null;
  nextServiceHourMeter?: number | null;
  nextServiceAt?: string | null;
  nextServiceNote?: string | null;

  componentCode?: string | null;
  failureMode?: string | null;
  failureCause?: string | null;
}

export function SegurancaEnsaioPainel({
  id,
  w,
  editavel,
}: {
  id: string;
  w: ChaoOficinaCampos;
  editavel: boolean;
}) {
  return (
    <Stack gap="sm">
      <SegurancaPainel id={id} w={w} editavel={editavel} />
      <EnsaioPainel id={id} w={w} editavel={editavel} />
      <ProximaPainel id={id} w={w} editavel={editavel} />
    </Stack>
  );
}

// ==== Segurança ============================================================

function SegurancaPainel({
  id,
  w,
  editavel,
}: {
  id: string;
  w: ChaoOficinaCampos;
  editavel: boolean;
}) {
  const queryClient = useQueryClient();
  const [riskLevel, setRiskLevel] = useState<string | null>(null);
  const [lotoTagNumber, setLotoTagNumber] = useState('');
  const [workPermitNumber, setWorkPermitNumber] = useState('');
  const [ppeRequired, setPpeRequired] = useState('');
  const [safetyNotes, setSafetyNotes] = useState('');

  useEffect(() => {
    setRiskLevel(w.riskLevel ?? null);
    setLotoTagNumber(w.lotoTagNumber ?? '');
    setWorkPermitNumber(w.workPermitNumber ?? '');
    setPpeRequired(w.ppeRequired ?? '');
    setSafetyNotes(w.safetyNotes ?? '');
  }, [w.riskLevel, w.lotoTagNumber, w.workPermitNumber, w.ppeRequired, w.safetyNotes]);

  const gravar = useMutation({
    mutationFn: (bloquear: boolean | null) =>
      api(`/work-orders/${id}/safety`, {
        method: 'PUT',
        body: {
          lotoApplied: bloquear == null ? Boolean(w.lotoApplied) : bloquear,
          lotoTagNumber: lotoTagNumber.trim() || null,
          workPermitNumber: workPermitNumber.trim() || null,
          riskLevel,
          ppeRequired: ppeRequired.trim() || null,
          safetyNotes: safetyNotes.trim() || null,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      notifications.show({ title: 'Segurança atualizada', message: '', color: 'green' });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  const bloqueado = Boolean(w.lotoApplied);

  return (
    <Painel
      titulo="Segurança — bloqueio e etiquetagem"
      acoes={
        editavel ? (
          <>
            <Badge
              size="sm"
              variant="light"
              color={bloqueado ? 'red' : 'gray'}
              leftSection={bloqueado ? <IconLock size={11} /> : <IconLockOpen size={11} />}
            >
              {bloqueado ? 'Bloqueada' : 'Sem bloqueio'}
            </Badge>
            <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
              <Button
                size="xs"
                variant="default"
                color={bloqueado ? 'green' : 'red'}
                leftSection={bloqueado ? <IconLockOpen size={13} /> : <IconLock size={13} />}
                onClick={() => gravar.mutate(!bloqueado)}
                loading={gravar.isPending}
              >
                {bloqueado ? 'Remover bloqueio' : 'Aplicar bloqueio'}
              </Button>
              <Button size="xs" onClick={() => gravar.mutate(null)} loading={gravar.isPending}>
                Guardar
              </Button>
            </div>
          </>
        ) : undefined
      }
    >
      <SeccaoForm titulo="Antes de começar">
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <Select
              label="Nível de risco"
              placeholder="Classificar"
              clearable
              data={[
                { value: 'LOW', label: 'Baixo' },
                { value: 'MEDIUM', label: 'Médio' },
                { value: 'HIGH', label: 'Alto' },
                { value: 'CRITICAL', label: 'Crítico' },
              ]}
              value={riskLevel}
              onChange={setRiskLevel}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput
              label="Número da etiqueta"
              placeholder="ET-0042"
              description="Sem ela o bloqueio não se rastreia."
              value={lotoTagNumber}
              onChange={(e) => setLotoTagNumber(e.currentTarget.value)}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput
              label="Permissão de trabalho"
              placeholder="PT-2026-014"
              value={workPermitNumber}
              onChange={(e) => setWorkPermitNumber(e.currentTarget.value)}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={12}>
            <TextInput
              label="Equipamento de proteção obrigatório"
              placeholder="Luvas, óculos e botas de biqueira de aço"
              value={ppeRequired}
              onChange={(e) => setPpeRequired(e.currentTarget.value)}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={12}>
            <Textarea
              label="Instruções de segurança"
              placeholder="Calçar as rodas e bloquear a ignição antes de levantar."
              autosize
              minRows={2}
              value={safetyNotes}
              onChange={(e) => setSafetyNotes(e.currentTarget.value)}
              readOnly={!editavel}
            />
          </Grid.Col>
        </Grid>
      </SeccaoForm>

      {(w.lotoAppliedAt || w.lotoRemovedAt) && (
        <Alert color={bloqueado ? 'red' : 'gray'} variant="light" p="xs">
          <Text size="xs">
            {w.lotoAppliedAt && (
              <>
                Bloqueada por <b>{w.lotoAppliedByLabel ?? '—'}</b> em{' '}
                {fmtDateTime(w.lotoAppliedAt)}
                {w.lotoTagNumber ? ` · etiqueta ${w.lotoTagNumber}` : ''}
              </>
            )}
            {w.lotoRemovedAt && (
              <>
                <br />
                Desbloqueada por <b>{w.lotoRemovedByLabel ?? '—'}</b> em{' '}
                {fmtDateTime(w.lotoRemovedAt)}
              </>
            )}
          </Text>
        </Alert>
      )}

      {riskLevel === 'HIGH' || riskLevel === 'CRITICAL' ? (
        !bloqueado && (
          <Alert color="orange" variant="light" p="xs" mt="xs">
            <Text size="xs">
              Risco {riskLevel === 'CRITICAL' ? 'crítico' : 'alto'} sem bloqueio aplicado. Num
              trabalho destes, o bloqueio é o que impede a máquina de arrancar com alguém debaixo
              dela.
            </Text>
          </Alert>
        )
      ) : null}
    </Painel>
  );
}

// ==== Ensaio final =========================================================

function EnsaioPainel({
  id,
  w,
  editavel,
}: {
  id: string;
  w: ChaoOficinaCampos;
  editavel: boolean;
}) {
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);
  const [distanceKm, setDistanceKm] = useState('');
  const [durationMinutes, setDurationMinutes] = useState('');
  const [notes, setNotes] = useState('');

  useEffect(() => {
    setKind(w.testKind ?? 'ROAD');
    setResult(w.testResult ?? null);
    setDistanceKm(w.testDistanceKm != null ? String(w.testDistanceKm) : '');
    setDurationMinutes(w.testDurationMinutes != null ? String(w.testDurationMinutes) : '');
    setNotes(w.testNotes ?? '');
  }, [w.testKind, w.testResult, w.testDistanceKm, w.testDurationMinutes, w.testNotes]);

  const gravar = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/test`, {
        method: 'PUT',
        body: {
          kind,
          result,
          distanceKm: distanceKm.trim() ? Number(distanceKm) : null,
          durationMinutes: durationMinutes.trim() ? Number(durationMinutes) : null,
          notes: notes.trim() || null,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      notifications.show({ title: 'Ensaio registado', message: '', color: 'green' });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível registar', message: e.message, color: 'red' }),
  });

  return (
    <Painel
      titulo="Ensaio final"
      acoes={
        editavel ? (
          <>
            {w.testResult && (
              <Badge
                size="sm"
                variant="light"
                color={
                  w.testResult === 'PASSED' ? 'green' : w.testResult === 'PARTIAL' ? 'yellow' : 'red'
                }
              >
                {w.testResultLabel}
              </Badge>
            )}
            <div style={{ marginLeft: 'auto' }}>
              <Button size="xs" onClick={() => gravar.mutate()} loading={gravar.isPending}>
                Guardar ensaio
              </Button>
            </div>
          </>
        ) : undefined
      }
    >
      <Text size="xs" c="dimmed" mb="xs">
        Entregar sem provar que ficou bom é devolver o problema ao condutor.
      </Text>
      <Grid gutter="xs">
        <Grid.Col span={{ base: 12, sm: 4 }}>
          <Select
            label="Tipo de ensaio"
            data={[
              { value: 'ROAD', label: 'Ensaio de estrada' },
              { value: 'LOAD', label: 'Prova de carga' },
              { value: 'BENCH', label: 'Ensaio em banco' },
              { value: 'FUNCTIONAL', label: 'Ensaio funcional' },
            ]}
            value={kind}
            onChange={setKind}
            readOnly={!editavel}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, sm: 2 }}>
          <TextInput
            label="Distância (km)"
            inputMode="decimal"
            value={distanceKm}
            onChange={(e) => setDistanceKm(e.currentTarget.value.replace(',', '.'))}
            readOnly={!editavel}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 6, sm: 2 }}>
          <TextInput
            label="Duração (min)"
            inputMode="numeric"
            value={durationMinutes}
            onChange={(e) => setDurationMinutes(e.currentTarget.value.replace(/\D/g, ''))}
            readOnly={!editavel}
          />
        </Grid.Col>
        <Grid.Col span={{ base: 12, sm: 4 }}>
          <Select
            label="Resultado"
            placeholder="Por decidir"
            clearable
            data={[
              { value: 'PASSED', label: 'Aprovado' },
              { value: 'PARTIAL', label: 'Aprovado com reservas' },
              { value: 'FAILED', label: 'Reprovado' },
            ]}
            value={result}
            onChange={setResult}
            readOnly={!editavel}
          />
        </Grid.Col>
        <Grid.Col span={12}>
          <Textarea
            label={result === 'FAILED' ? 'O que falhou (obrigatório)' : 'Notas do ensaio'}
            placeholder="Sem vibração até aos 90 km/h."
            autosize
            minRows={2}
            value={notes}
            onChange={(e) => setNotes(e.currentTarget.value)}
            readOnly={!editavel}
          />
        </Grid.Col>
      </Grid>
    </Painel>
  );
}

// ==== Próxima intervenção ==================================================

function ProximaPainel({
  id,
  w,
  editavel,
}: {
  id: string;
  w: ChaoOficinaCampos;
  editavel: boolean;
}) {
  const queryClient = useQueryClient();
  const [meter, setMeter] = useState('');
  const [hourMeter, setHourMeter] = useState('');
  const [at, setAt] = useState<Date | null>(null);
  const [note, setNote] = useState('');
  const [componentCode, setComponentCode] = useState('');
  const [failureMode, setFailureMode] = useState('');
  const [failureCause, setFailureCause] = useState('');

  useEffect(() => {
    setMeter(w.nextServiceMeter != null ? String(w.nextServiceMeter) : '');
    setHourMeter(w.nextServiceHourMeter != null ? String(w.nextServiceHourMeter) : '');
    setAt(w.nextServiceAt ? new Date(w.nextServiceAt) : null);
    setNote(w.nextServiceNote ?? '');
    setComponentCode(w.componentCode ?? '');
    setFailureMode(w.failureMode ?? '');
    setFailureCause(w.failureCause ?? '');
  }, [
    w.nextServiceMeter,
    w.nextServiceHourMeter,
    w.nextServiceAt,
    w.nextServiceNote,
    w.componentCode,
    w.failureMode,
    w.failureCause,
  ]);

  const gravarProxima = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/next-service`, {
        method: 'PUT',
        body: {
          meter: meter.trim() ? Number(meter) : null,
          hourMeter: hourMeter.trim() ? Number(hourMeter) : null,
          at: at ? at.toISOString() : null,
          note: note.trim() || null,
        },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      notifications.show({ title: 'Próxima intervenção marcada', message: '', color: 'green' });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível marcar', message: e.message, color: 'red' }),
  });

  const gravarCodigo = useMutation({
    mutationFn: () =>
      api(`/work-orders/${id}/failure-coding`, {
        method: 'PUT',
        body: { componentCode, failureMode, failureCause },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['work-order', id] });
      notifications.show({ title: 'Avaria codificada', message: '', color: 'green' });
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível codificar', message: e.message, color: 'red' }),
  });

  return (
    <Painel titulo="Próxima intervenção e codificação da avaria">
      <SeccaoForm
        titulo="O que a oficina recomendou"
        descricao="O plano preventivo trata do ciclo; isto guarda o que ficou recomendado nesta intervenção concreta."
      >
        <Grid gutter="xs">
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Aos quilómetros"
              placeholder="190000"
              inputMode="decimal"
              value={meter}
              onChange={(e) => setMeter(e.currentTarget.value.replace(',', '.'))}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 6, sm: 3 }}>
            <TextInput
              label="Às horas"
              placeholder="1500"
              inputMode="decimal"
              value={hourMeter}
              onChange={(e) => setHourMeter(e.currentTarget.value.replace(',', '.'))}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <DateTimePicker
              label="Ou na data"
              clearable
              valueFormat="DD/MM/YYYY"
              value={at}
              onChange={(v) => setAt(v as Date | null)}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 2 }}>
            {editavel && (
              <Button
                fullWidth
                mt={22}
                size="sm"
                onClick={() => gravarProxima.mutate()}
                loading={gravarProxima.isPending}
              >
                Marcar
              </Button>
            )}
          </Grid.Col>
          <Grid.Col span={12}>
            <TextInput
              label="Recomendação"
              placeholder="Voltar a ver o diferencial."
              value={note}
              onChange={(e) => setNote(e.currentTarget.value)}
              readOnly={!editavel}
            />
          </Grid.Col>
        </Grid>

        {(w.nextServiceMeter != null || w.nextServiceAt) && (
          <Alert color="gray" variant="light" p="xs" mt="xs">
            <Text size="xs">
              Marcada para{' '}
              {w.nextServiceMeter != null && <b>{fmtNumber(w.nextServiceMeter, 0)} km</b>}
              {w.nextServiceMeter != null && w.nextServiceHourMeter != null && ' · '}
              {w.nextServiceHourMeter != null && <b>{fmtNumber(w.nextServiceHourMeter, 0)} h</b>}
              {w.nextServiceAt && ` · ${fmtDateTime(w.nextServiceAt)}`}
            </Text>
          </Alert>
        )}
      </SeccaoForm>

      <SeccaoForm
        titulo="Codificação da avaria (norma ISO 14224)"
        descricao="O sistema já diz onde. Isto diz o que falhou e porquê, em código — que é o que permite somar avarias iguais em toda a frota."
      >
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput
              label="Componente"
              placeholder="BOMBA_INJECAO"
              value={componentCode}
              onChange={(e) => setComponentCode(e.currentTarget.value)}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput
              label="Modo de falha"
              placeholder="FUGA"
              value={failureMode}
              onChange={(e) => setFailureMode(e.currentTarget.value)}
              readOnly={!editavel}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput
              label="Causa"
              placeholder="DESGASTE"
              value={failureCause}
              onChange={(e) => setFailureCause(e.currentTarget.value)}
              readOnly={!editavel}
            />
          </Grid.Col>
        </Grid>
        {editavel && (
          <Group justify="flex-end" mt="xs">
            <Button
              size="xs"
              variant="default"
              onClick={() => gravarCodigo.mutate()}
              loading={gravarCodigo.isPending}
            >
              Guardar codificação
            </Button>
          </Group>
        )}
      </SeccaoForm>
    </Painel>
  );
}
