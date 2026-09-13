import { Atalhos } from './Atalhos';
import { BarraRodape } from './BarraRodape';
import { AppShell, Avatar, Badge, Burger, Group, Menu, NavLink, ScrollArea, Text, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconAlertTriangle,
  IconBell,
  IconBuildingStore,
  IconCategory,
  IconChartBar,
  IconClipboardList,
  IconLock,
  IconFileInvoice,
  IconFileText,
  IconGasStation,
  IconGauge,
  IconLogout,
  IconMap2,
  IconPackage,
  IconRoute,
  IconSettings,
  IconShieldLock,
  IconBuildingSkyscraper,
  IconSteeringWheel,
  IconDeviceCctv,
  IconTruck,
  IconUserCheck,
  IconUsers,
  IconWaveSine,
  IconCalendarRepeat,
  IconReportMoney,
} from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { MARCA } from '../theme';
import { NavLink as RouterLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { useAuth, type Role } from '../auth/AuthContext';

interface NavItem {
  to: string;
  label: string;
  icon: typeof IconGauge;
  /** Papel mínimo para o item aparecer. */
  minRole?: Role;
  /** Permissão de módulo que o ecrã exige para ser útil. */
  permissao?: string;
}

/** Uma secção do menu. Agrupar evita uma lista de quinze itens sem hierarquia. */
interface NavSection {
  title: string;
  items: NavItem[];
}

/**
 * Os módulos do sistema.
 *
 * <p>A ordem não é alfabética nem histórica: é a do dia de trabalho. Começa no
 * que está a acontecer agora (painel, rastreamento, alertas), passa ao que se
 * gere (equipamento, manutenção), depois ao que custa, e só no fim à
 * administração — que é onde se entra uma vez por mês.
 *
 * <p>Os nomes são os do vocabulário da indústria, não os do programador:
 * «Ordens de Serviço» e não «work orders», «Imobilização» e não «bloqueios»,
 * «Rastreamento» e não «mapa ao vivo».
 */
const NAV: NavSection[] = [
  {
    title: 'Operação',
    items: [
      { to: '/', label: 'Painel de controlo', icon: IconGauge },
      { to: '/mapa', label: 'GPS / Rastreamento', icon: IconMap2 },
      { to: '/alertas', label: 'Alertas e ocorrências', icon: IconAlertTriangle },
    ],
  },
  {
    title: 'Equipamento',
    items: [
      { to: '/ativos', label: 'Parque de equipamento', icon: IconTruck },
      { to: '/tipos-equipamento', label: 'Tipos de equipamento', icon: IconCategory,
        minRole: 'MANAGER' },
      { to: '/aparelhos-gps', label: 'Rastreadores GPS', icon: IconDeviceCctv,
        minRole: 'MANAGER' },
      { to: '/documentos', label: 'Documentos e validades', icon: IconFileText },
      { to: '/filiais', label: 'Filiais e centros de custo', icon: IconBuildingStore },
    ],
  },
  {
    title: 'Manutenção',
    items: [
      { to: '/ordens', label: 'Ordens de serviço', icon: IconClipboardList },
      { to: '/planos', label: 'Planos e intervalos', icon: IconCalendarRepeat },
      { to: '/preditiva', label: 'Manutenção preditiva', icon: IconWaveSine },
      { to: '/pecas', label: 'Peças e armazém', icon: IconPackage, minRole: 'TECHNICIAN' },
    ],
  },
  {
    title: 'Transporte',
    items: [
      { to: '/guias', label: 'Guias de transporte', icon: IconFileInvoice,
        minRole: 'TECHNICIAN' },
      { to: '/motoristas', label: 'Motoristas', icon: IconUserCheck },
      { to: '/rotas', label: 'Rotas e percursos', icon: IconRoute, minRole: 'MANAGER' },
      { to: '/conducao', label: 'Comportamento de condução', icon: IconSteeringWheel,
        minRole: 'MANAGER' },
    ],
  },
  {
    title: 'Custos',
    items: [
      { to: '/combustivel', label: 'Gestão de combustível', icon: IconGasStation,
        minRole: 'MANAGER' },
      { to: '/orcamentos', label: 'Orçamento anual', icon: IconReportMoney, permissao: 'COSTS_VIEW' },
      { to: '/relatorios', label: 'Relatórios e indicadores', icon: IconChartBar,
        permissao: 'REPORTS_VIEW' },
    ],
  },
  {
    title: 'Segurança',
    items: [
      { to: '/comandos', label: 'Imobilização de viaturas', icon: IconLock,
        minRole: 'MANAGER' },
      { to: '/auditoria', label: 'Registo de auditoria', icon: IconShieldLock,
        minRole: 'OWNER' },
    ],
  },
  {
    title: 'Administração',
    items: [
      { to: '/equipa', label: 'Utilizadores e permissões', icon: IconUsers },
      { to: '/definicoes', label: 'Configurações', icon: IconSettings,
        permissao: 'SETTINGS_MANAGE' },
    ],
  },
];

/** O menu do dono do sistema: as empresas clientes. Só quem é administrador da plataforma o vê. */
const NAV_PLATAFORMA: NavSection = {
  title: 'Plataforma',
  items: [{ to: '/plataforma', label: 'Empresas clientes', icon: IconBuildingSkyscraper }],
};

export function Shell() {
  const [opened, { toggle }] = useDisclosure();
  const { user, org, can, signOut, has } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  // O contador de avisos por ler acompanha a pessoa por todo o lado, por isso
  // vive aqui e não em cada página.
  const { data: unread } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => api<{ unread: number }>('/notifications/unread-count'),
    refetchInterval: 60_000,
  });

  // O administrador da plataforma sem empresa própria só tem a Plataforma; com
  // empresa, tem o menu normal e a Plataforma no fim.
  const visible = (org ? NAV : [])
    .map((section) => ({
      ...section,
      items: section.items.filter(
        (item) => (!item.minRole || can(item.minRole)) && (!item.permissao || has(item.permissao)),
      ),
    }))
    .filter((section) => section.items.length > 0)
    .concat(user?.admin ? [NAV_PLATAFORMA] : []);

  return (
    <AppShell
      header={{ height: 60 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      footer={{ height: 26 }}
      padding="lg"
    >
      <Atalhos />
      <AppShell.Header
        bg={MARCA.graphite}
        style={{
          // A faixa âmbar por baixo do preto é a assinatura da maquinaria
          // pesada — e serve de separador entre a moldura e o trabalho.
          borderBottom: `4px solid ${MARCA.gold}`,
        }}
      >
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm">
            <Burger
              opened={opened}
              onClick={toggle}
              hiddenFrom="sm"
              size="sm"
              color="white"
            />
            {/* Logótipo em caixa, como uma chapa de identificação de máquina:
                moldura âmbar, fundo preto, condensado maiúsculo. É a linguagem
                de quem marca equipamento para ser lido com pó pelo meio. */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                border: `2px solid ${MARCA.gold}`,
                padding: '2px 9px',
                background: '#000',
              }}
            >
              <Text
                fw={700}
                size="19px"
                c="white"
                style={{
                  fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                  letterSpacing: '0.06em',
                  lineHeight: 1.1,
                  whiteSpace: 'nowrap',
                }}
              >
                IMBONDEIRO<span style={{ color: MARCA.gold }}> OS</span>
              </Text>
            </div>
            {org && (
              <Text
                size="14px"
                c="graphite.3"
                visibleFrom="sm"
                style={{
                  fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                  textTransform: 'uppercase',
                  letterSpacing: '0.05em',
                }}
              >
                {org.name}
              </Text>
            )}
          </Group>

          <Group gap="xs">
            <UnstyledButton
              onClick={() => navigate('/notificacoes')}
              aria-label="Notificações"
              style={{ position: 'relative', padding: 6 }}
            >
              <IconBell size={20} color="white" />
              {!!unread?.unread && (
                <Badge
                  size="xs"
                  circle
                  color="red"
                  style={{ position: 'absolute', top: 0, right: 0 }}
                >
                  {unread.unread > 9 ? '9+' : unread.unread}
                </Badge>
              )}
            </UnstyledButton>

            <Menu position="bottom-end" withArrow>
              <Menu.Target>
                <UnstyledButton>
                  <Group gap="xs">
                    <Avatar color="gold" radius="xl" size={30}>
                      {initials(user?.name)}
                    </Avatar>
                    <div style={{ lineHeight: 1.2 }} className="mantine-visible-from-sm">
                      <Text size="sm" fw={600} c="white">
                        {user?.name}
                      </Text>
                      <Text size="xs" c="graphite.3">
                        {roleLabel(org?.myRole)}
                      </Text>
                    </div>
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Item leftSection={<IconLogout size={16} />} onClick={signOut}>
                  Terminar sessão
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar
        p="xs"
        bg={MARCA.graphite}
        style={{ borderRight: `1px solid ${MARCA.charcoal}` }}
      >
        <ScrollArea>
          {visible.map((section) => (
            <div key={section.title} style={{ marginBottom: 14 }}>
              <Text
                size="11px"
                fw={700}
                c="graphite.4"
                tt="uppercase"
                px="sm"
                style={{
                  fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                  letterSpacing: '0.1em',
                }}
                pb={4}
              >
                {section.title}
              </Text>
              {section.items.map((item) => {
                const active =
                  item.to === '/'
                    ? location.pathname === '/'
                    : location.pathname.startsWith(item.to);
                return (
                  <NavLink
                    key={item.to}
                    component={RouterLink}
                    to={item.to}
                    label={item.label}
                    leftSection={<item.icon size={17} stroke={1.6} />}
                    active={active}
                    onClick={() => opened && toggle()}
                    styles={{
                      root: {
                        borderRadius: 4,
                        marginBottom: 1,
                        // O item selecionado ganha uma barra dourada à esquerda
                        // em vez de um fundo cheio: sobre grafite, um bloco
                        // dourado ofusca o resto da lista.
                        borderLeft: active
                          ? `3px solid ${MARCA.gold}`
                          : '3px solid transparent',
                        backgroundColor: active ? 'rgba(176,141,60,0.14)' : 'transparent',
                        color: active ? '#fff' : '#a1a1aa',
                      },
                      label: {
                        fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                        fontSize: 15,
                        textTransform: 'uppercase',
                        letterSpacing: '0.03em',
                        fontWeight: active ? 700 : 500,
                      },
                    }}
                  />
                );
              })}
            </div>
          ))}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main bg={MARCA.surface}>
        <Outlet />
      </AppShell.Main>

      <AppShell.Footer>
        <BarraRodape />
      </AppShell.Footer>
    </AppShell>
  );
}

function initials(name?: string) {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

function roleLabel(role?: string) {
  switch (role) {
    case 'OWNER':
      return 'Dono';
    case 'MANAGER':
      return 'Gestor';
    case 'TECHNICIAN':
      return 'Técnico';
    case 'DRIVER':
      return 'Condutor';
    case 'VIEWER':
      return 'Consulta';
    default:
      return '';
  }
}
