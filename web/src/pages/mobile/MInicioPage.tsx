import { Loader, Stack, Text } from '@mantine/core';
import {
  IconAlertTriangle,
  IconChecklist,
  IconChevronRight,
  IconClipboardList,
  IconDroplet,
  IconGasStation,
  IconGauge,
  IconRoute,
  IconTool,
} from '@tabler/icons-react';
import { useAuth } from '../../auth/AuthContext';
import { useHome } from './comum';
import { BotaoGrande, Cartao, Etiqueta, Numero, Seccao } from './pecas';
import { AMBAR, BRANCO, CINZA, LARANJA, TITULO, VERDE, VERMELHO, corDoEstado, nomeDoEstado } from './tema';

/**
 * O «Hoje» do motorista.
 *
 * <p>Abre e responde a três perguntas sem carregar em nada: qual é a minha
 * viatura e como está, para onde vou hoje, e o que falta fazer antes de sair.
 * Tudo o resto são botões. Não há gráficos nem indicadores — quem está aqui
 * quer trabalhar, não analisar.
 */
export function MInicioPage() {
  const { data, isLoading } = useHome();
  const { has } = useAuth();
  const mecanico = has('WORKORDERS_MANAGE');

  const minhas = data?.myAssets ?? [];
  const principal = minhas[0];
  const rota = data?.route;
  const porFazer = minhas.some((a) => !a.inspectionDoneToday);

  const saudacao = () => {
    const h = new Date().getHours();
    if (h < 12) return 'Bom dia';
    if (h < 19) return 'Boa tarde';
    return 'Boa noite';
  };

  return (
    <Stack gap="md">
      <div>
        <Text style={{ fontFamily: TITULO, fontSize: 26, fontWeight: 700, lineHeight: 1.1 }}>
          {saudacao()}, {data?.userName?.split(' ')[0] ?? ''}
        </Text>
        <Text size="sm" style={{ color: CINZA }}>
          {new Date().toLocaleDateString('pt-PT', { weekday: 'long', day: 'numeric', month: 'long' })}
        </Text>
      </div>

      {isLoading && <Loader color="yellow" />}

      {/* ====== O que tem de ser feito antes de sair ====== */}
      {porFazer && (
        <Cartao destaque>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
            <IconChecklist size={26} style={{ color: AMBAR, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <Text fw={700} size="md">
                Inspeção diária por fazer
              </Text>
              <Text size="sm" style={{ color: CINZA }} mb={10}>
                São cinco minutos antes de arrancar. Qualquer ponto crítico reprovado impede a viatura de
                sair.
              </Text>
              <BotaoGrande to={`/m/inspecao${principal ? `?ativo=${principal.id}` : ''}`} icone={<IconChecklist size={20} />}>
                Fazer agora
              </BotaoGrande>
            </div>
          </div>
        </Cartao>
      )}

      {/* ====== A minha viatura ====== */}
      {minhas.length > 0 && (
        <div>
          <Seccao>{minhas.length > 1 ? 'As minhas viaturas' : 'A minha viatura'}</Seccao>
          <Stack gap={10}>
            {minhas.map((a) => (
              <Cartao key={a.id}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 10 }}>
                  <div>
                    <Text style={{ fontFamily: TITULO, fontSize: 24, fontWeight: 700, lineHeight: 1 }}>
                      {a.tag}
                    </Text>
                    <Text size="sm" style={{ color: CINZA }}>
                      {a.plate ? `${a.plate} · ` : ''}
                      {a.name}
                    </Text>
                  </div>
                  <Etiqueta cor={corDoEstado(a.status)}>{nomeDoEstado(a.status)}</Etiqueta>
                </div>

                <div style={{ display: 'flex', gap: 22, marginTop: 14 }}>
                  {a.meterLabel && <Numero valor={a.meterLabel} legenda="contador" />}
                  {a.nextMaintenanceIn && (
                    <Numero
                      valor={a.nextMaintenanceIn.replace('em ', '')}
                      legenda="p/ manutenção"
                      cor={
                        a.nextMaintenanceStatus === 'OVERDUE'
                          ? VERMELHO
                          : a.nextMaintenanceStatus === 'DUE_SOON'
                            ? LARANJA
                            : BRANCO
                      }
                    />
                  )}
                  <Numero
                    valor={a.inspectionDoneToday ? 'feita' : 'por fazer'}
                    legenda="inspeção de hoje"
                    cor={a.inspectionDoneToday ? VERDE : AMBAR}
                  />
                </div>

                {a.nextMaintenance && (
                  <div style={{ display: 'flex', gap: 7, alignItems: 'center', marginTop: 12 }}>
                    <IconTool size={15} style={{ color: CINZA, flexShrink: 0 }} />
                    <Text size="xs" style={{ color: CINZA }}>
                      A seguir: {a.nextMaintenance}
                    </Text>
                  </div>
                )}
              </Cartao>
            ))}
          </Stack>
        </div>
      )}

      {/* ====== A rota de hoje ====== */}
      <div>
        <Seccao>A rota de hoje</Seccao>
        {rota ? (
          <Cartao to="/m/rota">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                <IconRoute size={26} style={{ color: AMBAR, flexShrink: 0 }} />
                <div>
                  <Text fw={700} size="md" lh={1.2}>
                    {rota.name}
                  </Text>
                  <Text size="xs" style={{ color: CINZA }}>
                    {rota.code ? `${rota.code} · ` : ''}
                    {rota.distanceKm ? `${Math.round(Number(rota.distanceKm))} km` : 'distância por apurar'}
                    {rota.expectedMinutes
                      ? ` · ${Math.floor(rota.expectedMinutes / 60)} h ${rota.expectedMinutes % 60} min`
                      : ''}
                  </Text>
                </div>
              </div>
              <IconChevronRight size={20} style={{ color: CINZA }} />
            </div>
          </Cartao>
        ) : (
          <Cartao>
            <Text size="sm" style={{ color: CINZA }}>
              Não há rota marcada para hoje. Quando o gestor lhe atribuir uma, ela aparece aqui com o
              percurso no mapa.
            </Text>
          </Cartao>
        )}
      </div>

      {/* ====== Avisos ====== */}
      {(data?.warnings ?? []).length > 0 && (
        <div>
          <Seccao>A ter em conta</Seccao>
          <Cartao>
            <Stack gap={8}>
              {(data?.warnings ?? []).map((w) => (
                <div key={w} style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                  <IconAlertTriangle size={16} style={{ color: LARANJA, flexShrink: 0, marginTop: 2 }} />
                  <Text size="sm">{w}</Text>
                </div>
              ))}
            </Stack>
          </Cartao>
        </div>
      )}

      {/* ====== O que se faz aqui ====== */}
      <div>
        <Seccao>Registar</Seccao>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <Atalho to="/m/abastecer" icone={<IconGasStation size={26} />} titulo="Abastecer" sub="Litros e contador" />
          <Atalho to="/m/avaria" icone={<IconAlertTriangle size={26} />} titulo="Ocorrência" sub="Avaria, acidente, foto" cor={VERMELHO} />
          <Atalho
            to="/m/atestar"
            icone={<IconDroplet size={26} />}
            titulo="Atestar"
            sub="Água, óleo, travões"
          />
          {mecanico ? (
            <Atalho
              to="/m/ordens"
              icone={<IconClipboardList size={26} />}
              titulo="Ordens"
              sub={data ? `${data.myOpenOrders} por fazer` : ''}
            />
          ) : (
            <Atalho to="/m/perfil" icone={<IconGauge size={26} />} titulo="A minha conta" sub="Identificador e senha" />
          )}
        </div>
      </div>
    </Stack>
  );
}

function Atalho({
  to,
  icone,
  titulo,
  sub,
  cor = AMBAR,
}: {
  to: string;
  icone: React.ReactNode;
  titulo: string;
  sub?: string;
  cor?: string;
}) {
  return (
    <Cartao to={to}>
      <div style={{ color: cor, marginBottom: 8 }}>{icone}</div>
      <Text fw={700} size="sm" lh={1.2}>
        {titulo}
      </Text>
      {sub && (
        <Text size="xs" style={{ color: CINZA }}>
          {sub}
        </Text>
      )}
    </Cartao>
  );
}
