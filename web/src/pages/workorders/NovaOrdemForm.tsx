/**
 * Abertura de ordem de manutenção.
 *
 * <p>Este formulário não existia: não havia forma nenhuma de abrir uma ordem
 * pela aplicação. Tudo o que a ficha mostra — prazos, custo estimado, paragem,
 * segurança, garantia — só se conseguia preencher por chamadas à API.
 *
 * <p>Está organizado como a ficha de uma oficina a sério: identificação,
 * planeamento, custos, segurança. Só o ativo, o tipo e o título são
 * obrigatórios; o resto é o que distingue uma ficha completa de um bilhete.
 */
import {
  Alert,
  Button,
  Checkbox,
  Grid,
  Group,
  Modal,
  Select,
  Textarea,
  TextInput,
} from '@mantine/core';
import { DateTimePicker } from '@mantine/dates';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/client';
import { CampoConsulta } from '../../components/Consulta';
import { SeccaoForm } from '../../components/erp';

interface AssetOpcao {
  id: string;
  tag: string;
  name: string;
  assetTypeName?: string | null;
  locationName?: string | null;
  categoryLabel?: string | null;
}

interface Membro {
  userId: string;
  name: string;
}

interface Filial {
  id: string;
  name: string;
  kind: string;
}

interface Motorista {
  id: string;
  name: string;
}

const TIPOS = [
  { value: 'CORRECTIVE', label: 'Corretiva — avaria a reparar' },
  { value: 'PREVENTIVE', label: 'Preventiva — plano ou revisão' },
  { value: 'INSPECTION', label: 'Inspeção' },
  { value: 'PREDICTIVE', label: 'Preditiva — indício medido' },
  { value: 'EMERGENCY', label: 'Emergencial — viatura parada' },
  { value: 'OVERHAUL', label: 'Revisão geral' },
];

const PRIORIDADES = [
  { value: 'URGENT', label: 'Urgente — prazo 24 h' },
  { value: 'HIGH', label: 'Alta — prazo 3 dias' },
  { value: 'NORMAL', label: 'Normal — prazo 7 dias' },
  { value: 'LOW', label: 'Baixa — prazo 30 dias' },
];

/** Sistemas do ativo. Serve para ver avarias repetidas no mesmo sítio. */
const SISTEMAS = [
  'MOTOR',
  'TRAVAGEM',
  'TRANSMISSAO',
  'SUSPENSAO',
  'DIRECAO',
  'ELETRICO',
  'HIDRAULICO',
  'PNEUS',
  'CARROCARIA',
  'ARREFECIMENTO',
  'AR_CONDICIONADO',
  'OUTRO',
].map((s) => ({ value: s, label: s.replace(/_/g, ' ') }));

export function NovaOrdemForm({
  aberto,
  fechar,
  assetIdFixo,
  tituloInicial,
  descricaoInicial,
}: {
  aberto: boolean;
  fechar: () => void;
  /** Quando aberto a partir da lista, já se sabe qual é a viatura. */
  assetIdFixo?: string;
  /** Quando a ordem nasce de outra coisa — uma inspeção reprovada, por exemplo. */
  tituloInicial?: string;
  descricaoInicial?: string;
}) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [assetId, setAssetId] = useState<string | null>(assetIdFixo ?? null);
  const [type, setType] = useState<string | null>('CORRECTIVE');
  const [priority, setPriority] = useState<string | null>('NORMAL');
  const [title, setTitle] = useState(tituloInicial ?? '');
  const [description, setDescription] = useState(descricaoInicial ?? '');
  const [systemCode, setSystemCode] = useState<string | null>(null);
  const [assignedToUserId, setAssignedToUserId] = useState<string | null>(null);
  const [branchId, setBranchId] = useState<string | null>(null);
  const [driverId, setDriverId] = useState<string | null>(null);
  const [scheduledFor, setScheduledFor] = useState<Date | null>(null);
  const [dueAt, setDueAt] = useState<Date | null>(null);
  const [estimatedHours, setEstimatedHours] = useState('');
  const [estimatedCost, setEstimatedCost] = useState('');
  const [requiresShutdown, setRequiresShutdown] = useState(false);
  const [safetyNotes, setSafetyNotes] = useState('');
  const [underWarranty, setUnderWarranty] = useState(false);
  const [warrantyReference, setWarrantyReference] = useState('');

  // A viatura vem de fora quando a ordem é aberta a partir da lista. Sem isto,
  // abrir a ordem de um camião e depois de outro mostrava sempre o primeiro.
  const [ultimoFixo, setUltimoFixo] = useState<string | undefined>(assetIdFixo);
  if (assetIdFixo !== ultimoFixo) {
    setUltimoFixo(assetIdFixo);
    setAssetId(assetIdFixo ?? null);
  }

  // O mesmo para o texto que vem de fora: a ordem que nasce de uma inspeção
  // reprovada já traz o título e os pontos que falharam.
  const [ultimoTexto, setUltimoTexto] = useState<string | undefined>(tituloInicial);
  if (tituloInicial !== ultimoTexto) {
    setUltimoTexto(tituloInicial);
    if (tituloInicial) setTitle(tituloInicial);
    if (descricaoInicial) setDescription(descricaoInicial);
  }

  const { data: ativos } = useQuery({
    queryKey: ['assets', 'opcoes'],
    queryFn: () => api<{ content: AssetOpcao[] }>('/assets?size=300'),
    enabled: aberto && !assetIdFixo,
  });
  const { data: equipa } = useQuery({
    queryKey: ['team', 'opcoes'],
    queryFn: () => api<Membro[]>('/team/members'),
    enabled: aberto,
  });
  const { data: locais } = useQuery({
    queryKey: ['locations', 'opcoes'],
    queryFn: () => api<Filial[]>('/locations'),
    enabled: aberto,
  });
  const { data: motoristas } = useQuery({
    queryKey: ['drivers', 'opcoes'],
    queryFn: () => api<{ content: Motorista[] }>('/drivers?size=200'),
    enabled: aberto,
  });

  const criar = useMutation({
    mutationFn: () =>
      api<{ id: string; number: string }>('/work-orders', {
        method: 'POST',
        body: {
          assetId,
          type,
          priority,
          title: title.trim(),
          description: description.trim() || null,
          systemCode,
          assignedToUserId,
          branchId,
          driverId,
          scheduledFor: scheduledFor ? scheduledFor.toISOString() : null,
          dueAt: dueAt ? dueAt.toISOString() : null,
          // Vazio é vazio, não zero: zero seria uma estimativa de que não custa nada.
          estimatedHours: estimatedHours.trim() ? Number(estimatedHours) : null,
          estimatedCost: estimatedCost.trim() ? Number(estimatedCost) : null,
          requiresShutdown,
          safetyNotes: safetyNotes.trim() || null,
          underWarranty,
          warrantyReference: warrantyReference.trim() || null,
        },
      }),
    onSuccess: (nova) => {
      notifications.show({
        title: `Ordem ${nova.number} aberta`,
        message: 'A abrir a ficha para continuar o preenchimento.',
        color: 'green',
      });
      queryClient.invalidateQueries({ queryKey: ['work-orders'] });
      limpar();
      fechar();
      navigate(`/ordens/${nova.id}`);
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível abrir a ordem', message: e.message, color: 'red' }),
  });

  function limpar() {
    if (!assetIdFixo) setAssetId(null);
    setTitle('');
    setDescription('');
    setSystemCode(null);
    setAssignedToUserId(null);
    setBranchId(null);
    setDriverId(null);
    setScheduledFor(null);
    setDueAt(null);
    setEstimatedHours('');
    setEstimatedCost('');
    setRequiresShutdown(false);
    setSafetyNotes('');
    setUnderWarranty(false);
    setWarrantyReference('');
  }

  const faltaAlgo = !assetId || !type || title.trim().length === 0;
  const filiais = (locais ?? []).filter((l) => l.kind === 'BRANCH');

  return (
    <Modal
      opened={aberto}
      onClose={fechar}
      title="Abrir ordem de manutenção"
      size="xl"
      closeOnClickOutside={false}
    >
      <SeccaoForm titulo="Identificação" descricao="O que avariou e em que viatura.">
        <Grid gutter="xs">
          {!assetIdFixo && (
            <Grid.Col span={{ base: 12, sm: 6 }}>
              <CampoConsulta
                label="Viatura ou equipamento"
                placeholder="Escolha o ativo"
                required
                linhas={ativos?.content ?? []}
                valor={assetId}
                aoMudar={setAssetId}
                chave={(a) => a.id}
                rotulo={(a) => `${a.tag} — ${a.name}`}
                camposProcura={(a) => [a.tag, a.name, a.assetTypeName, a.locationName, a.categoryLabel]}
                tituloJanela="Escolher a viatura ou equipamento"
                idGrelha="consulta-ativos"
                colunas={[
                  { id: 'tag', titulo: 'Etiqueta', largura: 110, fixa: true, valor: (a) => a.tag, render: (a) => <b>{a.tag}</b> },
                  { id: 'nome', titulo: 'Nome', valor: (a) => a.name },
                  { id: 'tipo', titulo: 'Tipo', largura: 160, valor: (a) => a.assetTypeName ?? null },
                  { id: 'familia', titulo: 'Família', largura: 130, valor: (a) => a.categoryLabel ?? null },
                  { id: 'local', titulo: 'Local', largura: 170, valor: (a) => a.locationName ?? null },
                ]}
              />
            </Grid.Col>
          )}
          <Grid.Col span={{ base: 12, sm: assetIdFixo ? 12 : 6 }}>
            <Select
              label="Tipo de intervenção"
              required
              data={TIPOS}
              value={type}
              onChange={setType}
              allowDeselect={false}
            />
          </Grid.Col>
          <Grid.Col span={12}>
            <TextInput
              label="Título"
              required
              placeholder="Vibração forte ao travar acima dos 60 km/h"
              value={title}
              onChange={(e) => setTitle(e.currentTarget.value)}
              maxLength={200}
            />
          </Grid.Col>
          <Grid.Col span={12}>
            <Textarea
              label="Descrição do problema"
              placeholder="O que o condutor relatou, em que condições acontece, desde quando."
              autosize
              minRows={2}
              maxRows={6}
              value={description}
              onChange={(e) => setDescription(e.currentTarget.value)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <Select
              label="Sistema"
              placeholder="Onde é a avaria"
              description="Permite ver avarias repetidas no mesmo sítio."
              searchable
              clearable
              data={SISTEMAS}
              value={systemCode}
              onChange={setSystemCode}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <Select
              label="Filial"
              placeholder="Onde fica a viatura"
              clearable
              data={filiais.map((f) => ({ value: f.id, label: f.name }))}
              value={branchId}
              onChange={setBranchId}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <Select
              label="Motorista"
              placeholder="Quem conduzia"
              clearable
              searchable
              data={(motoristas?.content ?? []).map((m) => ({ value: m.id, label: m.name }))}
              value={driverId}
              onChange={setDriverId}
            />
          </Grid.Col>
        </Grid>
      </SeccaoForm>

      <SeccaoForm
        titulo="Planeamento"
        descricao="Sem prazo, toda a ordem está a horas e o cumprimento é sempre 100%."
      >
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <Select
              label="Prioridade"
              data={PRIORIDADES}
              value={priority}
              onChange={setPriority}
              allowDeselect={false}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <DateTimePicker
              label="Marcada para"
              placeholder="Quando entra na oficina"
              clearable
              valueFormat="DD/MM/YYYY HH:mm"
              value={scheduledFor}
              onChange={(v) => setScheduledFor(v as Date | null)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <DateTimePicker
              label="Prazo"
              placeholder="Vazio usa o prazo da prioridade"
              clearable
              valueFormat="DD/MM/YYYY HH:mm"
              value={dueAt}
              onChange={(v) => setDueAt(v as Date | null)}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <Select
              label="Responsável"
              placeholder="Quem trata"
              clearable
              searchable
              data={(equipa ?? []).map((m) => ({ value: m.userId, label: m.name }))}
              value={assignedToUserId}
              onChange={setAssignedToUserId}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput
              label="Horas estimadas"
              placeholder="6"
              inputMode="decimal"
              value={estimatedHours}
              onChange={(e) => setEstimatedHours(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 4 }}>
            <TextInput
              label="Custo estimado (Kz)"
              placeholder="250000"
              inputMode="decimal"
              value={estimatedCost}
              onChange={(e) => setEstimatedCost(e.currentTarget.value.replace(',', '.'))}
            />
          </Grid.Col>
        </Grid>
      </SeccaoForm>

      <SeccaoForm
        titulo="Segurança e garantia"
        descricao="Uma viatura imobilizada custa dinheiro por hora; a garantia pode recuperá-lo."
      >
        <Grid gutter="xs">
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <Checkbox
              label="Obriga a imobilizar a viatura"
              description="Conta as horas de paragem e o que elas custam."
              checked={requiresShutdown}
              onChange={(e) => setRequiresShutdown(e.currentTarget.checked)}
              mt={6}
            />
          </Grid.Col>
          <Grid.Col span={{ base: 12, sm: 6 }}>
            <Checkbox
              label="Coberta por garantia"
              checked={underWarranty}
              onChange={(e) => setUnderWarranty(e.currentTarget.checked)}
              mt={6}
            />
          </Grid.Col>
          {underWarranty && (
            <Grid.Col span={12}>
              <TextInput
                label="Referência da garantia"
                placeholder="Contrato, fatura ou número de processo"
                value={warrantyReference}
                onChange={(e) => setWarrantyReference(e.currentTarget.value)}
              />
            </Grid.Col>
          )}
          {requiresShutdown && (
            <Grid.Col span={12}>
              <Textarea
                label="Instruções de segurança"
                placeholder="Calçar as rodas e bloquear a ignição antes de levantar."
                autosize
                minRows={2}
                value={safetyNotes}
                onChange={(e) => setSafetyNotes(e.currentTarget.value)}
              />
            </Grid.Col>
          )}
        </Grid>
      </SeccaoForm>

      {faltaAlgo && (
        <Alert color="gray" variant="light" mb="sm" p="xs">
          Falta a viatura, o tipo ou o título. O resto pode preencher depois, na ficha.
        </Alert>
      )}

      <Group justify="flex-end" gap="xs">
        <Button variant="default" onClick={fechar}>
          Cancelar
        </Button>
        <Button onClick={() => criar.mutate()} loading={criar.isPending} disabled={faltaAlgo}>
          Abrir ordem
        </Button>
      </Group>
    </Modal>
  );
}
