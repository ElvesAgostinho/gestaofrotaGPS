const nf = new Intl.NumberFormat('pt-PT');
const cf = new Intl.NumberFormat('pt-PT', {
  style: 'currency',
  currency: 'AOA',
  maximumFractionDigits: 0,
});
const df = new Intl.DateTimeFormat('pt-PT', { dateStyle: 'medium' });
const dtf = new Intl.DateTimeFormat('pt-PT', { dateStyle: 'medium', timeStyle: 'short' });

/**
 * Número em português. `decimals` fixa as casas — útil em tabelas, onde
 * valores com número de casas diferente deixam de alinhar na vertical e
 * obrigam a ler cada linha em vez de comparar de relance.
 */
export const fmtNumber = (n: number | null | undefined, decimals?: number) => {
  if (n == null) return '—';
  if (decimals == null) return nf.format(n);
  return new Intl.NumberFormat('pt-PT', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(n);
};

export const fmtMoney = (n: number | null | undefined) =>
  n == null ? '—' : cf.format(n).replace('AOA', 'Kz');

export const fmtDate = (s: string | null | undefined) =>
  s ? df.format(new Date(s)) : '—';

export const fmtDateTime = (s: string | null | undefined) =>
  s ? dtf.format(new Date(s)) : '—';

export const fmtBytes = (n: number) => {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
};

export const meterLabel = (kind: string) =>
  kind === 'HOURMETER' ? 'Horímetro' : kind === 'ODOMETER' ? 'Hodómetro' : kind;

export const statusLabel: Record<string, string> = {
  OPERATIONAL: 'Operacional',
  MAINTENANCE: 'Em manutenção',
  DOWN: 'Parado',
  STANDBY: 'Em espera',
  RETIRED: 'Abatido',
};

export const photoKindLabel: Record<string, string> = {
  GENERAL: 'Geral',
  PLATE: 'Matrícula',
  DAMAGE: 'Avaria / dano',
  DOCUMENT: 'Documento',
  METER: 'Medidor',
};
