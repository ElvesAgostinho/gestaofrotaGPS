/**
 * Fotografias do ativo, organizadas por parte.
 *
 * <p>Uma pasta de fotografias soltas não diz nada a quem não esteve lá. Por
 * parte, dizem: o gestor vê o estado do motor, dos pneus e da cabine sem sair
 * do escritório — e quando a máquina volta da oficina, há com o que comparar.
 *
 * <p>As partes que ainda não têm fotografia aparecem na mesma, a cinzento. É
 * deliberado: mostrar só o que existe esconde o que falta, e o que falta é
 * precisamente o que o gestor precisa de mandar fotografar.
 */
import { Alert, Badge, Button, Group, Modal, Select, Text, TextInput } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconCamera, IconPhotoPlus, IconTrash } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, checkUploadSize } from '../../api/client';
import { Painel } from '../../components/erp';
import { fmtDateTime } from '../../lib/format';

export interface Foto {
  id: string;
  url: string;
  kind: string;
  kindLabel?: string | null;
  caption?: string | null;
  primary: boolean;
  createdAt: string;
}

/**
 * As partes que interessam, pela ordem por que se inspeciona uma máquina.
 *
 * <p>A lista é a mesma para toda a frota de propósito: um gestor que olha para
 * dez máquinas quer as mesmas vistas em todas, para as poder comparar.
 */
const PARTES = [
  { value: 'GENERAL', label: 'Vista geral' },
  { value: 'ENGINE', label: 'Motor' },
  { value: 'CABIN', label: 'Cabine' },
  { value: 'TYRES', label: 'Pneus e rodado' },
  { value: 'HYDRAULIC', label: 'Sistema hidráulico' },
  { value: 'ELECTRICAL', label: 'Sistema elétrico' },
  { value: 'CHASSIS', label: 'Chassi e estrutura' },
  { value: 'ATTACHMENT', label: 'Implemento ou concha' },
  { value: 'PLATE', label: 'Matrícula' },
  { value: 'METER', label: 'Contador' },
  { value: 'DAMAGE', label: 'Dano' },
  { value: 'DOCUMENT', label: 'Documento' },
];

/** As que valem sempre a pena ter, mesmo numa máquina simples. */
const ESSENCIAIS = ['GENERAL', 'ENGINE', 'CABIN', 'TYRES', 'PLATE', 'METER'];

export function FotografiasPorParte({
  assetId,
  fotos,
  editavel,
}: {
  assetId: string;
  fotos: Foto[];
  editavel: boolean;
}) {
  const queryClient = useQueryClient();
  const [parteAberta, setParteAberta] = useState<string | null>(null);
  const [ampliada, setAmpliada] = useState<Foto | null>(null);

  const remover = useMutation({
    mutationFn: (fid: string) => api(`/assets/${assetId}/photos/${fid}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['asset', assetId] }),
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível remover', message: e.message, color: 'red' }),
  });

  const porParte = new Map<string, Foto[]>();
  for (const f of fotos) {
    if (!porParte.has(f.kind)) porParte.set(f.kind, []);
    porParte.get(f.kind)!.push(f);
  }

  // Mostram-se as partes que têm fotografia, mais as essenciais que faltam.
  const visiveis = PARTES.filter(
    (p) => porParte.has(p.value) || ESSENCIAIS.includes(p.value),
  );
  const emFalta = ESSENCIAIS.filter((k) => !porParte.has(k));

  return (
    <>
      <UploadModal
        assetId={assetId}
        parte={parteAberta}
        fechar={() => setParteAberta(null)}
      />

      <Modal
        opened={ampliada != null}
        onClose={() => setAmpliada(null)}
        size="xl"
        title={ampliada ? (ampliada.kindLabel ?? ampliada.kind) : ''}
      >
        {ampliada && (
          <>
            <img
              src={ampliada.url}
              alt={ampliada.caption ?? ''}
              style={{ width: '100%', display: 'block', background: '#141416' }}
            />
            <Group justify="space-between" mt="xs">
              <div>
                {ampliada.caption && <Text size="sm">{ampliada.caption}</Text>}
                <Text size="xs" c="dimmed">
                  {fmtDateTime(ampliada.createdAt)}
                </Text>
              </div>
              {editavel && (
                <Button
                  size="xs"
                  variant="subtle"
                  color="red"
                  leftSection={<IconTrash size={13} />}
                  onClick={() => {
                    remover.mutate(ampliada.id);
                    setAmpliada(null);
                  }}
                >
                  Remover
                </Button>
              )}
            </Group>
          </>
        )}
      </Modal>

      <Painel
        titulo="Fotografias por parte"
        acoes={
          editavel ? (
            <Text size="xs" c="dimmed" style={{ paddingLeft: 4 }}>
              Carregue em cada parte para acrescentar uma fotografia
            </Text>
          ) : undefined
        }
        rodape={
          <Text size="xs" c="dimmed">
            {fotos.length} fotografia(s) · {porParte.size} de {PARTES.length} partes cobertas
          </Text>
        }
      >
        {emFalta.length > 0 && (
          <Alert color="gray" variant="light" p="xs" mb="sm" icon={<IconCamera size={15} />}>
            <Text size="sm">
              Faltam vistas essenciais: <b>{emFalta.map((k) => rotulo(k)).join(', ')}</b>. São as
              que dão ao gestor a noção do estado da máquina sem ter de ir ao parque.
            </Text>
          </Alert>
        )}

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(190px, 1fr))',
            gap: 10,
          }}
        >
          {visiveis.map((parte) => {
            const desta = porParte.get(parte.value) ?? [];
            const capa = desta[0];
            return (
              <div key={parte.value} style={{ border: '1px solid var(--erp-moldura)' }}>
                <div
                  style={{
                    position: 'relative',
                    height: 132,
                    background: 'var(--erp-grafite)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    cursor: capa || editavel ? 'pointer' : 'default',
                  }}
                  onClick={() => {
                    if (capa) setAmpliada(capa);
                    else if (editavel) setParteAberta(parte.value);
                  }}
                >
                  {capa ? (
                    <img
                      src={capa.url}
                      alt={parte.label}
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  ) : (
                    <div style={{ textAlign: 'center', color: '#52525b' }}>
                      <IconPhotoPlus size={30} stroke={1.4} />
                      <Text size="xs" mt={3} c="#71717a">
                        Sem fotografia
                      </Text>
                    </div>
                  )}
                  {desta.length > 1 && (
                    <Badge
                      size="xs"
                      style={{ position: 'absolute', top: 5, right: 5 }}
                      color="dark"
                    >
                      {desta.length}
                    </Badge>
                  )}
                </div>

                <Group
                  justify="space-between"
                  wrap="nowrap"
                  gap={4}
                  px={7}
                  py={4}
                  style={{ borderTop: `2px solid ${capa ? 'var(--erp-dourado)' : '#e2e2e6'}` }}
                >
                  <Text
                    size="xs"
                    fw={600}
                    style={{
                      fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                      textTransform: 'uppercase',
                      letterSpacing: '0.04em',
                      fontSize: 12.5,
                    }}
                  >
                    {parte.label}
                  </Text>
                  {editavel && (
                    <Button
                      size="compact-xs"
                      variant="subtle"
                      onClick={() => setParteAberta(parte.value)}
                      title={`Acrescentar fotografia de ${parte.label.toLowerCase()}`}
                    >
                      <IconCamera size={13} />
                    </Button>
                  )}
                </Group>
              </div>
            );
          })}
        </div>
      </Painel>
    </>
  );
}

function UploadModal({
  assetId,
  parte,
  fechar,
}: {
  assetId: string;
  parte: string | null;
  fechar: () => void;
}) {
  const queryClient = useQueryClient();
  const [caption, setCaption] = useState('');
  const [kind, setKind] = useState<string | null>(null);

  const escolhida = kind ?? parte;

  const enviar = useMutation({
    mutationFn: (file: File) => {
      const recusa = checkUploadSize(file);
      if (recusa) throw new Error(recusa);
      const form = new FormData();
      form.append('file', file);
      if (escolhida) form.append('kind', escolhida);
      if (caption.trim()) form.append('caption', caption.trim());
      return api(`/assets/${assetId}/photos`, { method: 'POST', body: form });
    },
    onSuccess: () => {
      notifications.show({ title: 'Fotografia carregada', message: '', color: 'green' });
      queryClient.invalidateQueries({ queryKey: ['asset', assetId] });
      setCaption('');
      setKind(null);
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível carregar', message: e.message, color: 'red' }),
  });

  return (
    <Modal
      opened={parte != null}
      onClose={fechar}
      title={`Fotografia — ${rotulo(escolhida ?? 'GENERAL')}`}
    >
      <Select
        label="Parte"
        data={PARTES}
        value={escolhida}
        onChange={setKind}
        allowDeselect={false}
        mb="xs"
      />
      <TextInput
        label="Legenda"
        placeholder="Fuga no retentor do lado direito"
        value={caption}
        onChange={(e) => setCaption(e.currentTarget.value)}
        mb="md"
      />
      <Group justify="flex-end" gap="xs">
        <Button variant="default" onClick={fechar}>
          Cancelar
        </Button>
        <Button component="label" loading={enviar.isPending}>
          Escolher ficheiro
          <input
            type="file"
            hidden
            accept="image/*"
            onChange={(e) => {
              const f = e.currentTarget.files?.[0];
              e.currentTarget.value = '';
              if (f) enviar.mutate(f);
            }}
          />
        </Button>
      </Group>
    </Modal>
  );
}

function rotulo(kind: string) {
  return PARTES.find((p) => p.value === kind)?.label ?? kind;
}
