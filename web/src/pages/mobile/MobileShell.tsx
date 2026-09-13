import { ActionIcon, Box, Group, Menu, Text, UnstyledButton } from '@mantine/core';
import {
  IconAlertTriangle,
  IconChecklist,
  IconClipboardList,
  IconDeviceDesktop,
  IconDotsVertical,
  IconGasStation,
  IconHome,
  IconLogout,
} from '@tabler/icons-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useConfigPublica } from '../../lib/marca';
import { MODO_KEY } from './modo';

/**
 * A casca da app do telemóvel: cabeçalho curto, conteúdo, e uma barra de
 * botões grandes em baixo — o que se consegue usar com uma mão, ao sol, com
 * luvas. Não há menus laterais nem grelhas: quem está aqui é o motorista ou
 * o mecânico, e vem fazer uma de quatro coisas.
 */
export function MobileShell() {
  const { user, org, signOut, has } = useAuth();
  const cfg = useConfigPublica();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const nome = cfg?.brand?.name ?? 'IMBONDEIRO OS';
  const mecanico = has('WORKORDERS_MANAGE');

  const itens = [
    { to: '/m', label: 'Início', icon: IconHome, end: true },
    { to: '/m/avaria', label: 'Avaria', icon: IconAlertTriangle },
    { to: '/m/inspecao', label: 'Inspeção', icon: IconChecklist },
    ...(mecanico ? [{ to: '/m/ordens', label: 'Ordens', icon: IconClipboardList }] : []),
    { to: '/m/abastecer', label: 'Abastecer', icon: IconGasStation },
  ];

  return (
    <Box style={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column', background: 'var(--mantine-color-body)' }}>
      <Group
        justify="space-between"
        px="md"
        py="xs"
        style={{
          background: '#1f2126',
          color: 'white',
          paddingTop: 'calc(var(--mantine-spacing-xs) + env(safe-area-inset-top))',
          position: 'sticky',
          top: 0,
          zIndex: 10,
        }}
      >
        <div style={{ lineHeight: 1.15 }}>
          <Text fw={700} size="md" c="white" style={{ fontFamily: 'Barlow Condensed, sans-serif', letterSpacing: '0.02em' }}>
            {nome}
          </Text>
          <Text size="xs" c="gray.5">
            {org?.name} · {user?.name}
          </Text>
        </div>
        <Menu position="bottom-end" withinPortal>
          <Menu.Target>
            <ActionIcon variant="subtle" color="gray" aria-label="Mais opções">
              <IconDotsVertical size={20} />
            </ActionIcon>
          </Menu.Target>
          <Menu.Dropdown>
            <Menu.Item
              leftSection={<IconDeviceDesktop size={16} />}
              onClick={() => {
                try {
                  localStorage.setItem(MODO_KEY, 'desktop');
                } catch {
                  /* sem armazenamento local continua na mesma */
                }
                navigate('/');
              }}
            >
              Versão completa
            </Menu.Item>
            <Menu.Item leftSection={<IconLogout size={16} />} onClick={signOut}>
              Terminar sessão
            </Menu.Item>
          </Menu.Dropdown>
        </Menu>
      </Group>

      <Box style={{ flex: 1, padding: '12px 14px 90px' }}>
        <Outlet />
      </Box>

      <Group
        grow
        gap={0}
        style={{
          position: 'fixed',
          left: 0,
          right: 0,
          bottom: 0,
          borderTop: '1px solid var(--mantine-color-default-border)',
          background: 'var(--mantine-color-body)',
          paddingBottom: 'env(safe-area-inset-bottom)',
          zIndex: 10,
        }}
      >
        {itens.map((i) => {
          const ativo = i.end ? pathname === i.to : pathname.startsWith(i.to);
          return (
            <UnstyledButton
              key={i.to}
              component={Link}
              to={i.to}
              aria-current={ativo ? 'page' : undefined}
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 2,
                padding: '10px 4px 8px',
                color: ativo ? 'var(--mantine-color-gold-7)' : 'var(--mantine-color-dimmed)',
                fontWeight: ativo ? 700 : 500,
                fontSize: 11,
              }}
            >
              <i.icon size={24} />
              {i.label}
            </UnstyledButton>
          );
        })}
      </Group>
    </Box>
  );
}
