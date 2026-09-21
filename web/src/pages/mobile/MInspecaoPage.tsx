import { Badge, Button, Card, Group, NumberInput, SegmentedControl, Select, Stack, Text, TextInput, Textarea, Title } from '@mantine/core';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { enviarJson } from './envio';
import { Erro, EscolherViatura, Feito, mensagemDe, useHome, viaturaInicial } from './comum';

interface Template {
  id: string;
  name: string;
  assetTypeId?: string | null;
  active: boolean;
  items: { id: string; text: string; verification: string; critical: boolean; sortOrder: number }[];
}

interface Item {
  text: string;
  critical: boolean;
  result: 'OK' | 'NOT_OK' | 'NA';
  note: string;
}

/**
 * A inspeção diária de partida quando a empresa ainda não desenhou a sua:
 * o que qualquer motorista de pesados verifica antes de sair. Os pontos
 * críticos reprovados avisam quem gere.
 */
const INSPECAO_BASE = {
  nome: 'Inspeção diária',
  itens: [
    ['Pneus (pressão, desgaste, cortes)', true],
    ['Travões (pedal, travão de mão)', true],
    ['Direção (folgas, ruídos)', true],
    ['Luzes, piscas e stop', true],
    ['Fugas debaixo da viatura (óleo, água, gasóleo)', true],
    ['Cintos de segurança', true],
    ['Nível de óleo do motor', false],
    ['Água do radiador', false],
    ['Combustível suficiente', false],
    ['Espelhos, vidros e limpa-vidros', false],
    ['Buzina', false],
    ['Extintor e triângulo', false],
    ['Documentos a bordo', false],
    ['Limpeza e estado geral', false],
  ] as [string, boolean][],
};

interface Execucao {
  id: string;
  outcome: string;
  itemsNotOk: number;
}

export function MInspecaoPage() {
  const { data: home } = useHome();
  const [params] = useSearchParams();
  const [ativo, setAtivo] = useState<string | null>(params.get('ativo'));
  const [modelo, setModelo] = useState<string>('base');
  const [itens, setItens] = useState<Item[]>([]);
  const [contador, setContador] = useState<number | string>('');
  const [notas, setNotas] = useState('');
  const [erro, setErro] = useState<string | null>(null);

  const { data: modelos } = useQuery({
    queryKey: ['checklist-templates'],
    queryFn: () => api<Template[]>('/checklist-templates'),
  });
  const ativos = useMemo(() => (modelos ?? []).filter((m) => m.active && m.items.length > 0), [modelos]);

  useEffect(() => {
    if (!ativo && home) setAtivo(viaturaInicial(home.assets));
  }, [home, ativo]);

  // Ao mudar de modelo, os pontos ficam por responder: ninguém responde por defeito.
  useEffect(() => {
    const t = ativos.find((m) => m.id === modelo);
    const lista: Item[] = t
      ? t.items.slice().sort((a, b) => a.sortOrder - b.sortOrder).map((i) => ({ text: i.text, critical: i.critical, result: 'OK', note: '' }))
      : INSPECAO_BASE.itens.map(([text, critical]) => ({ text, critical, result: 'OK', note: '' }));
    setItens(lista);
  }, [modelo, ativos]);

  const nomeModelo = ativos.find((m) => m.id === modelo)?.name ?? INSPECAO_BASE.nome;
  const reprovados = itens.filter((i) => i.result === 'NOT_OK');

  const enviar = useMutation({
    mutationFn: () =>
      enviarJson<Execucao>(
        `/assets/${ativo}/checklist-executions`,
        {
          templateId: modelo !== 'base' ? modelo : undefined,
          templateName: nomeModelo,
          meterValue: contador === '' ? undefined : Number(contador),
          notes: notas.trim() || undefined,
          items: itens.map((i) => ({ text: i.text, critical: i.critical, result: i.result, note: i.note.trim() || undefined })),
        },
        `Inspeção diária${reprovados.length > 0 ? ` · ${reprovados.length} reprovado(s)` : ''}`,
      ),
    onError: (e) => setErro(mensagemDe(e)),
  });

  if (enviar.isSuccess && !enviar.data.enviado) {
    return (
      <Feito
        titulo="Guardada no telemóvel"
        texto={
          reprovados.length > 0
            ? 'Não havia rede. A inspeção sobe assim que houver ligação — mas os pontos críticos reprovados impedem a viatura de sair já agora.'
            : 'Não havia rede. A inspeção sobe sozinha assim que houver ligação. Boa viagem.'
        }
      />
    );
  }

  if (enviar.isSuccess && enviar.data.enviado) {
    const r = enviar.data.dados;
    return (
      <Feito
        titulo={r.outcome === 'OK' ? 'Inspeção sem problemas' : `Inspeção registada com ${r.itemsNotOk} ponto(s) reprovado(s)`}
        texto={r.outcome === 'OK' ? 'Boa viagem.' : 'Quem gere a frota foi avisado dos pontos críticos.'}
      />
    );
  }

  return (
    <Stack gap="md">
      <Title order={3}>Inspeção diária</Title>
      <EscolherViatura assets={home?.assets ?? []} value={ativo} onChange={setAtivo} />
      {ativos.length > 0 && (
        <Select
          label="Lista de verificação"
          data={[{ value: 'base', label: INSPECAO_BASE.nome + ' (padrão)' }, ...ativos.map((m) => ({ value: m.id, label: m.name }))]}
          value={modelo}
          onChange={(v) => setModelo(v ?? 'base')}
          size="md"
          allowDeselect={false}
        />
      )}
      <NumberInput
        label="Contador no painel (km ou horas)"
        placeholder="Ex.: 125430"
        value={contador}
        onChange={setContador}
        min={0}
        thousandSeparator=" "
        size="md"
        inputMode="numeric"
      />
      <Stack gap={8}>
        {itens.map((item, idx) => (
          <Card key={item.text} padding="sm" radius="md" withBorder>
            <Stack gap={6}>
              <Group justify="space-between" wrap="nowrap" align="flex-start">
                <Text fw={600} size="sm" lh={1.25}>
                  {item.text}
                </Text>
                {item.critical && (
                  <Badge size="xs" color="red" variant="light" style={{ flexShrink: 0 }}>
                    crítico
                  </Badge>
                )}
              </Group>
              <SegmentedControl
                fullWidth
                size="sm"
                value={item.result}
                onChange={(v) => setItens((l) => l.map((x, i) => (i === idx ? { ...x, result: v as Item['result'] } : x)))}
                data={[
                  { value: 'OK', label: 'OK' },
                  { value: 'NOT_OK', label: 'Não OK' },
                  { value: 'NA', label: 'N/A' },
                ]}
                color={item.result === 'NOT_OK' ? 'red' : item.result === 'OK' ? 'green' : 'gray'}
              />
              {item.result === 'NOT_OK' && (
                <TextInput
                  placeholder="O que está mal?"
                  value={item.note}
                  onChange={(e) => {
                    const v = e.currentTarget.value;
                    setItens((l) => l.map((x, i) => (i === idx ? { ...x, note: v } : x)));
                  }}
                  maxLength={300}
                />
              )}
            </Stack>
          </Card>
        ))}
      </Stack>
      <Textarea label="Observações (opcional)" value={notas} onChange={(e) => setNotas(e.currentTarget.value)} autosize minRows={2} />
      <Erro mensagem={erro} />
      <Button
        size="lg"
        color={reprovados.length ? 'red' : 'green'}
        loading={enviar.isPending}
        disabled={!ativo || itens.length === 0}
        onClick={() => {
          setErro(null);
          enviar.mutate();
        }}
      >
        {reprovados.length ? `Registar com ${reprovados.length} reprovado(s)` : 'Registar inspeção — tudo OK'}
      </Button>
    </Stack>
  );
}
