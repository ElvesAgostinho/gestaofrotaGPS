import { NumberInput, SegmentedControl, Stack, Text, Textarea } from '@mantine/core';
import { IconAlertTriangle, IconDroplet, IconSend } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Erro, EscolherViatura, Feito, mensagemDe, useHome, viaturaInicial } from './comum';
import { enviarJson } from './envio';
import { BotaoGrande, Cartao, Seccao } from './pecas';
import { AMBAR, CINZA, LARANJA, TITULO, VERMELHO } from './tema';

interface Resultado {
  record: { liters: number; kindLabel: string; assetTag: string };
  warning?: string | null;
}

const FLUIDOS = [
  { value: 'COOLANT', label: 'Água' },
  { value: 'ENGINE_OIL', label: 'Óleo motor' },
  { value: 'HYDRAULIC', label: 'Hidráulico' },
  { value: 'BRAKE', label: 'Travões' },
];

/**
 * Atestar água, óleo ou líquido de travões.
 *
 * <p>É o registo mais banal da aplicação e um dos mais úteis. Atestar água é
 * um gesto tão comum que ninguém o conta — e é por ninguém o contar que uma
 * fuga pequena vive meses, até acabar em motor gripado ou em incêndio a meio
 * de uma viagem. Aqui conta-se: ao terceiro atesto em trinta dias, o gestor é
 * avisado com o número em cima da mesa.
 */
export function MAtestarPage() {
  const { data: home } = useHome();
  const [params] = useSearchParams();
  const queryClient = useQueryClient();
  const [ativo, setAtivo] = useState<string | null>(params.get('ativo'));
  const [fluido, setFluido] = useState('COOLANT');
  const [litros, setLitros] = useState<number | string>('');
  const [contador, setContador] = useState<number | string>('');
  const [nota, setNota] = useState('');
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!ativo && home) setAtivo(viaturaInicial(home.assets));
  }, [home, ativo]);

  const enviar = useMutation({
    mutationFn: () =>
      enviarJson<Resultado>(
        `/assets/${ativo}/fluid-topups`,
        {
          kind: fluido,
          liters: Number(litros),
          meterValue: contador === '' ? undefined : Number(contador),
          note: nota.trim() || undefined,
        },
        `Atesto · ${litros} L`,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mobile'] }),
    onError: (e) => setErro(mensagemDe(e)),
  });

  if (enviar.isSuccess && !enviar.data.enviado) {
    return (
      <Feito
        titulo="Guardado no telemóvel"
        texto="Não havia rede. O atesto sobe sozinho assim que houver ligação."
      />
    );
  }

  if (enviar.isSuccess && enviar.data.enviado) {
    const r = enviar.data.dados;
    return (
      <Stack gap="md">
        <Feito
          titulo="Atesto registado"
          texto={`${r.record.liters} L de ${r.record.kindLabel.toLowerCase()} em ${r.record.assetTag}.`}
        />
        {r.warning && (
          <Cartao destaque>
            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
              <IconAlertTriangle size={22} style={{ color: VERMELHO, flexShrink: 0 }} />
              <div>
                <Text fw={700} size="sm" mb={4}>
                  Atenção — o gestor foi avisado
                </Text>
                <Text size="sm">{r.warning}</Text>
              </div>
            </div>
          </Cartao>
        )}
      </Stack>
    );
  }

  const pronto = !!ativo && litros !== '' && Number(litros) > 0;
  const travoes = fluido === 'BRAKE';

  return (
    <Stack gap="md">
      <div>
        <Text style={{ fontFamily: TITULO, fontSize: 24, fontWeight: 700 }}>Atestar fluido</Text>
        <Text size="sm" style={{ color: CINZA }}>
          Água, óleo ou travões. Registe sempre — é assim que se descobre uma fuga a tempo.
        </Text>
      </div>

      <Erro mensagem={erro} />

      <Cartao>
        <Stack gap="sm">
          <SegmentedControl fullWidth size="sm" value={fluido} onChange={setFluido} data={FLUIDOS} />
          <EscolherViatura assets={home?.assets ?? []} value={ativo} onChange={setAtivo} />
          <NumberInput
            label="Litros atestados"
            placeholder="Ex.: 2"
            value={litros}
            onChange={setLitros}
            min={0.1}
            step={0.5}
            decimalScale={2}
            size="md"
            inputMode="decimal"
            required
          />
          <NumberInput
            label="Contador no painel"
            placeholder="Ex.: 125430"
            value={contador}
            onChange={setContador}
            min={0}
            thousandSeparator=" "
            size="md"
            inputMode="numeric"
          />
          <Textarea
            label="Observação"
            placeholder="Onde atestou, se viu pingar, se o motor estava quente…"
            value={nota}
            onChange={(e) => setNota(e.currentTarget.value)}
            autosize
            minRows={2}
            size="md"
          />
        </Stack>
      </Cartao>

      {travoes && (
        <Cartao destaque>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <IconAlertTriangle size={20} style={{ color: VERMELHO, flexShrink: 0 }} />
            <Text size="sm">
              O líquido dos travões não desaparece sozinho. Se foi preciso atestar, há fuga ou as
              pastilhas estão no fim — <b>a viatura não deve sair antes de ser vista</b>.
            </Text>
          </div>
        </Cartao>
      )}

      <div>
        <Seccao>Porque é que isto importa</Seccao>
        <Cartao>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <IconDroplet size={20} style={{ color: LARANJA, flexShrink: 0 }} />
            <Text size="sm" style={{ color: CINZA }}>
              Num circuito fechado, nada evapora. Uma viatura que leva água todas as semanas está a
              perdê-la — e o fim disso é o motor a gripar ou a pegar fogo na estrada. Cada atesto
              registado é uma peça desse sinal.
            </Text>
          </div>
        </Cartao>
      </div>

      <BotaoGrande
        onClick={() => enviar.mutate()}
        disabled={!pronto || enviar.isPending}
        icone={<IconSend size={20} />}
        cor={travoes ? VERMELHO : AMBAR}
      >
        {enviar.isPending ? 'A registar…' : 'Registar atesto'}
      </BotaoGrande>
    </Stack>
  );
}
