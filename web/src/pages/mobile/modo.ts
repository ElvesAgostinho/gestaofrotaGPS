import type { Role } from '../../auth/AuthContext';

/** Onde se guarda a escolha «telemóvel» / «versão completa» deste aparelho. */
export const MODO_KEY = 'imbondeiro.modo';

/**
 * Quem deve cair na app do telemóvel ao entrar: o motorista sempre (o resto
 * do sistema não é para ele); o mecânico quando está num ecrã pequeno. A
 * escolha explícita da pessoa («Versão completa» / «Modo telemóvel») ganha.
 */
export function prefereTelemovel(role?: Role): boolean {
  let escolha: string | null = null;
  try {
    escolha = localStorage.getItem(MODO_KEY);
  } catch {
    escolha = null;
  }
  if (escolha === 'desktop') return false;
  if (escolha === 'mobile') return true;
  if (role === 'DRIVER') return true;
  if (role === 'TECHNICIAN') return window.matchMedia('(max-width: 768px)').matches;
  return false;
}
