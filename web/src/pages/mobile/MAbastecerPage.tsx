import { Button, NumberInput, Stack, Switch, Text, TextInput, Title } from '@mantine/core';
import { useMutation } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { enviarJson } from './envio';
import { useAuth } from '../../auth/AuthContext';
import { Erro, EscolherViatura, Feito, mensagemDe, useHome, viaturaInicial } from './comum';

interface Registo {
  id: string;
  liters: number;
  assetTag: string;
}

/**
 * Abastecer: litros, contador, posto. O preço só a quem pode ver valores —
 * quem não pode, regista os litros e a empresa completa depois.
 */
export function MAbastecerPage() {
  const { data: home } = useHome();
  const { has } = useAuth();
  const veValores = has('COSTS_VIEW');
  const [params] = useSearchParams();
  const [ativo, setAtivo] = useState<string | null>(params.get('ativo'));
  const [litros, setLitros] = useState<number | string>('');
  const [contador, setContador] = useState<number | string>('');
  const [preco, setPreco] = useState<number | string>('');
  const [total, setTotal] = useState<number | string>('');
  const [posto, setPosto] = useState('');
  const [cheio, setCheio] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!ativo && home) setAtivo(viaturaInicial(home.assets));
  }, [home, ativo]);

  const enviar = useMutation({
    mutationFn: () =>
      enviarJson<Registo>(
        `/assets/${ativo}/fuel`,
        {
          liters: Number(litros),
          meterValue: contador === '' ? undefined : Number(contador),
          pricePerLiter: preco === '' ? undefined : Number(preco),
          totalCost: total === '' ? undefined : Number(total),
          station: posto.trim() || undefined,
          fullTank: cheio,
        },
        `Abastecimento · ${litros} L`,
      ),
    onError: (e) => setErro(mensagemDe(e)),
  });

  if (enviar.isSuccess && !enviar.data.enviado) {
    return (
      <Feito
        titulo="Guardado no telemóvel"
        texto="Não havia rede no posto. O abastecimento sobe sozinho assim que houver ligação."
      />
    );
  }

  if (enviar.isSuccess && enviar.data.enviado) {
    return (
      <Feito
        titulo="Abastecimento registado"
        texto={`${enviar.data.dados.liters} L na viatura ${enviar.data.dados.assetTag}.`}
      />
    );
  }

  return (
    <Stack gap="md">
      <Title order={3}>Abastecer</Title>
      <EscolherViatura assets={home?.assets ?? []} value={ativo} onChange={setAtivo} />
      <NumberInput label="Litros" placeholder="Ex.: 180" value={litros} onChange={setLitros} min={0.1} decimalScale={2} size="md" inputMode="decimal" required />
      <NumberInput
        label="Contador no painel (km ou horas)"
        description="Sem o contador não se calcula o consumo."
        placeholder="Ex.: 125430"
        value={contador}
        onChange={setContador}
        min={0}
        thousandSeparator=" "
        size="md"
        inputMode="numeric"
      />
      {veValores && (
        <>
          <NumberInput label="Preço por litro (Kz)" value={preco} onChange={setPreco} min={0} decimalScale={2} size="md" inputMode="decimal" />
          <NumberInput label="Total pago (Kz)" description="Se vazio, calcula-se pelos litros." value={total} onChange={setTotal} min={0} decimalScale={2} size="md" inputMode="decimal" />
        </>
      )}
      <TextInput label="Posto (opcional)" placeholder="Ex.: Pumangol Viana" value={posto} onChange={(e) => setPosto(e.currentTarget.value)} size="md" maxLength={160} />
      <Switch label="Depósito cheio" description="Só entre enchimentos completos há consumo fiável." checked={cheio} onChange={(e) => setCheio(e.currentTarget.checked)} size="md" />
      {!veValores && (
        <Text size="xs" c="dimmed">
          O valor pago é registado pela empresa.
        </Text>
      )}
      <Erro mensagem={erro} />
      <Button
        size="lg"
        color="green"
        loading={enviar.isPending}
        disabled={!ativo || litros === '' || Number(litros) <= 0}
        onClick={() => {
          setErro(null);
          enviar.mutate();
        }}
      >
        Registar abastecimento
      </Button>
    </Stack>
  );
}
