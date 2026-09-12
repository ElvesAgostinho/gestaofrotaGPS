import { Card, Group, Text, Tooltip } from '@mantine/core';
import { IconInfoCircle } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { MARCA } from '../theme';

/**
 * Um número que vale por si.
 *
 * <p>Um valor único — dinheiro por explicar, litros em risco, ordens fora de
 * prazo — não é um gráfico. Desenhá-lo como barra ou como anel gasta espaço
 * para dizer o mesmo com menos precisão.
 *
 * <p>A cor só entra quando <b>significa estado</b>. Um total de litros é
 * neutro; dinheiro por explicar é vermelho porque exige resposta. Pintar tudo
 * com a cor da marca faria com que nada saltasse à vista.
 */
export function Kpi({
  label,
  value,
  unit,
  hint,
  tone = 'neutral',
  footnote,
}: {
  label: string;
  value: ReactNode;
  unit?: string;
  /** Explicação do que o número quer dizer, para quem não o conhece. */
  hint?: string;
  tone?: 'neutral' | 'good' | 'warning' | 'critical' | 'brand';
  footnote?: ReactNode;
}) {
  const color = TONES[tone];

  return (
    <Card p="md" style={{ flex: '1 1 180px', minWidth: 170 }}>
      <Group gap={6} mb={2} wrap="nowrap">
        <Text size="11px" fw={700} c="dimmed" tt="uppercase" style={{ letterSpacing: '0.05em' }}>
          {label}
        </Text>
        {hint && (
          <Tooltip label={hint} multiline w={260} withArrow>
            <IconInfoCircle size={13} style={{ color: '#a1a1aa', flexShrink: 0 }} />
          </Tooltip>
        )}
      </Group>

      <Group gap={5} align="baseline" wrap="nowrap">
        <Text
          fw={700}
          style={{ fontSize: 26, lineHeight: 1.1, color, fontVariantNumeric: 'tabular-nums' }}
        >
          {value}
        </Text>
        {unit && (
          <Text size="sm" c="dimmed" fw={500}>
            {unit}
          </Text>
        )}
      </Group>

      {footnote && (
        <Text size="xs" c="dimmed" mt={6} lh={1.35}>
          {footnote}
        </Text>
      )}
    </Card>
  );
}

/**
 * Vermelho, laranja e verde querem dizer estado e mais nada. O dourado da marca
 * é para destaque sem juízo de valor — um total, uma contagem.
 */
const TONES = {
  neutral: '#27272a',
  good: '#1a7f4b',
  warning: '#b45309',
  critical: '#b42318',
  brand: MARCA.gold,
} as const;

/**
 * Barra de magnitude dentro de uma tabela.
 *
 * <p>Uma linha de números ordenada já diz quem gasta mais; a barra dá a
 * proporção sem obrigar a ler tudo. Fica atrás do texto, em cinzento, porque o
 * número é que é o dado — a barra é só a forma de o comparar de relance.
 */
export function MagnitudeBar({
  value,
  max,
  tone = 'neutral',
}: {
  value: number;
  max: number;
  tone?: 'neutral' | 'critical';
}) {
  const pct = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0;
  return (
    <div
      aria-hidden
      style={{
        height: 4,
        borderRadius: 2,
        background: '#e4e4e7',
        overflow: 'hidden',
        marginTop: 4,
      }}
    >
      <div
        style={{
          width: `${pct}%`,
          height: '100%',
          borderRadius: 2,
          background: tone === 'critical' ? '#b42318' : MARCA.gold,
        }}
      />
    </div>
  );
}

/** Cabeçalho de página: título, uma linha a dizer para que serve, e ações. */
export function PageHeader({
  title,
  subtitle,
  actions,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <Group justify="space-between" align="flex-start" wrap="nowrap">
      {/* O título assenta numa barra âmbar, como a chapa de um equipamento.
          É o que dá ao ecrã o registo industrial sem encher de decoração. */}
      <div style={{ borderLeft: '4px solid var(--erp-dourado)', paddingLeft: 10 }}>
        <Text
          component="h1"
          fw={700}
          style={{
            fontFamily: '"Barlow Condensed", Barlow, sans-serif',
            fontSize: 27,
            lineHeight: 1.1,
            margin: 0,
            textTransform: 'uppercase',
            letterSpacing: '0.02em',
          }}
        >
          {title}
        </Text>
        {subtitle && (
          <Text c="dimmed" size="sm" mt={1}>
            {subtitle}
          </Text>
        )}
      </div>
      {actions}
    </Group>
  );
}
