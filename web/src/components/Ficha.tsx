import { Group, Text } from '@mantine/core';
import type { ReactNode } from 'react';

/**
 * A ficha de um ERP: cabeçalho fixo com a identidade do registo, a barra de
 * ferramentas com as ações, e os campos em grelha densa por baixo.
 *
 * <p>Num modal cria-se; numa ficha trabalha-se. Quem abre um camião quer ver
 * de relance a etiqueta, o estado, o medidor, a filial — e ter «Nova ordem» e
 * «Imprimir» à mão sem procurar. Um cartão bonito com a foto grande e os
 * dados espalhados é um sítio bonito, não uma ferramenta.
 */

export function CabecalhoFicha({
  imagem,
  identificador,
  titulo,
  subtitulo,
  etiquetas,
  acoes,
}: {
  /** Chapa à esquerda: fotografia ou silhueta. */
  imagem?: ReactNode;
  /** O que identifica o registo: a etiqueta, o número do documento. */
  identificador: string;
  titulo: string;
  subtitulo?: ReactNode;
  /** Estados, criticidade — o que se lê antes de ler o resto. */
  etiquetas?: ReactNode;
  /** A barra de ferramentas da ficha. */
  acoes?: ReactNode;
}) {
  return (
    <div className="painel-vidro" style={{ borderRadius: 2 }}>
      <div
        className="painel-vidro-titulo"
        style={{
          display: 'flex',
          alignItems: 'stretch',
          gap: 14,
          padding: 0,
          borderBottom: '2px solid var(--erp-dourado)',
        }}
      >
        {imagem && (
          <div
            style={{
              width: 118,
              minHeight: 74,
              flexShrink: 0,
              background: '#000',
              borderRight: '2px solid var(--erp-dourado)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              overflow: 'hidden',
              color: 'var(--erp-dourado)',
            }}
          >
            {imagem}
          </div>
        )}
        <div style={{ flex: 1, minWidth: 0, padding: '8px 12px 8px 0' }}>
          <Group gap="sm" align="baseline" wrap="wrap">
            <span
              style={{
                fontFamily: 'var(--erp-condensada)',
                fontSize: 26,
                fontWeight: 700,
                letterSpacing: '0.03em',
                color: 'var(--erp-dourado)',
                lineHeight: 1.1,
              }}
            >
              {identificador}
            </span>
            <span style={{ fontSize: 15, fontWeight: 600, color: '#fff' }}>{titulo}</span>
            {etiquetas}
          </Group>
          {subtitulo && (
            <div
              style={{
                marginTop: 2,
                fontFamily: 'var(--erp-condensada)',
                fontSize: 12.5,
                letterSpacing: '0.06em',
                textTransform: 'uppercase',
                color: 'rgba(255,255,255,0.72)',
              }}
            >
              {subtitulo}
            </div>
          )}
        </div>
      </div>

      {acoes && (
        <div
          className="painel-vidro-barra"
          style={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', padding: '4px 6px' }}
        >
          {acoes}
        </div>
      )}
    </div>
  );
}

/**
 * Campos de leitura em grelha densa: rótulo pequeno em cima, valor por baixo,
 * separados por fios. É o bloco «Dados gerais» de qualquer ficha de ERP.
 */
export function CamposFicha({
  campos,
  colunas = 4,
}: {
  campos: { rotulo: string; valor: ReactNode; largura?: number }[];
  colunas?: number;
}) {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${colunas}, minmax(0, 1fr))`,
        background: '#fff',
        border: '1px solid var(--erp-moldura)',
      }}
    >
      {campos.map((c, i) => (
        <div
          key={c.rotulo + i}
          style={{
            gridColumn: c.largura ? `span ${c.largura}` : undefined,
            padding: '5px 10px',
            borderRight: '1px solid var(--erp-moldura-suave)',
            borderBottom: '1px solid var(--erp-moldura-suave)',
            minWidth: 0,
          }}
        >
          <Text
            size="xs"
            c="dimmed"
            fw={700}
            style={{
              fontFamily: 'var(--erp-condensada)',
              textTransform: 'uppercase',
              letterSpacing: '0.06em',
              fontSize: 10.5,
            }}
          >
            {c.rotulo}
          </Text>
          <div style={{ fontSize: 13.5, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {c.valor == null || c.valor === '' ? <span style={{ color: '#9a9aa0' }}>—</span> : c.valor}
          </div>
        </div>
      ))}
    </div>
  );
}
