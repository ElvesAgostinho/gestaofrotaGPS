/**
 * As peças de que a aplicação do motorista é feita.
 *
 * <p>Cartões escuros de canto arredondado, barras de título em âmbar, botões
 * altos. São poucas e repetem-se em todos os ecrãs — é isso que faz uma
 * aplicação parecer uma aplicação, e não um site encolhido.
 */
import { UnstyledButton } from '@mantine/core';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { AMBAR, BRANCO, CINZA, PRETO, PRETO_CARTAO, PRETO_LINHA, TITULO, TOQUE } from './tema';

/** Um cartão escuro. Com `to`, é um cartão em que se carrega. */
export function Cartao({
  children,
  to,
  onClick,
  destaque,
  padding = 14,
}: {
  children: ReactNode;
  to?: string;
  onClick?: () => void;
  /** Contorno âmbar: para o que exige acção agora. */
  destaque?: boolean;
  padding?: number;
}) {
  const estilo: React.CSSProperties = {
    display: 'block',
    width: '100%',
    background: PRETO_CARTAO,
    border: `1px solid ${destaque ? AMBAR : PRETO_LINHA}`,
    borderRadius: 14,
    padding,
    color: BRANCO,
    textAlign: 'left',
  };
  if (to) {
    return (
      <UnstyledButton component={Link} to={to} style={estilo}>
        {children}
      </UnstyledButton>
    );
  }
  if (onClick) {
    return (
      <UnstyledButton onClick={onClick} style={estilo}>
        {children}
      </UnstyledButton>
    );
  }
  return <div style={estilo}>{children}</div>;
}

/** O título de uma secção: pequeno, em maiúsculas, discreto. */
export function Seccao({ children, direita }: { children: ReactNode; direita?: ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
      <span
        style={{
          fontFamily: TITULO,
          fontSize: 14,
          fontWeight: 700,
          letterSpacing: '0.08em',
          textTransform: 'uppercase',
          color: CINZA,
        }}
      >
        {children}
      </span>
      {direita}
    </div>
  );
}

/** Um número grande, com a sua legenda por baixo. */
export function Numero({ valor, legenda, cor = BRANCO }: { valor: string; legenda: string; cor?: string }) {
  // «128 400 km» não pode partir ao meio: um número em duas linhas deixa de se
  // ler de relance, que é a única maneira como isto vai ser lido.
  const tamanho = valor.length > 12 ? 19 : valor.length > 8 ? 23 : 30;
  return (
    <div style={{ minWidth: 0 }}>
      <div
        style={{
          fontFamily: TITULO,
          fontSize: tamanho,
          fontWeight: 700,
          lineHeight: 1.05,
          color: cor,
          whiteSpace: 'nowrap',
        }}
      >
        {valor}
      </div>
      <div style={{ fontSize: 11, color: CINZA, textTransform: 'uppercase', letterSpacing: '0.06em', marginTop: 2 }}>
        {legenda}
      </div>
    </div>
  );
}

/** Uma etiqueta de estado, sempre com palavra — nunca só cor. */
export function Etiqueta({ cor, children }: { cor: string; children: ReactNode }) {
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 5,
        background: `${cor}22`,
        color: cor,
        border: `1px solid ${cor}55`,
        borderRadius: 999,
        padding: '2px 9px',
        fontSize: 11,
        fontWeight: 600,
      }}
    >
      <span style={{ width: 6, height: 6, borderRadius: '50%', background: cor }} />
      {children}
    </span>
  );
}

/** O botão grande de acção. O âmbar é o que se faz agora. */
export function BotaoGrande({
  children,
  to,
  onClick,
  icone,
  cor = AMBAR,
  disabled,
}: {
  children: ReactNode;
  to?: string;
  onClick?: () => void;
  icone?: ReactNode;
  cor?: string;
  disabled?: boolean;
}) {
  const estilo: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 10,
    width: '100%',
    minHeight: TOQUE,
    borderRadius: 12,
    background: disabled ? PRETO_LINHA : cor,
    color: disabled ? CINZA : PRETO,
    fontWeight: 700,
    fontSize: 16,
    letterSpacing: '0.01em',
    border: 'none',
  };
  if (to && !disabled) {
    return (
      <UnstyledButton component={Link} to={to} style={estilo}>
        {icone}
        {children}
      </UnstyledButton>
    );
  }
  return (
    <UnstyledButton onClick={disabled ? undefined : onClick} style={estilo} disabled={disabled}>
      {icone}
      {children}
    </UnstyledButton>
  );
}

/** Uma linha «rótulo → valor», como nas fichas do sistema. */
export function Linha({ rotulo, valor }: { rotulo: string; valor: ReactNode }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'space-between',
        gap: 12,
        padding: '7px 0',
        borderBottom: `1px solid ${PRETO_LINHA}`,
      }}
    >
      <span style={{ color: CINZA, fontSize: 13 }}>{rotulo}</span>
      <span style={{ fontSize: 14, fontWeight: 600, textAlign: 'right' }}>{valor}</span>
    </div>
  );
}

/** O fundo de toda a aplicação. */
export const FUNDO: React.CSSProperties = { background: PRETO, color: BRANCO };
