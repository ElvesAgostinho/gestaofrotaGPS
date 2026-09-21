import { ActionIcon, Box, Group, Menu, Text, UnstyledButton } from '@mantine/core';
import {
  IconAlertTriangle,
  IconChecklist,
  IconClipboardList,
  IconCloudUpload,
  IconDeviceDesktop,
  IconDotsVertical,
  IconGasStation,
  IconHome,
  IconLogout,
  IconRoute,
  IconUser,
} from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthContext';
import { useConfigPublica } from '../../lib/marca';
import { MODO_KEY } from './modo';
import { ligarSincronizacaoAutomatica, sincronizar, subscreverFila } from './envio';
import { AMBAR, BRANCO, CINZA, PRETO, PRETO_CARTAO, PRETO_LINHA, TITULO } from './tema';

/**
 * A casca da aplicação do motorista.
 *
 * <p>Fundo preto e âmbar — as cores do sistema, mas invertidas: num telemóvel
 * dentro de uma cabina, o fundo escuro lê-se melhor ao sol e não encandeia à
 * noite. Cabeçalho curto, conteúdo, e uma barra de cinco botões grandes em
 * baixo, ao alcance do polegar. Não há menus laterais nem grelhas: quem está
 * aqui vem fazer uma coisa e voltar à estrada.
 */
export function MobileShell() {
  const { user, org, signOut, has } = useAuth();
  const cfg = useConfigPublica();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const nome = cfg?.brand?.name ?? 'IMBONDEIRO OS';
  const mecanico = has('WORKORDERS_MANAGE');
  const [porEnviar, setPorEnviar] = useState(0);

  // A fila é ligada uma vez: tudo o que ficou por enviar sobe quando a rede
  // voltar, sem o motorista ter de se lembrar de nada.
  useEffect(() => {
    ligarSincronizacaoAutomatica();
    return subscreverFila(setPorEnviar);
  }, []);

  const itens = [
    { to: '/m', label: 'Hoje', icon: IconHome, end: true },
    { to: '/m/rota', label: 'Rota', icon: IconRoute },
    { to: '/m/inspecao', label: 'Inspeção', icon: IconChecklist },
    ...(mecanico
      ? [{ to: '/m/ordens', label: 'Ordens', icon: IconClipboardList }]
      : [{ to: '/m/abastecer', label: 'Abastecer', icon: IconGasStation }]),
    { to: '/m/perfil', label: 'Perfil', icon: IconUser },
  ];

  return (
    <Box
      style={{
        minHeight: '100dvh',
        display: 'flex',
        flexDirection: 'column',
        background: PRETO,
        color: BRANCO,
      }}
    >
      <Group
        justify="space-between"
        px="md"
        py="xs"
        style={{
          background: PRETO_CARTAO,
          borderBottom: `1px solid ${PRETO_LINHA}`,
          paddingTop: 'calc(var(--mantine-spacing-xs) + env(safe-area-inset-top))',
          position: 'sticky',
          top: 0,
          zIndex: 10,
        }}
      >
        <div style={{ lineHeight: 1.15 }}>
          <Text fw={700} size="md" style={{ fontFamily: TITULO, letterSpacing: '0.04em', color: AMBAR }}>
            {nome}
          </Text>
          <Text size="xs" style={{ color: CINZA }}>
            {org?.name} · {user?.name}
          </Text>
        </div>
        <Group gap={4} wrap="nowrap">
          <UnstyledButton
            component={Link}
            to="/m/avaria"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              background: '#EF444422',
              border: '1px solid #EF444466',
              color: '#FCA5A5',
              borderRadius: 999,
              padding: '6px 12px',
              fontWeight: 700,
              fontSize: 13,
            }}
          >
            <IconAlertTriangle size={16} />
            Avaria
          </UnstyledButton>
          <Menu position="bottom-end" withinPortal>
            <Menu.Target>
              <ActionIcon variant="subtle" color="gray" aria-label="Mais opções">
                <IconDotsVertical size={20} />
              </ActionIcon>
            </Menu.Target>
            <Menu.Dropdown>
              {/* A versão completa é o sistema de gestão: um motorista não
                  tem lá nada a fazer, e o servidor também já não lho deixa
                  ver. Esconder a opção evita a pergunta. */}
              {org?.myRole !== 'DRIVER' && (
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
              )}
              <Menu.Item leftSection={<IconLogout size={16} />} onClick={signOut}>
                Terminar sessão
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Group>
      </Group>

      {porEnviar > 0 && (
        <UnstyledButton
          onClick={() => void sincronizar()}
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            width: '100%',
            padding: '8px 12px',
            background: '#F9731622',
            borderBottom: '1px solid #F9731655',
            color: '#FDBA74',
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          <IconCloudUpload size={16} />
          {porEnviar} registo(s) por enviar — toque para tentar agora
        </UnstyledButton>
      )}

      <Box style={{ flex: 1, padding: '14px 14px 96px' }}>
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
          borderTop: `1px solid ${PRETO_LINHA}`,
          background: PRETO_CARTAO,
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
                gap: 3,
                padding: '10px 4px 9px',
                color: ativo ? AMBAR : CINZA,
                fontWeight: ativo ? 700 : 500,
                fontSize: 11,
                borderTop: `2px solid ${ativo ? AMBAR : 'transparent'}`,
              }}
            >
              <i.icon size={23} />
              {i.label}
            </UnstyledButton>
          );
        })}
      </Group>
    </Box>
  );
}
