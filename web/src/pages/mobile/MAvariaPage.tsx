import {
  FileButton,
  Loader,
  NumberInput,
  SegmentedControl,
  Stack,
  Switch,
  Text,
  TextInput,
  Textarea,
} from '@mantine/core';
import { IconCamera, IconMapPin, IconSend, IconX } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { enviarFormulario } from './envio';
import { Erro, EscolherViatura, Feito, mensagemDe, useHome, viaturaInicial } from './comum';
import { BotaoGrande, Cartao, Seccao } from './pecas';
import { AMBAR, CINZA, TITULO, VERDE, VERMELHO } from './tema';

interface Ordem {
  id: string;
  number: string;
  assetTag: string;
}

const TIPOS = [
  { value: 'AVARIA', label: 'Avaria' },
  { value: 'ACIDENTE', label: 'Acidente' },
  { value: 'PNEU', label: 'Pneu' },
  { value: 'COMBUSTIVEL', label: 'Combustível' },
];

/**
 * Comunicar o que aconteceu na estrada.
 *
 * <p>Três coisas fazem a diferença entre isto e um telefonema: as
 * <b>fotografias</b> tiradas ali, o <b>sítio exacto</b> apanhado pelo GPS, e o
 * facto de abrir uma ordem no sistema sozinho. Um acidente descrito por
 * palavras discute-se durante semanas; fotografado e localizado, resolve-se.
 */
export function MAvariaPage() {
  const { data: home } = useHome();
  const [params] = useSearchParams();
  const queryClient = useQueryClient();
  const [ativo, setAtivo] = useState<string | null>(params.get('ativo'));
  const [tipo, setTipo] = useState('AVARIA');
  const [titulo, setTitulo] = useState('');
  const [detalhe, setDetalhe] = useState('');
  const [contador, setContador] = useState<number | string>('');
  const [parada, setParada] = useState(false);
  const [fotos, setFotos] = useState<File[]>([]);
  const [previews, setPreviews] = useState<string[]>([]);
  const [local, setLocal] = useState<{ lat: number; lon: number } | null>(null);
  const [aLocalizar, setALocalizar] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    if (!ativo && home) setAtivo(viaturaInicial(home.assets));
  }, [home, ativo]);

  useEffect(() => {
    const urls = fotos.map((f) => URL.createObjectURL(f));
    setPreviews(urls);
    return () => urls.forEach((u) => URL.revokeObjectURL(u));
  }, [fotos]);

  // O local apanha-se ao abrir o ecrã: quem teve um acidente não vai lembrar-se
  // de carregar num botão para o fazer.
  useEffect(() => {
    if (!('geolocation' in navigator)) return;
    setALocalizar(true);
    navigator.geolocation.getCurrentPosition(
      (p) => {
        setLocal({ lat: p.coords.latitude, lon: p.coords.longitude });
        setALocalizar(false);
      },
      () => setALocalizar(false),
      { enableHighAccuracy: true, timeout: 15_000, maximumAge: 60_000 },
    );
  }, []);

  const enviar = useMutation({
    mutationFn: () => {
      const campos: { name: string; value: string | Blob; filename?: string }[] = [
        { name: 'assetId', value: ativo ?? '' },
        { name: 'title', value: titulo.trim() },
        { name: 'kind', value: tipo },
        { name: 'stopped', value: String(parada) },
      ];
      if (detalhe.trim()) campos.push({ name: 'description', value: detalhe.trim() });
      if (contador !== '' && contador !== null) campos.push({ name: 'meterValue', value: String(contador) });
      if (local) {
        campos.push({ name: 'latitude', value: String(local.lat) });
        campos.push({ name: 'longitude', value: String(local.lon) });
      }
      fotos.forEach((f, i) => campos.push({ name: 'photos', value: f, filename: f.name || `foto-${i + 1}.jpg` }));
      return enviarFormulario<Ordem>('/mobile/occurrences', campos,
        `Ocorrência · ${titulo.trim() || 'sem título'}`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mobile'] }),
    onError: (e) => setErro(mensagemDe(e)),
  });

  if (enviar.isSuccess && !enviar.data.enviado) {
    return (
      <Feito
        titulo="Guardado no telemóvel"
        texto="Não havia rede. Fica guardado com as fotografias e sobe sozinho assim que houver ligação — não precisa de voltar a escrever nada."
      />
    );
  }

  if (enviar.isSuccess && enviar.data.enviado) {
    return (
      <Feito
        titulo={`Comunicado — ordem ${enviar.data.dados.number}`}
        texto={
          parada
            ? 'O gestor foi avisado de que a viatura está parada. Fique em segurança e aguarde instruções.'
            : 'O gestor foi avisado e a ordem já está aberta com as suas fotografias.'
        }
      />
    );
  }

  const pronto = !!ativo && titulo.trim().length >= 3;

  return (
    <Stack gap="md">
      <div>
        <Text style={{ fontFamily: TITULO, fontSize: 24, fontWeight: 700 }}>Comunicar ocorrência</Text>
        <Text size="sm" style={{ color: CINZA }}>
          O que aconteceu, onde, e com fotografias. Chega ao gestor na hora.
        </Text>
      </div>

      <Erro mensagem={erro} />

      <Cartao>
        <Stack gap="sm">
          <SegmentedControl fullWidth size="sm" value={tipo} onChange={setTipo} data={TIPOS} />
          <EscolherViatura assets={home?.assets ?? []} value={ativo} onChange={setAtivo} />
          <TextInput
            label="O que se passa"
            placeholder={tipo === 'ACIDENTE' ? 'Ex.: colisão na traseira' : 'Ex.: fumo branco no escape'}
            value={titulo}
            onChange={(e) => setTitulo(e.currentTarget.value)}
            size="md"
            required
          />
          <Textarea
            label="Detalhes"
            placeholder="O que ouviu, o que viu, há quanto tempo. Quanto mais souberem, menos tempo a viatura fica parada."
            value={detalhe}
            onChange={(e) => setDetalhe(e.currentTarget.value)}
            autosize
            minRows={3}
            size="md"
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
          <Switch
            checked={parada}
            onChange={(e) => setParada(e.currentTarget.checked)}
            label="A viatura está parada e não pode andar"
            size="md"
            color="red"
          />
        </Stack>
      </Cartao>

      <div>
        <Seccao>Fotografias</Seccao>
        <Cartao>
          {previews.length > 0 && (
            <div style={{ display: 'flex', gap: 8, overflowX: 'auto', marginBottom: 10 }}>
              {previews.map((src, i) => (
                <div key={src} style={{ position: 'relative', flexShrink: 0 }}>
                  <img
                    src={src}
                    alt={`Fotografia ${i + 1}`}
                    style={{ width: 96, height: 96, objectFit: 'cover', borderRadius: 10 }}
                  />
                  <button
                    type="button"
                    onClick={() => setFotos((f) => f.filter((_, j) => j !== i))}
                    aria-label="Remover fotografia"
                    style={{
                      position: 'absolute',
                      top: 4,
                      right: 4,
                      border: 'none',
                      borderRadius: '50%',
                      width: 24,
                      height: 24,
                      background: '#000000AA',
                      color: '#fff',
                    }}
                  >
                    <IconX size={14} />
                  </button>
                </div>
              ))}
            </div>
          )}
          <FileButton
            onChange={(novas) => setFotos((f) => [...f, ...(novas ?? [])].slice(0, 6))}
            accept="image/*"
            multiple
          >
            {(props) => (
              <BotaoGrande {...props} icone={<IconCamera size={20} />} cor={AMBAR}>
                {fotos.length === 0 ? 'Tirar fotografia' : 'Mais uma fotografia'}
              </BotaoGrande>
            )}
          </FileButton>
          <Text size="xs" style={{ color: CINZA }} mt={8}>
            Até seis. Num acidente, fotografe a sua viatura, a outra, as matrículas e a estrada — é o
            que a seguradora vai pedir.
          </Text>
        </Cartao>
      </div>

      <Cartao>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          {aLocalizar ? (
            <Loader size="xs" color="yellow" />
          ) : (
            <IconMapPin size={20} style={{ color: local ? VERDE : CINZA }} />
          )}
          <Text size="sm" style={{ color: local ? undefined : CINZA }}>
            {aLocalizar
              ? 'A apanhar o local…'
              : local
                ? `Local apanhado: ${local.lat.toFixed(5)}, ${local.lon.toFixed(5)}`
                : 'Sem local — autorize a localização para o gestor saber onde está.'}
          </Text>
        </div>
      </Cartao>

      <BotaoGrande
        onClick={() => enviar.mutate()}
        disabled={!pronto || enviar.isPending}
        icone={<IconSend size={20} />}
        cor={parada ? VERMELHO : AMBAR}
      >
        {enviar.isPending ? 'A enviar…' : parada ? 'Comunicar — viatura parada' : 'Comunicar'}
      </BotaoGrande>
    </Stack>
  );
}
