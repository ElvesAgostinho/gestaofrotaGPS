/**
 * Página pública — o que o IMBONDEIRO OS é, para quem ainda não comprou.
 *
 * <p>Não pede sessão. É a porta de entrada: quem chega vê o que o sistema faz,
 * com números concretos em vez de promessas, e depois entra ou pede contacto.
 *
 * <p>As afirmações aqui são as que o sistema cumpre mesmo — cada uma
 * corresponde a funcionalidade que existe e foi testada. Uma página comercial
 * que promete o que o produto não faz produz uma demonstração embaraçosa e um
 * cliente perdido.
 */
import { Button, Container, Grid, Group, Stack, Text, Title } from '@mantine/core';
import {
  IconAlertTriangle,
  IconChartBar,
  IconClipboardList,
  IconGasStation,
  IconMap2,
  IconRuler,
  IconShieldLock,
  IconTruck,
} from '@tabler/icons-react';
import { Link } from 'react-router-dom';
import { CenaCamiao, CenaGerador, CenaRetroescavadora } from './CenasEquipamento';

const CONDENSADA = '"Barlow Condensed", Barlow, Impact, sans-serif';
const AMBAR = '#F5A800';
const PRETO = '#141416';
const CARVAO = '#2D2D2F';

export function LandingPage() {
  return (
    <div style={{ background: '#fff' }}>
      <Cabecalho />
      <Heroi />
      <Numeros />
      <OQueFaz />
      <Equipamento />
      <Confianca />
      <Chamada />
      <Rodape />
    </div>
  );
}

// ==== Cabeçalho ============================================================

function Cabecalho() {
  return (
    <>
      <div style={{ background: PRETO, borderBottom: `4px solid ${AMBAR}` }}>
        <Container size="xl">
          <Group h={64} justify="space-between">
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                border: `2px solid ${AMBAR}`,
                padding: '3px 11px',
                background: '#000',
              }}
            >
              <Text
                fw={700}
                c="white"
                style={{ fontFamily: CONDENSADA, fontSize: 22, letterSpacing: '0.06em' }}
              >
                IMBONDEIRO<span style={{ color: AMBAR }}> OS</span>
              </Text>
            </div>

            <Group gap="lg" visibleFrom="sm">
              {['O que faz', 'Equipamento', 'Porquê o IMBONDEIRO OS'].map((t, i) => (
                <Text
                  key={t}
                  component="a"
                  href={`#s${i}`}
                  c="white"
                  style={{
                    fontFamily: CONDENSADA,
                    textTransform: 'uppercase',
                    letterSpacing: '0.06em',
                    fontSize: 15,
                    fontWeight: 600,
                    textDecoration: 'none',
                  }}
                >
                  {t}
                </Text>
              ))}
            </Group>

            <Button component={Link} to="/entrar" size="sm">
              Entrar
            </Button>
          </Group>
        </Container>
      </div>
    </>
  );
}

// ==== Herói ================================================================

function Heroi() {
  return (
    <div style={{ background: PRETO, overflow: 'hidden' }}>
      <Container size="xl" py={48}>
        <Grid gutter={40} align="center">
          <Grid.Col span={{ base: 12, md: 6 }}>
            <Text
              style={{
                fontFamily: CONDENSADA,
                color: AMBAR,
                textTransform: 'uppercase',
                letterSpacing: '0.16em',
                fontSize: 15,
                fontWeight: 600,
              }}
            >
              Gestão de frota e manutenção
            </Text>
            <Title
              order={1}
              c="white"
              style={{
                fontFamily: CONDENSADA,
                fontSize: 58,
                lineHeight: 1.02,
                textTransform: 'uppercase',
                margin: '8px 0 16px',
              }}
            >
              A sua frota<br />
              <span style={{ color: AMBAR }}>sem surpresas</span>
            </Title>
            <Text c="#c4c4c9" size="lg" style={{ maxWidth: 520, lineHeight: 1.55 }}>
              Camiões, máquinas e geradores numa só plataforma. Sabe onde estão, quanto
              consomem, quando param — e quanto isso lhe custa, ao litro e à hora.
            </Text>
            <Group gap="sm" mt={26}>
              <Button component={Link} to="/entrar" size="md">
                Entrar no sistema
              </Button>
              <Button
                component="a"
                href="#s0"
                size="md"
                variant="outline"
                color="gray.3"
                style={{ borderColor: '#52525b', color: '#fff' }}
              >
                Ver o que faz
              </Button>
            </Group>
          </Grid.Col>

          <Grid.Col span={{ base: 12, md: 6 }}>
            <CenaCamiao altura={250} />
          </Grid.Col>
        </Grid>
      </Container>
    </div>
  );
}

// ==== Números ==============================================================

/**
 * Os números que o sistema calcula.
 *
 * <p>São as metas do plano de manutenção preventiva que uma empresa de
 * equipamento pesado persegue — e que o IMBONDEIRO OS calcula a partir dos dados
 * reais, não estimativas.
 */
function Numeros() {
  const itens = [
    { valor: '≥ 90%', rotulo: 'Disponibilidade', nota: 'Horas disponíveis sobre horas planeadas' },
    { valor: '≥ 500 h', rotulo: 'MTBF', nota: 'Horas de operação por avaria' },
    { valor: '≤ 4 h', rotulo: 'MTTR', nota: 'Tempo médio de reparação' },
    { valor: '≥ 95%', rotulo: 'Cumprimento', nota: 'Ordens executadas sobre planeadas' },
  ];
  return (
    <div style={{ background: AMBAR }}>
      <Container size="xl" py={26}>
        <Grid gutter="lg">
          {itens.map((i) => (
            <Grid.Col key={i.rotulo} span={{ base: 6, md: 3 }}>
              <Text
                style={{
                  fontFamily: CONDENSADA,
                  fontSize: 36,
                  fontWeight: 700,
                  color: PRETO,
                  lineHeight: 1,
                }}
              >
                {i.valor}
              </Text>
              <Text
                style={{
                  fontFamily: CONDENSADA,
                  textTransform: 'uppercase',
                  letterSpacing: '0.08em',
                  fontSize: 14,
                  fontWeight: 600,
                  color: PRETO,
                }}
              >
                {i.rotulo}
              </Text>
              <Text size="xs" style={{ color: '#4a3600' }}>
                {i.nota}
              </Text>
            </Grid.Col>
          ))}
        </Grid>
      </Container>
    </div>
  );
}

// ==== O que faz ============================================================

function OQueFaz() {
  const blocos = [
    {
      icone: IconGasStation,
      titulo: 'Controlo de combustível',
      texto:
        'Compara cada abastecimento com o consumo normal da própria viatura e assinala o que sai fora: depósitos que não cabem no tanque, consumos ao dobro, cartões usados longe da máquina. Cada anomalia traz as contas ao lado.',
    },
    {
      icone: IconClipboardList,
      titulo: 'Ordens de manutenção',
      texto:
        'Da avaria à assinatura: diagnóstico, orçamentos de várias oficinas para comparar, aprovação por limite de valor, custos de mão de obra e peças, horas paradas e o que elas custam.',
    },
    {
      icone: IconRuler,
      titulo: 'O que o mecânico mede',
      texto:
        'Espessura de pastilhas, piso dos pneus, tensões, pressões, prova de carga. Com limites de serviço — e o veredicto é do servidor, não de quem escreve. Não se marca como boa uma peça que está fora.',
    },
    {
      icone: IconMap2,
      titulo: 'GPS e mapa ao vivo',
      texto:
        'Viaturas no mapa com o rumo, a deslizar em tempo real. Ligação a servidor Traccar próprio, com bloqueio de ignição sujeito a aprovação e registo de quem pediu e porquê.',
    },
    {
      icone: IconTruck,
      titulo: 'Planos preventivos',
      texto:
        'Planos completos prontos a aplicar — retroescavadora, camião pesado, gerador — com intervalos por horas ou quilómetros, ferramentas e peças previstas. Vencido o intervalo, a ordem abre-se com um clique.',
    },
    {
      icone: IconChartBar,
      titulo: 'Relatórios que decidem',
      texto:
        'Custo de manutenção por ativo, com custo por quilómetro e dias parada — o relatório que responde se vale a pena continuar a reparar uma viatura. Tudo em CSV, para abrir no Excel em português.',
    },
  ];

  return (
    <Container size="xl" py={56} id="s0">
      <Seccao titulo="O que o sistema faz" />
      <Grid gutter="lg" mt="lg">
        {blocos.map((b) => (
          <Grid.Col key={b.titulo} span={{ base: 12, sm: 6, md: 4 }}>
            <div style={{ borderTop: `3px solid ${AMBAR}`, paddingTop: 14, height: '100%' }}>
              <b.icone size={30} stroke={1.5} color={PRETO} />
              <Text
                mt={8}
                style={{
                  fontFamily: CONDENSADA,
                  fontSize: 21,
                  fontWeight: 700,
                  textTransform: 'uppercase',
                  letterSpacing: '0.02em',
                }}
              >
                {b.titulo}
              </Text>
              <Text size="sm" c="dimmed" mt={4} style={{ lineHeight: 1.55 }}>
                {b.texto}
              </Text>
            </div>
          </Grid.Col>
        ))}
      </Grid>
    </Container>
  );
}

// ==== Equipamento ==========================================================

function Equipamento() {
  const familias = [
    {
      cena: <CenaCamiao altura={190} />,
      titulo: 'Viaturas',
      texto:
        'Camiões, ligeiros e reboques. Quilómetros em tempo real pelo GPS, consumo por 100 km, revisões por quilometragem, condutores e pontuação de condução.',
    },
    {
      cena: <CenaRetroescavadora altura={190} />,
      titulo: 'Máquinas',
      texto:
        'Retroescavadoras, pás e equipamento de obra. Horas de trabalho, lubrificação às 50 h, plano por sistema às 250, 500, 1000 e 2000 horas, análise de óleo e vibração.',
    },
    {
      cena: <CenaGerador altura={190} />,
      titulo: 'Geradores',
      texto:
        'Grupos electrogéneos. Ensaio em carga com registo de potência, tensão por fase, frequência e resistência de isolamento — a única prova de que arrancam quando faltar a energia.',
    },
  ];

  return (
    <div style={{ background: '#f4f4f5' }} id="s1">
      <Container size="xl" py={56}>
        <Seccao titulo="Uma plataforma, três famílias" />
        <Text c="dimmed" mt={6} style={{ maxWidth: 640 }}>
          Um camião, uma retroescavadora e um gerador não se gerem da mesma maneira. O IMBONDEIRO OS
          separa-os por família e trata cada um como ele precisa — sem obrigar a três sistemas
          diferentes.
        </Text>
        <Grid gutter="lg" mt="xl">
          {familias.map((f) => (
            <Grid.Col key={f.titulo} span={{ base: 12, md: 4 }}>
              <div style={{ background: PRETO, border: `2px solid ${AMBAR}` }}>{f.cena}</div>
              <Text
                mt={12}
                style={{
                  fontFamily: CONDENSADA,
                  fontSize: 23,
                  fontWeight: 700,
                  textTransform: 'uppercase',
                }}
              >
                {f.titulo}
              </Text>
              <Text size="sm" c="dimmed" mt={2} style={{ lineHeight: 1.55 }}>
                {f.texto}
              </Text>
            </Grid.Col>
          ))}
        </Grid>
      </Container>
    </div>
  );
}

// ==== Confiança ============================================================

function Confianca() {
  const pontos = [
    {
      icone: IconShieldLock,
      titulo: 'Os dados de cada empresa ficam na empresa',
      texto:
        'Cada consulta é filtrada pela empresa de quem a faz. Quem não tem permissão de custos não recebe os valores — não são escondidos no ecrã, são removidos antes de saírem do servidor.',
    },
    {
      icone: IconAlertTriangle,
      titulo: 'O sistema diz quando não sabe',
      texto:
        'Sem servidor de GPS configurado, diz que não consegue bloquear — em vez de fingir que enviou. Sem leitura do contador, diz que não há consumo. Um sistema que inventa números é pior do que não ter sistema.',
    },
  ];

  return (
    <Container size="xl" py={56} id="s2">
      <Seccao titulo="Porquê o IMBONDEIRO OS" />
      <Grid gutter="xl" mt="lg">
        {pontos.map((p) => (
          <Grid.Col key={p.titulo} span={{ base: 12, md: 6 }}>
            <Group align="flex-start" gap="md" wrap="nowrap">
              <div
                style={{
                  background: PRETO,
                  color: AMBAR,
                  padding: 11,
                  flexShrink: 0,
                }}
              >
                <p.icone size={26} stroke={1.6} />
              </div>
              <div>
                <Text
                  style={{
                    fontFamily: CONDENSADA,
                    fontSize: 20,
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    lineHeight: 1.15,
                  }}
                >
                  {p.titulo}
                </Text>
                <Text size="sm" c="dimmed" mt={4} style={{ lineHeight: 1.55 }}>
                  {p.texto}
                </Text>
              </div>
            </Group>
          </Grid.Col>
        ))}
      </Grid>
    </Container>
  );
}

// ==== Chamada ==============================================================

function Chamada() {
  return (
    <div style={{ background: AMBAR }}>
      <Container size="xl" py={40}>
        <Group justify="space-between" align="center" wrap="wrap" gap="lg">
          <div>
            <Title
              order={2}
              style={{
                fontFamily: CONDENSADA,
                fontSize: 38,
                textTransform: 'uppercase',
                color: PRETO,
                lineHeight: 1.05,
              }}
            >
              Comece pela sua frota
            </Title>
            <Text style={{ color: '#4a3600', maxWidth: 560 }} mt={4}>
              Importe os ativos de uma folha de cálculo e comece a registar. Os números aparecem
              a partir do primeiro abastecimento.
            </Text>
          </div>
          <Group gap="sm">
            <Button component={Link} to="/entrar" size="lg" color="dark">
              Entrar
            </Button>
            <Button
              component="a"
              href="mailto:comercial@imbondeiro.ao"
              size="lg"
              variant="outline"
              color="dark"
            >
              Falar connosco
            </Button>
          </Group>
        </Group>
      </Container>
    </div>
  );
}

// ==== Rodapé ===============================================================

function Rodape() {
  const colunas = [
    { titulo: 'Sistema', itens: ['Ativos', 'Manutenção', 'Combustível', 'GPS e mapa'] },
    { titulo: 'Gestão', itens: ['Relatórios', 'Indicadores', 'Auditoria', 'Equipa'] },
    { titulo: 'Equipamento', itens: ['Viaturas', 'Máquinas', 'Geradores'] },
    { titulo: 'Empresa', itens: ['Sobre', 'Contactos', 'Termos'] },
  ];
  return (
    <div style={{ background: CARVAO, color: '#e2e2e6' }}>
      <Container size="xl" py={44}>
        <Grid gutter="lg">
          {colunas.map((c) => (
            <Grid.Col key={c.titulo} span={{ base: 6, md: 3 }}>
              <Text
                style={{
                  fontFamily: CONDENSADA,
                  color: AMBAR,
                  textTransform: 'uppercase',
                  letterSpacing: '0.08em',
                  fontSize: 15,
                  fontWeight: 700,
                }}
              >
                {c.titulo}
              </Text>
              <Stack gap={3} mt={8}>
                {c.itens.map((i) => (
                  <Text key={i} size="sm" c="#c4c4c9">
                    {i}
                  </Text>
                ))}
              </Stack>
            </Grid.Col>
          ))}
        </Grid>
        <Text size="xs" c="#8a8a90" mt={34}>
          © {new Date().getFullYear()} IMBONDEIRO OS · Gestão de frota e manutenção · Angola
        </Text>
      </Container>
    </div>
  );
}

function Seccao({ titulo }: { titulo: string }) {
  return (
    <div>
      <div style={{ width: 56, height: 4, background: AMBAR, marginBottom: 10 }} />
      <Title
        order={2}
        style={{
          fontFamily: CONDENSADA,
          fontSize: 38,
          textTransform: 'uppercase',
          lineHeight: 1.05,
        }}
      >
        {titulo}
      </Title>
    </div>
  );
}
