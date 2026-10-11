import { Button, Group, Text } from '@mantine/core';
import { IconArrowUp } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { aplicarActualizacao, subscreverVersaoNova } from '../lib/actualizacao';

/**
 * «Há uma versão nova» — a barra que faltava.
 *
 * <p>Fica em cima de tudo e não se fecha sozinha, mas também não interrompe:
 * quem está a meio de um lançamento acaba-o e actualiza quando quiser. O que
 * não pode voltar a acontecer é alguém olhar para um ecrã antigo sem saber
 * que é antigo.
 */
export function NovaVersao() {
  const [ha, setHa] = useState(false);
  useEffect(() => subscreverVersaoNova(setHa), []);

  if (!ha) {
    return null;
  }

  return (
    <Group
      gap="sm"
      wrap="nowrap"
      justify="center"
      style={{
        position: 'fixed',
        bottom: 'calc(env(safe-area-inset-bottom) + 12px)',
        left: '50%',
        transform: 'translateX(-50%)',
        zIndex: 1000,
        padding: '8px 14px',
        borderRadius: 2,
        border: '1px solid var(--mantine-color-yellow-5)',
        background: 'var(--mantine-color-body)',
        boxShadow: '0 6px 24px rgba(0,0,0,0.25)',
        maxWidth: 'calc(100vw - 32px)',
      }}
    >
      <Text size="sm" fw={600}>
        Há uma versão nova do sistema.
      </Text>
      <Button size="compact-sm" leftSection={<IconArrowUp size={14} />} onClick={aplicarActualizacao}>
        Actualizar
      </Button>
    </Group>
  );
}
