/**
 * Peças de um ERP clássico, na pele MARCA.
 *
 * <p>Existem para que os ecrãs não repitam a mesma moldura vinte vezes e para
 * que mudar o aspeto do sistema seja mudar um ficheiro, não vinte.
 *
 * <p>A regra de cor mantém-se do tema: o dourado veste a moldura e o que está
 * selecionado; nunca veste estado. Numa frota, verde/laranja/vermelho já
 * significam operacional/atenção/parado, e pintar a marca por cima disso
 * tornaria o ecrã ilegível ao fim de duas linhas.
 */
import { Group, Text } from '@mantine/core';
import type { ReactNode } from 'react';

/**
 * Painel com barra de título — a janela de um ERP.
 *
 * <p>`acoes` é a barra de ferramentas: fica logo abaixo do título, que é onde
 * um utilizador de ERP a procura, e não espalhada pelo ecrã.
 */
export function Painel({
  titulo,
  acoes,
  rodape,
  children,
  semPadding,
}: {
  titulo: string;
  acoes?: ReactNode;
  rodape?: ReactNode;
  children: ReactNode;
  semPadding?: boolean;
}) {
  return (
    <div className="painel-vidro" style={{ borderRadius: 2 }}>
      <div
        className="painel-vidro-titulo"
        style={{
          color: '#fff',
          padding: '6px 10px',
          fontFamily: 'var(--erp-condensada)',
          fontSize: 14,
          fontWeight: 600,
          letterSpacing: '0.07em',
          textTransform: 'uppercase',
          // O fio âmbar por baixo repete a faixa do cabeçalho: o mesmo sinal
          // a dizer «aqui começa uma zona de trabalho».
          borderBottom: '2px solid var(--erp-dourado)',
        }}
      >
        {titulo}
      </div>

      {acoes && (
        <div
          className="painel-vidro-barra"
          style={{
            display: 'flex',
            gap: 2,
            alignItems: 'center',
            flexWrap: 'wrap',
            padding: '4px 6px',
          }}
        >
          {acoes}
        </div>
      )}

      <div className="painel-corpo" style={{ padding: semPadding ? 0 : 10 }}>
        {children}
      </div>

      {rodape && (
        <div
          style={{
            padding: '3px 10px',
            fontSize: 11,
            color: 'var(--erp-texto-suave)',
            background: 'var(--erp-superficie)',
            borderTop: '1px solid var(--erp-moldura)',
          }}
        >
          {rodape}
        </div>
      )}
    </div>
  );
}

/** Botão da barra de ferramentas: liso até lhe passarem por cima. */
export function BotaoBarra({
  children,
  onClick,
  icone,
  destaque,
  desativado,
  titulo,
}: {
  children: ReactNode;
  onClick?: () => void;
  icone?: ReactNode;
  destaque?: boolean;
  desativado?: boolean;
  titulo?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={desativado}
      title={destaque ? (titulo ? titulo + ' (F2)' : 'F2') : titulo}
      // O botão principal da barra é o que o F2 carrega.
      data-atalho={destaque ? 'novo' : undefined}
      style={{
        font: 'inherit',
        display: 'inline-flex',
        alignItems: 'center',
        gap: 5,
        padding: '3px 10px',
        // Sobre âmbar o texto é preto: branco sobre #F5A800 fica muito abaixo
        // do mínimo de contraste, e um botão principal que se lê mal carrega-se
        // por engano.
        color: desativado ? '#a1a1aa' : destaque ? 'var(--erp-grafite)' : 'var(--erp-texto)',
        background: destaque ? 'var(--erp-dourado)' : 'rgba(255,255,255,0.45)',
        backdropFilter: destaque ? undefined : 'blur(8px) saturate(130%)',
        WebkitBackdropFilter: destaque ? undefined : 'blur(8px) saturate(130%)',
        boxShadow: destaque
          ? 'inset 0 1px 0 rgba(255,255,255,0.45), 0 1px 6px rgba(245,168,0,0.35)'
          : 'inset 0 1px 0 rgba(255,255,255,0.5)',
        border: `1px solid ${destaque ? 'var(--erp-dourado)' : 'rgba(140,140,150,0.28)'}`,
        fontFamily: 'var(--erp-condensada)',
        textTransform: 'uppercase',
        fontWeight: destaque ? 700 : 600,
        letterSpacing: '0.04em',
        fontSize: 13,
        borderRadius: 2,
        cursor: desativado ? 'not-allowed' : 'pointer',
      }}
      onMouseEnter={(e) => {
        if (desativado || destaque) return;
        e.currentTarget.style.background = '#fff';
        e.currentTarget.style.borderColor = 'var(--erp-moldura)';
      }}
      onMouseLeave={(e) => {
        if (desativado || destaque) return;
        e.currentTarget.style.background = 'rgba(255,255,255,0.45)';
        e.currentTarget.style.borderColor = 'rgba(140,140,150,0.28)';
      }}
    >
      {icone}
      {children}
    </button>
  );
}

/** Separador vertical entre grupos de botões. */
export function SeparadorBarra() {
  return (
    <div
      style={{
        width: 1,
        height: 18,
        background: 'var(--erp-moldura)',
        margin: '0 4px',
      }}
    />
  );
}

/**
 * Secção de um formulário — o agrupamento que falta a um formulário longo.
 *
 * <p>Trinta campos seguidos são ilegíveis. Os mesmos trinta em seis grupos com
 * nome são um formulário que se preenche sem medo de falhar alguma coisa.
 */
export function SeccaoForm({
  titulo,
  descricao,
  children,
}: {
  titulo: string;
  descricao?: string;
  children: ReactNode;
}) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div
        style={{
          borderBottom: '2px solid var(--erp-dourado)',
          paddingBottom: 3,
          marginBottom: 8,
        }}
      >
        <Text size="xs" fw={700} tt="uppercase" style={{ letterSpacing: '0.04em' }}>
          {titulo}
        </Text>
        {descricao && (
          <Text size="xs" c="dimmed" mt={1}>
            {descricao}
          </Text>
        )}
      </div>
      {children}
    </div>
  );
}

/** Barra de estado do rodapé: contagens e totais, como num ERP. */
export function BarraEstado({ itens }: { itens: { rotulo: string; valor: ReactNode }[] }) {
  return (
    <Group gap="lg" wrap="wrap">
      {itens.map((i) => (
        <Text key={i.rotulo} size="xs" c="dimmed">
          {i.rotulo}: <b style={{ color: 'var(--erp-texto)' }}>{i.valor}</b>
        </Text>
      ))}
    </Group>
  );
}

/** Ponto de cor que antecede um rótulo de estado. A cor ajuda; o texto decide. */
export function Ponto({ cor }: { cor: string }) {
  return (
    <span
      style={{
        display: 'inline-block',
        width: 7,
        height: 7,
        borderRadius: 2,
        background: cor,
        marginRight: 6,
        verticalAlign: 'middle',
      }}
    />
  );
}

/** Cores de estado das ordens de manutenção, partilhadas entre ecrãs. */
export const COR_ESTADO_OM: Record<string, string> = {
  OPEN: '#2563eb',
  PLANNED: '#0891b2',
  DIAGNOSIS: '#7c3aed',
  QUOTING: '#a16207',
  AWAITING_APPROVAL: '#ea580c',
  APPROVED: '#0d9488',
  IN_PROGRESS: '#ca8a04',
  AWAITING_PARTS: '#c2410c',
  TESTING: '#0284c7',
  DONE: '#16a34a',
  VERIFIED: '#059669',
  CLOSED: '#4b5563',
  REJECTED: '#dc2626',
  CANCELLED: '#9ca3af',
};

export const COR_PRIORIDADE_OM: Record<string, string> = {
  LOW: '#6b7280',
  NORMAL: '#2563eb',
  HIGH: '#ea580c',
  URGENT: '#dc2626',
};
