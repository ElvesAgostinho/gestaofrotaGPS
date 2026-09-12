import { Badge, Button, Checkbox, Group, Modal, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { api } from '../../api/client';

interface Permissao {
  code: string;
  module: string;
  label: string;
  defaultRoles: string[];
}

export interface MembroComPermissoes {
  id: string;
  name: string;
  role: string;
  roleLabel: string;
  permissions?: string[];
  granted?: string[];
  denied?: string[];
}

/**
 * O que esta pessoa pode fazer, módulo a módulo.
 *
 * <p>O papel dá um conjunto por omissão; aqui marca-se ou desmarca-se cada
 * permissão por cima. Marcar o que o papel não traz é «dar»; desmarcar o que o
 * papel traz é «tirar». O servidor guarda só as diferenças, por isso mudar o
 * papel mais tarde mantém as exceções que a pessoa tinha.
 */
export function PermissoesMembro({
  membro,
  aberto,
  fechar,
}: {
  membro: MembroComPermissoes | null;
  aberto: boolean;
  fechar: () => void;
}) {
  const queryClient = useQueryClient();

  const { data: catalogo } = useQuery({
    queryKey: ['team', 'permissions'],
    queryFn: () => api<Permissao[]>('/team/permissions'),
    enabled: aberto,
  });

  /** As que ficam ativas depois das alterações. */
  const [ativas, setAtivas] = useState<Set<string>>(new Set());

  useEffect(() => {
    if (aberto && membro) setAtivas(new Set(membro.permissions ?? []));
  }, [aberto, membro]);

  const porModulo = useMemo(() => {
    const m = new Map<string, Permissao[]>();
    for (const p of catalogo ?? []) {
      if (!m.has(p.module)) m.set(p.module, []);
      m.get(p.module)!.push(p);
    }
    return [...m.entries()];
  }, [catalogo]);

  const doPapel = (p: Permissao) => !!membro && p.defaultRoles.includes(membro.role);

  const guardar = useMutation({
    mutationFn: () => {
      // Só as diferenças ao papel viajam.
      const granted = (catalogo ?? []).filter((p) => ativas.has(p.code) && !doPapel(p)).map((p) => p.code);
      const denied = (catalogo ?? []).filter((p) => !ativas.has(p.code) && doPapel(p)).map((p) => p.code);
      return api(`/team/members/${membro!.id}`, { method: 'PATCH', body: { granted, denied } });
    },
    onSuccess: () => {
      notifications.show({
        title: 'Permissões guardadas',
        message: `${membro?.name}. A sessão dessa pessoa foi terminada para as novas permissões valerem já.`,
        color: 'green',
        autoClose: 8000,
      });
      queryClient.invalidateQueries({ queryKey: ['team'] });
      fechar();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível guardar', message: e.message, color: 'red' }),
  });

  if (!membro) return null;

  return (
    <Modal opened={aberto} onClose={fechar} title={`Permissões — ${membro.name}`} size="lg" centered>
      <Stack gap="md">
        <Text size="sm" c="dimmed">
          O papel <b>{membro.roleLabel}</b> traz as permissões marcadas com «do papel». Pode dar outras
          por cima, ou tirar as do papel. Só as diferenças ficam guardadas.
        </Text>

        {porModulo.map(([modulo, lista]) => (
          <div key={modulo}>
            <Text
              size="xs"
              fw={700}
              tt="uppercase"
              c="dimmed"
              style={{ fontFamily: 'var(--erp-condensada)', letterSpacing: '0.08em', marginBottom: 4 }}
            >
              {modulo}
            </Text>
            <Stack gap={4} pl="xs">
              {lista.map((p) => {
                const padrao = doPapel(p);
                const ativa = ativas.has(p.code);
                return (
                  <Group key={p.code} gap="sm" wrap="nowrap" align="flex-start">
                    <Checkbox
                      size="sm"
                      checked={ativa}
                      onChange={(e) => {
                        const n = new Set(ativas);
                        if (e.currentTarget.checked) n.add(p.code);
                        else n.delete(p.code);
                        setAtivas(n);
                      }}
                      label={p.label}
                    />
                    {padrao && ativa && (
                      <Badge size="xs" variant="light" color="gray">
                        do papel
                      </Badge>
                    )}
                    {padrao && !ativa && (
                      <Badge size="xs" variant="light" color="red">
                        tirada
                      </Badge>
                    )}
                    {!padrao && ativa && (
                      <Badge size="xs" variant="light" color="teal">
                        dada
                      </Badge>
                    )}
                  </Group>
                );
              })}
            </Stack>
          </div>
        ))}

        <Group justify="flex-end">
          <Button variant="default" onClick={fechar}>
            Cancelar
          </Button>
          <Button loading={guardar.isPending} onClick={() => guardar.mutate()}>
            Guardar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
