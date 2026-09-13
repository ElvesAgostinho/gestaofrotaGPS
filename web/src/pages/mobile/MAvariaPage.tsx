import { Button, FileButton, Group, Image, NumberInput, Stack, Switch, Text, TextInput, Textarea, Title } from '@mantine/core';
import { IconCamera, IconX } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api } from '../../api/client';
import { Erro, EscolherViatura, Feito, mensagemDe, useHome, viaturaInicial } from './comum';

interface Ordem {
  id: string;
  number: string;
  assetTag: string;
}

/**
 * Comunicar uma avaria em quatro toques: viatura, o que se passa, fotografia,
 * enviar. Do outro lado abre-se uma ordem corretiva e quem gere é avisado —
 * mas isso o motorista não precisa de saber.
 */
export function MAvariaPage() {
  const { data: home } = useHome();
  const [params] = useSearchParams();
  const queryClient = useQueryClient();
  const [ativo, setAtivo] = useState<string | null>(params.get('ativo'));
  const [titulo, setTitulo] = useState('');
  const [detalhe, setDetalhe] = useState('');
  const [contador, setContador] = useState<number | string>('');
  const [parada, setParada] = useState(false);
  const [foto, setFoto] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!ativo && home) setAtivo(viaturaInicial(home.assets));
  }, [home, ativo]);

  useEffect(() => {
    if (!foto) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(foto);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [foto]);

  const enviar = useMutation({
    mutationFn: () => {
      const form = new FormData();
      form.append('assetId', ativo ?? '');
      form.append('title', titulo.trim());
      if (detalhe.trim()) form.append('description', detalhe.trim());
      if (contador !== '' && contador !== null) form.append('meterValue', String(contador));
      form.append('stopped', String(parada));
      if (foto) form.append('photo', foto);
      return api<Ordem>('/mobile/breakdowns', { method: 'POST', body: form });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mobile'] }),
    onError: (e) => setErro(mensagemDe(e)),
  });

  if (enviar.isSuccess) {
    return (
      <Feito
        titulo="Avaria comunicada"
        texto={`Ficou registada como ordem ${enviar.data.number} na viatura ${enviar.data.assetTag}. A oficina foi avisada.`}
      />
    );
  }

  return (
    <Stack gap="md">
      <Title order={3}>Comunicar avaria</Title>
      <EscolherViatura assets={home?.assets ?? []} value={ativo} onChange={setAtivo} />
      <TextInput
        label="O que se passa?"
        placeholder="Ex.: luz do motor acesa, pneu furado, não pega"
        value={titulo}
        onChange={(e) => setTitulo(e.currentTarget.value)}
        size="md"
        maxLength={200}
        required
      />
      <Textarea
        label="Mais detalhes (opcional)"
        placeholder="Quando começou, barulhos, onde está a viatura…"
        value={detalhe}
        onChange={(e) => setDetalhe(e.currentTarget.value)}
        autosize
        minRows={2}
        size="md"
      />
      <NumberInput
        label="Contador (km ou horas, se souber)"
        placeholder="Ex.: 125430"
        value={contador}
        onChange={setContador}
        min={0}
        thousandSeparator=" "
        size="md"
        inputMode="numeric"
      />
      <Switch
        label="A viatura está parada — não anda"
        description="Marca a avaria como urgente"
        checked={parada}
        onChange={(e) => setParada(e.currentTarget.checked)}
        size="md"
      />
      <div>
        <Text size="sm" fw={500} mb={4}>
          Fotografia (opcional)
        </Text>
        {preview ? (
          <Stack gap={6}>
            <Image src={preview} radius="md" mah={260} fit="contain" />
            <Button variant="default" leftSection={<IconX size={16} />} onClick={() => setFoto(null)}>
              Tirar outra
            </Button>
          </Stack>
        ) : (
          <FileButton onChange={setFoto} accept="image/*" capture="environment">
            {(props) => (
              <Button {...props} variant="light" size="md" leftSection={<IconCamera size={18} />} fullWidth>
                Tirar fotografia
              </Button>
            )}
          </FileButton>
        )}
      </div>
      <Erro mensagem={erro} />
      <Group grow>
        <Button
          size="lg"
          color="red"
          loading={enviar.isPending}
          disabled={!ativo || titulo.trim().length < 3}
          onClick={() => {
            setErro(null);
            enviar.mutate();
          }}
        >
          Enviar
        </Button>
      </Group>
    </Stack>
  );
}
