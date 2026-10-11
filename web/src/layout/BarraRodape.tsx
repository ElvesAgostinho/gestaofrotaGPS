import { Text, Tooltip, UnstyledButton } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { aplicarActualizacao, verificarActualizacao } from '../lib/actualizacao';

/** Injetada pelo Vite a partir do package.json. */
declare const __VERSAO__: string;
const VERSAO = typeof __VERSAO__ === 'string' ? __VERSAO__ : '0.2';

/**
 * A barra de estado do rodapé — a de qualquer ERP.
 *
 * <p>Diz, sem a pessoa ter de perguntar: em que empresa está, quem é e com
 * que papel, se o servidor responde, e a hora. O «servidor: ligado» é medido
 * a sério a cada 30 segundos contra {@code /health}; quando a rede cai, é
 * aqui que se vê primeiro — antes de um «guardar» falhar.
 */
export function BarraRodape() {
  const { user, org } = useAuth();

  const saude = useQuery({
    queryKey: ['health'],
    queryFn: async () => {
      const inicio = performance.now();
      const r = await fetch('/api/v1/health', { cache: 'no-store' });
      if (!r.ok) throw new Error('sem resposta');
      const corpo = (await r.json()) as { status: string; database: string };
      return { ...corpo, ms: Math.round(performance.now() - inicio) };
    },
    refetchInterval: 30_000,
    retry: 0,
  });

  const [agora, setAgora] = useState(new Date());
  useEffect(() => {
    const t = setInterval(() => setAgora(new Date()), 15_000);
    return () => clearInterval(t);
  }, []);

  const ligado = saude.isSuccess && saude.data.status === 'ok';
  const degradado = saude.isSuccess && saude.data.status !== 'ok';
  const cor = saude.isPending ? '#9a9aa0' : ligado ? '#16a34a' : degradado ? '#f59e0b' : '#dc2626';
  const rotulo = saude.isPending
    ? 'a verificar'
    : ligado
      ? `ligado · ${saude.data.ms} ms`
      : degradado
        ? 'base de dados com problemas'
        : 'sem ligação ao servidor';

  return (
    <div className="barra-rodape">
      <Celula rotulo="Empresa" valor={org?.name ?? '—'} />
      <Celula rotulo="Utilizador" valor={user ? `${user.name}${papel(org?.myRole)}` : '—'} />
      <Tooltip label="Medido a cada 30 segundos contra o servidor" withArrow>
        <div className="barra-rodape-celula">
          <span className="barra-rodape-rotulo">Servidor</span>
          <span
            style={{
              display: 'inline-block',
              width: 8,
              height: 8,
              borderRadius: '50%',
              background: cor,
              marginRight: 6,
              verticalAlign: 'middle',
            }}
          />
          <span>{rotulo}</span>
        </div>
      </Tooltip>
      <div style={{ flex: 1 }} />
      <Tooltip label="F1 mostra os atalhos de teclado" withArrow>
        <div className="barra-rodape-celula">
          <span className="barra-rodape-rotulo">Atalhos</span>
          <span>F1</span>
        </div>
      </Tooltip>
      <CelulaVersao />
      <Celula
        rotulo="Hora"
        valor={agora.toLocaleTimeString('pt-AO', { hour: '2-digit', minute: '2-digit' })}
      />
    </div>
  );
}

function papel(p?: string | null) {
  const nomes: Record<string, string> = {
    OWNER: 'Dono',
    MANAGER: 'Gestor',
    TECHNICIAN: 'Técnico',
    DRIVER: 'Condutor',
    VIEWER: 'Consulta',
  };
  return p ? ` (${nomes[p] ?? p})` : '';
}

/**
 * A versão, e um clique para confirmar que é a última.
 *
 * <p>Quem suporta o sistema ao telefone precisa de duas coisas: saber que
 * versão a pessoa tem à frente, e poder dizer-lhe «clique aí» em vez de
 * explicar como se limpa a cache de um browser.
 */
function CelulaVersao() {
  const [aVerificar, setAVerificar] = useState(false);

  const verificar = async () => {
    setAVerificar(true);
    const resultado = await verificarActualizacao();
    setAVerificar(false);
    if (resultado === true) {
      notifications.show({
        color: 'yellow',
        title: 'Versão nova disponível',
        message: 'Toque em «Actualizar» na barra em baixo, ou aqui para recarregar já.',
        onClick: aplicarActualizacao,
      });
    } else if (resultado === false) {
      notifications.show({ color: 'green', message: 'Já está na versão mais recente.' });
    } else {
      notifications.show({
        color: 'gray',
        message: 'Não foi possível verificar agora (sem rede ou em desenvolvimento).',
      });
    }
  };

  return (
    <Tooltip label="Clique para verificar se há versão nova" withArrow>
      <UnstyledButton className="barra-rodape-celula" onClick={() => void verificar()}>
        <span className="barra-rodape-rotulo">Versão</span>
        <Text span size="xs" fw={600} style={{ color: 'inherit' }}>
          {aVerificar ? 'a verificar…' : VERSAO}
        </Text>
      </UnstyledButton>
    </Tooltip>
  );
}

function Celula({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="barra-rodape-celula">
      <span className="barra-rodape-rotulo">{rotulo}</span>
      <Text span size="xs" fw={600} style={{ color: 'inherit' }}>
        {valor}
      </Text>
    </div>
  );
}
